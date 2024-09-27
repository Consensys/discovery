/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.schema;

import static org.ethereum.beacon.discovery.schema.NodeRecordBuilder.addCustomField;

import com.google.common.base.Preconditions;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableSet;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.crypto.SECP256K1.SecretKey;
import org.bouncycastle.math.ec.ECPoint;
import org.ethereum.beacon.discovery.util.Functions;
import org.ethereum.beacon.discovery.util.Utils;

public class IdentitySchemaV4Interpreter implements IdentitySchemaInterpreter {

  private static final Logger LOG = LogManager.getLogger();

  private final LoadingCache<Bytes, Bytes> nodeIdCache =
      CacheBuilder.newBuilder()
          .maximumSize(4000)
          .build(CacheLoader.from(IdentitySchemaV4Interpreter::calculateNodeIdImpl));

  private static final ImmutableSet<String> ADDRESS_IP_V4_FIELD_NAMES =
      ImmutableSet.of(EnrField.IP_V4, EnrField.UDP);

  private static final ImmutableSet<String> ADDRESS_IP_V6_FIELD_NAMES =
      ImmutableSet.of(EnrField.IP_V6, EnrField.UDP_V6);

  @Override
  public boolean isValid(final NodeRecord nodeRecord) {
    if (!IdentitySchemaInterpreter.super.isValid(nodeRecord)) {
      return false;
    }
    if (nodeRecord.get(EnrField.PKEY_SECP256K1) == null) {
      LOG.trace(
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
  public Bytes getNodeId(final NodeRecord nodeRecord) {
    Bytes pkey = (Bytes) nodeRecord.get(EnrField.PKEY_SECP256K1);
    Preconditions.checkNotNull(pkey, "Missing PKEY_SECP256K1 field");
    return nodeIdCache.getUnchecked(pkey);
  }

  private static Bytes calculateNodeIdImpl(final Bytes publicKey) {
    ECPoint pudDestPoint = Functions.publicKeyToPoint(publicKey);
    Bytes xPart =
        Bytes.wrap(
            Utils.extractBytesFromUnsignedBigInt(pudDestPoint.getXCoord().toBigInteger(), 32));
    Bytes yPart =
        Bytes.wrap(
            Utils.extractBytesFromUnsignedBigInt(pudDestPoint.getYCoord().toBigInteger(), 32));
    return Functions.hashKeccak(Bytes.concatenate(xPart, yPart));
  }

  @Override
  public void sign(final NodeRecord nodeRecord, final SecretKey secretKey) {
    Bytes signature =
        Functions.sign(secretKey, Functions.hashKeccak(nodeRecord.serializeNoSignature()));
    nodeRecord.setSignature(signature);
  }

  @Override
  public Optional<InetSocketAddress> getUdpAddress(final NodeRecord nodeRecord) {
    return addressFromFields(nodeRecord, EnrField.IP_V4, EnrField.UDP);
  }

  @Override
  public Optional<InetSocketAddress> getUdp6Address(NodeRecord nodeRecord) {
    return addressFromFields(nodeRecord, EnrField.IP_V6, EnrField.UDP_V6)
        .or(() -> addressFromFields(nodeRecord, EnrField.IP_V6, EnrField.UDP));
  }

  @Override
  public Optional<InetSocketAddress> getTcpAddress(final NodeRecord nodeRecord) {
    return addressFromFields(nodeRecord, EnrField.IP_V4, EnrField.TCP);
  }

  @Override
  public Optional<InetSocketAddress> getTcp6Address(NodeRecord nodeRecord) {
    return addressFromFields(nodeRecord, EnrField.IP_V6, EnrField.TCP_V6)
        .or(() -> addressFromFields(nodeRecord, EnrField.IP_V6, EnrField.TCP));
  }

  @Override
  public NodeRecord createWithNewAddress(
      final NodeRecord nodeRecord,
      final InetSocketAddress newAddress,
      final Optional<Integer> newTcpPort,
      final SecretKey secretKey) {
    final List<EnrField> fields =
        getAllFieldsThatMatch(
            nodeRecord,
            field -> {
              // don't match the address fields that we are going to change
              final Set<String> addressFieldsToChange =
                  newAddress.getAddress() instanceof Inet6Address
                      ? ADDRESS_IP_V6_FIELD_NAMES
                      : ADDRESS_IP_V4_FIELD_NAMES;
              return !addressFieldsToChange.contains(field.getName());
            });
    NodeRecordBuilder.addFieldsForAddress(
        fields, newAddress.getAddress(), newAddress.getPort(), newTcpPort);
    final NodeRecord newRecord = NodeRecord.fromValues(this, nodeRecord.getSeq().add(1), fields);
    sign(newRecord, secretKey);
    return newRecord;
  }

  @Override
  public NodeRecord createWithUpdatedCustomField(
      final NodeRecord nodeRecord,
      final String fieldName,
      final Bytes value,
      final SecretKey secretKey) {
    final List<EnrField> fields =
        getAllFieldsThatMatch(nodeRecord, field -> !field.getName().equals(fieldName));
    addCustomField(fields, fieldName, value);
    final NodeRecord newRecord = NodeRecord.fromValues(this, nodeRecord.getSeq().add(1), fields);
    sign(newRecord, secretKey);
    return newRecord;
  }

  @Override
  public Bytes calculateNodeId(final Bytes publicKey) {
    return nodeIdCache.getUnchecked(publicKey);
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
      LOG.trace("Unable to resolve host: {}", ipBytes);
      return Optional.empty();
    }
  }

  private static InetAddress getInetAddress(final Bytes address) throws UnknownHostException {
    return InetAddress.getByAddress(address.toArrayUnsafe());
  }

  private static Stream<EnrField> streamAllFields(final NodeRecord nodeRecord) {
    final List<EnrField> fields = new ArrayList<>();
    nodeRecord.forEachField((name, value) -> fields.add(new EnrField(name, value)));
    return fields.stream();
  }

  private static List<EnrField> getAllFieldsThatMatch(
      final NodeRecord nodeRecord, final Predicate<? super EnrField> predicate) {
    return streamAllFields(nodeRecord).filter(predicate).collect(Collectors.toList());
  }
}
