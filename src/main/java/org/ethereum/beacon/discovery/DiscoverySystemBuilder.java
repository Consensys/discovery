/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.ethereum.beacon.discovery;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.Arrays.asList;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.database.Database;
import org.ethereum.beacon.discovery.scheduler.Schedulers;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordFactory;
import org.ethereum.beacon.discovery.storage.LocalNodeRecordStore;
import org.ethereum.beacon.discovery.storage.NodeBucketStorage;
import org.ethereum.beacon.discovery.storage.NodeBucketStorageImpl;
import org.ethereum.beacon.discovery.storage.NodeSerializerFactory;
import org.ethereum.beacon.discovery.storage.NodeTable;
import org.ethereum.beacon.discovery.storage.NodeTableStorage;
import org.ethereum.beacon.discovery.storage.NodeTableStorageFactory;
import org.ethereum.beacon.discovery.storage.NodeTableStorageFactoryImpl;
import org.ethereum.beacon.discovery.task.DiscoveryTaskManager;

public class DiscoverySystemBuilder {
  private static final AtomicInteger COUNTER = new AtomicInteger();
  private List<NodeRecord> bootnodes = Collections.emptyList();
  private NodeRecord localNodeRecord;
  private Bytes privateKey;
  private final NodeRecordFactory nodeRecordFactory = NodeRecordFactory.DEFAULT;
  private Database database;
  private Schedulers schedulers;

  public DiscoverySystemBuilder localNodeRecord(final NodeRecord localNodeRecord) {
    this.localNodeRecord = localNodeRecord;
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

  public DiscoverySystem build() {
    checkNotNull(localNodeRecord, "Missing local node record");
    checkNotNull(privateKey, "Missing private key");

    if (database == null) {
      database = Database.inMemoryDB();
    }
    final NodeTableStorageFactory nodeTableStorageFactory = new NodeTableStorageFactoryImpl();
    final NodeSerializerFactory serializerFactory = new NodeSerializerFactory(nodeRecordFactory);
    final NodeTableStorage nodeTableStorage =
        nodeTableStorageFactory.createTable(
            database, serializerFactory, oldSeq -> localNodeRecord, () -> bootnodes);
    final NodeTable nodeTable = nodeTableStorage.get();
    if (schedulers == null) {
      schedulers = Schedulers.createDefault();
    }
    final NodeBucketStorage nodeBucketStorage =
        new NodeBucketStorageImpl(database, serializerFactory, localNodeRecord);
    final int clientNumber = COUNTER.incrementAndGet();
    final LocalNodeRecordStore localNodeRecordStore =
        new LocalNodeRecordStore(localNodeRecord, privateKey);
    final DiscoveryManager discoveryManager =
        new DiscoveryManagerImpl(
            nodeTable,
            nodeBucketStorage,
            localNodeRecordStore,
            privateKey,
            nodeRecordFactory,
            schedulers.newSingleThreadDaemon("client-" + clientNumber));

    final DiscoveryTaskManager discoveryTaskManager =
        new DiscoveryTaskManager(
            discoveryManager,
            nodeTable,
            nodeBucketStorage,
            localNodeRecord,
            schedulers.newSingleThreadDaemon("tasks-" + clientNumber),
            true,
            true);
    return new DiscoverySystem(discoveryManager, discoveryTaskManager, nodeTable, bootnodes);
  }
}
