/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.schema;

import static org.ethereum.beacon.discovery.schema.NodeRecordBuilder.addCustomField;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.bouncycastle.math.ec.ECPoint;
import org.ethereum.beacon.discovery.util.Functions;
import org.ethereum.beacon.discovery.util.Utils;

public class IdentitySchemaV4Interpreter implements IdentitySchemaInterpreter {
  private static final Logger logger = LogManager.getLogger();

  private static final ImmutableSet<String> ADDRESS_FIELD_NAMES =
      ImmutableSet.of(EnrField.IP_V4, EnrField.IP_V6, EnrField.UDP, EnrField.UDP_V6);

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

  @Override
  public NodeRecord createWithNewAddress(
      final NodeRecord nodeRecord, final InetSocketAddress newAddress, final Bytes privateKey) {
    final List<EnrField> fields =
        getAllFieldsThatMatch(nodeRecord, field -> !ADDRESS_FIELD_NAMES.contains(field.getName()));

    NodeRecordBuilder.addFieldsForUdpAddress(fields, newAddress.getAddress(), newAddress.getPort());
    final NodeRecord newRecord = NodeRecord.fromValues(this, nodeRecord.getSeq().add(1), fields);
    sign(newRecord, privateKey);
    return newRecord;
  }

  @Override
  public NodeRecord createWithUpdatedCustomField(
      NodeRecord nodeRecord, String fieldName, Bytes value, Bytes privateKey) {
    final List<EnrField> fields =
        getAllFieldsThatMatch(nodeRecord, field -> !field.getName().equals(fieldName));
    addCustomField(fields, fieldName, value);
    final NodeRecord newRecord = NodeRecord.fromValues(this, nodeRecord.getSeq().add(1), fields);
    sign(newRecord, privateKey);
    return newRecord;
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

  private static Stream<EnrField> getAllFields(final NodeRecord nodeRecord) {
    final List<EnrField> fields = new ArrayList<>();
    nodeRecord.forEachField((name, value) -> fields.add(new EnrField(name, value)));
    return fields.stream();
  }

  private static List<EnrField> getAllFieldsThatMatch(
      final NodeRecord nodeRecord, final Predicate<? super EnrField> predicate) {
    return getAllFields(nodeRecord).filter(predicate).collect(Collectors.toList());
  }
}
