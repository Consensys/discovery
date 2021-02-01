/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.storage;

import java.util.Optional;
import org.ethereum.beacon.discovery.schema.NodeRecord;

/** Listens for a node record updates */
public interface NewAddressHandler {

  NewAddressHandler NOOP = (oldVal, newVal) -> Optional.of(newVal);

  Optional<NodeRecord> newAddress(NodeRecord oldRecord, NodeRecord proposedRecord);
}
