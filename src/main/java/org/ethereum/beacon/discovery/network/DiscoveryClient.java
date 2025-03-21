/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.network;

import java.net.InetSocketAddress;
import org.apache.tuweni.v2.bytes.Bytes;

/** Discovery client sends outgoing messages */
public interface DiscoveryClient {
  void stop();

  void send(Bytes data, InetSocketAddress destination);
}
