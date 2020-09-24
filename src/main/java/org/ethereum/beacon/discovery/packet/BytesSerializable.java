/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.ethereum.beacon.discovery.packet;

import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.util.DecodeException;

/** Any class which can be encoded/decoded to/from {@link Bytes} and is backed by {@link Bytes} */
public interface BytesSerializable {

  /**
   * Validates that backed bytes are valid serialization of class
   *
   * @throws DecodeException if not valid
   */
  void validate() throws DecodeException;

  /** Return backing bytes */
  Bytes getBytes();
}
