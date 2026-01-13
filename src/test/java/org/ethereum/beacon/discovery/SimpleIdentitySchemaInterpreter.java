/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes;
import org.apache.tuweni.units.bigints.UInt64;
import org.ethereum.beacon.discovery.crypto.Signer;
import org.ethereum.beacon.discovery.schema.EnrField;
import org.ethereum.beacon.discovery.schema.IdentitySchema;
import org.ethereum.beacon.discovery.schema.IdentitySchemaInterpreter;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordFactory;
import org.ethereum.beacon.discovery.storage.NewAddressHandler;

public class SimpleIdentitySchemaInterpreter implements IdentitySchemaInterpreter {

  public static final NewAddressHandler ADDRESS_UPDATER =
      (oldRecord, newAddress) ->
          Optional.of(
              oldRecord.withNewAddress(newAddress, Optional.empty(), Optional.empty(), null));

  public static NodeRecord createNodeRecord(final int nodeId) {
    return createNodeRecord(Bytes.ofUnsignedInt(nodeId));
  }

  public static NodeRecord createNodeRecord(
      final Bytes nodeId, final InetSocketAddress udpAddress) {
    return createNodeRecord(
        nodeId,
        new EnrField(EnrField.IP_V4, Bytes.wrap(udpAddress.getAddress().getAddress())),
        new EnrField(EnrField.UDP, udpAddress.getPort()));
  }

  public static NodeRecord createNodeRecord(final Bytes nodeId, final EnrField... extraFields) {
    final List<EnrField> fields = new ArrayList<>(List.of(extraFields));
    fields.add(new EnrField(EnrField.ID, IdentitySchema.V4));
    fields.add(new EnrField(EnrField.PKEY_SECP256K1, nodeId));
    return new NodeRecordFactory(new SimpleIdentitySchemaInterpreter())
        .createFromValues(UInt64.ONE, fields);
  }

  @Override
  public IdentitySchema getScheme() {
    return IdentitySchema.V4;
  }

  @Override
  public void sign(final NodeRecord nodeRecord, final Signer signer) {
    nodeRecord.setSignature(MutableBytes.create(96));
  }

  @Override
  public Bytes getNodeId(final NodeRecord nodeRecord) {
    Bytes prototype = (Bytes) nodeRecord.get(EnrField.PKEY_SECP256K1);
    // Aligning it for correct 32 bytes
    if (prototype.size() <= 32) {
      return Bytes32.leftPad(prototype);
    } else {
      return prototype.slice(0, 32);
    }
  }

  @Override
  public Optional<InetSocketAddress> getUdpAddress(final NodeRecord nodeRecord) {
    try {
      final Bytes ipBytes = (Bytes) nodeRecord.get(EnrField.IP_V4);
      if (ipBytes == null) {
        return Optional.empty();
      }
      final InetAddress ipAddress = InetAddress.getByAddress(ipBytes.toArrayUnsafe());
      final int port = (int) nodeRecord.get(EnrField.UDP);
      return Optional.of(new InetSocketAddress(ipAddress, port));
    } catch (UnknownHostException e) {
      return Optional.empty();
    }
  }

  @Override
  public Optional<InetSocketAddress> getUdp6Address(NodeRecord nodeRecord) {
    return Optional.empty();
  }

  @Override
  public Optional<InetSocketAddress> getTcpAddress(final NodeRecord nodeRecord) {
    return Optional.empty();
  }

  @Override
  public Optional<InetSocketAddress> getTcp6Address(NodeRecord nodeRecord) {
    return Optional.empty();
  }

  @Override
  public Optional<InetSocketAddress> getQuicAddress(NodeRecord nodeRecord) {
    return Optional.empty();
  }

  @Override
  public Optional<InetSocketAddress> getQuic6Address(NodeRecord nodeRecord) {
    return Optional.empty();
  }

  @Override
  public NodeRecord createWithNewAddress(
      final NodeRecord nodeRecord,
      final InetSocketAddress newAddress,
      final Optional<Integer> newTcpPort,
      final Optional<Integer> newQuicPort,
      final Signer signer) {
    final NodeRecord newRecord = createNodeRecord(getNodeId(nodeRecord), newAddress);
    sign(newRecord, signer);
    return newRecord;
  }

  @Override
  public NodeRecord createWithUpdatedCustomField(
      final NodeRecord nodeRecord, final String fieldName, final Bytes value, final Signer signer) {
    final List<EnrField> fields = new ArrayList<>();
    nodeRecord.forEachField(
        (key, existingValue) -> {
          if (!key.equals(fieldName)) {
            fields.add(new EnrField(key, existingValue));
          }
        });
    fields.add(new EnrField(fieldName, value));
    final NodeRecord newRecord = NodeRecord.fromValues(this, nodeRecord.getSeq().add(1), fields);
    sign(newRecord, signer);
    return newRecord;
  }

  @Override
  public Bytes calculateNodeId(final Bytes publicKey) {
    final NodeRecord nodeRecord =
        createNodeRecord(publicKey, new InetSocketAddress("127.0.0.1", 2));
    return nodeRecord.getNodeId();
  }
}
