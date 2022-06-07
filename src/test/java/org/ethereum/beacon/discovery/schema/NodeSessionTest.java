/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.crypto.SECP256K1.SecretKey;
import org.ethereum.beacon.discovery.SimpleIdentitySchemaInterpreter;
import org.ethereum.beacon.discovery.message.V5Message;
import org.ethereum.beacon.discovery.network.NetworkParcel;
import org.ethereum.beacon.discovery.pipeline.handler.NodeSessionManager;
import org.ethereum.beacon.discovery.pipeline.info.Request;
import org.ethereum.beacon.discovery.pipeline.info.RequestInfo;
import org.ethereum.beacon.discovery.scheduler.ExpirationScheduler;
import org.ethereum.beacon.discovery.schema.NodeSession.SessionState;
import org.ethereum.beacon.discovery.storage.KBuckets;
import org.ethereum.beacon.discovery.storage.LocalNodeRecordStore;
import org.ethereum.beacon.discovery.storage.NewAddressHandler;
import org.ethereum.beacon.discovery.storage.NodeRecordListener;
import org.ethereum.beacon.discovery.util.Functions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;
import org.mockito.ArgumentCaptor;

public class NodeSessionTest {
  private static final SecretKey SECRET_KEY = Functions.randomKeyPair().secretKey();
  private final NodeSessionManager nodeSessionManager = mock(NodeSessionManager.class);
  private final Bytes32 nodeId = Bytes32.ZERO;

  @SuppressWarnings("unchecked")
  private final Consumer<NetworkParcel> outgoingPipeline = mock(Consumer.class);

  @SuppressWarnings("unchecked")
  private final ExpirationScheduler<Bytes> expirationScheduler = mock(ExpirationScheduler.class);

  private final KBuckets kBuckets = mock(KBuckets.class);
  private final LocalNodeRecordStore localNodeRecordStore =
      new LocalNodeRecordStore(
          SimpleIdentitySchemaInterpreter.createNodeRecord(Bytes32.fromHexString("0x123456")),
          SECRET_KEY,
          mock(NodeRecordListener.class),
          mock(NewAddressHandler.class));

  private final NodeSession session =
      new NodeSession(
          nodeId,
          Optional.empty(),
          InetSocketAddress.createUnresolved("127.0.0.1", 2999),
          nodeSessionManager,
          localNodeRecordStore,
          SECRET_KEY,
          kBuckets,
          outgoingPipeline,
          new Random(1342),
          expirationScheduler);

  @Test
  void onNodeRecordReceived_shouldUpdateRecordWhenNoPreviousValue() {
    final NodeRecord nodeRecord =
        SimpleIdentitySchemaInterpreter.createNodeRecord(
            nodeId, new InetSocketAddress("127.0.0.1", 2));
    session.onNodeRecordReceived(nodeRecord);
    assertThat(session.getNodeRecord()).contains(nodeRecord);
  }

  @Test
  void onNodeRecordReceived_shouldUpdateRecordWhenSeqNumHigher() {
    final NodeRecord nodeRecord =
        SimpleIdentitySchemaInterpreter.createNodeRecord(
            nodeId, new InetSocketAddress("127.0.0.1", 2));
    session.onNodeRecordReceived(nodeRecord);
    assertThat(session.getNodeRecord()).contains(nodeRecord);

    final NodeRecord updatedRecord =
        nodeRecord.withUpdatedCustomField("eth2", Bytes.fromHexString("0x12"), SECRET_KEY);
    session.onNodeRecordReceived(updatedRecord);
    assertThat(session.getNodeRecord()).contains(updatedRecord);
  }

  @Test
  void onNodeRecordReceived_shouldNotUpdateRecordWhenSeqNumLower() {
    final NodeRecord nodeRecord =
        SimpleIdentitySchemaInterpreter.createNodeRecord(
            nodeId, new InetSocketAddress("127.0.0.1", 2));

    final NodeRecord updatedRecord =
        nodeRecord.withUpdatedCustomField("eth2", Bytes.fromHexString("0x12"), SECRET_KEY);
    session.onNodeRecordReceived(updatedRecord);
    assertThat(session.getNodeRecord()).contains(updatedRecord);

    session.onNodeRecordReceived(nodeRecord);
    assertThat(session.getNodeRecord()).contains(updatedRecord);
  }

  @Test
  void onNodeRecordReceived_shouldNotUpdateRecordWhenSeqNumEqual() {
    final NodeRecord nodeRecord =
        SimpleIdentitySchemaInterpreter.createNodeRecord(
            nodeId, new InetSocketAddress("127.0.0.1", 2));

    final NodeRecord updatedRecord1a =
        nodeRecord.withUpdatedCustomField("eth2", Bytes.fromHexString("0x12"), SECRET_KEY);
    session.onNodeRecordReceived(updatedRecord1a);
    assertThat(session.getNodeRecord()).contains(updatedRecord1a);

    final NodeRecord updatedRecord1b =
        nodeRecord.withUpdatedCustomField("eth2", Bytes.fromHexString("0x9999"), SECRET_KEY);
    session.onNodeRecordReceived(updatedRecord1b);
    assertThat(session.getNodeRecord()).contains(updatedRecord1a);
  }

  @ParameterizedTest
  @EnumSource(
      value = SessionState.class,
      names = {"WHOAREYOU_SENT", "RANDOM_PACKET_SENT"},
      mode = Mode.INCLUDE)
  void createNextRequest_shouldResetPartialHandshakeStatesWhenRequestTimesOut(
      final SessionState state) {
    final Request<?> request = createRequestMock();
    final RequestInfo requestInfo = session.createNextRequest(request);

    final ArgumentCaptor<Runnable> timeoutHandlerCaptor = ArgumentCaptor.forClass(Runnable.class);
    verify(expirationScheduler).put(eq(requestInfo.getRequestId()), timeoutHandlerCaptor.capture());

    final Runnable timeoutHandler = timeoutHandlerCaptor.getValue();
    session.setState(state);

    timeoutHandler.run();
    assertThat(session.getState()).isEqualTo(SessionState.INITIAL);
  }

  @Test
  void createNextRequest_shouldNotResetAuthenticatedStatesWhenRequestTimesOut() {
    final Request<?> request = createRequestMock();
    final RequestInfo requestInfo = session.createNextRequest(request);

    final ArgumentCaptor<Runnable> timeoutHandlerCaptor = ArgumentCaptor.forClass(Runnable.class);
    verify(expirationScheduler).put(eq(requestInfo.getRequestId()), timeoutHandlerCaptor.capture());

    final Runnable timeoutHandler = timeoutHandlerCaptor.getValue();
    session.setState(SessionState.AUTHENTICATED);

    timeoutHandler.run();
    assertThat(session.getState()).isEqualTo(SessionState.AUTHENTICATED);
  }

  private Request<?> createRequestMock() {
    final Request<?> request = mock(Request.class);
    when(request.getResultPromise()).thenReturn(new CompletableFuture<>());
    // avoid failing when logging is on
    when(request.getRequestMessageFactory())
        .thenReturn(
            bytes -> {
              final V5Message mock = mock(V5Message.class);
              when(mock.toString()).thenReturn("");
              return mock;
            });

    return request;
  }
}
