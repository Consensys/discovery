/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.schema;

import org.web3j.rlp.RlpType;

/** Encoder/decoder for fields of ethereum node record */
public interface EnrFieldInterpreter {
  Object decode(String key, RlpType rlpType);

  RlpType encode(String key, Object object);
}
