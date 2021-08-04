/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.storage;

import java.net.InetSocketAddress;
import java.util.Optional;
import org.ethereum.beacon.discovery.schema.NodeRecord;

/** Listens for a node record updates */
public interface NewAddressHandler {

  NewAddressHandler NOOP = (oldRecord, newAddress) -> Optional.empty();

  Optional<NodeRecord> newAddress(NodeRecord oldRecord, InetSocketAddress newAddress);
}
