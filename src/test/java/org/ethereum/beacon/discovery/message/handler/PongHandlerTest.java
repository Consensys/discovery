/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.message.handler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt64;
import org.ethereum.beacon.discovery.TestUtil;
import org.ethereum.beacon.discovery.message.PongMessage;
import org.ethereum.beacon.discovery.message.handler.PongHandler.EnrUpdater;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeSession;
import org.junit.jupiter.api.Test;

class PongHandlerTest {

  private static final Bytes REQUEST_ID = Bytes.fromHexString("0x7777");
  private static final Bytes RECIPIENT_IP = Bytes.fromHexString("0x00000000");
  private static final int RECIPIENT_PORT = 6000;
  private final ExternalAddressSelector externalAddressSelector =
      mock(ExternalAddressSelector.class);
  private final NodeSession session = mock(NodeSession.class);
  private final EnrUpdater enrUpdater = mock(EnrUpdater.class);

  private final PongHandler handler = new PongHandler(externalAddressSelector, enrUpdater);

  @Test
  void shouldRequestPeersEnrWhenPongSequenceNumberIsHigher() {
    final NodeRecord currentNodeRecord = TestUtil.generateNode(8000).getNodeRecord();
    when(session.getNodeRecord()).thenReturn(Optional.of(currentNodeRecord));
    handler.handle(
        new PongMessage(
            REQUEST_ID, currentNodeRecord.getSeq().add(1), RECIPIENT_IP, RECIPIENT_PORT),
        session);

    verify(enrUpdater).requestUpdatedEnr(currentNodeRecord);
  }

  @Test
  void shouldNotRequestPeersEnrWhenCurrentEnrIsUnknown() {
    // Can't request peers if we don't have an ENR because don't know how to contact the node
    when(session.getNodeRecord()).thenReturn(Optional.empty());
    handler.handle(
        new PongMessage(REQUEST_ID, UInt64.valueOf(0), RECIPIENT_IP, RECIPIENT_PORT), session);

    verify(enrUpdater, never()).requestUpdatedEnr(any());
  }

  @Test
  void shouldNotRequestPeersEnrWhenPongSequenceNumberIsTheSame() {
    final NodeRecord currentNodeRecord = TestUtil.generateNode(8000).getNodeRecord();
    when(session.getNodeRecord()).thenReturn(Optional.of(currentNodeRecord));
    handler.handle(
        new PongMessage(REQUEST_ID, currentNodeRecord.getSeq(), RECIPIENT_IP, RECIPIENT_PORT),
        session);

    verify(session, never()).createNextRequest(any());
  }

  @Test
  void shouldNotRequestPeersEnrWhenPongSequenceNumberIsLower() {
    final NodeRecord currentNodeRecord =
        TestUtil.generateNode(8000)
            .getNodeRecord()
            .withUpdatedCustomField("test", Bytes.EMPTY, Bytes.EMPTY);
    when(session.getNodeRecord()).thenReturn(Optional.of(currentNodeRecord));
    handler.handle(
        new PongMessage(
            REQUEST_ID, currentNodeRecord.getSeq().subtract(1), RECIPIENT_IP, RECIPIENT_PORT),
        session);

    verify(session, never()).createNextRequest(any());
  }
}
