/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.schema;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.ethereum.beacon.discovery.task.TaskStatus.AWAIT;

import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.ethereum.beacon.discovery.message.V5Message;
import org.ethereum.beacon.discovery.network.NetworkParcel;
import org.ethereum.beacon.discovery.network.NetworkParcelV5;
import org.ethereum.beacon.discovery.packet.AuthData;
import org.ethereum.beacon.discovery.packet.Header;
import org.ethereum.beacon.discovery.packet.OrdinaryMessagePacket;
import org.ethereum.beacon.discovery.packet.Packet;
import org.ethereum.beacon.discovery.packet.RawPacket;
import org.ethereum.beacon.discovery.pipeline.info.Request;
import org.ethereum.beacon.discovery.pipeline.info.RequestInfo;
import org.ethereum.beacon.discovery.scheduler.ExpirationScheduler;
import org.ethereum.beacon.discovery.storage.AuthTagRepository;
import org.ethereum.beacon.discovery.storage.LocalNodeRecordStore;
import org.ethereum.beacon.discovery.storage.NodeBucket;
import org.ethereum.beacon.discovery.storage.NodeBucketStorage;
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

  public static final int NONCE_SIZE = 12;
  public static final int REQUEST_ID_SIZE = 8;
  private static final boolean IS_LIVENESS_UPDATE = true;
  private final Bytes32 homeNodeId;
  private final LocalNodeRecordStore localNodeRecordStore;
  private final AuthTagRepository authTagRepo;
  private final NodeTable nodeTable;
  private final NodeBucketStorage nodeBucketStorage;
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

  public NodeSession(
      Bytes nodeId,
      Optional<NodeRecord> nodeRecord,
      InetSocketAddress remoteAddress,
      LocalNodeRecordStore localNodeRecordStore,
      Bytes staticNodeKey,
      NodeTable nodeTable,
      NodeBucketStorage nodeBucketStorage,
      AuthTagRepository authTagRepo,
      Consumer<NetworkParcel> outgoingPipeline,
      Random rnd,
      ExpirationScheduler<Bytes> requestExpirationScheduler) {
    this.nodeId = nodeId;
    this.nodeRecord = nodeRecord;
    this.remoteAddress = remoteAddress;
    this.localNodeRecordStore = localNodeRecordStore;
    this.authTagRepo = authTagRepo;
    this.nodeTable = nodeTable;
    this.nodeBucketStorage = nodeBucketStorage;
    this.staticNodeKey = staticNodeKey;
    this.homeNodeId = Bytes32.wrap(localNodeRecordStore.getLocalNodeRecord().getNodeId());
    this.outgoingPipeline = outgoingPipeline;
    this.rnd = rnd;
    this.requestExpirationScheduler = requestExpirationScheduler;
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
    Header<AuthData> header =
        Header.createOrdinaryHeader(getHomeNodeId(), Bytes12.wrap(getAuthTag().get()));
    OrdinaryMessagePacket packet = OrdinaryMessagePacket.create(header, message, getInitiatorKey());
    sendOutgoing(packet);
  }

  public void sendOutgoing(Packet<?> packet) {
    logger.trace(() -> String.format("Sending outgoing packet %s in session %s", packet, this));
    Bytes16 destNodeId = Bytes16.wrap(getNodeId(), 0);
    RawPacket rawPacket = RawPacket.create(generateAesCtrIV(), packet, destNodeId);
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
  public synchronized RequestInfo createNextRequest(Request request) {
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

  /** Generates random nonce of {@link #NONCE_SIZE} size */
  public synchronized Bytes12 generateNonce() {
    byte[] nonce = new byte[NONCE_SIZE];
    rnd.nextBytes(nonce);
    return Bytes12.wrap(nonce);
  }

  /** If true indicates that handshake is complete */
  public synchronized boolean isAuthenticated() {
    return SessionState.AUTHENTICATED.equals(state);
  }

  /** Resets stored authTags for this session making them obsolete */
  public void cleanup() {
    authTagRepo.expire(this);
  }

  public Optional<Bytes12> getAuthTag() {
    return authTagRepo.getTag(this);
  }

  public void setAuthTag(Bytes12 authTag) {
    authTagRepo.put(authTag, this);
  }

  public Bytes32 getHomeNodeId() {
    return homeNodeId;
  }

  public Bytes16 generateAesCtrIV() {
    byte[] ivBytes = new byte[16];
    rnd.nextBytes(ivBytes);
    return Bytes16.wrap(ivBytes);
  }

  /** @return initiator key, also known as write key */
  public Bytes getInitiatorKey() {
    return initiatorKey;
  }

  public void setInitiatorKey(Bytes initiatorKey) {
    this.initiatorKey = initiatorKey;
  }

  /** @return recipient key, also known as read key */
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

  public synchronized void clearRequestInfo(Bytes requestId, Object result) {
    final RequestInfo requestInfo = clearRequestInfo(requestId);
    checkNotNull(requestInfo, "Attempting to clear an unknown request");
    requestInfo.getRequest().getResultPromise().complete(result);
  }

  /** Updates nodeRecord {@link NodeStatus} to ACTIVE of the node associated with this session */
  public synchronized void updateLiveness() {
    nodeRecord.ifPresent(
        record -> {
          NodeRecordInfo nodeRecordInfo =
              new NodeRecordInfo(record, Functions.getTime(), NodeStatus.ACTIVE, 0);
          nodeTable.save(nodeRecordInfo);
          nodeBucketStorage.put(nodeRecordInfo);
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

  public NodeTable getNodeTable() {
    return nodeTable;
  }

  public void putRecordInBucket(NodeRecordInfo nodeRecordInfo) {
    nodeBucketStorage.put(nodeRecordInfo);
  }

  public Optional<NodeBucket> getBucket(int index) {
    return nodeBucketStorage.get(index);
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
