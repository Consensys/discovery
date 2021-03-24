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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.pipeline.Envelope;
import org.ethereum.beacon.discovery.pipeline.EnvelopeHandler;
import org.ethereum.beacon.discovery.pipeline.Field;
import org.ethereum.beacon.discovery.pipeline.HandlerUtil;
import org.ethereum.beacon.discovery.pipeline.Pipeline;
import org.ethereum.beacon.discovery.scheduler.ExpirationScheduler;
import org.ethereum.beacon.discovery.scheduler.ExpirationSchedulerFactory;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordInfo;
import org.ethereum.beacon.discovery.schema.NodeSession;
import org.ethereum.beacon.discovery.storage.LocalNodeRecordStore;
import org.ethereum.beacon.discovery.storage.NodeBucketStorage;
import org.ethereum.beacon.discovery.storage.NodeTable;
import org.ethereum.beacon.discovery.type.Bytes12;

/**
 * Performs {@link Field#SESSION_LOOKUP} request. Looks up for Node session based on NodeId, which
 * should be in request field and stores it in {@link Field#SESSION} field.
 */
public class NodeSessionManager implements EnvelopeHandler {
  private static final int SESSION_CLEANUP_DELAY_SECONDS = 180;
  private static final int REQUEST_CLEANUP_DELAY_SECONDS = 60;
  private static final Logger logger = LogManager.getLogger(NodeSessionManager.class);
  private final LocalNodeRecordStore localNodeRecordStore;
  private final Bytes staticNodeKey;
  private final NodeBucketStorage nodeBucketStorage;
  private final Map<SessionKey, NodeSession> recentSessions = new ConcurrentHashMap<>();
  private final Map<Bytes12, NodeSession> lastNonceToSession = new ConcurrentHashMap<>();
  private final NodeTable nodeTable;
  private final Pipeline outgoingPipeline;
  private final ExpirationScheduler<SessionKey> sessionExpirationScheduler;
  private final ExpirationScheduler<Bytes> requestExpirationScheduler;

  public NodeSessionManager(
      LocalNodeRecordStore localNodeRecordStore,
      Bytes staticNodeKey,
      NodeBucketStorage nodeBucketStorage,
      NodeTable nodeTable,
      Pipeline outgoingPipeline,
      ExpirationSchedulerFactory expirationSchedulerFactory) {
    this.localNodeRecordStore = localNodeRecordStore;
    this.staticNodeKey = staticNodeKey;
    this.nodeBucketStorage = nodeBucketStorage;
    this.nodeTable = nodeTable;
    this.outgoingPipeline = outgoingPipeline;
    this.sessionExpirationScheduler =
        expirationSchedulerFactory.create(SESSION_CLEANUP_DELAY_SECONDS, TimeUnit.SECONDS);
    this.requestExpirationScheduler =
        expirationSchedulerFactory.create(REQUEST_CLEANUP_DELAY_SECONDS, TimeUnit.SECONDS);
  }

  @Override
  public void handle(Envelope envelope) {
    if (!HandlerUtil.requireField(Field.SESSION_LOOKUP, envelope)) {
      return;
    }
    if (envelope.contains(Field.SESSION)) {
      return;
    }
    logger.trace("Envelope {} in NodeIdToSession, requirements are satisfied!", envelope.getId());

    SessionLookup sessionRequest = envelope.get(Field.SESSION_LOOKUP);
    envelope.remove(Field.SESSION_LOOKUP);
    logger.trace(
        "Envelope {}: Session lookup requested for nodeId {}", envelope.getId(), sessionRequest);

    getOrCreateSession(sessionRequest.getNodeId(), envelope)
        .ifPresentOrElse(
            nodeSession -> {
              envelope.put(Field.SESSION, nodeSession);
              logger.trace("Session resolved: {} in envelope #{}", nodeSession, envelope.getId());
            },
            () ->
                logger.trace(
                    "Session could not be resolved or created for {}", sessionRequest.getNodeId()));
  }

  private Optional<NodeSession> getOrCreateSession(Bytes nodeId, Envelope envelope) {
    return getRemoteSocketAddress(envelope)
        .map(
            remoteSocketAddress -> {
              SessionKey sessionKey = new SessionKey(nodeId, remoteSocketAddress);
              NodeSession context =
                  recentSessions.computeIfAbsent(sessionKey, this::createNodeSession);

              sessionExpirationScheduler.put(sessionKey, () -> deleteSession(sessionKey));
              return context;
            });
  }

  public void dropSession(NodeSession session) {
    SessionKey sessionKey = new SessionKey(session.getNodeId(), session.getRemoteAddress());
    sessionExpirationScheduler.cancel(sessionKey);
    deleteSession(sessionKey);
  }

  private void deleteSession(SessionKey sessionKey) {
    NodeSession removedSession = recentSessions.remove(sessionKey);
    if (removedSession != null) {
      removedSession.getLastOutboundNonce().ifPresent(lastNonceToSession::remove);
    }
  }

  @VisibleForTesting
  public Optional<NodeSession> getNodeSession(Bytes nodeId) {
    return recentSessions.entrySet().stream()
        .filter(e -> e.getKey().nodeId.equals(nodeId))
        .map(Map.Entry::getValue)
        .findFirst();
  }

  public Optional<NodeSession> getNodeSessionByLastOutboundNonce(Bytes12 nonce) {
    return Optional.ofNullable(lastNonceToSession.get(nonce));
  }

  public void onSessionLastNonceUpdate(
      NodeSession session, Optional<Bytes12> previousNonce, Bytes12 newNonce) {
    previousNonce.ifPresent(lastNonceToSession::remove);
    lastNonceToSession.put(newNonce, session);
  }

  private NodeSession createNodeSession(final SessionKey key) {
    Optional<NodeRecord> nodeRecord = nodeTable.getNode(key.nodeId).map(NodeRecordInfo::getNode);
    SecureRandom random = new SecureRandom();
    return new NodeSession(
        key.nodeId,
        nodeRecord,
        key.remoteSocketAddress,
        this,
        localNodeRecordStore,
        staticNodeKey,
        nodeTable,
        nodeBucketStorage,
        outgoingPipeline::push,
        random,
        requestExpirationScheduler);
  }

  private Optional<InetSocketAddress> getRemoteSocketAddress(final Envelope envelope) {
    return Optional.ofNullable(envelope.get(Field.REMOTE_SENDER))
        .or(() -> envelope.get(Field.NODE).getUdpAddress());
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
