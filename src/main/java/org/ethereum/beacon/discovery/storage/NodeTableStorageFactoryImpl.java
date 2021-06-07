/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.storage;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import org.apache.tuweni.units.bigints.UInt64;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordInfo;

public class NodeTableStorageFactoryImpl implements NodeTableStorageFactory {

  /**
   * Creates storage for nodes table
   *
   * @param homeNodeProvider Home node provider, accepts old sequence number of home node, usually
   *     sequence number is increased by 1 on each restart and ENR is signed with new sequence
   *     number
   * @param bootNodesSupplier boot nodes provider
   * @return {@link NodeTableStorage} from `database` but if it doesn't exist, creates new one with
   *     home node provided by `homeNodeSupplier` and boot nodes provided with `bootNodesSupplier`.
   *     Uses `serializerFactory` for node records serialization.
   */
  @Override
  public NodeTableStorage createTable(
      Function<UInt64, NodeRecord> homeNodeProvider, Supplier<List<NodeRecord>> bootNodesSupplier) {
    NodeTableStorage nodeTableStorage = new NodeTableStorageImpl();

    // Init storage with boot nodes
    bootNodesSupplier
        .get()
        .forEach(
            nodeRecord -> {
              checkArgument(nodeRecord.isValid(), "Invalid bootnode: " + nodeRecord.asEnr());
              NodeRecordInfo nodeRecordInfo = NodeRecordInfo.createDefault(nodeRecord);
              nodeTableStorage.get().save(nodeRecordInfo);
            });
    // Rewrite home node with updated sequence number on init
    NodeRecord updatedHomeNodeRecord = homeNodeProvider.apply(UInt64.ZERO);
    checkArgument(updatedHomeNodeRecord.isValid(), "Local node record is invalid");
    nodeTableStorage.getHomeNodeSource().set(NodeRecordInfo.createDefault(updatedHomeNodeRecord));

    return nodeTableStorage;
  }

  @Override
  public NodeBucketStorage createBucketStorage(NodeRecord homeNode) {
    return new NodeBucketStorageImpl(homeNode);
  }
}
