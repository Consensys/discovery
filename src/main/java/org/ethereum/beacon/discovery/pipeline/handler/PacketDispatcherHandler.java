/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.pipeline.handler;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.v2.bytes.Bytes32;
import org.ethereum.beacon.discovery.packet.HandshakeMessagePacket;
import org.ethereum.beacon.discovery.packet.OrdinaryMessagePacket;
import org.ethereum.beacon.discovery.packet.Packet;
import org.ethereum.beacon.discovery.packet.WhoAreYouPacket;
import org.ethereum.beacon.discovery.pipeline.Envelope;
import org.ethereum.beacon.discovery.pipeline.EnvelopeHandler;
import org.ethereum.beacon.discovery.pipeline.Field;
import org.ethereum.beacon.discovery.pipeline.HandlerUtil;
import org.ethereum.beacon.discovery.schema.NodeSession;
import org.ethereum.beacon.discovery.util.Utils;

/** Matches the current session state and inbound packet */
public class PacketDispatcherHandler implements EnvelopeHandler {

  private static final Logger LOG = LogManager.getLogger(PacketDispatcherHandler.class);

  @Override
  public void handle(Envelope envelope) {
    if (!HandlerUtil.requireField(Field.SESSION, envelope)) {
      return;
    }
    if (!HandlerUtil.requireField(Field.PACKET, envelope)) {
      return;
    }
    LOG.trace(
        () ->
            String.format(
                "Envelope %s in PacketDispatcherHandler, requirements are satisfied!",
                envelope.getIdString()));

    Packet<?> packet = envelope.get(Field.PACKET);
    NodeSession session = envelope.get(Field.SESSION);

    try {
      switch (session.getState()) {
        case INITIAL:
          // when there is no session with a node we are expecting only OrdinaryMessagePacket
          // whether it's a random message (if a remote node has no session with us and wants
          // to initiate one) or it's a regular message (if a remote node has a session with us
          // but we were either dropped the session on our side or restarted the node)
          if (packet instanceof OrdinaryMessagePacket) {
            envelope.put(Field.UNAUTHORIZED_PACKET_MESSAGE, (OrdinaryMessagePacket) packet);
          } else {
            // Any other packet strictly means remote peer misbehavior
            throw new IllegalStateException(
                "Session ("
                    + session
                    + ") in state "
                    + session.getState()
                    + " received unexpected packet "
                    + packet);
          }
          break;
        case RANDOM_PACKET_SENT:
          // This state means that local node has wanted to originate a handshake and sent a
          // random packet.
          if (packet instanceof WhoAreYouPacket) {
            // We are expecting WHOAREYOU message from the remote node.
            envelope.put(Field.PACKET_WHOAREYOU, (WhoAreYouPacket) packet);
          } else if (packet instanceof OrdinaryMessagePacket) {
            // However the remote node could also send us a random packet at the same moment.
            // In this case the following rule applies: the node with larger nodeId should response
            // another node should ignore
            Bytes32 remoteNodeId =
                ((OrdinaryMessagePacket) packet).getHeader().getAuthData().getSourceNodeId();
            if (Utils.compareBytes(session.getHomeNodeId(), remoteNodeId) > 0) {
              envelope.put(Field.UNAUTHORIZED_PACKET_MESSAGE, (OrdinaryMessagePacket) packet);
            } // else ignore
          } else {
            // Handshake packet is considered as remote peer misbehaviour
            throw new IllegalStateException(
                "Session ("
                    + session
                    + ") in state "
                    + session.getState()
                    + " received unexpected packet "
                    + packet);
          }
          break;
        case WHOAREYOU_SENT:
          // This state indicates that we sent WHOAREYOU in response to a random or regular message
          if (packet instanceof HandshakeMessagePacket) {
            // We are expecting Handshake packet
            envelope.put(Field.PACKET_HANDSHAKE, (HandshakeMessagePacket) packet);
          } else if (packet instanceof OrdinaryMessagePacket) {
            // this can be the case if a remote node has an old session with our node
            // and sending us regular messages. Again send WhoAreYou
            envelope.put(Field.UNAUTHORIZED_PACKET_MESSAGE, (OrdinaryMessagePacket) packet);
          } else {
            // WHOAREYOU packet is considered as remote peer misbehaviour
            throw new IllegalStateException(
                "Session ("
                    + session
                    + ") in state "
                    + session.getState()
                    + " received unexpected packet "
                    + packet);
          }
          break;
        case AUTHENTICATED:
          if (packet instanceof OrdinaryMessagePacket) {
            // just a regular message
            // if this message can't be decrypted this may mean the remote node dropped the session
            // and attempting to establish a new one
            envelope.put(Field.PACKET_MESSAGE, (OrdinaryMessagePacket) packet);
          } else if (packet instanceof WhoAreYouPacket) {
            // the remote node dropped the session and attempting to establish a new one in response
            // to our regular message
            envelope.put(Field.PACKET_WHOAREYOU, (WhoAreYouPacket) packet);
          } else {
            // Handshake packet is considered as remote peer misbehaviour
            throw new IllegalStateException(
                "Session ("
                    + session
                    + ") in state "
                    + session.getState()
                    + " received unexpected packet "
                    + packet);
          }
          break;
        default:
          throw new RuntimeException("Impossible!");
      }
    } catch (Exception e) {
      envelope.put(Field.BAD_PACKET, packet);
      envelope.put(Field.BAD_EXCEPTION, e);
    }
  }
}
