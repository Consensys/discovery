/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.ethereum.beacon.discovery.schema;

import static com.google.common.base.Preconditions.checkArgument;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.crypto.SECP256K1.SecretKey;
import org.apache.tuweni.units.bigints.UInt64;
import org.ethereum.beacon.discovery.InMemorySecurityModule;
import org.ethereum.beacon.discovery.SecurityModule;

public class NodeRecordBuilder {

  private final List<EnrField> fields = new ArrayList<>();
  private NodeRecordFactory nodeRecordFactory = NodeRecordFactory.DEFAULT;
  private Optional<SecurityModule> securityModule = Optional.empty();
  private UInt64 seq = UInt64.ONE;

  public NodeRecordBuilder nodeRecordFactory(final NodeRecordFactory nodeRecordFactory) {
    this.nodeRecordFactory = nodeRecordFactory;
    return this;
  }

  public NodeRecordBuilder seq(final UInt64 seq) {
    this.seq = seq;
    return this;
  }

  public NodeRecordBuilder seq(final int seq) {
    return seq(UInt64.valueOf(seq));
  }

  public NodeRecordBuilder publicKey(final Bytes publicKey) {
    fields.add(new EnrField(EnrField.PKEY_SECP256K1, publicKey));
    return this;
  }

  public NodeRecordBuilder secretKey(final SecretKey secretKey) {
    this.securityModule = Optional.of(new InMemorySecurityModule(secretKey));
    publicKey(securityModule.get().deriveCompressedPublicKeyFromPrivate());
    return this;
  }

  public NodeRecordBuilder address(final String ipAddress, final int port) {
    return address(ipAddress, port, port);
  }

  public NodeRecordBuilder address(final String ipAddress, final int udpPort, final int tcpPort) {
    return address(ipAddress, udpPort, tcpPort, Optional.empty());
  }

  public NodeRecordBuilder address(
      final String ipAddress, final int udpPort, final int tcpPort, final int quicPort) {
    return address(ipAddress, udpPort, tcpPort, Optional.of(quicPort));
  }

  public NodeRecordBuilder address(
      final String ipAddress,
      final int udpPort,
      final int tcpPort,
      final Optional<Integer> quicPort) {
    try {
      final InetAddress inetAddress = InetAddress.getByName(ipAddress);
      addFieldsForAddress(fields, inetAddress, udpPort, Optional.of(tcpPort), quicPort);
    } catch (UnknownHostException e) {
      throw new IllegalArgumentException("Unable to resolve address: " + ipAddress);
    }
    return this;
  }

  public NodeRecordBuilder customField(final String fieldName, final Bytes value) {
    fields.add(new EnrField(fieldName, value));
    return this;
  }

  static void addFieldsForAddress(
      final List<EnrField> fields,
      final InetAddress inetAddress,
      final int udpPort,
      final Optional<Integer> newTcpPort,
      final Optional<Integer> newQuicPort) {
    final Bytes address = Bytes.wrap(inetAddress.getAddress());
    final boolean isIpV6 = inetAddress instanceof Inet6Address;
    fields.add(new EnrField(isIpV6 ? EnrField.IP_V6 : EnrField.IP_V4, address));
    fields.add(new EnrField(isIpV6 ? EnrField.UDP_V6 : EnrField.UDP, udpPort));
    newTcpPort.ifPresent(
        tcpPort -> fields.add(new EnrField(isIpV6 ? EnrField.TCP_V6 : EnrField.TCP, tcpPort)));
    newQuicPort.ifPresent(
        quicPort -> fields.add(new EnrField(isIpV6 ? EnrField.QUIC_V6 : EnrField.QUIC, quicPort)));
  }

  static void addCustomField(
      final List<EnrField> fields, final String fieldName, final Bytes value) {
    fields.add(new EnrField(fieldName, value));
  }

  public NodeRecord build() {
    fields.add(new EnrField(EnrField.ID, IdentitySchema.V4));
    final NodeRecord nodeRecord = nodeRecordFactory.createFromValues(seq, fields);
    securityModule.ifPresent(nodeRecord::sign);
    checkArgument(
        nodeRecord.isValid(),
        "Generated node record was not valid. Ensure all required fields were supplied");
    return nodeRecord;
  }
}
