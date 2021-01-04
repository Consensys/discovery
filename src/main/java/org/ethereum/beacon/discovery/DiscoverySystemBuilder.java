/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.ethereum.beacon.discovery;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.Arrays.asList;
import static java.util.Objects.requireNonNullElseGet;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.database.Database;
import org.ethereum.beacon.discovery.network.NettyDiscoveryServer;
import org.ethereum.beacon.discovery.network.NettyDiscoveryServerImpl;
import org.ethereum.beacon.discovery.scheduler.ExpirationSchedulerFactory;
import org.ethereum.beacon.discovery.scheduler.Schedulers;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordFactory;
import org.ethereum.beacon.discovery.storage.LocalNodeRecordStore;
import org.ethereum.beacon.discovery.storage.NodeBucketStorage;
import org.ethereum.beacon.discovery.storage.NodeBucketStorageImpl;
import org.ethereum.beacon.discovery.storage.NodeRecordListener;
import org.ethereum.beacon.discovery.storage.NodeSerializerFactory;
import org.ethereum.beacon.discovery.storage.NodeTable;
import org.ethereum.beacon.discovery.storage.NodeTableStorage;
import org.ethereum.beacon.discovery.storage.NodeTableStorageFactory;
import org.ethereum.beacon.discovery.storage.NodeTableStorageFactoryImpl;
import org.ethereum.beacon.discovery.task.DiscoveryTaskManager;

public class DiscoverySystemBuilder {

  private static final AtomicInteger COUNTER = new AtomicInteger();
  private List<NodeRecord> bootnodes = Collections.emptyList();
  private Optional<InetSocketAddress> listenAddress = Optional.empty();
  private NodeRecord localNodeRecord;
  private Bytes privateKey;
  private final NodeRecordFactory nodeRecordFactory = NodeRecordFactory.DEFAULT;
  private Database database;
  private Schedulers schedulers;
  private NodeRecordListener localNodeRecordListener = (a, b) -> {};
  private Duration retryTimeout = DiscoveryTaskManager.DEFAULT_RETRY_TIMEOUT;
  private Duration lifeCheckInterval = DiscoveryTaskManager.DEFAULT_LIVE_CHECK_INTERVAL;
  private int trafficReadLimit = 250000; // bytes per sec
  private TalkHandler talkHandler = TalkHandler.NOOP;
  private NettyDiscoveryServer discoveryServer = null;

  public DiscoverySystemBuilder trafficReadLimit(final int trafficReadLimit) {
    this.trafficReadLimit = trafficReadLimit;
    return this;
  }

  public DiscoverySystemBuilder localNodeRecord(final NodeRecord localNodeRecord) {
    this.localNodeRecord = localNodeRecord;
    return this;
  }

  public DiscoverySystemBuilder listen(final String listenAddress, final int listenPort) {
    this.listenAddress = Optional.of(new InetSocketAddress(listenAddress, listenPort));
    return this;
  }

  public DiscoverySystemBuilder privateKey(final Bytes privateKey) {
    this.privateKey = privateKey;
    return this;
  }

  public DiscoverySystemBuilder bootnodes(final String... enrs) {
    bootnodes =
        Stream.of(enrs)
            .map(enr -> enr.startsWith("enr:") ? enr.substring("enr:".length()) : enr)
            .map(nodeRecordFactory::fromBase64)
            .collect(Collectors.toList());
    return this;
  }

  public DiscoverySystemBuilder bootnodes(final List<NodeRecord> records) {
    bootnodes = records;
    return this;
  }

  public DiscoverySystemBuilder bootnodes(final NodeRecord... records) {
    bootnodes = asList(records);
    return this;
  }

  public DiscoverySystemBuilder database(final Database database) {
    this.database = database;
    return this;
  }

  public DiscoverySystemBuilder schedulers(final Schedulers schedulers) {
    this.schedulers = schedulers;
    return this;
  }

  public DiscoverySystemBuilder localNodeRecordListener(final NodeRecordListener listener) {
    this.localNodeRecordListener = listener;
    return this;
  }

  public DiscoverySystemBuilder retryTimeout(Duration retryTimeout) {
    this.retryTimeout = retryTimeout;
    return this;
  }

  public DiscoverySystemBuilder lifeCheckInterval(Duration lifeCheckInterval) {
    this.lifeCheckInterval = lifeCheckInterval;
    return this;
  }

  public DiscoverySystemBuilder talkHandler(TalkHandler talkHandler) {
    this.talkHandler = talkHandler;
    return this;
  }

  public DiscoverySystemBuilder discoveryServer(NettyDiscoveryServer discoveryServer) {
    this.discoveryServer = discoveryServer;
    return this;
  }

  private void createDefaults() {
    database = requireNonNullElseGet(database, () -> Database.inMemoryDB());
    schedulers = requireNonNullElseGet(schedulers, () -> Schedulers.createDefault());
    discoveryServer =
        requireNonNullElseGet(
            discoveryServer,
            () ->
                new NettyDiscoveryServerImpl(
                    listenAddress
                        .or(localNodeRecord::getUdpAddress)
                        .orElseThrow(
                            () ->
                                new IllegalArgumentException(
                                    "Local node record must contain an IP and UDP port")),
                    trafficReadLimit));

    nodeTableStorage =
        requireNonNullElseGet(
            nodeTableStorage,
            () ->
                nodeTableStorageFactory.createTable(
                    database, serializerFactory, oldSeq -> localNodeRecord, () -> bootnodes));
    nodeTable = requireNonNullElseGet(nodeTable, () -> nodeTableStorage.get());
    nodeBucketStorage =
        requireNonNullElseGet(
            nodeBucketStorage,
            () -> new NodeBucketStorageImpl(database, serializerFactory, localNodeRecord));
    localNodeRecordStore =
        requireNonNullElseGet(
            localNodeRecordStore,
            () -> new LocalNodeRecordStore(localNodeRecord, privateKey, localNodeRecordListener));
    expirationSchedulerFactory =
        requireNonNullElseGet(
            expirationSchedulerFactory,
            () ->
                new ExpirationSchedulerFactory(
                    Executors.newSingleThreadScheduledExecutor(
                        new ThreadFactoryBuilder()
                            .setNameFormat("discovery-expiration-%d")
                            .build())));
  }

  final NodeTableStorageFactory nodeTableStorageFactory = new NodeTableStorageFactoryImpl();
  final NodeSerializerFactory serializerFactory = new NodeSerializerFactory(nodeRecordFactory);
  final int clientNumber = COUNTER.incrementAndGet();

  NodeTableStorage nodeTableStorage;
  NodeTable nodeTable;
  NodeBucketStorage nodeBucketStorage;
  LocalNodeRecordStore localNodeRecordStore;
  ExpirationSchedulerFactory expirationSchedulerFactory;

  public DiscoverySystem build() {
    checkNotNull(localNodeRecord, "Missing local node record");
    checkNotNull(privateKey, "Missing private key");
    createDefaults();

    final DiscoveryManager discoveryManager = buildDiscoveryManager();

    final DiscoveryTaskManager discoveryTaskManager =
        new DiscoveryTaskManager(
            discoveryManager,
            nodeTable,
            nodeBucketStorage,
            localNodeRecord,
            schedulers.newSingleThreadDaemon("discovery-tasks-" + clientNumber),
            true,
            true,
            expirationSchedulerFactory,
            retryTimeout,
            lifeCheckInterval);
    return new DiscoverySystem(
        discoveryManager, discoveryTaskManager, expirationSchedulerFactory, nodeTable, bootnodes);
  }

  @VisibleForTesting
  DiscoveryManagerImpl buildDiscoveryManager() {
    createDefaults();
    return new DiscoveryManagerImpl(
        discoveryServer,
        nodeTable,
        nodeBucketStorage,
        localNodeRecordStore,
        privateKey,
        nodeRecordFactory,
        schedulers.newSingleThreadDaemon("discovery-client-" + clientNumber),
        expirationSchedulerFactory,
        talkHandler);
  }
}
