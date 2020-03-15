package org.ethereum.beacon.discovery.schema;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt64;
import org.junit.jupiter.api.Test;

class IdentitySchemaV4InterpreterTest {

  private static final Bytes PUB_KEY =
      Bytes.fromHexString("0x0295A5A50F083697FF8557F3C6FE0CDF8E8EC2141D15F19A5A45571ED9C38CE181");
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
