/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.message;

import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.schema.NodeRecordFactory;
import org.ethereum.beacon.discovery.schema.Protocol;
import org.ethereum.beacon.discovery.util.DecodeException;
import org.ethereum.beacon.discovery.util.RlpUtil;
import org.web3j.rlp.RlpString;
import org.web3j.rlp.RlpType;

public class DiscoveryV5Message implements DiscoveryMessage {
  private final Bytes bytes;

  public DiscoveryV5Message(Bytes bytes) {
    this.bytes = bytes;
  }

  public static DiscoveryV5Message from(V5Message v5Message) {
    return new DiscoveryV5Message(v5Message.getBytes());
  }

  @Override
  public Bytes getBytes() {
    return bytes;
  }

  @Override
  public Protocol getProtocol() {
    return Protocol.V5;
  }

  public MessageCode getCode() {
    return MessageCode.fromNumber(getBytes().get(0));
  }

  public V5Message create(NodeRecordFactory nodeRecordFactory) {
    MessageCode code = MessageCode.fromNumber(getBytes().get(0));
    Bytes messageBytes = getBytes().slice(1);
    if (code == null) {
      throw new DecodeException("Invalid message code: " + getBytes().get(0));
    }
    switch (code) {
      case PING:
        {
          return PingMessage.fromBytes(messageBytes);
        }
      case PONG:
        {
          return PongMessage.fromBytes(messageBytes);
        }
      case FINDNODE:
        {
          return FindNodeMessage.fromBytes(messageBytes);
        }
      case NODES:
        {
          return NodesMessage.fromBytes(messageBytes, nodeRecordFactory);
        }
      case TALKREQ:
        {
          return TalkReqMessage.fromBytes(messageBytes);
        }
      case TALKRESP:
        {
          return TalkRespMessage.fromBytes(messageBytes);
        }
      default:
        {
          throw new RuntimeException(
              String.format(
                  "Creation of discovery V5 messages from code %s is not supported", code));
        }
    }
  }

  @Override
  public String toString() {
    return "DiscoveryV5Message{" + "code=" + getCode() + ", bytes=" + getBytes() + '}';
  }
}
