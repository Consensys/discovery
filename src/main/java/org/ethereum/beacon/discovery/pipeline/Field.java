/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.pipeline;

import java.net.InetSocketAddress;
import org.ethereum.beacon.discovery.message.V5Message;
import org.ethereum.beacon.discovery.packet.HandshakeMessagePacket;
import org.ethereum.beacon.discovery.packet.MessagePacket;
import org.ethereum.beacon.discovery.packet.OrdinaryMessagePacket;
import org.ethereum.beacon.discovery.packet.Packet;
import org.ethereum.beacon.discovery.packet.WhoAreYouPacket;
import org.ethereum.beacon.discovery.pipeline.handler.SessionLookup;
import org.ethereum.beacon.discovery.pipeline.info.Request;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeSession;
import org.ethereum.beacon.discovery.type.Bytes16;

public class Field<T> {

  public static final Field<SessionLookup> SESSION_LOOKUP =
      new Field<>("SESSION_LOOKUP"); // Node id, requests session lookup
  public static final Field<NodeSession> SESSION =
      new Field<>("SESSION"); // Node session
  public static final Field<Request<?>> REQUEST =
      new Field<>("REQUEST"); // Task to perform
  public static final Field<Object> INCOMING =
      new Field<>("INCOMING"); // Raw incoming data
  public static final Field<Packet<?>> PACKET =
      new Field<>("PACKET"); // unknown inbound packet
  public static final Field<WhoAreYouPacket> PACKET_WHOAREYOU =
      new Field<>("PACKET_WHOAREYOU"); // WhoAreYou packet
  public static final Field<HandshakeMessagePacket> PACKET_HANDSHAKE =
      new Field<>("PACKET_HANDSHAKE"); // Auth header message packet
  public static final Field<MessagePacket<?>> PACKET_MESSAGE =
      new Field<>("PACKET_MESSAGE"); // Standard message packet
  public static final Field<OrdinaryMessagePacket> UNAUTHORIZED_PACKET_MESSAGE =
      new Field<>("UNAUTHORIZED_PACKET_MESSAGE"); // Standard message packet
  public static final Field<V5Message> MESSAGE =
      new Field<>("MESSAGE"); // Message extracted from the packet
  public static final Field<Bytes16> MASKING_IV =
      new Field<>("MASKING_IV"); // need to keep RawPacket IV for decrypting a message
  public static final Field<InetSocketAddress> REMOTE_SENDER =
      new Field<>("REMOTE_SENDER"); // InetSocketAddress of remote sender
  public static final Field<NodeRecord> NODE =
      new Field<>("NODE"); // Sender/recipient node
  public static final Field<Object> BAD_PACKET =
      new Field<>("BAD_PACKET"); // Bad, rejected packet
  public static final Field<Throwable> BAD_EXCEPTION =
      new Field<>("BAD_EXCEPTION"); // Stores exception for bad packet or message

  private final String name;

  public Field(String name) {
    this.name = name;
  }

  @Override
  public String toString() {
    return "Field[" + name + ']';
  }
}
