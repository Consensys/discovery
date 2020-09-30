/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.ethereum.beacon.discovery.type;

import org.apache.tuweni.bytes.Bytes;

public class Bytes8 extends DelegateBytes {

  public static Bytes8 wrap(Bytes bytes) {
    return new Bytes8(bytes);
  }

  private Bytes8(Bytes delegate) {
    super(delegate, 8);
  }
}
