/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.ethereum.beacon.discovery.message;

import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.schema.DiscoveryProtocol;

/** Abstract discovery message */
public interface Message {

  DiscoveryProtocol getDiscoveryProtocol();

  Bytes getBytes();
}
