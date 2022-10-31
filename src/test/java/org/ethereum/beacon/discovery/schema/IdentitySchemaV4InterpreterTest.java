/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.crypto.SECP256K1.SecretKey;
import org.apache.tuweni.rlp.RLP;
import org.apache.tuweni.units.bigints.UInt64;
import org.ethereum.beacon.discovery.util.DecodeException;
import org.ethereum.beacon.discovery.util.Functions;
import org.junit.jupiter.api.Test;

class IdentitySchemaV4InterpreterTest {

  private static final Bytes PUB_KEY =
      Bytes.fromHexString("0x02197B9014C6C0500CF168BD1F17A3B4A1307251849A5ECEEE0B5EBC76A7EBDB37");
  private static final Bytes32 PRIV_KEY =
      Bytes32.fromHexString("0x2E953344686E18C99CDE5292D822D4427BDC5B473F3A6D69D6D0D897D9595110");
  private static final SecretKey SECRET_KEY = Functions.createSecretKey(PRIV_KEY);
  private static final Bytes IPV6_LOCALHOST =
      Bytes.fromHexString("0x00000000000000000000000000000001");

  private final IdentitySchemaV4Interpreter interpreter = new IdentitySchemaV4Interpreter();

  @Test
  public void shouldNotHaveTcpAddressForRecordWithNoIp() {
    assertThat(getTcpAddressForNodeRecordWithFields()).isEmpty();
  }

  @Test
  public void shouldNotHaveTcpAddressForRecordWithIpButNoPort() {
    assertThat(
            getTcpAddressForNodeRecordWithFields(
                new EnrField(EnrField.IP_V4, Bytes.wrap(new byte[] {127, 0, 0, 1}))))
        .isEmpty();
  }

  @Test
  public void shouldNotHaveTcpAddressForRecordWithIpAndUdpPortButNoTcpPort() {
    assertThat(
            getTcpAddressForNodeRecordWithFields(
                new EnrField(EnrField.IP_V4, Bytes.wrap(new byte[] {127, 0, 0, 1})),
                new EnrField(EnrField.UDP, 30303)))
        .isEmpty();
  }

  @Test
  public void shouldUseV4TcpPortIfV6IpSpecifiedWithNoV6TcpPort() {
    assertThat(
            getTcpAddressForNodeRecordWithFields(
                new EnrField(EnrField.IP_V6, IPV6_LOCALHOST),
                new EnrField(EnrField.TCP, 30303),
                new EnrField(EnrField.UDP_V6, 100)))
        .contains(new InetSocketAddress("::1", 30303));
  }

  @Test
  public void shouldNotHaveTcpAddressForRecordWithV4IpAndV6TcpPort() {
    assertThat(
            getTcpAddressForNodeRecordWithFields(
                new EnrField(EnrField.IP_V4, IPV6_LOCALHOST), new EnrField(EnrField.TCP_V6, 30303)))
        .isEmpty();
  }

  @Test
  public void shouldNotHaveTcpAddressForRecordWithPortButNoIp() {
    assertThat(getTcpAddressForNodeRecordWithFields(new EnrField(EnrField.TCP, 30303))).isEmpty();
  }

  @Test
  public void shouldGetTcpInetAddressForIpV4Record() {
    // IP address bytes are unsigned. Make sure we handle that correctly.
    final Optional<InetSocketAddress> result =
        getTcpAddressForNodeRecordWithFields(
            new EnrField(EnrField.IP_V4, Bytes.wrap(new byte[] {-127, 24, 31, 22})),
            new EnrField(EnrField.TCP, 1234));
    assertThat(result).contains(new InetSocketAddress("129.24.31.22", 1234));
  }

  @Test
  public void shouldGetTcpInetAddressForIpV6Record() {
    final Optional<InetSocketAddress> result =
        getTcpAddressForNodeRecordWithFields(
            new EnrField(EnrField.IP_V6, IPV6_LOCALHOST), new EnrField(EnrField.TCP_V6, 1234));
    assertThat(result).contains(new InetSocketAddress("::1", 1234));
  }

  @Test
  public void shouldNotHaveUdpAddressForRecordWithNoIp() {
    assertThat(getUdpAddressForNodeRecordWithFields()).isEmpty();
  }

  @Test
  public void shouldNotHaveUdpAddressForRecordWithIpButNoPort() {
    assertThat(
            getUdpAddressForNodeRecordWithFields(
                new EnrField(EnrField.IP_V4, Bytes.wrap(new byte[] {127, 0, 0, 1}))))
        .isEmpty();
  }

  @Test
  public void shouldNotHaveUdpAddressForRecordWithIpAndUdpPortButNoUdpPort() {
    assertThat(
            getUdpAddressForNodeRecordWithFields(
                new EnrField(EnrField.IP_V4, Bytes.wrap(new byte[] {127, 0, 0, 1})),
                new EnrField(EnrField.TCP, 30303)))
        .isEmpty();
  }

  @Test
  public void shouldUseV4UdpPortIfV6IpSpecifiedWithNoV6UdpPort() {
    assertThat(
            getUdpAddressForNodeRecordWithFields(
                new EnrField(EnrField.IP_V6, IPV6_LOCALHOST),
                new EnrField(EnrField.UDP, 30303),
                new EnrField(EnrField.TCP_V6, 100)))
        .contains(new InetSocketAddress("::1", 30303));
  }

  @Test
  public void shouldNotHaveUdpAddressForRecordWithV4IpAndV6UdpPort() {
    assertThat(
            getUdpAddressForNodeRecordWithFields(
                new EnrField(EnrField.IP_V4, IPV6_LOCALHOST), new EnrField(EnrField.UDP_V6, 30303)))
        .isEmpty();
  }

  @Test
  public void shouldNotHaveUdpAddressForRecordWithPortButNoIp() {
    assertThat(getUdpAddressForNodeRecordWithFields(new EnrField(EnrField.UDP, 30303))).isEmpty();
  }

  @Test
  public void shouldGetUdpInetAddressForIpV4Record() {
    // IP address bytes are unsigned. Make sure we handle that correctly.
    final Optional<InetSocketAddress> result =
        getUdpAddressForNodeRecordWithFields(
            new EnrField(EnrField.IP_V4, Bytes.wrap(new byte[] {-127, 24, 31, 22})),
            new EnrField(EnrField.UDP, 1234));
    assertThat(result).contains(new InetSocketAddress("129.24.31.22", 1234));
  }

  @Test
  public void shouldGetUdpInetAddressForIpV6Record() {
    final Optional<InetSocketAddress> result =
        getTcpAddressForNodeRecordWithFields(
            new EnrField(EnrField.IP_V6, IPV6_LOCALHOST), new EnrField(EnrField.TCP_V6, 1234));
    assertThat(result).contains(new InetSocketAddress("::1", 1234));
  }

  @Test
  public void shouldUpdateIpV4AddressAndPort() {
    final NodeRecord initialRecord =
        createNodeRecord(
            new EnrField(EnrField.IP_V4, Bytes.wrap(new byte[4])),
            new EnrField(EnrField.UDP, 3030));
    final InetSocketAddress newSocketAddress = new InetSocketAddress("127.0.0.1", 40404);
    final NodeRecord newRecord =
        interpreter.createWithNewAddress(
            initialRecord, newSocketAddress, Optional.of(5667), SECRET_KEY);

    assertThat(newRecord.getUdpAddress()).contains(newSocketAddress);
    assertThat(newRecord.getTcpAddress())
        .contains(new InetSocketAddress(newSocketAddress.getAddress(), 5667));
    assertThat(newRecord.get(EnrField.IP_V4))
        .isEqualTo(Bytes.wrap(newSocketAddress.getAddress().getAddress()));
  }

  @Test
  public void shouldUpdateCustomFieldValue() {
    final String customFieldName = "custom_field_name";
    final Bytes customFieldValue1 = Bytes.fromHexString("0xdeadbeef");
    final Bytes customFieldValue2 = Bytes.fromHexString("0xbeef");
    final NodeRecord initialRecord =
        createNodeRecord(
            new EnrField(EnrField.IP_V4, Bytes.wrap(new byte[4])),
            new EnrField(EnrField.UDP, 3030),
            new EnrField(customFieldName, customFieldValue1));

    assertThat(initialRecord.get(customFieldName)).isEqualTo(customFieldValue1);

    final NodeRecord newRecord =
        interpreter.createWithUpdatedCustomField(
            initialRecord, customFieldName, customFieldValue2, SECRET_KEY);

    assertThat(newRecord.get(customFieldName)).isEqualTo(customFieldValue2);
  }

  @Test
  public void shouldUpdateIpV6AddressAndPort() throws Exception {
    final NodeRecord initialRecord =
        createNodeRecord(
            new EnrField(EnrField.IP_V6, Bytes.wrap(new byte[16])),
            new EnrField(EnrField.UDP_V6, 3030));
    final InetSocketAddress newSocketAddress =
        new InetSocketAddress(InetAddress.getByAddress(IPV6_LOCALHOST.toArrayUnsafe()), 40404);
    final NodeRecord newRecord =
        interpreter.createWithNewAddress(
            initialRecord, newSocketAddress, Optional.of(5667), SECRET_KEY);

    assertThat(newRecord.getUdpAddress()).contains(newSocketAddress);
    assertThat(newRecord.getTcpAddress())
        .contains(new InetSocketAddress(newSocketAddress.getAddress(), 5667));
    assertThat(newRecord.get(EnrField.IP_V6)).isEqualTo(IPV6_LOCALHOST);
  }

  @Test
  public void shouldSwitchFromIpV4ToIpV6() throws Exception {
    final NodeRecord initialRecord =
        createNodeRecord(
            new EnrField(EnrField.IP_V4, Bytes.wrap(new byte[4])),
            new EnrField(EnrField.UDP, 3030));
    final InetSocketAddress newSocketAddress =
        new InetSocketAddress(InetAddress.getByAddress(IPV6_LOCALHOST.toArrayUnsafe()), 40404);
    final NodeRecord newRecord =
        interpreter.createWithNewAddress(
            initialRecord, newSocketAddress, Optional.of(5667), SECRET_KEY);

    assertThat(newRecord.getUdpAddress()).contains(newSocketAddress);
    assertThat(newRecord.getTcpAddress())
        .contains(new InetSocketAddress(newSocketAddress.getAddress(), 5667));
    assertThat(newRecord.get(EnrField.IP_V6)).isEqualTo(IPV6_LOCALHOST);
  }

  @Test
  public void shouldSwitchFromIpV6ToIpV4() {
    final NodeRecord initialRecord =
        createNodeRecord(
            new EnrField(EnrField.IP_V6, IPV6_LOCALHOST), new EnrField(EnrField.UDP_V6, 3030));
    final InetSocketAddress newSocketAddress = new InetSocketAddress("127.0.0.1", 40404);
    final NodeRecord newRecord =
        interpreter.createWithNewAddress(
            initialRecord, newSocketAddress, Optional.of(5667), SECRET_KEY);

    assertThat(newRecord.getUdpAddress()).contains(newSocketAddress);
    assertThat(newRecord.getTcpAddress())
        .contains(new InetSocketAddress(newSocketAddress.getAddress(), 5667));
    assertThat(newRecord.get(EnrField.IP_V4)).isEqualTo(Bytes.wrap(new byte[] {127, 0, 0, 1}));
  }

  @Test
  public void enrDeserializationWithDuplicateFieldKeyShouldFail() {
    NodeRecord nodeRecord = createNodeRecord(new EnrField(EnrField.TCP, 1234));
    final List<String> keys = new ArrayList<>();
    nodeRecord.forEachField((key, value) -> keys.add(key));
    keys.sort(Comparator.naturalOrder());
    keys.add(keys.get(keys.size() - 1)); // Duplicate the last key
    Bytes duplicateEntryBytes = RLP.encode(writer -> nodeRecord.writeRlp(writer, true, keys));
    assertThatThrownBy(() -> NodeRecordFactory.DEFAULT.fromBytes(duplicateEntryBytes))
        .isInstanceOf(DecodeException.class);
  }

  @Test
  public void enrDeserializationWithWrongKeyOrderShouldFail() {
    NodeRecord nodeRecord =
        createNodeRecord(new EnrField(EnrField.TCP, 1234), new EnrField(EnrField.UDP, 5678));

    final List<String> keys = new ArrayList<>();
    nodeRecord.forEachField((key, value) -> keys.add(key));
    keys.sort(Comparator.<String>naturalOrder().reversed()); // Reversed order
    Bytes invalidEnrBytes = RLP.encode(writer -> nodeRecord.writeRlp(writer, true, keys));
    assertThatThrownBy(() -> NodeRecordFactory.DEFAULT.fromBytes(invalidEnrBytes))
        .isInstanceOf(DecodeException.class);
  }

  private Optional<InetSocketAddress> getTcpAddressForNodeRecordWithFields(
      final EnrField... fields) {
    return interpreter.getTcpAddress(createNodeRecord(fields));
  }

  private Optional<InetSocketAddress> getUdpAddressForNodeRecordWithFields(
      final EnrField... fields) {
    return interpreter.getUdpAddress(createNodeRecord(fields));
  }

  private NodeRecord createNodeRecord(final EnrField... fields) {
    final ArrayList<EnrField> fieldList = new ArrayList<>(Arrays.asList(fields));
    fieldList.add(new EnrField(EnrField.ID, IdentitySchema.V4));
    fieldList.add(new EnrField(EnrField.PKEY_SECP256K1, PUB_KEY));
    return NodeRecordFactory.DEFAULT.createFromValues(UInt64.ZERO, fieldList);
  }
}
