/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.message.handler;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.message.PongMessage;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeSession;
import org.ethereum.beacon.discovery.storage.LocalNodeRecordStore;
import org.ethereum.beacon.discovery.task.TaskType;

public class PongHandler implements MessageHandler<PongMessage> {
  private static final Logger logger = LogManager.getLogger();
  private final LocalNodeRecordStore localNodeRecordStore;

  public PongHandler(final LocalNodeRecordStore localNodeRecordStore) {
    this.localNodeRecordStore = localNodeRecordStore;
  }

  @Override
  public void handle(PongMessage message, NodeSession session) {
    final NodeRecord currentNodeRecord = localNodeRecordStore.getLocalNodeRecord();
    final Optional<InetSocketAddress> currentAddress = currentNodeRecord.getUdpAddress();
    if (currentAddress.isEmpty() || addressDiffers(message, currentAddress.orElseThrow())) {
      try {
        localNodeRecordStore.onSocketAddressChanged(
            new InetSocketAddress(
                InetAddress.getByAddress(message.getRecipientIp().toArrayUnsafe()),
                message.getRecipientPort()));
      } catch (UnknownHostException e) {
        logger.trace("Failed to update local node record because recipient IP was invalid", e);
      }
    }
    session.clearRequestId(message.getRequestId(), TaskType.PING);
  }

  private boolean addressDiffers(
      final PongMessage message, final InetSocketAddress currentAddress) {
    return !Bytes.wrap(currentAddress.getAddress().getAddress()).equals(message.getRecipientIp())
        || currentAddress.getPort() != message.getRecipientPort();
  }
}
