/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.message;

import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.schema.DiscoveryProtocol;
import org.ethereum.beacon.discovery.schema.NodeRecordFactory;
import org.ethereum.beacon.discovery.util.DecodeException;

public class DiscoveryV5MessageDecoder implements DiscoveryMessageDecoder {
  private final NodeRecordFactory nodeRecordFactory;

  public DiscoveryV5MessageDecoder(NodeRecordFactory nodeRecordFactory) {
    this.nodeRecordFactory = nodeRecordFactory;
  }

  @Override
  public DiscoveryProtocol getProtocol() {
    return DiscoveryProtocol.V5;
  }

  @Override
  public V5Message decode(Bytes bytes) {
    MessageCode code = MessageCode.fromNumber(bytes.get(0));
    if (code == null) {
      throw new DecodeException("Invalid message code: " + bytes.get(0));
    }
    Bytes messageBytes = bytes.slice(1);
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
}
