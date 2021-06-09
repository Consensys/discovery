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
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeSession;

public class PongHandler implements MessageHandler<PongMessage> {
  private static final Logger logger = LogManager.getLogger();
  private final ExternalAddressSelector externalAddressSelector;
  private final EnrUpdater enrUpdater;

  public PongHandler(
      final ExternalAddressSelector externalAddressSelector, final EnrUpdater enrUpdater) {
    this.externalAddressSelector = externalAddressSelector;
    this.enrUpdater = enrUpdater;
  }

  @Override
  public void handle(PongMessage message, NodeSession session) {
    final Optional<InetSocketAddress> currentAddress = session.getReportedExternalAddress();
    // If we have an outdated ENR, request the latest version.
    session
        .getNodeRecord()
        .filter(currentRecord -> isUpdateRequired(message, currentRecord))
        .ifPresent(enrUpdater::requestUpdatedEnr);
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
        logger.trace("Failed to update local node record because recipient IP was invalid", e);
      }
    }
    session.clearRequestInfo(message.getRequestId(), null);
  }

  private boolean isUpdateRequired(
      final PongMessage message, final org.ethereum.beacon.discovery.schema.NodeRecord record) {
    return record.getSeq().compareTo(message.getEnrSeq()) < 0;
  }

  private boolean addressDiffers(
      final PongMessage message, final InetSocketAddress currentAddress) {
    return !Bytes.wrap(currentAddress.getAddress().getAddress()).equals(message.getRecipientIp())
        || currentAddress.getPort() != message.getRecipientPort();
  }

  public interface EnrUpdater {

    EnrUpdater NOOP = currentRecord -> {};

    void requestUpdatedEnr(NodeRecord currentRecord);
  }
}
