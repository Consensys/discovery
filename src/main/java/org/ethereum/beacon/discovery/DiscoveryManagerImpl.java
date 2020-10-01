/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery;

import com.google.common.annotations.VisibleForTesting;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.message.FindNodeMessage;
import org.ethereum.beacon.discovery.message.PingMessage;
import org.ethereum.beacon.discovery.network.DiscoveryClient;
import org.ethereum.beacon.discovery.network.NettyDiscoveryClientImpl;
import org.ethereum.beacon.discovery.network.NettyDiscoveryServer;
import org.ethereum.beacon.discovery.network.NettyDiscoveryServerImpl;
import org.ethereum.beacon.discovery.network.NetworkParcel;
import org.ethereum.beacon.discovery.pipeline.Envelope;
import org.ethereum.beacon.discovery.pipeline.Field;
import org.ethereum.beacon.discovery.pipeline.Pipeline;
import org.ethereum.beacon.discovery.pipeline.PipelineImpl;
import org.ethereum.beacon.discovery.pipeline.handler.BadPacketHandler;
import org.ethereum.beacon.discovery.pipeline.handler.HandshakeMessagePacketHandler;
import org.ethereum.beacon.discovery.pipeline.handler.IncomingDataPacker;
import org.ethereum.beacon.discovery.pipeline.handler.MessageHandler;
import org.ethereum.beacon.discovery.pipeline.handler.MessagePacketHandler;
import org.ethereum.beacon.discovery.pipeline.handler.NewTaskHandler;
import org.ethereum.beacon.discovery.pipeline.handler.NextTaskHandler;
import org.ethereum.beacon.discovery.pipeline.handler.NodeIdToSession;
import org.ethereum.beacon.discovery.pipeline.handler.NodeSessionRequestHandler;
import org.ethereum.beacon.discovery.pipeline.handler.OutgoingParcelHandler;
import org.ethereum.beacon.discovery.pipeline.handler.PacketDispatcherHandler;
import org.ethereum.beacon.discovery.pipeline.handler.UnauthorizedMessagePacketHandler;
import org.ethereum.beacon.discovery.pipeline.handler.UnknownPacketTagToSender;
import org.ethereum.beacon.discovery.pipeline.handler.WhoAreYouPacketHandler;
import org.ethereum.beacon.discovery.pipeline.info.FindNodeResponseHandler;
import org.ethereum.beacon.discovery.pipeline.info.MultiPacketResponseHandler;
import org.ethereum.beacon.discovery.pipeline.info.Request;
import org.ethereum.beacon.discovery.scheduler.ExpirationSchedulerFactory;
import org.ethereum.beacon.discovery.scheduler.Scheduler;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordFactory;
import org.ethereum.beacon.discovery.storage.AuthTagRepository;
import org.ethereum.beacon.discovery.storage.LocalNodeRecordStore;
import org.ethereum.beacon.discovery.storage.NodeBucketStorage;
import org.ethereum.beacon.discovery.storage.NodeTable;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.ReplayProcessor;

public class DiscoveryManagerImpl implements DiscoveryManager {
  private static final Logger LOG = LogManager.getLogger();

  private final ReplayProcessor<NetworkParcel> outgoingMessages = ReplayProcessor.cacheLast();
  private final NettyDiscoveryServer discoveryServer;
  private final Pipeline incomingPipeline = new PipelineImpl();
  private final Pipeline outgoingPipeline = new PipelineImpl();
  private final LocalNodeRecordStore localNodeRecordStore;
  private final TalkHandler talkHandler;
  private volatile DiscoveryClient discoveryClient;

  public DiscoveryManagerImpl(
      Optional<InetSocketAddress> listenAddress,
      NodeTable nodeTable,
      NodeBucketStorage nodeBucketStorage,
      LocalNodeRecordStore localNodeRecordStore,
      Bytes homeNodePrivateKey,
      NodeRecordFactory nodeRecordFactory,
      Scheduler taskScheduler,
      ExpirationSchedulerFactory expirationSchedulerFactory,
      TalkHandler talkHandler) {
    this.localNodeRecordStore = localNodeRecordStore;
    this.talkHandler = talkHandler;
    final NodeRecord homeNodeRecord = localNodeRecordStore.getLocalNodeRecord();
    AuthTagRepository authTagRepo = new AuthTagRepository();

    this.discoveryServer =
        new NettyDiscoveryServerImpl(
            listenAddress
                .or(homeNodeRecord::getUdpAddress)
                .orElseThrow(
                    () ->
                        new IllegalArgumentException(
                            "Local node record must contain an IP and UDP port")));
    NodeIdToSession nodeIdToSession =
        new NodeIdToSession(
            localNodeRecordStore,
            homeNodePrivateKey,
            nodeBucketStorage,
            authTagRepo,
            nodeTable,
            outgoingPipeline,
            expirationSchedulerFactory);
    incomingPipeline
        .addHandler(new IncomingDataPacker(homeNodeRecord.getNodeId()))
        .addHandler(new UnknownPacketTagToSender())
        .addHandler(nodeIdToSession)
        .addHandler(new PacketDispatcherHandler())
        .addHandler(new WhoAreYouPacketHandler(outgoingPipeline, taskScheduler))
        .addHandler(
            new HandshakeMessagePacketHandler(outgoingPipeline, taskScheduler, nodeRecordFactory))
        .addHandler(new MessagePacketHandler(nodeRecordFactory))
        .addHandler(new UnauthorizedMessagePacketHandler())
        .addHandler(new MessageHandler(nodeRecordFactory, localNodeRecordStore, talkHandler))
        .addHandler(new BadPacketHandler());
    final FluxSink<NetworkParcel> outgoingSink = outgoingMessages.sink();
    outgoingPipeline
        .addHandler(new OutgoingParcelHandler(outgoingSink))
        .addHandler(new NodeSessionRequestHandler())
        .addHandler(nodeIdToSession)
        .addHandler(new NewTaskHandler())
        .addHandler(new NextTaskHandler(outgoingPipeline, taskScheduler));
  }

  @Override
  public CompletableFuture<Void> start() {
    incomingPipeline.build();
    outgoingPipeline.build();
    Flux.from(discoveryServer.getIncomingPackets())
        .onErrorContinue((err, msg) -> LOG.debug("Error while processing message: " + err))
        .subscribe(incomingPipeline::push);
    return discoveryServer
        .start()
        .thenAccept(
            channel -> discoveryClient = new NettyDiscoveryClientImpl(outgoingMessages, channel));
  }

  @Override
  public void stop() {
    final DiscoveryClient client = this.discoveryClient;
    if (client != null) {
      client.stop();
    }
    discoveryServer.stop();
  }

  @Override
  public NodeRecord getLocalNodeRecord() {
    return localNodeRecordStore.getLocalNodeRecord();
  }

  @Override
  public void updateCustomFieldValue(final String fieldName, final Bytes value) {
    localNodeRecordStore.onCustomFieldValueChanged(fieldName, value);
  }

  private <T> CompletableFuture<T> executeTaskImpl(NodeRecord nodeRecord, Request<T> request) {
    Envelope envelope = new Envelope();
    envelope.put(Field.NODE, nodeRecord);
    envelope.put(Field.REQUEST, request);
    outgoingPipeline.push(envelope);
    return request.getResultPromise();
  }

  @Override
  public CompletableFuture<Void> findNodes(NodeRecord nodeRecord, List<Integer> distances) {
    Request<Void> request =
        new Request<>(
            new CompletableFuture<>(),
            reqId -> new FindNodeMessage(reqId, distances),
            new FindNodeResponseHandler());
    return executeTaskImpl(nodeRecord, request);
  }

  @Override
  public CompletableFuture<Void> ping(NodeRecord nodeRecord) {
    Request<Void> request =
        new Request<>(
            new CompletableFuture<>(),
            reqId -> new PingMessage(reqId, nodeRecord.getSeq()),
            MultiPacketResponseHandler.SINGLE_PACKET_RESPONSE_HANDLER);
    return executeTaskImpl(nodeRecord, request);
  }

  @Override
  public CompletableFuture<Bytes> talk(NodeRecord nodeRecord, String protocol, Bytes request) {
    return null;
  }

  @VisibleForTesting
  public Publisher<NetworkParcel> getOutgoingMessages() {
    return outgoingMessages;
  }
}
