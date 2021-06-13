/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.message.handler;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt64;
import org.ethereum.beacon.discovery.message.PongMessage;
import org.ethereum.beacon.discovery.schema.NodeSession;
import org.junit.jupiter.api.Test;

class PongHandlerTest {

  private static final Bytes REQUEST_ID = Bytes.fromHexString("0x7777");
  private static final Bytes RECIPIENT_IP = Bytes.fromHexString("0x00000000");
  private static final int RECIPIENT_PORT = 6000;
  private final ExternalAddressSelector externalAddressSelector =
      mock(ExternalAddressSelector.class);
  private final NodeSession session = mock(NodeSession.class);
  private final EnrUpdateTracker enrUpdateTracker = mock(EnrUpdateTracker.class);

  private final PongHandler handler = new PongHandler(externalAddressSelector, enrUpdateTracker);

  @Test
  void shouldCheckForUpdatedEnr() {
    final UInt64 reportedSeqNum = UInt64.valueOf(6);
    handler.handle(
        new PongMessage(REQUEST_ID, reportedSeqNum, RECIPIENT_IP, RECIPIENT_PORT), session);

    verify(enrUpdateTracker).updateIfRequired(session, reportedSeqNum);
  }
}
