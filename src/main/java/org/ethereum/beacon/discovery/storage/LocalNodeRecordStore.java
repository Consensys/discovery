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

  public LocalNodeRecordStore(
      NodeRecord record, Bytes privateKey, NodeRecordListener recordListener) {
    this.latestRecord = record;
    this.privateKey = privateKey;
    this.recordListener = recordListener;
  }

  public NodeRecord getLocalNodeRecord() {
    return latestRecord;
  }

  public void onSocketAddressChanged(final InetSocketAddress newAddress) {
    NodeRecord oldRecord = this.latestRecord;
    NodeRecord newRecord = oldRecord.withNewAddress(newAddress, privateKey);
    this.latestRecord = newRecord;
    recordListener.recordUpdated(oldRecord, newRecord);
  }

  public void onCustomFieldValueChanged(final String fieldName, Bytes value) {
    NodeRecord oldRecord = this.latestRecord;
    NodeRecord newRecord = oldRecord.withUpdatedCustomField(fieldName, value, privateKey);
    this.latestRecord = newRecord;
    recordListener.recordUpdated(oldRecord, newRecord);
  }
}
