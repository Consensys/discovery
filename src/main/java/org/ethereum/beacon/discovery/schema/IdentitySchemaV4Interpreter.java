/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.schema;

import com.google.common.base.Preconditions;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.bouncycastle.math.ec.ECPoint;
import org.ethereum.beacon.discovery.util.Functions;
import org.ethereum.beacon.discovery.util.Utils;

public class IdentitySchemaV4Interpreter implements IdentitySchemaInterpreter {
  private static final Logger logger = LogManager.getLogger();

  @Override
  public boolean isValid(NodeRecord nodeRecord) {
    if (!IdentitySchemaInterpreter.super.isValid(nodeRecord)) {
      return false;
    }
    if (nodeRecord.get(EnrFieldV4.PKEY_SECP256K1) == null) {
      logger.trace(
          "Field {} does not exist but required for scheme {}",
          EnrFieldV4.PKEY_SECP256K1,
          getScheme());
      return false;
    }
    Bytes pubKey = (Bytes) nodeRecord.get(EnrFieldV4.PKEY_SECP256K1); // compressed
    return Functions.verifyECDSASignature(
        nodeRecord.getSignature(), Functions.hashKeccak(nodeRecord.serializeNoSignature()), pubKey);
  }

  @Override
  public IdentitySchema getScheme() {
    return IdentitySchema.V4;
  }

  @Override
  public Bytes getNodeId(NodeRecord nodeRecord) {
    Bytes pkey = (Bytes) nodeRecord.get(EnrFieldV4.PKEY_SECP256K1);
    Preconditions.checkNotNull(pkey, "Missing PKEY_SECP256K1 field");
    ECPoint pudDestPoint = Functions.publicKeyToPoint(pkey);
    Bytes xPart =
        Bytes.wrap(
            Utils.extractBytesFromUnsignedBigInt(pudDestPoint.getXCoord().toBigInteger(), 32));
    Bytes yPart =
        Bytes.wrap(
            Utils.extractBytesFromUnsignedBigInt(pudDestPoint.getYCoord().toBigInteger(), 32));
    return Functions.hashKeccak(Bytes.concatenate(xPart, yPart));
  }

  @Override
  public void sign(NodeRecord nodeRecord, Bytes privateKey) {
    Bytes signature =
        Functions.sign(privateKey, Functions.hashKeccak(nodeRecord.serializeNoSignature()));
    nodeRecord.setSignature(signature);
  }
}
