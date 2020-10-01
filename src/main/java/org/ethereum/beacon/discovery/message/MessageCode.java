/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.message;

import java.util.HashMap;
import java.util.Map;

/**
 * Discovery protocol message types as described in <a
 * href="https://github.com/ethereum/devp2p/blob/master/discv5/discv5-wire.md#protocol-messages">https://github.com/ethereum/devp2p/blob/master/discv5/discv5-wire.md#protocol-messages</a>
 */
public enum MessageCode {

  /**
   * PING checks whether the recipient is alive and informs it about the sender's ENR sequence
   * number.
   */
  PING(0x01),

  /** PONG is the reply to PING. */
  PONG(0x02),

  /** FINDNODE queries for nodes at the given logarithmic distance from the recipient's node ID. */
  FINDNODE(0x03),

  /**
   * NODES is the response to a FINDNODE or TOPICQUERY message. Multiple NODES messages may be sent
   * as responses to a single query.
   */
  NODES(0x04),

  /**
   * TALKREQ sends an application-level request. The purpose of this message is pre-negotiating
   * connections made through another application-specific protocol identified by protocol.
   */
  TALKREQ(0x05),

  /** TALKRESP is the response to TALKREQ. */
  TALKRESP(0x06),

  /**
   * REGTOPIC registers the sender for the given topic with a ticket. The ticket must be valid and
   * its waiting time must have elapsed before using the ticket.
   */
  REGTOPIC(0x07),

  /**
   * TICKET is the response to REQTICKET. It contains a ticket which can be used to register for the
   * requested topic.
   */
  TICKET(0x08),

  /** REGCONFIRMATION is the response to REGTOPIC. */
  REGCONFIRMATION(0x09),

  /**
   * TOPICQUERY requests nodes in the topic queue of the given topic. The response is a NODES
   * message containing node records registered for the topic.
   */
  TOPICQUERY(0x0A);

  private static final Map<Integer, MessageCode> codeMap = new HashMap<>();

  static {
    for (MessageCode type : MessageCode.values()) {
      codeMap.put(type.code, type);
    }
  }

  private int code;

  MessageCode(int code) {
    this.code = code;
  }

  public static MessageCode fromNumber(int i) {
    return codeMap.get(i);
  }

  public byte byteCode() {
    return (byte) code;
  }
}
