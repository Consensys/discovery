/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.pipeline.handler;

import com.google.common.base.Preconditions;
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
import org.ethereum.beacon.discovery.network.NetworkParcelV5;
import org.ethereum.beacon.discovery.pipeline.Envelope;
import org.ethereum.beacon.discovery.pipeline.EnvelopeHandler;
import org.ethereum.beacon.discovery.pipeline.Field;
import org.ethereum.beacon.discovery.pipeline.HandlerUtil;
import org.ethereum.beacon.discovery.pipeline.Pipeline;
import org.ethereum.beacon.discovery.scheduler.ExpirationScheduler;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordInfo;
import org.ethereum.beacon.discovery.schema.NodeSession;
import org.ethereum.beacon.discovery.storage.AuthTagRepository;
import org.ethereum.beacon.discovery.storage.NodeBucketStorage;
import org.ethereum.beacon.discovery.storage.NodeTable;

/**
 * Performs {@link Field#SESSION_LOOKUP} request. Looks up for Node session based on NodeId, which
 * should be in request field and stores it in {@link Field#SESSION} field.
 */
public class NodeIdToSession implements EnvelopeHandler {
  private static final int CLEANUP_DELAY_SECONDS = 180;
  private static final Logger logger = LogManager.getLogger(NodeIdToSession.class);
  private final NodeRecord homeNodeRecord;
  private final Bytes staticNodeKey;
  private final NodeBucketStorage nodeBucketStorage;
  private final AuthTagRepository authTagRepo;
  private final Map<SessionKey, NodeSession> recentSessions = new ConcurrentHashMap<>();
  private final NodeTable nodeTable;
  private final Pipeline outgoingPipeline;
  private ExpirationScheduler<SessionKey> sessionExpirationScheduler =
      new ExpirationScheduler<>(CLEANUP_DELAY_SECONDS, TimeUnit.SECONDS);

  public NodeIdToSession(
      NodeRecord homeNodeRecord,
      Bytes staticNodeKey,
      NodeBucketStorage nodeBucketStorage,
      AuthTagRepository authTagRepo,
      NodeTable nodeTable,
      Pipeline outgoingPipeline) {
    this.homeNodeRecord = homeNodeRecord;
    this.staticNodeKey = staticNodeKey;
    this.nodeBucketStorage = nodeBucketStorage;
    this.authTagRepo = authTagRepo;
    this.nodeTable = nodeTable;
    this.outgoingPipeline = outgoingPipeline;
  }

  @Override
  public void handle(Envelope envelope) {
    logger.trace(
        "Envelope {} in NodeIdToSession, checking requirements satisfaction", envelope.getId());
    if (!HandlerUtil.requireField(Field.SESSION_LOOKUP, envelope)) {
      return;
    }
    logger.trace("Envelope {} in NodeIdToSession, requirements are satisfied!", envelope.getId());

    SessionLookup sessionRequest = (SessionLookup) envelope.get(Field.SESSION_LOOKUP);
    envelope.remove(Field.SESSION_LOOKUP);
    logger.trace(
        "Envelope {}: Session lookup requested for nodeId {}",
        envelope.getId(),
        sessionRequest.getNodeId());
    NodeSession nodeSession = getOrCreateSession(sessionRequest.getNodeId(), envelope);
    envelope.put(Field.SESSION, nodeSession);
    logger.trace("Session resolved: {} in envelope #{}", nodeSession, envelope.getId());
  }

  private NodeSession getOrCreateSession(Bytes nodeId, Envelope envelope) {
    SessionKey sessionKey = SessionKey.create(nodeId, envelope);
    NodeSession context =
        recentSessions.computeIfAbsent(sessionKey, key -> createNodeSession(nodeId, envelope));

    sessionExpirationScheduler.put(
        sessionKey,
        () -> {
          recentSessions.remove(sessionKey);
          context.cleanup();
        });
    return context;
  }

  private NodeSession createNodeSession(final Bytes nodeId, final Envelope envelope) {
    final InetSocketAddress remoteSocketAddress = getRemoteSocketAddress(envelope);
    Optional<NodeRecord> nodeRecord = nodeTable.getNode(nodeId).map(NodeRecordInfo::getNode);
    SecureRandom random = new SecureRandom();
    return new NodeSession(
        nodeId,
        nodeRecord,
        homeNodeRecord,
        staticNodeKey,
        nodeTable,
        nodeBucketStorage,
        authTagRepo,
        packet ->
            outgoingPipeline.push(
                new NetworkParcelV5(packet, nodeRecord, Optional.ofNullable(remoteSocketAddress))),
        random);
  }

  private InetSocketAddress getRemoteSocketAddress(final Envelope envelope) {
    return (InetSocketAddress) envelope.get(Field.REMOTE_SENDER);
  }

  private static class SessionKey {
    private final Bytes nodeId;
    private final InetSocketAddress remoteSender;

    private SessionKey(final Bytes nodeId, final InetSocketAddress remoteSender) {
      Preconditions.checkNotNull(remoteSender);
      this.nodeId = nodeId;
      this.remoteSender = remoteSender;
    }

    public static SessionKey create(final Bytes nodeId, final Envelope envelope) {
      final InetSocketAddress inetSocketAddress =
          Optional.ofNullable((InetSocketAddress) envelope.get(Field.REMOTE_SENDER))
              .or(() -> ((NodeRecord) envelope.get(Field.NODE)).getUdpAddress())
              .orElse(null);
      return new SessionKey(nodeId, inetSocketAddress);
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
      return Objects.equals(nodeId, that.nodeId) && Objects.equals(remoteSender, that.remoteSender);
    }

    @Override
    public int hashCode() {
      return Objects.hash(nodeId, remoteSender);
    }
  }
}
