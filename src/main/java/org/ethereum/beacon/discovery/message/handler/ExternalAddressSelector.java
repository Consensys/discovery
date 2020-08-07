/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.message.handler;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.annotations.VisibleForTesting;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;
import org.ethereum.beacon.discovery.storage.LocalNodeRecordStore;

public class ExternalAddressSelector {

  static final int MIN_CONFIRMATIONS = 2;
  static final int MAX_EXTERNAL_ADDRESS_COUNT = 50;
  static final Duration TTL = Duration.ofMinutes(5);

  /**
   * Tracks an estimate of the number of nodes that have reported a particular external address. The
   * number of addresses we keep counts for is limited to avoid peers filling our memory by
   * reporting a large number of different address.
   *
   * <p>The count must never be greater than the number of unique nodes that have reported that
   * address.
   *
   * <p>Also tracks the last time an address is reported so that stale addresses can be removed even
   * if they have accumlated a lot of reports.
   */
  private final Map<InetSocketAddress, ReportData> reportedAddressCounts = new HashMap<>();

  private final LocalNodeRecordStore localNodeRecordStore;

  public ExternalAddressSelector(final LocalNodeRecordStore localNodeRecordStore) {
    this.localNodeRecordStore = localNodeRecordStore;
  }

  public void onExternalAddressReport(
      final Optional<InetSocketAddress> previouslyReportedAddress,
      final InetSocketAddress reportedAddress,
      final Instant reportedTime) {

    previouslyReportedAddress.ifPresent(this::removeVote);
    addVote(reportedAddress, reportedTime);

    removeStaleAddresses(reportedTime);
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

  private void removeStaleAddresses(final Instant now) {
    final Instant cutOffTime = now.minus(TTL);
    final List<InetSocketAddress> staleAddresses =
        reportedAddressCounts.entrySet().stream()
            .filter(entry -> entry.getValue().lastReportedBefore(cutOffTime))
            .map(Entry::getKey)
            .collect(Collectors.toList());
    staleAddresses.forEach(reportedAddressCounts::remove);
  }

  private void limitTrackedAddresses() {
    while (reportedAddressCounts.size() > MAX_EXTERNAL_ADDRESS_COUNT) {
      // Too many addresses being tracked, remove the one with the fewest votes
      reportedAddressCounts.entrySet().stream()
          .min(Entry.comparingByValue(Comparator.comparing(ReportData::getLastReportedTime)))
          .map(Entry::getKey)
          .ifPresent(reportedAddressCounts::remove);
    }
  }

  private void addVote(final InetSocketAddress reportedAddress, final Instant reportTime) {
    reportedAddressCounts.compute(
        reportedAddress,
        (key, currentValue) ->
            currentValue != null ? currentValue.addReport(reportTime) : new ReportData(reportTime));
  }

  private void removeVote(final InetSocketAddress previousAddress) {
    reportedAddressCounts.compute(
        previousAddress,
        (key, currentValue) -> currentValue != null ? currentValue.removeReport() : null);
  }

  private Optional<InetSocketAddress> selectExternalAddress() {
    return reportedAddressCounts.entrySet().stream()
        .filter(entry -> entry.getValue().getReportCount() >= MIN_CONFIRMATIONS)
        .max(Entry.comparingByValue(Comparator.comparing(ReportData::getReportCount)))
        .map(Entry::getKey);
  }

  @VisibleForTesting
  void assertInvariants() {
    checkState(
        reportedAddressCounts.size() <= MAX_EXTERNAL_ADDRESS_COUNT,
        "Should limit number of tracked addresses. Size %s",
        reportedAddressCounts.size());
    checkState(
        reportedAddressCounts.values().stream().noneMatch(data -> data.getReportCount() <= 0),
        "Should not have counts less than 1 (%s)",
        reportedAddressCounts);
  }

  private static class ReportData {
    private int reportCount;
    private Instant lastReportedTime;

    public ReportData(final Instant time) {
      this.reportCount = 1;
      this.lastReportedTime = time;
    }

    public ReportData addReport(final Instant time) {
      lastReportedTime = time;
      reportCount++;
      return this;
    }

    public ReportData removeReport() {
      reportCount--;
      return reportCount > 0 ? this : null;
    }

    public Instant getLastReportedTime() {
      return lastReportedTime;
    }

    public int getReportCount() {
      return reportCount;
    }

    public boolean lastReportedBefore(final Instant cutOffTime) {
      return lastReportedTime.isBefore(cutOffTime);
    }
  }
}
