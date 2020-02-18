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
import org.ethereum.beacon.discovery.storage.NodeBucketStorage;
import org.ethereum.beacon.discovery.storage.NodeBucketStorageImpl;
import org.ethereum.beacon.discovery.storage.NodeSerializerFactory;
import org.ethereum.beacon.discovery.storage.NodeTableStorage;
import org.ethereum.beacon.discovery.storage.NodeTableStorageFactory;
import org.ethereum.beacon.discovery.storage.NodeTableStorageFactoryImpl;

public class DiscoveryManagerBuilder {
  private static final AtomicInteger COUNTER = new AtomicInteger();
  private List<NodeRecord> bootnodes = Collections.emptyList();
  private NodeRecord localNodeRecord;
  private Bytes privateKey;
  private final NodeRecordFactory nodeRecordFactory = NodeRecordFactory.DEFAULT;
  private Database database;
  private Schedulers schedulers;

  public DiscoveryManagerBuilder localNodeRecord(final NodeRecord localNodeRecord) {
    this.localNodeRecord = localNodeRecord;
    return this;
  }

  public DiscoveryManagerBuilder privateKey(final Bytes privateKey) {
    this.privateKey = privateKey;
    return this;
  }

  public DiscoveryManagerBuilder bootnodes(final String... enrs) {
    bootnodes =
        Stream.of(enrs)
            .map(enr -> enr.startsWith("enr:") ? enr.substring("enr:".length()) : enr)
            .map(nodeRecordFactory::fromBase64)
            .collect(Collectors.toList());
    return this;
  }

  public DiscoveryManagerBuilder bootnodes(final NodeRecord... records) {
    bootnodes = asList(records);
    return this;
  }

  public DiscoveryManagerBuilder database(final Database database) {
    this.database = database;
    return this;
  }

  public DiscoveryManagerBuilder schedulers(final Schedulers schedulers) {
    this.schedulers = schedulers;
    return this;
  }

  public DiscoveryManager build() {
    checkNotNull(localNodeRecord, "Missing local node record");
    checkNotNull(privateKey, "Missing private key");

    if (database == null) {
      database = Database.inMemoryDB();
    }
    final NodeTableStorageFactory nodeTableStorageFactory = new NodeTableStorageFactoryImpl();
    final NodeSerializerFactory serializerFactory = new NodeSerializerFactory(nodeRecordFactory);
    final NodeTableStorage nodeTable =
        nodeTableStorageFactory.createTable(
            database, serializerFactory, oldSeq -> localNodeRecord, () -> bootnodes);
    if (schedulers == null) {
      schedulers = Schedulers.createDefault();
    }
    final NodeBucketStorage nodeBucketStorage =
        new NodeBucketStorageImpl(database, serializerFactory, localNodeRecord);
    return new DiscoveryManagerImpl(
        nodeTable.get(),
        nodeBucketStorage,
        localNodeRecord,
        privateKey,
        nodeRecordFactory,
        schedulers.newSingleThreadDaemon("client-" + COUNTER.incrementAndGet()));
  }
}
