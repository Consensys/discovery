/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.pipeline.handler;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.AddressAccessPolicy;
import org.ethereum.beacon.discovery.pipeline.Envelope;
import org.ethereum.beacon.discovery.pipeline.Field;
import org.junit.jupiter.api.Test;

class PacketSourceFilterTest {

  private static final InetSocketAddress DISALLOWED_ADDRESS =
      new InetSocketAddress(InetAddress.getLoopbackAddress(), 12345);
  private static final InetSocketAddress ALLOWED_ADDRESS =
      new InetSocketAddress(InetAddress.getLoopbackAddress(), 8080);

  private final AddressAccessPolicy addressAccessPolicy =
      address -> !address.equals(DISALLOWED_ADDRESS);

  private final PacketSourceFilter filter = new PacketSourceFilter(addressAccessPolicy);

  @Test
  void shouldAllowPacketsWhenSourceAllowed() {
    final Envelope envelope = new Envelope();
    final Bytes incoming = Bytes.fromHexString("0x1234");
    envelope.put(Field.INCOMING, incoming);
    envelope.put(Field.REMOTE_SENDER, ALLOWED_ADDRESS);
    filter.handle(envelope);

    assertThat(envelope.get(Field.INCOMING)).isEqualTo(incoming);
  }

  @Test
  void shouldDropPacketsWhenSourceDisallowed() {
    final Envelope envelope = new Envelope();
    final Bytes incoming = Bytes.fromHexString("0x1234");
    envelope.put(Field.INCOMING, incoming);
    envelope.put(Field.REMOTE_SENDER, DISALLOWED_ADDRESS);
    filter.handle(envelope);

    assertThat(envelope.get(Field.INCOMING)).isNull();
  }
}
