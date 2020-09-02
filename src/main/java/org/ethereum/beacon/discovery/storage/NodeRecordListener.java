/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.storage;

import org.ethereum.beacon.discovery.schema.NodeRecord;

/** Listens for a node record updates */
public interface NodeRecordListener {

  void recordUpdated(NodeRecord oldRecord, NodeRecord newRecord);
}
