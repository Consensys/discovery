/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.network;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.MessageToMessageDecoder;
import java.util.List;
import org.apache.tuweni.v2.bytes.Bytes;
import org.ethereum.beacon.discovery.pipeline.Envelope;
import org.ethereum.beacon.discovery.pipeline.Field;

/** UDP Packet -> BytesValue converter with default Netty interface */
public class DatagramToEnvelope extends MessageToMessageDecoder<DatagramPacket> {
  @Override
  protected void decode(ChannelHandlerContext ctx, DatagramPacket msg, List<Object> out) {
    final Envelope envelope = new Envelope();
    final ByteBuf buf = msg.content();
    final byte[] data = new byte[buf.readableBytes()];
    buf.readBytes(data);
    envelope.put(Field.INCOMING, Bytes.wrap(data));
    envelope.put(Field.REMOTE_SENDER, msg.sender());
    out.add(envelope);
  }
}
