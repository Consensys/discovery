/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.schema;

import java.net.InetSocketAddress;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;

/**
 * Interprets identity schema of ethereum node record:
 *
 * <ul>
 *   <li>derives node id from node record
 *   <li>>signs node record
 *   <li>verifies signature of node record
 * </ul>
 */
public interface IdentitySchemaInterpreter {
  /** Returns supported scheme */
  IdentitySchema getScheme();

  /* Signs nodeRecord, modifying it */
  void sign(NodeRecord nodeRecord, Bytes privateKey);

  /** Verifies that `nodeRecord` is of scheme implementation */
  default boolean isValid(NodeRecord nodeRecord) {
    return nodeRecord.getIdentityScheme().equals(getScheme());
  }

  /** Delivers nodeId according to identity scheme scheme */
  Bytes getNodeId(NodeRecord nodeRecord);

  Optional<InetSocketAddress> getUdpAddress(NodeRecord nodeRecord);

  Optional<InetSocketAddress> getTcpAddress(NodeRecord nodeRecord);

  NodeRecord createWithNewAddress(
      NodeRecord nodeRecord, InetSocketAddress newAddress, Bytes privateKey);
}
