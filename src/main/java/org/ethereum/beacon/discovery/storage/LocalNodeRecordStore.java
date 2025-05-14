/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.storage;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.crypto.SECP256K1.SecretKey;
import org.ethereum.beacon.discovery.schema.NodeRecord;

public class LocalNodeRecordStore {

  private final AtomicReference<NodeRecord> latestRecord;
  private final SecretKey secretKey;
  private final NodeRecordListener recordListener;
  private final NewAddressHandler newAddressHandler;

  public LocalNodeRecordStore(
      final NodeRecord record,
      final SecretKey secretKey,
      final NodeRecordListener recordListener,
      final NewAddressHandler newAddressHandler) {
    this.latestRecord = new AtomicReference<>(record);
    this.secretKey = secretKey;
    this.recordListener = recordListener;
    this.newAddressHandler = newAddressHandler;
  }

  public NodeRecord getLocalNodeRecord() {
    return latestRecord.get();
  }

  public void onSocketAddressChanged(final InetSocketAddress newAddress) {
    newAddressHandler
        .newAddress(latestRecord.get(), newAddress)
        .ifPresent(
            newRecord -> {
              final NodeRecord oldRecord = latestRecord.getAndSet(newRecord);
              if (!newRecord.equals(oldRecord)) {
                recordListener.recordUpdated(oldRecord, newRecord);
              }
            });
  }

  public void onCustomFieldValueChanged(final String fieldName, final Bytes value) {
    final NodeRecord oldRecord =
        latestRecord.getAndUpdate(
            nodeRecord -> {
              // We need to check the value before upgrading otherwise we end up increasing the
              // sequence number (even if we are updating with the same value)
              if (!value.equals(nodeRecord.get(fieldName))) {
                return nodeRecord.withUpdatedCustomField(fieldName, value, secretKey);
              } else {
                return nodeRecord;
              }
            });
    // Check if we actually updated the value in the record
    if (!value.equals(oldRecord.get(fieldName))) {
      recordListener.recordUpdated(oldRecord, latestRecord.get());
    }
  }
}
