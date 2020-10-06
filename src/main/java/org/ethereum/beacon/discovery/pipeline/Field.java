/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.pipeline;

public enum Field {
  SESSION_LOOKUP, // Node id, requests session lookup
  SESSION, // Node session
  INCOMING, // Raw incoming data
  REMOTE_SENDER, // InetSocketAddress of remote sender
  PACKET_UNKNOWN, // Unknown packet
  PACKET_WHOAREYOU, // WhoAreYou packet
  PACKET_AUTH_HEADER_MESSAGE, // Auth header message packet
  PACKET_MESSAGE, // Standard message packet
  UNAUTHORIZED_PACKET_MESSAGE, // Standard message packet
  MESSAGE, // Message extracted from the packet
  MASKING_IV, // need to keep RawPacket IV for decrypting a message
  NODE, // Sender/recipient node
  BAD_PACKET, // Bad, rejected packet
  BAD_MESSAGE, // Bad, rejected message
  BAD_EXCEPTION, // Stores exception for bad packet or message
  REQUEST, // Task to perform

  PACKET,
}
