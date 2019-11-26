/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.schema;

import org.web3j.rlp.RlpString;
import org.web3j.rlp.RlpType;

/** Encoder/decoder for fields of ethereum node record */
public interface EnrFieldInterpreter {
  Object decode(String key, RlpString rlpString);

  RlpType encode(String key, Object object);
}
