/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.message.handler;

import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.Optional;

public interface ExternalAddressSelector {

  ExternalAddressSelector NOOP = (_1, _2, _3) -> {};

  void onExternalAddressReport(
      final Optional<InetSocketAddress> previouslyReportedAddress,
      final InetSocketAddress reportedAddress,
      final Instant reportedTime);
}
