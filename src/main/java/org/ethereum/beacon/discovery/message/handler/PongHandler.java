/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.message.handler;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.message.PongMessage;
import org.ethereum.beacon.discovery.schema.NodeSession;

public class PongHandler implements MessageHandler<PongMessage> {
  private static final Logger LOG = LogManager.getLogger();
  private final ExternalAddressSelector externalAddressSelector;
  private final EnrUpdateTracker enrUpdateTracker;

  public PongHandler(
      final ExternalAddressSelector externalAddressSelector,
      final EnrUpdateTracker enrUpdateTracker) {
    this.externalAddressSelector = externalAddressSelector;
    this.enrUpdateTracker = enrUpdateTracker;
  }

  @Override
  public void handle(PongMessage message, NodeSession session) {
    final Optional<InetSocketAddress> currentAddress = session.getReportedExternalAddress();
    if (currentAddress.isEmpty() || addressDiffers(message, currentAddress.orElseThrow())) {
      try {
        final InetSocketAddress reportedAddress =
            new InetSocketAddress(
                InetAddress.getByAddress(message.getRecipientIp().toArrayUnsafe()),
                message.getRecipientPort());
        session.setReportedExternalAddress(reportedAddress);
        externalAddressSelector.onExternalAddressReport(
            currentAddress, reportedAddress, Instant.now());
      } catch (UnknownHostException e) {
        LOG.trace("Failed to update local node record because recipient IP was invalid", e);
      }
    }
    session.clearRequestInfo(message.getRequestId(), null);
    enrUpdateTracker.updateIfRequired(session, message.getEnrSeq());
  }

  private boolean addressDiffers(
      final PongMessage message, final InetSocketAddress currentAddress) {
    return !Bytes.wrap(currentAddress.getAddress().getAddress()).equals(message.getRecipientIp())
        || currentAddress.getPort() != message.getRecipientPort();
  }
}
