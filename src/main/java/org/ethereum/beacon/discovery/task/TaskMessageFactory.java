/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.task;

import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.message.DiscoveryV5Message;
import org.ethereum.beacon.discovery.message.FindNodeMessage;
import org.ethereum.beacon.discovery.message.PingMessage;
import org.ethereum.beacon.discovery.message.V5Message;
import org.ethereum.beacon.discovery.packet.MessagePacket;
import org.ethereum.beacon.discovery.pipeline.info.FindNodeRequestInfo;
import org.ethereum.beacon.discovery.pipeline.info.RequestInfo;
import org.ethereum.beacon.discovery.schema.NodeSession;

public class TaskMessageFactory {
  public static MessagePacket createPacketFromRequest(
      RequestInfo requestInfo, Bytes authTag, NodeSession session) {
    switch (requestInfo.getTaskType()) {
      case PING:
        {
          return createPingPacket(authTag, session, requestInfo.getRequestId());
        }
      case FINDNODE:
        {
          FindNodeRequestInfo nodeRequestInfo = (FindNodeRequestInfo) requestInfo;
          return createFindNodePacket(
              authTag, session, requestInfo.getRequestId(), nodeRequestInfo.getDistance());
        }
      default:
        {
          throw new RuntimeException(
              String.format("Type %s is not supported!", requestInfo.getTaskType()));
        }
    }
  }

  public static V5Message createMessageFromRequest(RequestInfo requestInfo, NodeSession session) {
    switch (requestInfo.getTaskType()) {
      case PING:
        {
          return createPing(session, requestInfo.getRequestId());
        }
      case FINDNODE:
        {
          FindNodeRequestInfo nodeRequestInfo = (FindNodeRequestInfo) requestInfo;
          return createFindNode(requestInfo.getRequestId(), nodeRequestInfo.getDistance());
        }
      default:
        {
          throw new RuntimeException(
              String.format("Type %s is not supported!", requestInfo.getTaskType()));
        }
    }
  }

  public static MessagePacket createPingPacket(
      Bytes authTag, NodeSession session, Bytes requestId) {

    return MessagePacket.create(
        session.getHomeNodeId(),
        session.getNodeRecord().getNodeId(),
        authTag,
        session.getInitiatorKey(),
        DiscoveryV5Message.from(createPing(session, requestId)));
  }

  public static PingMessage createPing(NodeSession session, Bytes requestId) {
    return new PingMessage(requestId, session.getNodeRecord().getSeq());
  }

  public static MessagePacket createFindNodePacket(
      Bytes authTag, NodeSession session, Bytes requestId, int distance) {
    FindNodeMessage findNodeMessage = createFindNode(requestId, distance);
    return MessagePacket.create(
        session.getHomeNodeId(),
        session.getNodeRecord().getNodeId(),
        authTag,
        session.getInitiatorKey(),
        DiscoveryV5Message.from(findNodeMessage));
  }

  public static FindNodeMessage createFindNode(Bytes requestId, int distance) {
    return new FindNodeMessage(requestId, distance);
  }
}
