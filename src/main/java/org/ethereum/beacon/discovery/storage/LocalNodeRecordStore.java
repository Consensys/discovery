/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.storage;

import java.net.InetSocketAddress;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.schema.NodeRecord;

public class LocalNodeRecordStore {

  private volatile NodeRecord latestRecord;
  private final Bytes privateKey;
  private final NodeRecordListener recordListener;
  private final NewAddressListener newAddressListener;

  public LocalNodeRecordStore(
      NodeRecord record,
      Bytes privateKey,
      NodeRecordListener recordListener,
      NewAddressListener newAddressListener) {
    this.latestRecord = record;
    this.privateKey = privateKey;
    this.recordListener = recordListener;
    this.newAddressListener = newAddressListener;
  }

  public NodeRecord getLocalNodeRecord() {
    return latestRecord;
  }

  public void onSocketAddressChanged(final InetSocketAddress newAddress) {
    NodeRecord oldRecord = this.latestRecord;
    NodeRecord newRecord = oldRecord.withNewAddress(newAddress, privateKey);
    newAddressListener
        .newAddress(oldRecord, newRecord)
        .ifPresent(record -> this.latestRecord = record);
  }

  public void onCustomFieldValueChanged(final String fieldName, Bytes value) {
    NodeRecord oldRecord = this.latestRecord;
    NodeRecord newRecord = oldRecord.withUpdatedCustomField(fieldName, value, privateKey);
    this.latestRecord = newRecord;
    recordListener.recordUpdated(oldRecord, newRecord);
  }
}
