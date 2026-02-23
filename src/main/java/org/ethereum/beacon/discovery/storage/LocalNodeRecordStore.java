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

  private volatile NodeRecord latestRecord;
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

  public NodeRecord getLocalNodeRecord() {
    return latestRecord;
  }

  public void onSocketAddressChanged(final InetSocketAddress newAddress) {
    NodeRecord oldRecord = this.latestRecord;
    newAddressHandler
        .newAddress(oldRecord, newAddress)
        .ifPresent(
            record -> {
              this.latestRecord = record;
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
   * @param boundAddress the address returned by the server after binding (bind IP + actual port)
   */
  public void onBoundPortResolved(final InetSocketAddress boundAddress) {
    final NodeRecord oldRecord = this.latestRecord;
    final boolean isIpv6 = boundAddress.getAddress() instanceof Inet6Address;
    final Optional<InetSocketAddress> currentUdpAddr =
        isIpv6 ? oldRecord.getUdp6Address() : oldRecord.getUdpAddress();
    if (currentUdpAddr.isEmpty() || currentUdpAddr.get().getPort() != 0) {
      return;
    }
    final InetSocketAddress newUdpAddress =
        new InetSocketAddress(currentUdpAddr.get().getAddress(), boundAddress.getPort());
    final Optional<Integer> existingTcpPort =
        isIpv6
            ? oldRecord.getTcp6Address().map(InetSocketAddress::getPort)
            : oldRecord.getTcpAddress().map(InetSocketAddress::getPort);
    final Optional<Integer> existingQuicPort =
        isIpv6
            ? oldRecord.getQuic6Address().map(InetSocketAddress::getPort)
            : oldRecord.getQuicAddress().map(InetSocketAddress::getPort);
    final NodeRecord newRecord =
        oldRecord.withNewAddress(newUdpAddress, existingTcpPort, existingQuicPort, signer);
    this.latestRecord = newRecord;
    if (!newRecord.equals(oldRecord)) {
      recordListener.recordUpdated(oldRecord, newRecord);
    }
  }

  public void onCustomFieldValueChanged(final String fieldName, final Bytes value) {
    NodeRecord oldRecord = this.latestRecord;
    NodeRecord newRecord = oldRecord.withUpdatedCustomField(fieldName, value, signer);
    this.latestRecord = newRecord;
    recordListener.recordUpdated(oldRecord, newRecord);
  }
}
