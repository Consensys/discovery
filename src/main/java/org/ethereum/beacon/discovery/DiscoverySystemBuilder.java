/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.ethereum.beacon.discovery;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.Arrays.asList;
import static java.util.Objects.requireNonNullElseGet;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Streams;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.tuweni.crypto.SECP256K1.SecretKey;
import org.ethereum.beacon.discovery.liveness.LivenessChecker;
import org.ethereum.beacon.discovery.liveness.LivenessChecker.Pinger;
import org.ethereum.beacon.discovery.message.handler.DefaultExternalAddressSelector;
import org.ethereum.beacon.discovery.message.handler.ExternalAddressSelector;
import org.ethereum.beacon.discovery.network.NettyDiscoveryServer;
import org.ethereum.beacon.discovery.network.NettyDiscoveryServerImpl;
import org.ethereum.beacon.discovery.scheduler.ExpirationSchedulerFactory;
import org.ethereum.beacon.discovery.scheduler.Schedulers;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordFactory;
import org.ethereum.beacon.discovery.storage.KBuckets;
import org.ethereum.beacon.discovery.storage.LocalNodeRecordStore;
import org.ethereum.beacon.discovery.storage.NewAddressHandler;
import org.ethereum.beacon.discovery.storage.NodeRecordListener;
import org.ethereum.beacon.discovery.task.DiscoveryTaskManager;

public class DiscoverySystemBuilder {

  private static final AtomicInteger COUNTER = new AtomicInteger();
  private List<NodeRecord> bootnodes = Collections.emptyList();
  private Optional<List<InetSocketAddress>> listenAddresses = Optional.empty();
  private NodeRecord localNodeRecord;
  private SecretKey secretKey;
  private NodeRecordFactory nodeRecordFactory = NodeRecordFactory.DEFAULT;
  private Schedulers schedulers;
  private NodeRecordListener localNodeRecordListener = NodeRecordListener.NOOP;
  private NewAddressHandler newAddressHandler;
  private Duration retryTimeout = DiscoveryTaskManager.DEFAULT_RETRY_TIMEOUT;
  private Duration recursiveLookupInterval = DiscoveryTaskManager.DEFAULT_RECURSIVE_LOOKUP_INTERVAL;
  private Duration lifeCheckInterval = DiscoveryTaskManager.DEFAULT_LIVE_CHECK_INTERVAL;
  private int trafficReadLimit = 250000; // bytes per sec
  private TalkHandler talkHandler = TalkHandler.NOOP;
  private List<NettyDiscoveryServer> discoveryServers;
  private ExternalAddressSelector externalAddressSelector;
  private AddressAccessPolicy addressAccessPolicy = AddressAccessPolicy.ALLOW_ALL;
  private final Clock clock = Clock.systemUTC();
  private final LivenessChecker livenessChecker = new LivenessChecker(clock);

  public DiscoverySystemBuilder trafficReadLimit(final int trafficReadLimit) {
    this.trafficReadLimit = trafficReadLimit;
    return this;
  }

  public DiscoverySystemBuilder localNodeRecord(final NodeRecord localNodeRecord) {
    this.localNodeRecord = localNodeRecord;
    return this;
  }

  public DiscoverySystemBuilder listen(final String listenAddress, final int listenPort) {
    this.listenAddresses = Optional.of(List.of(new InetSocketAddress(listenAddress, listenPort)));
    return this;
  }

  public DiscoverySystemBuilder listen(
      final List<String> listenAddresses, final List<Integer> listenPorts) {
    this.listenAddresses =
        Optional.of(
            Streams.zip(listenAddresses.stream(), listenPorts.stream(), InetSocketAddress::new)
                .collect(Collectors.toList()));
    return this;
  }

  public DiscoverySystemBuilder secretKey(final SecretKey secretKey) {
    this.secretKey = secretKey;
    return this;
  }

  public DiscoverySystemBuilder nodeRecordFactory(NodeRecordFactory nodeRecordFactory) {
    this.nodeRecordFactory = nodeRecordFactory;
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

  public DiscoverySystemBuilder schedulers(final Schedulers schedulers) {
    this.schedulers = schedulers;
    return this;
  }

  public DiscoverySystemBuilder localNodeRecordListener(final NodeRecordListener listener) {
    this.localNodeRecordListener = listener;
    return this;
  }

  public DiscoverySystemBuilder newAddressHandler(final NewAddressHandler handler) {
    this.newAddressHandler = handler;
    return this;
  }

  public DiscoverySystemBuilder recursiveLookupInterval(final Duration recursiveLookupInterval) {
    this.recursiveLookupInterval = recursiveLookupInterval;
    return this;
  }

  public DiscoverySystemBuilder retryTimeout(final Duration retryTimeout) {
    this.retryTimeout = retryTimeout;
    return this;
  }

  public DiscoverySystemBuilder lifeCheckInterval(final Duration lifeCheckInterval) {
    this.lifeCheckInterval = lifeCheckInterval;
    return this;
  }

  public DiscoverySystemBuilder talkHandler(final TalkHandler talkHandler) {
    this.talkHandler = talkHandler;
    return this;
  }

  public DiscoverySystemBuilder discoveryServer(final NettyDiscoveryServer discoveryServer) {
    this.discoveryServers = List.of(discoveryServer);
    return this;
  }

  public DiscoverySystemBuilder discoveryServers(final NettyDiscoveryServer... discoveryServers) {
    this.discoveryServers = Arrays.asList(discoveryServers);
    return this;
  }

  public DiscoverySystemBuilder externalAddressSelector(
      final ExternalAddressSelector externalAddressSelector) {
    this.externalAddressSelector = externalAddressSelector;
    return this;
  }

  public DiscoverySystemBuilder addressAccessPolicy(final AddressAccessPolicy addressAccessPolicy) {
    this.addressAccessPolicy = addressAccessPolicy;
    return this;
  }

  private void createDefaults() {
    newAddressHandler =
        requireNonNullElseGet(
            newAddressHandler,
            () ->
                (oldRecord, newAddress) ->
                    Optional.of(
                        oldRecord.withNewAddress(
                            newAddress,
                            oldRecord.getTcpAddress().map(InetSocketAddress::getPort),
                            secretKey)));
    schedulers = requireNonNullElseGet(schedulers, Schedulers::createDefault);
    final List<InetSocketAddress> serverListenAddresses =
        listenAddresses.orElseGet(
            () -> {
              final List<InetSocketAddress> localNodeRecordAddresses = new ArrayList<>();
              localNodeRecord.getUdpAddress().ifPresent(localNodeRecordAddresses::add);
              localNodeRecord.getUdp6Address().ifPresent(localNodeRecordAddresses::add);
              if (localNodeRecordAddresses.isEmpty()) {
                throw new IllegalArgumentException(
                    "Local node record must contain an IP and UDP port(s)");
              }
              return localNodeRecordAddresses;
            });
    discoveryServers =
        requireNonNullElseGet(
            discoveryServers,
            () ->
                serverListenAddresses.stream()
                    .map(
                        serverListenAddress ->
                            new NettyDiscoveryServerImpl(serverListenAddress, trafficReadLimit))
                    .collect(Collectors.toList()));

    localNodeRecordStore =
        requireNonNullElseGet(
            localNodeRecordStore,
            () ->
                new LocalNodeRecordStore(
                    localNodeRecord, secretKey, localNodeRecordListener, newAddressHandler));
    nodeBucketStorage =
        requireNonNullElseGet(
            nodeBucketStorage, () -> new KBuckets(clock, localNodeRecordStore, livenessChecker));
    expirationSchedulerFactory =
        requireNonNullElseGet(
            expirationSchedulerFactory,
            () ->
                new ExpirationSchedulerFactory(
                    Executors.newSingleThreadScheduledExecutor(
                        new ThreadFactoryBuilder()
                            .setNameFormat("discovery-expiration-%d")
                            .build())));
    externalAddressSelector =
        requireNonNullElseGet(
            externalAddressSelector,
            () -> new DefaultExternalAddressSelector(localNodeRecordStore));
  }

  final int clientNumber = COUNTER.incrementAndGet();

  KBuckets nodeBucketStorage;
  LocalNodeRecordStore localNodeRecordStore;
  ExpirationSchedulerFactory expirationSchedulerFactory;

  public DiscoverySystem build() {
    checkNotNull(localNodeRecord, "Missing local node record");
    checkNotNull(secretKey, "Missing secret key");
    createDefaults();

    // Check local node record is valid
    checkArgument(
        localNodeRecordStore.getLocalNodeRecord().isValid(), "Local node record is invalid");

    final DiscoveryManager discoveryManager = buildDiscoveryManager();
    livenessChecker.setPinger(new AsyncPinger(discoveryManager::ping));

    final DiscoveryTaskManager discoveryTaskManager =
        new DiscoveryTaskManager(
            discoveryManager,
            localNodeRecordStore.getLocalNodeRecord().getNodeId(),
            nodeBucketStorage,
            schedulers.newSingleThreadDaemon("discovery-tasks-" + clientNumber),
            expirationSchedulerFactory,
            recursiveLookupInterval,
            retryTimeout,
            lifeCheckInterval);
    return new DiscoverySystem(
        discoveryManager,
        discoveryTaskManager,
        expirationSchedulerFactory,
        nodeBucketStorage,
        bootnodes);
  }

  @VisibleForTesting
  DiscoveryManagerImpl buildDiscoveryManager() {
    createDefaults();
    return new DiscoveryManagerImpl(
        discoveryServers,
        nodeBucketStorage,
        localNodeRecordStore,
        secretKey,
        nodeRecordFactory,
        schedulers.newSingleThreadDaemon("discovery-client-" + clientNumber),
        expirationSchedulerFactory,
        talkHandler,
        externalAddressSelector,
        addressAccessPolicy);
  }

  /**
   * A {@link Pinger} wrapper implementation that ensures that the ping is triggered from a separate
   * thread, with no locks held. This ensures that locks are always acquired "outside to in".
   * Without this the {@link LivenessChecker} may be in a synchronized block when it triggers a ping
   * which then needs to access {@link KBuckets} and take its lock out of the usual order. By
   * releasing the {@link LivenessChecker} lock first, this ensures a consistent order is always
   * used.
   *
   * <p>It also means that pings are consistently triggered in the same way that external code
   * calling {@link DiscoveryManager#ping(NodeRecord)} would.
   */
  private static final class AsyncPinger implements Pinger {
    private final Pinger delegate;

    private AsyncPinger(final Pinger delegate) {
      this.delegate = delegate;
    }

    @Override
    public CompletableFuture<Void> ping(final NodeRecord node) {
      return CompletableFuture.supplyAsync(() -> delegate.ping(node).orTimeout(500, MILLISECONDS))
          .thenCompose(Function.identity());
    }
  }
}
