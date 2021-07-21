/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.schema;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.ethereum.beacon.discovery.task.TaskStatus.AWAIT;
import static org.ethereum.beacon.discovery.task.TaskStatus.SENT;

import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.ethereum.beacon.discovery.message.V5Message;
import org.ethereum.beacon.discovery.network.NetworkParcel;
import org.ethereum.beacon.discovery.network.NetworkParcelV5;
import org.ethereum.beacon.discovery.packet.HandshakeMessagePacket;
import org.ethereum.beacon.discovery.packet.HandshakeMessagePacket.HandshakeAuthData;
import org.ethereum.beacon.discovery.packet.Header;
import org.ethereum.beacon.discovery.packet.OrdinaryMessagePacket;
import org.ethereum.beacon.discovery.packet.OrdinaryMessagePacket.OrdinaryAuthData;
import org.ethereum.beacon.discovery.packet.Packet;
import org.ethereum.beacon.discovery.packet.RawPacket;
import org.ethereum.beacon.discovery.packet.WhoAreYouPacket;
import org.ethereum.beacon.discovery.pipeline.handler.NodeSessionManager;
import org.ethereum.beacon.discovery.pipeline.info.Request;
import org.ethereum.beacon.discovery.pipeline.info.RequestInfo;
import org.ethereum.beacon.discovery.scheduler.ExpirationScheduler;
import org.ethereum.beacon.discovery.storage.KBuckets;
import org.ethereum.beacon.discovery.storage.LocalNodeRecordStore;
import org.ethereum.beacon.discovery.storage.NodeTable;
import org.ethereum.beacon.discovery.type.Bytes12;
import org.ethereum.beacon.discovery.type.Bytes16;
import org.ethereum.beacon.discovery.util.Functions;

/**
 * Stores session status and all keys for discovery message exchange between us, `homeNode` and the
 * other `node`
 */
public class NodeSession {
  private static final Logger logger = LogManager.getLogger(NodeSession.class);

  public static final int REQUEST_ID_SIZE = 8;
  private static final boolean IS_LIVENESS_UPDATE = true;
  private final Bytes32 homeNodeId;
  private final LocalNodeRecordStore localNodeRecordStore;
  private final NodeSessionManager nodeSessionManager;
  private final NodeTable nodeTable;
  private final KBuckets nodeBucketStorage;
  private final InetSocketAddress remoteAddress;
  private final Consumer<NetworkParcel> outgoingPipeline;
  private final Random rnd;
  private final Bytes nodeId;
  private Optional<NodeRecord> nodeRecord;
  private SessionState state = SessionState.INITIAL;
  private Bytes idNonce;
  private Bytes initiatorKey;
  private Bytes recipientKey;
  private final Map<Bytes, RequestInfo> requestIdStatuses = new ConcurrentHashMap<>();
  private final ExpirationScheduler<Bytes> requestExpirationScheduler;
  private final Bytes staticNodeKey;
  private Optional<InetSocketAddress> reportedExternalAddress = Optional.empty();
  private Optional<Bytes> whoAreYouChallenge = Optional.empty();
  private Optional<Bytes12> lastOutboundNonce = Optional.empty();
  private final Function<Random, Bytes12> nonceGenerator;

  public NodeSession(
      Bytes nodeId,
      Optional<NodeRecord> nodeRecord,
      InetSocketAddress remoteAddress,
      NodeSessionManager nodeSessionManager,
      LocalNodeRecordStore localNodeRecordStore,
      Bytes staticNodeKey,
      NodeTable nodeTable,
      KBuckets nodeBucketStorage,
      Consumer<NetworkParcel> outgoingPipeline,
      Random rnd,
      ExpirationScheduler<Bytes> requestExpirationScheduler) {
    this.nodeId = nodeId;
    this.nodeRecord = nodeRecord;
    this.remoteAddress = remoteAddress;
    this.localNodeRecordStore = localNodeRecordStore;
    this.nodeSessionManager = nodeSessionManager;
    this.nodeTable = nodeTable;
    this.nodeBucketStorage = nodeBucketStorage;
    this.staticNodeKey = staticNodeKey;
    this.homeNodeId = Bytes32.wrap(localNodeRecordStore.getLocalNodeRecord().getNodeId());
    this.outgoingPipeline = outgoingPipeline;
    this.rnd = rnd;
    this.requestExpirationScheduler = requestExpirationScheduler;
    this.nonceGenerator = new NonceGenerator();
  }

  public Bytes getNodeId() {
    return nodeId;
  }

  public Optional<NodeRecord> getNodeRecord() {
    return nodeRecord;
  }

  public InetSocketAddress getRemoteAddress() {
    return remoteAddress;
  }

  public Optional<Bytes> getWhoAreYouChallenge() {
    return whoAreYouChallenge;
  }

  public synchronized void updateNodeRecord(NodeRecord nodeRecord) {
    logger.trace(
        () ->
            String.format(
                "NodeRecord updated from %s to %s in session %s",
                this.nodeRecord, nodeRecord, this));
    this.nodeRecord = Optional.of(nodeRecord);
  }

  public void sendOutgoingOrdinary(V5Message message) {
    logger.trace(() -> String.format("Sending outgoing message %s in session %s", message, this));
    Bytes16 maskingIV = generateMaskingIV();
    Header<OrdinaryAuthData> header =
        Header.createOrdinaryHeader(getHomeNodeId(), Bytes12.wrap(generateNonce()));
    OrdinaryMessagePacket packet =
        OrdinaryMessagePacket.create(maskingIV, header, message, getInitiatorKey());
    sendOutgoing(maskingIV, packet);
  }

  public void sendOutgoingRandom(Bytes randomData) {
    Header<OrdinaryAuthData> header =
        Header.createOrdinaryHeader(getHomeNodeId(), Bytes12.wrap(generateNonce()));
    OrdinaryMessagePacket packet = OrdinaryMessagePacket.createRandom(header, randomData);
    logger.trace(
        () -> String.format("Sending outgoing Random message %s in session %s", packet, this));
    sendOutgoing(generateMaskingIV(), packet);
  }

  public void sendOutgoingWhoAreYou(WhoAreYouPacket packet) {
    logger.trace(
        () -> String.format("Sending outgoing WhoAreYou message %s in session %s", packet, this));
    Bytes16 maskingIV = generateMaskingIV();
    whoAreYouChallenge = Optional.of(Bytes.wrap(maskingIV, packet.getHeader().getBytes()));
    sendOutgoing(maskingIV, packet);
  }

  public void sendOutgoingHandshake(Header<HandshakeAuthData> header, V5Message message) {
    logger.trace(
        () ->
            String.format(
                "Sending outgoing Handshake message %s, %s in session %s", header, message, this));
    Bytes16 maskingIV = generateMaskingIV();
    HandshakeMessagePacket handshakeMessagePacket =
        HandshakeMessagePacket.create(maskingIV, header, message, getInitiatorKey());
    sendOutgoing(maskingIV, handshakeMessagePacket);
  }

  private void sendOutgoing(Bytes16 maskingIV, Packet<?> packet) {
    Bytes16 destNodeId = Bytes16.wrap(getNodeId(), 0);
    RawPacket rawPacket = RawPacket.createAndMask(maskingIV, packet, destNodeId);
    outgoingPipeline.accept(new NetworkParcelV5(rawPacket, remoteAddress));
  }

  /**
   * Creates object with request information: requestId etc, RequestInfo, designed to maintain
   * request status and its changes. Also stores info in session repository to track related
   * messages.
   *
   * <p>The value selected as request ID must allow for concurrent conversations. Using a timestamp
   * can result in parallel conversations with the same id, so this should be avoided. Request IDs
   * also prevent replay of responses. Using a simple counter would be fine if the implementation
   * could ensure that restarts or even re-installs would increment the counter based on previously
   * saved state in all circumstances. The easiest to implement is a random number.
   */
  public synchronized RequestInfo createNextRequest(Request<?> request) {
    byte[] requestId = new byte[REQUEST_ID_SIZE];
    rnd.nextBytes(requestId);
    Bytes wrappedId = Bytes.wrap(requestId);
    if (IS_LIVENESS_UPDATE) {
      request
          .getResultPromise()
          .whenComplete(
              (aVoid, throwable) -> {
                if (throwable == null) {
                  updateLiveness();
                }
              });
    }
    RequestInfo requestInfo = RequestInfo.create(wrappedId, request);
    requestIdStatuses.put(wrappedId, requestInfo);
    requestExpirationScheduler.put(
        wrappedId,
        new Runnable() {
          @Override
          public void run() {
            logger.debug(
                () ->
                    String.format(
                        "Request %s expired for id %s in session %s: no reply",
                        requestInfo, wrappedId, this));
            requestIdStatuses.remove(wrappedId);
          }
        });
    return requestInfo;
  }

  /** Updates request info. Thread-safe. */
  public synchronized void cancelAllRequests(String message) {
    logger.debug(() -> String.format("Cancelling all requests in session %s", this));
    Set<Bytes> requestIdsCopy = new HashSet<>(requestIdStatuses.keySet());
    requestIdsCopy.forEach(
        requestId -> {
          RequestInfo requestInfo = clearRequestInfo(requestId);
          requestInfo
              .getRequest()
              .getResultPromise()
              .completeExceptionally(
                  new RuntimeException(
                      String.format(
                          "Request %s cancelled due to reason: %s", requestInfo, message)));
        });
  }

  /** Generates random nonce */
  public Bytes12 generateNonce() {
    final Optional<Bytes12> oldNonce;
    final Bytes12 newNonce;
    synchronized (this) {
      newNonce = nonceGenerator.apply(rnd);
      oldNonce = lastOutboundNonce;
      lastOutboundNonce = Optional.of(newNonce);
    }
    nodeSessionManager.onSessionLastNonceUpdate(this, oldNonce, newNonce);
    return newNonce;
  }

  public synchronized Optional<Bytes12> getLastOutboundNonce() {
    return lastOutboundNonce;
  }

  /** If true indicates that handshake is complete */
  public synchronized boolean isAuthenticated() {
    return SessionState.AUTHENTICATED.equals(state);
  }

  public Bytes32 getHomeNodeId() {
    return homeNodeId;
  }

  public Bytes16 generateMaskingIV() {
    byte[] ivBytes = new byte[16];
    rnd.nextBytes(ivBytes);
    return Bytes16.wrap(ivBytes);
  }

  /** return initiator key, also known as write key */
  public Bytes getInitiatorKey() {
    return initiatorKey;
  }

  public void setInitiatorKey(Bytes initiatorKey) {
    this.initiatorKey = initiatorKey;
  }

  /** return recipient key, also known as read key */
  public Bytes getRecipientKey() {
    return recipientKey;
  }

  public void setRecipientKey(Bytes recipientKey) {
    this.recipientKey = recipientKey;
  }

  public Optional<InetSocketAddress> getReportedExternalAddress() {
    return reportedExternalAddress;
  }

  public void setReportedExternalAddress(final InetSocketAddress reportedExternalAddress) {
    this.reportedExternalAddress = Optional.of(reportedExternalAddress);
  }

  @SuppressWarnings("unchecked")
  public synchronized <T> void clearRequestInfo(Bytes requestId, T result) {
    final RequestInfo requestInfo = clearRequestInfo(requestId);
    checkNotNull(requestInfo, "Attempting to clear an unknown request");
    ((Request<T>) requestInfo.getRequest()).getResultPromise().complete(result);
  }

  /** Updates nodeRecord {@link NodeStatus} to ACTIVE of the node associated with this session */
  public synchronized void updateLiveness() {
    nodeRecord.ifPresent(
        record -> {
          NodeRecordInfo nodeRecordInfo =
              new NodeRecordInfo(record, Functions.getTime(), NodeStatus.ACTIVE, 0);
          nodeTable.save(nodeRecordInfo);
          nodeBucketStorage.onNodeContacted(record);
        });
  }

  private synchronized RequestInfo clearRequestInfo(Bytes requestId) {
    RequestInfo requestInfo = requestIdStatuses.remove(requestId);
    requestExpirationScheduler.cancel(requestId);
    return requestInfo;
  }

  public synchronized Optional<RequestInfo> getRequestInfo(Bytes requestId) {
    RequestInfo requestInfo = requestIdStatuses.get(requestId);
    return requestId == null ? Optional.empty() : Optional.of(requestInfo);
  }

  /**
   * Returns any queued {@link RequestInfo} which was not started because session is not
   * authenticated
   */
  public synchronized Optional<RequestInfo> getFirstAwaitRequestInfo() {
    return requestIdStatuses.values().stream()
        .filter(requestInfo -> AWAIT.equals(requestInfo.getTaskStatus()))
        .findFirst();
  }

  public synchronized Optional<RequestInfo> getFirstSentRequestInfo() {
    return requestIdStatuses.values().stream()
        .filter(requestInfo -> SENT.equals(requestInfo.getTaskStatus()))
        .findFirst();
  }

  public NodeTable getNodeTable() {
    return nodeTable;
  }

  public Stream<NodeRecord> getNodeRecordsInBucket(int distance) {
    return nodeBucketStorage.getLiveNodeRecords(distance);
  }

  public synchronized Bytes getIdNonce() {
    return idNonce;
  }

  public synchronized void setIdNonce(Bytes idNonce) {
    this.idNonce = idNonce;
  }

  public NodeRecord getHomeNodeRecord() {
    return localNodeRecordStore.getLocalNodeRecord();
  }

  @Override
  public String toString() {
    return "NodeSession{" + nodeId + " (" + state + ")}";
  }

  public synchronized SessionState getState() {
    return state;
  }

  public synchronized void setState(SessionState newStatus) {
    logger.debug(
        () -> String.format("Switching status of node %s from %s to %s", nodeId, state, newStatus));
    this.state = newStatus;
  }

  public Bytes getStaticNodeKey() {
    return staticNodeKey;
  }

  public enum SessionState {
    INITIAL, // other side is trying to connect, or we are initiating (before random packet is sent
    WHOAREYOU_SENT, // other side is initiator, we've sent whoareyou in response
    RANDOM_PACKET_SENT, // our node is initiator, we've sent random packet
    AUTHENTICATED
  }
}
