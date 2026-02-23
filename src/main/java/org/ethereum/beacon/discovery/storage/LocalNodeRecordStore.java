/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.storage;

import java.net.Inet6Address;
import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.crypto.Signer;
import org.ethereum.beacon.discovery.schema.NodeRecord;

public class LocalNodeRecordStore {

  private final AtomicReference<NodeRecord> latestRecord;
  private final Signer signer;
  private final NodeRecordListener recordListener;
  private final NewAddressHandler newAddressHandler;

  public LocalNodeRecordStore(
      final NodeRecord record,
      final Signer signer,
      final NodeRecordListener recordListener,
      final NewAddressHandler newAddressHandler) {
    this.latestRecord = new AtomicReference<>(record);
    this.signer = signer;
    this.recordListener = recordListener;
    this.newAddressHandler = newAddressHandler;
  }

  public NodeRecord getLocalNodeRecord() {
    return latestRecord.get();
  }

  public void onSocketAddressChanged(final InetSocketAddress newAddress) {
    NodeRecord oldRecord = this.latestRecord.get();
    newAddressHandler
        .newAddress(oldRecord, newAddress)
        .ifPresent(
            record -> {
              this.latestRecord.set(record);
              if (!record.equals(oldRecord)) {
                recordListener.recordUpdated(oldRecord, record);
              }
            });
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
   * concurrently. A CAS retry loop ensures that each update is applied to the latest record so
   * neither family's port is lost.
   *
   * @param boundAddress the address returned by the server after binding (bind IP + actual port)
   */
  public void onBoundPortResolved(final InetSocketAddress boundAddress) {
    final boolean isIpv6 = boundAddress.getAddress() instanceof Inet6Address;
    NodeRecord current;
    NodeRecord updated;
    do {
      current = latestRecord.get();
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
      updated = current.withNewAddress(newUdpAddress, existingTcpPort, existingQuicPort, signer);
    } while (!latestRecord.compareAndSet(current, updated));
    if (!updated.equals(current)) {
      recordListener.recordUpdated(current, updated);
    }
  }

  public void onCustomFieldValueChanged(final String fieldName, final Bytes value) {
    NodeRecord oldRecord = this.latestRecord.get();
    NodeRecord newRecord = oldRecord.withUpdatedCustomField(fieldName, value, signer);
    this.latestRecord.set(newRecord);
    recordListener.recordUpdated(oldRecord, newRecord);
  }
}
