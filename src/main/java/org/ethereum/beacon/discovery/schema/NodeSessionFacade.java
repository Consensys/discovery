/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.schema;

import org.apache.tuweni.bytes.Bytes;

import java.net.InetSocketAddress;
import java.util.Optional;

/**
 * Stores session status and all keys for discovery message exchange between us, `homeNode` and the
 * other `node`
 */
public interface NodeSessionFacade {

  Bytes getNodeId();

  Optional<NodeRecord> getNodeRecord();

  InetSocketAddress getRemoteAddress();

  /** If true indicates that handshake is complete */
  boolean isAuthenticated();

  Optional<InetSocketAddress> getReportedExternalAddress();

  SessionState getState();

  void drop();

  enum SessionState {
    INITIAL, // other side is trying to connect, or we are initiating (before random packet is sent
    WHOAREYOU_SENT, // other side is initiator, we've sent whoareyou in response
    RANDOM_PACKET_SENT, // our node is initiator, we've sent random packet
    AUTHENTICATED
  }
}
