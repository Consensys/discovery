/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.schema;

import org.apache.tuweni.v2.rlp.RLPWriter;

/** Encoder/decoder for fields of ethereum node record */
public interface EnrFieldInterpreter {
  Object decode(String key, Object rlpType);

  void encode(RLPWriter writer, String key, Object object);
}
