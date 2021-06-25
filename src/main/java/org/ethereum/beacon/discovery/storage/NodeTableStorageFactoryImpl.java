/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.storage;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.List;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordInfo;

public class NodeTableStorageFactoryImpl implements NodeTableStorageFactory {

  /**
   * Creates storage for nodes table
   *
   * @param bootnodes list of boot nodes
   * @return {@link NodeTableStorage} from `database` but if it doesn't exist, creates new one with
   *     home node provided by `homeNodeSupplier` and boot nodes provided with `bootNodesSupplier`.
   *     Uses `serializerFactory` for node records serialization.
   */
  @Override
  public NodeTableStorage createTable(List<NodeRecord> bootnodes) {
    NodeTableStorage nodeTableStorage = new NodeTableStorageImpl();

    // Init storage with boot nodes
    bootnodes.forEach(
        nodeRecord -> {
          checkArgument(nodeRecord.isValid(), "Invalid bootnode: " + nodeRecord.asEnr());
          NodeRecordInfo nodeRecordInfo = NodeRecordInfo.createDefault(nodeRecord);
          nodeTableStorage.get().save(nodeRecordInfo);
        });
    return nodeTableStorage;
  }
}
