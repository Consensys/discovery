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
import org.apache.tuweni.bytes.MutableBytes;
import org.apache.tuweni.units.bigints.UInt64;
import org.ethereum.beacon.discovery.schema.EnrField;
import org.ethereum.beacon.discovery.schema.IdentitySchema;
import org.ethereum.beacon.discovery.schema.IdentitySchemaInterpreter;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordFactory;

public class SimpleIdentitySchemaInterpreter implements IdentitySchemaInterpreter {

  public static NodeRecord createNodeRecord(
      final Bytes nodeId, final InetSocketAddress udpAddress) {
    return new NodeRecordFactory(new SimpleIdentitySchemaInterpreter())
        .createFromValues(
            UInt64.ONE,
            new EnrField(EnrField.ID, IdentitySchema.V4),
            new EnrField(EnrField.PKEY_SECP256K1, nodeId),
            new EnrField(EnrField.IP_V4, Bytes.wrap(udpAddress.getAddress().getAddress())),
            new EnrField(EnrField.UDP, udpAddress.getPort()));
  }

  public static NodeRecord createNodeRecord(final Bytes nodeId) {
    return new NodeRecordFactory(new SimpleIdentitySchemaInterpreter())
        .createFromValues(
            UInt64.ONE,
            new EnrField(EnrField.ID, IdentitySchema.V4),
            new EnrField(EnrField.PKEY_SECP256K1, nodeId));
  }

  @Override
  public IdentitySchema getScheme() {
    return IdentitySchema.V4;
  }

  @Override
  public void sign(final NodeRecord nodeRecord, final Bytes privateKey) {
    nodeRecord.setSignature(MutableBytes.create(96));
  }

  @Override
  public Bytes getNodeId(final NodeRecord nodeRecord) {
    return (Bytes) nodeRecord.get(EnrField.PKEY_SECP256K1);
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
  public Optional<InetSocketAddress> getTcpAddress(final NodeRecord nodeRecord) {
    return Optional.empty();
  }

  @Override
  public NodeRecord createWithNewAddress(
      final NodeRecord nodeRecord, final InetSocketAddress newAddress, final Bytes privateKey) {
    final NodeRecord newRecord = createNodeRecord(getNodeId(nodeRecord), newAddress);
    sign(newRecord, privateKey);
    return newRecord;
  }

  @Override
  public NodeRecord createWithUpdatedCustomField(
      NodeRecord nodeRecord, String fieldName, Bytes value, Bytes privateKey) {
    final List<EnrField> fields = new ArrayList<>();
    nodeRecord.forEachField(
        (key, existingValue) -> {
          if (!key.equals(fieldName)) {
            fields.add(new EnrField(key, existingValue));
          }
        });
    fields.add(new EnrField(fieldName, value));
    final NodeRecord newRecord = NodeRecord.fromValues(this, nodeRecord.getSeq().add(1), fields);
    sign(newRecord, privateKey);
    return newRecord;
  }
}
