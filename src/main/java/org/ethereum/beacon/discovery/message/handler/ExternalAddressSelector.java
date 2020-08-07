/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.message.handler;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import org.ethereum.beacon.discovery.storage.LocalNodeRecordStore;

public class ExternalAddressSelector {

  static final int MIN_CONFIRMATIONS = 5;
  static final int MAX_EXTERNAL_ADDRESS_COUNT = 50;

  /**
   * Tracks an estimate of the number of nodes that have reported a particular external address. The
   * number of addresses we keep counts for is limited to avoid peers filling our memory by
   * reporting a large number of different address.
   *
   * <p>The count must never be greater than the number of unique nodes that have reported that
   * address.
   */
  private final Map<InetSocketAddress, Integer> reportedAddressCounts = new HashMap<>();

  private final LocalNodeRecordStore localNodeRecordStore;

  public ExternalAddressSelector(final LocalNodeRecordStore localNodeRecordStore) {
    this.localNodeRecordStore = localNodeRecordStore;
  }

  public void onExternalAddressReport(
      final Optional<InetSocketAddress> previouslyReportedAddress,
      final InetSocketAddress reportedAddress) {

    previouslyReportedAddress.ifPresent(this::removeVote);
    addVote(reportedAddress);

    limitTrackedAddresses();

    selectExternalAddress()
        .ifPresent(
            selectedAddress -> {
              final Optional<InetSocketAddress> currentAddress =
                  localNodeRecordStore.getLocalNodeRecord().getUdpAddress();
              if (currentAddress.map(current -> !current.equals(selectedAddress)).orElse(true)) {
                localNodeRecordStore.onSocketAddressChanged(selectedAddress);
              }
            });
  }

  private void limitTrackedAddresses() {
    while (reportedAddressCounts.size() > MAX_EXTERNAL_ADDRESS_COUNT) {
      // Too many addresses being tracked, remove the one with the fewest votes
      reportedAddressCounts.entrySet().stream()
          .min(Entry.comparingByValue())
          .map(Entry::getKey)
          .ifPresent(reportedAddressCounts::remove);
    }
  }

  private void addVote(final InetSocketAddress reportedAddress) {
    reportedAddressCounts.compute(
        reportedAddress, (key, currentValue) -> currentValue != null ? currentValue + 1 : 1);
  }

  private void removeVote(final InetSocketAddress previousAddress) {
    reportedAddressCounts.compute(
        previousAddress,
        (key, currentValue) -> {
          if (currentValue == null || currentValue <= 1) {
            // If the value has been pruned or would wind up as 0 just remove it
            return null;
          }
          return currentValue - 1;
        });
  }

  private Optional<InetSocketAddress> selectExternalAddress() {
    return reportedAddressCounts.entrySet().stream()
        .filter(entry -> entry.getValue() >= MIN_CONFIRMATIONS)
        .max(Entry.comparingByValue())
        .map(Entry::getKey);
  }
}
