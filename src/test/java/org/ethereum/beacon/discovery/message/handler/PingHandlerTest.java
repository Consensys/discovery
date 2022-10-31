/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.message.handler;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.InetSocketAddress;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt64;
import org.ethereum.beacon.discovery.TestUtil;
import org.ethereum.beacon.discovery.TestUtil.NodeInfo;
import org.ethereum.beacon.discovery.message.PingMessage;
import org.ethereum.beacon.discovery.message.PongMessage;
import org.ethereum.beacon.discovery.schema.NodeSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PingHandlerTest {
  private static final InetSocketAddress REMOTE_ADDRESS = new InetSocketAddress("localhost", 25234);
  private final NodeSession session = mock(NodeSession.class);
  private final EnrUpdateTracker enrUpdateTracker = mock(EnrUpdateTracker.class);
  private final NodeInfo localNode = TestUtil.generateNode(9000);

  private final PingHandler handler = new PingHandler(enrUpdateTracker);

  @BeforeEach
  void setUp() {
    when(session.getHomeNodeRecord()).thenReturn(localNode.getNodeRecord());
    when(session.getRemoteAddress()).thenReturn(REMOTE_ADDRESS);
  }

  @Test
  void shouldRespondWithOurSequenceNumberAndRemoteAddressInfo() {
    final Bytes requestId = Bytes.fromHexString("0x4678");
    final PingMessage message = new PingMessage(requestId, UInt64.valueOf(3));
    handler.handle(message, session);

    verify(session)
        .sendOutgoingOrdinary(
            new PongMessage(
                requestId,
                localNode.getNodeRecord().getSeq(),
                Bytes.wrap(REMOTE_ADDRESS.getAddress().getAddress()),
                REMOTE_ADDRESS.getPort()));
  }

  @Test
  void shouldCheckForUpdatedEnr() {
    final UInt64 reportedSeqNum = UInt64.valueOf(6);
    final Bytes requestId = Bytes.fromHexString("0x4678");
    final PingMessage message = new PingMessage(requestId, reportedSeqNum);
    handler.handle(message, session);

    verify(enrUpdateTracker).updateIfRequired(session, reportedSeqNum);
  }
}
