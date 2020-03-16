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

  public LocalNodeRecordStore(final NodeRecord record, final Bytes privateKey) {
    latestRecord = record;
    this.privateKey = privateKey;
  }

  public NodeRecord getLocalNodeRecord() {
    return latestRecord;
  }

  public void onSocketAddressChanged(final InetSocketAddress newAddress) {
    final NodeRecord newRecord = this.latestRecord.withNewAddress(newAddress, privateKey);
    this.latestRecord = newRecord;
  }
}
