/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.storage;

import java.net.Inet6Address;
import java.net.InetSocketAddress;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.crypto.Signer;
import org.ethereum.beacon.discovery.schema.NodeRecord;

public class LocalNodeRecordStore {

  private NodeRecord latestRecord;
  private final Signer signer;
  private final NodeRecordListener recordListener;
  private final NewAddressHandler newAddressHandler;

  public LocalNodeRecordStore(
      final NodeRecord record,
      final Signer signer,
      final NodeRecordListener recordListener,
      final NewAddressHandler newAddressHandler) {
    this.latestRecord = record;
    this.signer = signer;
    this.recordListener = recordListener;
    this.newAddressHandler = newAddressHandler;
  }

  public synchronized NodeRecord getLocalNodeRecord() {
    return latestRecord;
  }

  public synchronized void onSocketAddressChanged(final InetSocketAddress newAddress) {
    NodeRecord oldRecord = this.latestRecord;
    newAddressHandler
        .newAddress(oldRecord, newAddress)
        .ifPresent(record -> updateLocalNodeRecord(oldRecord, record, false));
  }

  /**
   * Updates the UDP or UDP6 port in the local node record after the OS assigns an actual port for a
   * previously ephemeral (port 0) bind.
   *
   * <p>Unlike {@link #onSocketAddressChanged}, this method bypasses the {@link NewAddressHandler}
   * because port resolution is an internal OS operation, not an externally-reported address change.
   * The advertised IP is preserved from the existing ENR; only the port is updated.
   *
   * <p>If the current ENR port is not 0, this method is a no-op.
   *
   * <p>In dual-stack configurations both the IPv4 and IPv6 servers may call this method
   * concurrently. Updates are serialized across all local ENR writer paths so neither family's port
   * is lost and listener notifications remain ordered.
   *
   * @param boundAddress the address returned by the server after binding (bind IP + actual port)
   */
  public synchronized void onBoundPortResolved(final InetSocketAddress boundAddress) {
    final boolean isIpv6 = boundAddress.getAddress() instanceof Inet6Address;
    final NodeRecord current = latestRecord;
    final Optional<InetSocketAddress> currentUdpAddr =
        isIpv6 ? current.getUdp6Address() : current.getUdpAddress();
    if (currentUdpAddr.isEmpty() || currentUdpAddr.get().getPort() != 0) {
      return;
    }
    final InetSocketAddress newUdpAddress =
        new InetSocketAddress(currentUdpAddr.get().getAddress(), boundAddress.getPort());
    final Optional<Integer> existingTcpPort =
        isIpv6
            ? current.getTcp6Address().map(InetSocketAddress::getPort)
            : current.getTcpAddress().map(InetSocketAddress::getPort);
    final Optional<Integer> existingQuicPort =
        isIpv6
            ? current.getQuic6Address().map(InetSocketAddress::getPort)
            : current.getQuicAddress().map(InetSocketAddress::getPort);
    final NodeRecord updated =
        current.withNewAddress(newUdpAddress, existingTcpPort, existingQuicPort, signer);
    updateLocalNodeRecord(current, updated, false);
  }

  public synchronized void onCustomFieldValueChanged(final String fieldName, final Bytes value) {
    final NodeRecord oldRecord = this.latestRecord;
    final NodeRecord newRecord = oldRecord.withUpdatedCustomField(fieldName, value, signer);
    updateLocalNodeRecord(oldRecord, newRecord, true);
  }

  private void updateLocalNodeRecord(
      final NodeRecord oldRecord, final NodeRecord newRecord, final boolean notifyWhenUnchanged) {
    this.latestRecord = newRecord;
    if (notifyWhenUnchanged || !newRecord.equals(oldRecord)) {
      recordListener.recordUpdated(oldRecord, newRecord);
    }
  }
}
