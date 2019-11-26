/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.mock;

import org.apache.tuweni.bytes.MutableBytes;
import org.ethereum.beacon.discovery.schema.IdentitySchemaV4Interpreter;
import org.ethereum.beacon.discovery.schema.NodeRecord;

public class IdentitySchemaV4InterpreterMock extends IdentitySchemaV4Interpreter {
  @Override
  public void verify(NodeRecord nodeRecord) {
    // Don't verify signature
  }

  @Override
  public void sign(NodeRecord nodeRecord, Object signOptions) {
    nodeRecord.setSignature(MutableBytes.create(96));
  }
}
