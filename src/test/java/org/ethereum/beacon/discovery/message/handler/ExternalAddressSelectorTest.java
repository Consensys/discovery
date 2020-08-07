/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.message.handler;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.SimpleIdentitySchemaInterpreter;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.storage.LocalNodeRecordStore;
import org.junit.jupiter.api.Test;

class ExternalAddressSelectorTest {

  private static final InetSocketAddress ADDRESS1 = new InetSocketAddress("127.0.0.1", 2000);
  private static final InetSocketAddress ADDRESS2 = new InetSocketAddress("127.0.0.2", 2002);
  private static final InetSocketAddress ADDRESS3 = new InetSocketAddress("127.0.0.3", 2003);
  private final Bytes nodeId = Bytes.fromHexString("0x1234567890");
  private final NodeRecord originalNodeRecord =
      SimpleIdentitySchemaInterpreter.createNodeRecord(nodeId, ADDRESS1);
  private final LocalNodeRecordStore localNodeRecordStore =
      new LocalNodeRecordStore(originalNodeRecord, nodeId);

  private final ExternalAddressSelector selector =
      new ExternalAddressSelector(localNodeRecordStore);

  @Test
  void shouldNotChangeAddressUntilMinConfirmationsReached() {
    for (int i = 0; i < ExternalAddressSelector.MIN_CONFIRMATIONS - 1; i++) {
      selector.onExternalAddressReport(Optional.empty(), ADDRESS2);
      assertSelectedAddress(ADDRESS1);
    }
    selector.onExternalAddressReport(Optional.empty(), ADDRESS2);
    assertSelectedAddress(ADDRESS2);
  }

  @Test
  void shouldSelectMostVotedForAddress() {
    for (int i = 0; i < ExternalAddressSelector.MIN_CONFIRMATIONS; i++) {
      selector.onExternalAddressReport(Optional.empty(), ADDRESS2);
    }
    for (int i = 0; i < ExternalAddressSelector.MIN_CONFIRMATIONS + 1; i++) {
      selector.onExternalAddressReport(Optional.empty(), ADDRESS3);
    }
    assertSelectedAddress(ADDRESS3);
  }

  @Test
  void shouldReduceCountWhenNodeChangesReportedAddress() {
    for (int i = 0; i < ExternalAddressSelector.MIN_CONFIRMATIONS - 1; i++) {
      selector.onExternalAddressReport(Optional.empty(), ADDRESS2);
      assertSelectedAddress(ADDRESS1);
    }

    // On the edge of switching to ADDRESS2 but now one of the peers changes its report
    selector.onExternalAddressReport(Optional.of(ADDRESS2), ADDRESS3);
    assertSelectedAddress(ADDRESS1);

    // So it takes two more reports for ADDRESS2 to tick over
    selector.onExternalAddressReport(Optional.empty(), ADDRESS2);
    assertSelectedAddress(ADDRESS1);

    selector.onExternalAddressReport(Optional.empty(), ADDRESS2);
    assertSelectedAddress(ADDRESS2);
  }

  @Test
  void shouldNotReduceCountBelowZero() {
    // Got a node that switched away from ADDRESS2 when it wasn't previously voting for it
    // Can happen because of limiting the number of reported addresses
    selector.onExternalAddressReport(Optional.of(ADDRESS2), ADDRESS1);
    // Still only need MIN_CONFIRMATIONS to switch to ADDRESS2
    for (int i = 0; i < ExternalAddressSelector.MIN_CONFIRMATIONS; i++) {
      selector.onExternalAddressReport(Optional.empty(), ADDRESS2);
    }
    assertSelectedAddress(ADDRESS2);
  }

  @Test
  void shouldLimitNumberOfTrackedAddresses() {
    // Address 2 needs one more vote to be selected
    for (int i = 0; i < ExternalAddressSelector.MIN_CONFIRMATIONS - 1; i++) {
      selector.onExternalAddressReport(Optional.empty(), ADDRESS2);
      assertSelectedAddress(ADDRESS1);
    }

    // Report a lot of different address to overflow the cache
    for (int i = 0; i < ExternalAddressSelector.MAX_EXTERNAL_ADDRESS_COUNT; i++) {
      selector.onExternalAddressReport(Optional.empty(), new InetSocketAddress(3000 + i));
    }

    // Only the least reported address should be removed so ADDRESS2 still only needs one more vote
    selector.onExternalAddressReport(Optional.empty(), ADDRESS2);
    assertSelectedAddress(ADDRESS2);
  }

  private void assertSelectedAddress(final InetSocketAddress address2) {
    assertThat(localNodeRecordStore.getLocalNodeRecord().getUdpAddress()).contains(address2);
  }
}
