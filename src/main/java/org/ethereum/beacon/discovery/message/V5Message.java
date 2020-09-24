/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.message;

import org.apache.tuweni.bytes.Bytes;

/** Message of V5 discovery protocol version */
public interface V5Message {

  Bytes getRequestId();

  Bytes getBytes();
}
