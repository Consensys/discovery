/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.ethereum.beacon.discovery.type;

import org.apache.tuweni.bytes.Bytes;

public class Bytes2 extends DelegateBytes {

  public static Bytes2 fromHexString(String hex) {
    return new Bytes2(Bytes.fromHexString(hex));
  }

  public static Bytes2 wrap(Bytes bytes) {
    return new Bytes2(bytes);
  }

  private Bytes2(Bytes delegate) {
    super(delegate, 2);
  }
}
