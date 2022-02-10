/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.pipeline.handler;

import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ethereum.beacon.discovery.packet.Packet;
import org.ethereum.beacon.discovery.packet.WhoAreYouPacket;
import org.ethereum.beacon.discovery.pipeline.Envelope;
import org.ethereum.beacon.discovery.pipeline.EnvelopeHandler;
import org.ethereum.beacon.discovery.pipeline.Field;
import org.ethereum.beacon.discovery.pipeline.HandlerUtil;
import org.ethereum.beacon.discovery.schema.NodeSession;

/**
 * Resolves session using `nonceRepository` for `WHOAREYOU` packets which should be placed in {@link
 * Field#PACKET_WHOAREYOU}
 */
public class WhoAreYouSessionResolver implements EnvelopeHandler {
  private static final Logger logger = LogManager.getLogger(WhoAreYouSessionResolver.class);
  private final NodeSessionManager nodeSessionManager;

  public WhoAreYouSessionResolver(NodeSessionManager nodeSessionManager) {
    this.nodeSessionManager = nodeSessionManager;
  }

  @Override
  public void handle(Envelope envelope) {
    if (!HandlerUtil.requireField(Field.PACKET, envelope)) {
      return;
    }
    Packet<?> packet = envelope.get(Field.PACKET);
    if (!(packet instanceof WhoAreYouPacket)) {
      return;
    }

    logger.trace(
        () ->
            String.format(
                "Envelope %s in WhoAreYouSessionResolver, requirements are satisfied!",
                envelope.getIdString()));

    WhoAreYouPacket whoAreYouPacket = (WhoAreYouPacket) packet;
    Optional<NodeSession> nodeSessionMaybe =
        nodeSessionManager.getNodeSessionByLastOutboundNonce(
            whoAreYouPacket.getHeader().getStaticHeader().getNonce());

    nodeSessionMaybe.ifPresentOrElse(
        session -> {
          envelope.put(Field.SESSION, session);
        },
        () -> {
          logger.trace("Unexpected WHOAREYOU packet: no source nonce found");
          envelope.put(Field.BAD_PACKET, packet);
          envelope.put(Field.BAD_EXCEPTION, new RuntimeException("Not expected WHOAREYOU packet"));
        });
  }
}
