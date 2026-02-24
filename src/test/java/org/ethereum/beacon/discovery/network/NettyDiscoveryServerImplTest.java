/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.ethereum.beacon.discovery.network;

import static org.assertj.core.api.Assertions.assertThat;

import io.netty.channel.socket.nio.NioDatagramChannel;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

public class NettyDiscoveryServerImplTest {

  @Test
  void getListenAddressReturnsOriginalAddressBeforeStart() {
    final InetSocketAddress address = new InetSocketAddress("127.0.0.1", 30303);
    final NettyDiscoveryServerImpl server = new NettyDiscoveryServerImpl(address, 0);
    assertThat(server.getListenAddress()).isEqualTo(address);
  }

  @Test
  void getListenAddressReturnsActualBoundPortAfterStartWithEphemeralPort() throws Exception {
    final NettyDiscoveryServerImpl server =
        new NettyDiscoveryServerImpl(new InetSocketAddress("127.0.0.1", 0), 0);
    assertThat(server.getListenAddress().getPort()).isEqualTo(0);

    final NioDatagramChannel channel = server.start().get(10, TimeUnit.SECONDS);
    try {
      final int actualPort = server.getListenAddress().getPort();
      assertThat(actualPort).isNotEqualTo(0);
      assertThat(actualPort).isEqualTo(channel.localAddress().getPort());
    } finally {
      server.stop();
    }
  }
}
