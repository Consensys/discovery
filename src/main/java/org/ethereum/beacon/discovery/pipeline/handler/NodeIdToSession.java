/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.pipeline.handler;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.security.SecureRandom;
import java.util.Map;
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
import org.javatuples.Pair;

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
  private final Map<Bytes, NodeSession> recentSessions =
      new ConcurrentHashMap<>(); // nodeId -> session
  private final NodeTable nodeTable;
  private final Pipeline outgoingPipeline;
  private ExpirationScheduler<Bytes> sessionExpirationScheduler =
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
  @SuppressWarnings({"unchecked", "rawtypes"})
  public void handle(Envelope envelope) {
    logger.trace(
        () ->
            String.format(
                "Envelope %s in NodeIdToSession, checking requirements satisfaction",
                envelope.getId()));
    if (!HandlerUtil.requireField(Field.SESSION_LOOKUP, envelope)) {
      return;
    }
    logger.trace(
        () ->
            String.format(
                "Envelope %s in NodeIdToSession, requirements are satisfied!", envelope.getId()));

    Pair<Bytes, Runnable> sessionRequest =
        (Pair<Bytes, Runnable>) envelope.get(Field.SESSION_LOOKUP);
    envelope.remove(Field.SESSION_LOOKUP);
    logger.trace(
        () ->
            String.format(
                "Envelope %s: Session lookup requested for nodeId %s",
                envelope.getId(), sessionRequest.getValue0()));
    Optional<NodeSession> nodeSessionOptional = getSession(sessionRequest.getValue0(), envelope);
    if (nodeSessionOptional.isPresent()) {
      envelope.put(Field.SESSION, nodeSessionOptional.get());
      logger.trace(
          () ->
              String.format(
                  "Session resolved: %s in envelope #%s",
                  nodeSessionOptional.get(), envelope.getId()));
    } else {
      logger.debug(
          () ->
              String.format(
                  "Envelope %s: Session not resolved for nodeId %s",
                  envelope.getId(), sessionRequest.getValue0()));
      sessionRequest.getValue1().run();
    }
  }

  private Optional<NodeSession> getSession(Bytes nodeId, Envelope envelope) {
    NodeSession context = recentSessions.get(nodeId);
    if (context == null) {
      final InetSocketAddress remoteSocketAddress = getRemoteSocketAddress(envelope);
      Optional<NodeRecord> nodeRecord = nodeTable.getNode(nodeId).map(NodeRecordInfo::getNode);
      //              .orElseGet(
      //                  () ->
      //                      NodeRecord.fromValues(
      //                          new WhoAreYouIdentitySchemaInterpreter(),
      //                          UInt64.ZERO,
      //                          List.of(
      //                              Pair.with(WhoAreYouIdentitySchemaInterpreter.NODE_ID_FIELD,
      // nodeId),
      //                              Pair.with(EnrField.ID, IdentitySchema.V4),
      //                              Pair.with(EnrField.IP_V4, getIpBytes(remoteSocketAddress)),
      //                              Pair.with(EnrField.UDP_V4, remoteSocketAddress.getPort()))));
      SecureRandom random = new SecureRandom();
      context =
          new NodeSession(
              nodeId,
              nodeRecord,
              homeNodeRecord,
              staticNodeKey,
              nodeTable,
              nodeBucketStorage,
              authTagRepo,
              packet ->
                  outgoingPipeline.push(
                      new NetworkParcelV5(
                          packet, nodeRecord, Optional.ofNullable(remoteSocketAddress))),
              random);
      recentSessions.put(nodeId, context);
    }

    final NodeSession contextBackup = context;
    sessionExpirationScheduler.put(
        nodeId,
        () -> {
          recentSessions.remove(nodeId);
          contextBackup.cleanup();
        });
    return Optional.of(context);
  }

  private Bytes getIpBytes(final InetSocketAddress remoteSocketAddress) {
    final InetAddress remoteIp = remoteSocketAddress.getAddress();
    Bytes addressBytes = Bytes.wrap(remoteIp.getAddress());
    return Bytes.concatenate(Bytes.wrap(new byte[4 - addressBytes.size()]), addressBytes);
  }

  private InetSocketAddress getRemoteSocketAddress(final Envelope envelope) {
    return (InetSocketAddress) envelope.get(Field.REMOTE_SENDER);
  }
}
