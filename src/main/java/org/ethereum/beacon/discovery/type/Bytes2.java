/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.ethereum.beacon.discovery.type;

import static com.google.common.base.Preconditions.checkArgument;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.DelegatingBytes;

public class Bytes2 extends DelegatingBytes {

  public static Bytes2 fromHexString(String hex) {
    return new Bytes2(Bytes.fromHexString(hex));
  }

  public static Bytes2 wrap(Bytes bytes) {
    return new Bytes2(bytes);
  }

  private Bytes2(Bytes delegate) {
    super(delegate);
    checkArgument(delegate.size() == 2, "Expected Bytes of size 2");
  }
}
