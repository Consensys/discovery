/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.message;

import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.schema.Protocol;

/** Discovery message */
public interface DiscoveryMessage {
  Protocol getProtocol();

  Bytes getBytes();
}
