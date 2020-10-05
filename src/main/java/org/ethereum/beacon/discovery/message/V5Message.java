/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.message;

import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.schema.DiscoveryProtocol;

/** Message of V5 discovery protocol version */
public interface V5Message extends Message {

  int MAX_REQUEST_ID_SIZE = 8;

  MessageCode getCode();

  Bytes getRequestId();

  @Override
  default DiscoveryProtocol getDiscoveryProtocol() {
    return DiscoveryProtocol.V5;
  }
}
