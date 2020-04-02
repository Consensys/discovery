/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery;

import com.google.common.annotations.VisibleForTesting;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.network.DiscoveryClient;
import org.ethereum.beacon.discovery.network.NettyDiscoveryClientImpl;
import org.ethereum.beacon.discovery.network.NettyDiscoveryServer;
import org.ethereum.beacon.discovery.network.NettyDiscoveryServerImpl;
import org.ethereum.beacon.discovery.network.NetworkParcel;
import org.ethereum.beacon.discovery.pipeline.Envelope;
import org.ethereum.beacon.discovery.pipeline.Field;
import org.ethereum.beacon.discovery.pipeline.Pipeline;
import org.ethereum.beacon.discovery.pipeline.PipelineImpl;
import org.ethereum.beacon.discovery.pipeline.handler.AuthHeaderMessagePacketHandler;
import org.ethereum.beacon.discovery.pipeline.handler.BadPacketHandler;
import org.ethereum.beacon.discovery.pipeline.handler.IncomingDataPacker;
import org.ethereum.beacon.discovery.pipeline.handler.MessageHandler;
import org.ethereum.beacon.discovery.pipeline.handler.MessagePacketHandler;
import org.ethereum.beacon.discovery.pipeline.handler.NewTaskHandler;
import org.ethereum.beacon.discovery.pipeline.handler.NextTaskHandler;
import org.ethereum.beacon.discovery.pipeline.handler.NodeIdToSession;
import org.ethereum.beacon.discovery.pipeline.handler.NodeSessionRequestHandler;
import org.ethereum.beacon.discovery.pipeline.handler.NotExpectedIncomingPacketHandler;
import org.ethereum.beacon.discovery.pipeline.handler.OutgoingParcelHandler;
import org.ethereum.beacon.discovery.pipeline.handler.UnknownPacketTagToSender;
import org.ethereum.beacon.discovery.pipeline.handler.UnknownPacketTypeByStatus;
import org.ethereum.beacon.discovery.pipeline.handler.WhoAreYouAttempt;
import org.ethereum.beacon.discovery.pipeline.handler.WhoAreYouPacketHandler;
import org.ethereum.beacon.discovery.pipeline.handler.WhoAreYouSessionResolver;
import org.ethereum.beacon.discovery.scheduler.ExpirationSchedulerFactory;
import org.ethereum.beacon.discovery.scheduler.Scheduler;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordFactory;
import org.ethereum.beacon.discovery.storage.AuthTagRepository;
import org.ethereum.beacon.discovery.storage.LocalNodeRecordStore;
import org.ethereum.beacon.discovery.storage.NodeBucketStorage;
import org.ethereum.beacon.discovery.storage.NodeTable;
import org.ethereum.beacon.discovery.task.TaskOptions;
import org.ethereum.beacon.discovery.task.TaskType;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.ReplayProcessor;

public class DiscoveryManagerImpl implements DiscoveryManager {
  private final ReplayProcessor<NetworkParcel> outgoingMessages = ReplayProcessor.cacheLast();
  private final NettyDiscoveryServer discoveryServer;
  private final Pipeline incomingPipeline = new PipelineImpl();
  private final Pipeline outgoingPipeline = new PipelineImpl();
  private final LocalNodeRecordStore localNodeRecordStore;
  private volatile DiscoveryClient discoveryClient;

  public DiscoveryManagerImpl(
      NodeTable nodeTable,
      NodeBucketStorage nodeBucketStorage,
      LocalNodeRecordStore localNodeRecordStore,
      Bytes homeNodePrivateKey,
      NodeRecordFactory nodeRecordFactory,
      Scheduler taskScheduler,
      ExpirationSchedulerFactory expirationSchedulerFactory) {
    this.localNodeRecordStore = localNodeRecordStore;
    final NodeRecord homeNodeRecord = localNodeRecordStore.getLocalNodeRecord();
    AuthTagRepository authTagRepo = new AuthTagRepository();
    final InetSocketAddress listenAddress =
        homeNodeRecord
            .getUdpAddress()
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Local node record must contain an IP and UDP port"));
    this.discoveryServer = new NettyDiscoveryServerImpl(listenAddress);
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
        .addHandler(new IncomingDataPacker())
        .addHandler(new WhoAreYouAttempt(homeNodeRecord.getNodeId()))
        .addHandler(new WhoAreYouSessionResolver(authTagRepo))
        .addHandler(new UnknownPacketTagToSender(homeNodeRecord.getNodeId()))
        .addHandler(nodeIdToSession)
        .addHandler(new UnknownPacketTypeByStatus())
        .addHandler(new NotExpectedIncomingPacketHandler())
        .addHandler(new WhoAreYouPacketHandler(outgoingPipeline, taskScheduler))
        .addHandler(
            new AuthHeaderMessagePacketHandler(outgoingPipeline, taskScheduler, nodeRecordFactory))
        .addHandler(new MessagePacketHandler())
        .addHandler(new MessageHandler(nodeRecordFactory, localNodeRecordStore))
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
    Flux.from(discoveryServer.getIncomingPackets()).subscribe(incomingPipeline::push);
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

  private CompletableFuture<Void> executeTaskImpl(
      NodeRecord nodeRecord, TaskType taskType, TaskOptions taskOptions) {
    Envelope envelope = new Envelope();
    envelope.put(Field.NODE, nodeRecord);
    CompletableFuture<Void> future = new CompletableFuture<>();
    envelope.put(Field.TASK, taskType);
    envelope.put(Field.FUTURE, future);
    envelope.put(Field.TASK_OPTIONS, taskOptions);
    outgoingPipeline.push(envelope);
    return future;
  }

  @Override
  public CompletableFuture<Void> findNodes(NodeRecord nodeRecord, int distance) {
    return executeTaskImpl(nodeRecord, TaskType.FINDNODE, new TaskOptions(true, distance));
  }

  @Override
  public CompletableFuture<Void> ping(NodeRecord nodeRecord) {
    return executeTaskImpl(nodeRecord, TaskType.PING, new TaskOptions(true));
  }

  @VisibleForTesting
  public Publisher<NetworkParcel> getOutgoingMessages() {
    return outgoingMessages;
  }
}
