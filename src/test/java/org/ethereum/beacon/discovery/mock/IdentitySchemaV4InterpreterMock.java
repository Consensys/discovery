/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.mock;

import org.apache.tuweni.bytes.MutableBytes;
import org.apache.tuweni.crypto.SECP256K1.SecretKey;
import org.ethereum.beacon.discovery.schema.IdentitySchemaV4Interpreter;
import org.ethereum.beacon.discovery.schema.NodeRecord;

public class IdentitySchemaV4InterpreterMock extends IdentitySchemaV4Interpreter {
  @Override
  public boolean isValid(NodeRecord nodeRecord) {
    // Don't verify signature
    return true;
  }

  @Override
  public void sign(NodeRecord nodeRecord, SecretKey secretKey) {
    nodeRecord.setSignature(MutableBytes.create(96));
  }
}
