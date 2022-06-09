/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.storage;

import java.net.InetSocketAddress;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.crypto.SECP256K1.SecretKey;
import org.ethereum.beacon.discovery.schema.NodeRecord;

public class LocalNodeRecordStore {

  private volatile NodeRecord latestRecord;
  private final SecretKey secretKey;
  private final NodeRecordListener recordListener;
  private final NewAddressHandler newAddressHandler;

  public LocalNodeRecordStore(
      final NodeRecord record,
      final SecretKey secretKey,
      final NodeRecordListener recordListener,
      final NewAddressHandler newAddressHandler) {
    this.latestRecord = record;
    this.secretKey = secretKey;
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

  public void onCustomFieldValueChanged(final String fieldName, final Bytes value) {
    NodeRecord oldRecord = this.latestRecord;
    NodeRecord newRecord = oldRecord.withUpdatedCustomField(fieldName, value, secretKey);
    this.latestRecord = newRecord;
    recordListener.recordUpdated(oldRecord, newRecord);
  }
}
