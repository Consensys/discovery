/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.schema;

import com.google.common.base.Preconditions;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Optional;
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
    if (nodeRecord.get(EnrField.PKEY_SECP256K1) == null) {
      logger.trace(
          "Field {} does not exist but required for scheme {}",
          EnrField.PKEY_SECP256K1,
          getScheme());
      return false;
    }
    Bytes pubKey = (Bytes) nodeRecord.get(EnrField.PKEY_SECP256K1); // compressed
    return Functions.verifyECDSASignature(
        nodeRecord.getSignature(), Functions.hashKeccak(nodeRecord.serializeNoSignature()), pubKey);
  }

  @Override
  public IdentitySchema getScheme() {
    return IdentitySchema.V4;
  }

  @Override
  public Bytes getNodeId(NodeRecord nodeRecord) {
    Bytes pkey = (Bytes) nodeRecord.get(EnrField.PKEY_SECP256K1);
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

  @Override
  public Optional<InetSocketAddress> getUdpAddress(final NodeRecord nodeRecord) {
    return addressFromFields(nodeRecord, EnrField.IP_V4, EnrField.UDP)
        .or(() -> addressFromFields(nodeRecord, EnrField.IP_V6, EnrField.UDP_V6))
        .or(() -> addressFromFields(nodeRecord, EnrField.IP_V6, EnrField.UDP));
  }

  @Override
  public Optional<InetSocketAddress> getTcpAddress(final NodeRecord nodeRecord) {
    return addressFromFields(nodeRecord, EnrField.IP_V4, EnrField.TCP)
        .or(() -> addressFromFields(nodeRecord, EnrField.IP_V6, EnrField.TCP_V6))
        .or(() -> addressFromFields(nodeRecord, EnrField.IP_V6, EnrField.TCP));
  }

  private static Optional<InetSocketAddress> addressFromFields(
      final NodeRecord nodeRecord, final String ipField, final String portField) {
    if (!nodeRecord.containsKey(ipField) || !nodeRecord.containsKey(portField)) {
      return Optional.empty();
    }
    final Bytes ipBytes = (Bytes) nodeRecord.get(ipField);
    final int port = (int) nodeRecord.get(portField);
    try {
      return Optional.of(new InetSocketAddress(getInetAddress(ipBytes), port));
    } catch (final UnknownHostException e) {
      logger.trace("Unable to resolve host: {}", ipBytes);
      return Optional.empty();
    }
  }

  private static InetAddress getInetAddress(final Bytes address) throws UnknownHostException {
    return InetAddress.getByAddress(address.toArrayUnsafe());
  }
}
