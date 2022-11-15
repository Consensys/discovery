/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.pipeline.handler;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.annotations.VisibleForTesting;
import java.net.InetSocketAddress;
import java.security.SecureRandom;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.crypto.SECP256K1.SecretKey;
import org.ethereum.beacon.discovery.pipeline.Envelope;
import org.ethereum.beacon.discovery.pipeline.EnvelopeHandler;
import org.ethereum.beacon.discovery.pipeline.Field;
import org.ethereum.beacon.discovery.pipeline.HandlerUtil;
import org.ethereum.beacon.discovery.pipeline.Pipeline;
import org.ethereum.beacon.discovery.scheduler.ExpirationScheduler;
import org.ethereum.beacon.discovery.scheduler.ExpirationSchedulerFactory;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeSession;
import org.ethereum.beacon.discovery.storage.KBuckets;
import org.ethereum.beacon.discovery.storage.LocalNodeRecordStore;
import org.ethereum.beacon.discovery.type.Bytes12;
import org.ethereum.beacon.discovery.util.Functions;

/**
 * Performs {@link Field#SESSION_LOOKUP} request. Looks up for Node session based on NodeId, which
 * should be in request field and stores it in {@link Field#SESSION} field.
 */
public class NodeSessionManager implements EnvelopeHandler {
  private static final int SESSION_CLEANUP_DELAY_SECONDS = 180;
  private static final int REQUEST_CLEANUP_DELAY_SECONDS = 60;
  private static final Logger LOG = LogManager.getLogger(NodeSessionManager.class);
  private final LocalNodeRecordStore localNodeRecordStore;
  private final SecretKey staticNodeKey;
  private final KBuckets nodeBucketStorage;
  private final Map<SessionKey, NodeSession> recentSessions = new ConcurrentHashMap<>();
  private final Map<Bytes12, NodeSession> lastNonceToSession = new ConcurrentHashMap<>();
  private final Pipeline outgoingPipeline;
  private final ExpirationScheduler<SessionKey> sessionExpirationScheduler;
  private final ExpirationScheduler<Bytes> requestExpirationScheduler;

  public NodeSessionManager(
      final LocalNodeRecordStore localNodeRecordStore,
      final SecretKey staticNodeKey,
      final KBuckets nodeBucketStorage,
      final Pipeline outgoingPipeline,
      final ExpirationSchedulerFactory expirationSchedulerFactory) {
    this.localNodeRecordStore = localNodeRecordStore;
    this.staticNodeKey = staticNodeKey;
    this.nodeBucketStorage = nodeBucketStorage;
    this.outgoingPipeline = outgoingPipeline;
    this.sessionExpirationScheduler =
        expirationSchedulerFactory.create(SESSION_CLEANUP_DELAY_SECONDS, TimeUnit.SECONDS);
    this.requestExpirationScheduler =
        expirationSchedulerFactory.create(REQUEST_CLEANUP_DELAY_SECONDS, TimeUnit.SECONDS);
  }

  @Override
  public void handle(final Envelope envelope) {
    if (!HandlerUtil.requireField(Field.SESSION_LOOKUP, envelope)) {
      return;
    }
    if (envelope.contains(Field.SESSION)) {
      return;
    }
    LOG.trace(
        "Envelope {} in NodeSessionManager, requirements are satisfied!", envelope.getIdString());

    SessionLookup sessionRequest = envelope.get(Field.SESSION_LOOKUP);
    envelope.remove(Field.SESSION_LOOKUP);
    LOG.trace(
        "Envelope {}: Session lookup requested for nodeId {}",
        envelope.getIdString(),
        sessionRequest);

    getOrCreateSession(sessionRequest, envelope)
        .ifPresentOrElse(
            nodeSession -> {
              envelope.put(Field.SESSION, nodeSession);
              LOG.trace(
                  "Session resolved: {} in envelope #{}", nodeSession, envelope.getIdString());
            },
            () ->
                LOG.trace(
                    "Session could not be resolved or created for {}", sessionRequest.getNodeId()));
  }

  private Optional<NodeSession> getOrCreateSession(
      final SessionLookup sessionLookup, final Envelope envelope) {
    return getRemoteSocketAddress(envelope)
        .map(
            remoteSocketAddress -> {
              SessionKey sessionKey =
                  new SessionKey(sessionLookup.getNodeId(), remoteSocketAddress);
              NodeSession context =
                  recentSessions.computeIfAbsent(
                      sessionKey,
                      existingSessionKey ->
                          createNodeSession(existingSessionKey, sessionLookup.getNodeRecord()));

              sessionExpirationScheduler.put(sessionKey, () -> deleteSession(sessionKey));
              return context;
            });
  }

  public void dropSession(final NodeSession session) {
    SessionKey sessionKey = new SessionKey(session.getNodeId(), session.getRemoteAddress());
    sessionExpirationScheduler.cancel(sessionKey);
    deleteSession(sessionKey);
  }

  private void deleteSession(final SessionKey sessionKey) {
    NodeSession removedSession = recentSessions.remove(sessionKey);
    if (removedSession != null) {
      // Mark inactive to prevent registering any new nonces
      removedSession.markInactive();
      // And then clean up the last recorded nonce, if any
      removedSession.getLastOutboundNonce().ifPresent(lastNonceToSession::remove);
    }
  }

  @VisibleForTesting
  public Optional<NodeSession> getNodeSession(final Bytes nodeId) {
    return recentSessions.entrySet().stream()
        .filter(e -> e.getKey().nodeId.equals(nodeId))
        .map(Map.Entry::getValue)
        .findFirst();
  }

  public Optional<NodeSession> getNodeSessionByLastOutboundNonce(final Bytes12 nonce) {
    return Optional.ofNullable(lastNonceToSession.get(nonce));
  }

  public void onSessionLastNonceUpdate(
      final NodeSession session, final Optional<Bytes12> previousNonce, final Bytes12 newNonce) {
    previousNonce.ifPresent(lastNonceToSession::remove);
    lastNonceToSession.put(newNonce, session);
  }

  private NodeSession createNodeSession(
      final SessionKey key, final Optional<NodeRecord> suppliedNodeRecord) {
    Optional<NodeRecord> nodeRecord =
        suppliedNodeRecord.or(() -> nodeBucketStorage.getNode(key.nodeId));
    SecureRandom random = Functions.getRandom();
    return new NodeSession(
        key.nodeId,
        nodeRecord,
        key.remoteSocketAddress,
        this,
        localNodeRecordStore,
        staticNodeKey,
        nodeBucketStorage,
        outgoingPipeline::push,
        random,
        requestExpirationScheduler);
  }

  private Optional<InetSocketAddress> getRemoteSocketAddress(final Envelope envelope) {
    return Optional.ofNullable(envelope.get(Field.REMOTE_SENDER))
        .or(() -> envelope.get(Field.NODE).getUdpAddress());
  }

  public Stream<NodeRecord> streamActiveSessions() {
    return recentSessions.values().stream()
        .filter(NodeSession::isAuthenticated)
        .flatMap(session -> session.getNodeRecord().stream());
  }

  private static class SessionKey {
    private final Bytes nodeId;
    private final InetSocketAddress remoteSocketAddress;

    private SessionKey(final Bytes nodeId, final InetSocketAddress remoteSocketAddress) {
      checkNotNull(remoteSocketAddress);
      this.nodeId = nodeId;
      this.remoteSocketAddress = remoteSocketAddress;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final SessionKey that = (SessionKey) o;
      return Objects.equals(nodeId, that.nodeId)
          && Objects.equals(remoteSocketAddress, that.remoteSocketAddress);
    }

    @Override
    public int hashCode() {
      return Objects.hash(nodeId, remoteSocketAddress);
    }
  }
}
