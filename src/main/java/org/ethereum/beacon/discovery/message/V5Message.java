/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.message;

import org.apache.tuweni.bytes.Bytes;

/** Message of V5 discovery protocol version */
public interface V5Message {

  int MAX_REQUEST_ID_SIZE = 8;

  Bytes getRequestId();

  Bytes getBytes();
}
