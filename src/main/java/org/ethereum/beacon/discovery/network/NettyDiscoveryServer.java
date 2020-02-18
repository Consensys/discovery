/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.network;

import io.netty.channel.socket.nio.NioDatagramChannel;
import java.util.concurrent.CompletableFuture;

/** Netty-specific extension of {@link DiscoveryServer}. Made to reuse server channel for client. */
public interface NettyDiscoveryServer extends DiscoveryServer {

  @Override
  CompletableFuture<NioDatagramChannel> start();
}
