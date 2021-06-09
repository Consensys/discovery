/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.message.handler;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.message.FindNodeMessage;
import org.ethereum.beacon.discovery.message.PongMessage;
import org.ethereum.beacon.discovery.pipeline.info.FindNodeResponseHandler;
import org.ethereum.beacon.discovery.pipeline.info.Request;
import org.ethereum.beacon.discovery.schema.NodeSession;

public class PongHandler implements MessageHandler<PongMessage> {
  private static final Logger logger = LogManager.getLogger();
  private final ExternalAddressSelector externalAddressSelector;

  public PongHandler(final ExternalAddressSelector externalAddressSelector) {
    this.externalAddressSelector = externalAddressSelector;
  }

  @Override
  public void handle(PongMessage message, NodeSession session) {
    final Optional<InetSocketAddress> currentAddress = session.getReportedExternalAddress();
    if (session.getNodeRecord().map(record -> isUpdateRequired(message, record)).orElse(true)) {
      // We either don't have an ENR for the peer yet or it is out of date. Request a new one.
      session.createNextRequest(
          new Request<>(
              new CompletableFuture<>(),
              reqId -> new FindNodeMessage(reqId, List.of(0)),
              new FindNodeResponseHandler()));
    }
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
}
