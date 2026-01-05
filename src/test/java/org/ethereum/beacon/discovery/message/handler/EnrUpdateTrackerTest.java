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
import org.apache.tuweni.crypto.SECP256K1.SecretKey;
import org.apache.tuweni.units.bigints.UInt64;
import org.ethereum.beacon.discovery.InMemorySecurityModule;
import org.ethereum.beacon.discovery.TestUtil;
import org.ethereum.beacon.discovery.message.handler.EnrUpdateTracker.EnrUpdater;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeSession;
import org.ethereum.beacon.discovery.util.Functions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EnrUpdateTrackerTest {
  private static final SecretKey SECRET_KEY = Functions.randomKeyPair().secretKey();
  private final NodeSession session = mock(NodeSession.class);
  private final EnrUpdater enrUpdater = mock(EnrUpdater.class);

  private final EnrUpdateTracker tracker = new EnrUpdateTracker(enrUpdater);

  @BeforeEach
  void setUp() {
    when(session.isAuthenticated()).thenReturn(true);
  }

  @Test
  void shouldRequestPeersEnrWhenPongSequenceNumberIsHigher() {
    final NodeRecord currentNodeRecord = TestUtil.generateNode(8000).getNodeRecord();
    when(session.getNodeRecord()).thenReturn(Optional.of(currentNodeRecord));
    tracker.updateIfRequired(session, currentNodeRecord.getSeq().add(1));

    verify(enrUpdater).requestUpdatedEnr(currentNodeRecord);
  }

  @Test
  void shouldNotRequestPeersEnrWhenCurrentEnrIsUnknown() {
    // Can't request peers if we don't have an ENR because don't know how to contact the node
    when(session.getNodeRecord()).thenReturn(Optional.empty());
    tracker.updateIfRequired(session, UInt64.valueOf(0));

    verify(enrUpdater, never()).requestUpdatedEnr(any());
  }

  @Test
  void shouldNotRequestPeersEnrWhenPongSequenceNumberIsTheSame() {
    final NodeRecord currentNodeRecord = TestUtil.generateNode(8000).getNodeRecord();
    when(session.getNodeRecord()).thenReturn(Optional.of(currentNodeRecord));
    tracker.updateIfRequired(session, currentNodeRecord.getSeq());

    verify(session, never()).createNextRequest(any());
  }

  @Test
  void shouldNotRequestPeersEnrWhenPongSequenceNumberIsLower() {
    final NodeRecord currentNodeRecord =
        TestUtil.generateNode(8000)
            .getNodeRecord()
            .withUpdatedCustomField("test", Bytes.EMPTY, InMemorySecurityModule.create(SECRET_KEY));
    when(session.getNodeRecord()).thenReturn(Optional.of(currentNodeRecord));
    tracker.updateIfRequired(session, currentNodeRecord.getSeq().subtract(1));

    verify(session, never()).createNextRequest(any());
  }

  @Test
  void shouldNotRequestPeersEnrWhenPongSequenceNumberIsHigherButSessionIsNotAuthenticated() {
    when(session.isAuthenticated()).thenReturn(false);
    final NodeRecord currentNodeRecord = TestUtil.generateNode(8000).getNodeRecord();
    when(session.getNodeRecord()).thenReturn(Optional.of(currentNodeRecord));
    tracker.updateIfRequired(session, currentNodeRecord.getSeq().add(1));

    verify(enrUpdater, never()).requestUpdatedEnr(currentNodeRecord);
  }
}
