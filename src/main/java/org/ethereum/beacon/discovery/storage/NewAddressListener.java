/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.storage;

import org.ethereum.beacon.discovery.schema.NodeRecord;

import java.net.InetSocketAddress;
import java.util.Optional;

/** Listens for a node record updates */
public interface NewAddressListener {

  NewAddressListener NOOP = (oldVal, newVal) -> Optional.of(newVal);

  Optional<NodeRecord> newAddress(NodeRecord oldRecord, NodeRecord proposedRecord);
}
