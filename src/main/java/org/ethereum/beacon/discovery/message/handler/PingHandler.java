/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.message.handler;

import java.net.InetSocketAddress;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.ethereum.beacon.discovery.message.DiscoveryV5Message;
import org.ethereum.beacon.discovery.message.PingMessage;
import org.ethereum.beacon.discovery.message.PongMessage;
import org.ethereum.beacon.discovery.packet.MessagePacket;
import org.ethereum.beacon.discovery.packet5_1.AuthData;
import org.ethereum.beacon.discovery.packet5_1.Header;
import org.ethereum.beacon.discovery.packet5_1.OrdinaryMessagePacket;
import org.ethereum.beacon.discovery.packet5_1.RawPacket;
import org.ethereum.beacon.discovery.packet5_1.StaticHeader.Flag;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeSession;
import org.ethereum.beacon.discovery.type.Bytes12;
import org.ethereum.beacon.discovery.type.Bytes16;

public class PingHandler implements MessageHandler<PingMessage> {
  @Override
  public void handle(PingMessage message, NodeSession session) {
    final NodeRecord nodeRecord = session.getNodeRecord().orElseThrow();
    final InetSocketAddress remoteAddress = session.getRemoteAddress();
    PongMessage responseMessage =
        new PongMessage(
            message.getRequestId(),
            nodeRecord.getSeq(),
            Bytes.wrap(remoteAddress.getAddress().getAddress()),
            remoteAddress.getPort());

    session.sendOutgoingOrdinary(responseMessage);
  }
}
