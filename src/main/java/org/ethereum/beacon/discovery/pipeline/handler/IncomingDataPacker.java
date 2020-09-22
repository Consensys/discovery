/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.pipeline.handler;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.packet.DecodeException;
import org.ethereum.beacon.discovery.packet.Packet;
import org.ethereum.beacon.discovery.packet.RawPacket;
import org.ethereum.beacon.discovery.pipeline.Envelope;
import org.ethereum.beacon.discovery.pipeline.EnvelopeHandler;
import org.ethereum.beacon.discovery.pipeline.Field;
import org.ethereum.beacon.discovery.pipeline.HandlerUtil;
import org.ethereum.beacon.discovery.type.Bytes16;

/** Handles raw BytesValue incoming data in {@link Field#INCOMING} */
public class IncomingDataPacker implements EnvelopeHandler {
  private static final Logger logger = LogManager.getLogger(IncomingDataPacker.class);
  private static final int MAX_PACKET_SIZE = 1280;
  private final Bytes16 homeNodeId;

  public IncomingDataPacker(Bytes homeNodeId) {
    this.homeNodeId = Bytes16.wrap(homeNodeId, 0);
  }

  @Override
  public void handle(Envelope envelope) {
    logger.trace(
        () ->
            String.format(
                "Envelope %s in IncomingDataPacker, checking requirements satisfaction",
                envelope.getId()));
    if (!HandlerUtil.requireField(Field.INCOMING, envelope)) {
      return;
    }
    logger.trace(
        () ->
            String.format(
                "Envelope %s in IncomingDataPacker, requirements are satisfied!",
                envelope.getId()));

    Bytes rawPacketBytes = (Bytes) envelope.get(Field.INCOMING);
    try {
      if (rawPacketBytes.size() > MAX_PACKET_SIZE) {
        throw new DecodeException("Packet is too large: " + rawPacketBytes.size());
      }
      RawPacket rawPacket = RawPacket.decode(rawPacketBytes);
      Packet<?> packet = rawPacket.decodePacket(homeNodeId);
      // check that AES/CTR decoded correctly
      packet.getHeader().validate();

      envelope.put(Field.PACKET, packet);
      logger.trace(
          () -> String.format("Incoming packet %s in envelope #%s", rawPacket, envelope.getId()));
    } catch (Exception ex) {
      envelope.put(Field.BAD_PACKET, rawPacketBytes);
      envelope.put(Field.BAD_EXCEPTION, ex);
      envelope.put(Field.BAD_MESSAGE, "Incoming packet verification not passed");
      logger.trace(
          () ->
              String.format(
                  "Bad incoming packet %s in envelope #%s", rawPacketBytes, envelope.getId()));
    }
    envelope.remove(Field.INCOMING);
  }
}
