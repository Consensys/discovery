/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery;

import java.net.InetSocketAddress;
import org.ethereum.beacon.discovery.schema.NodeRecord;

@FunctionalInterface
public interface AddressAccessPolicy {
  AddressAccessPolicy ALLOW_ALL = __ -> true;

  boolean allow(InetSocketAddress address);

  default boolean allow(NodeRecord record) {
    return record.getTcpAddress().map(this::allow).orElse(true);
  }
}
