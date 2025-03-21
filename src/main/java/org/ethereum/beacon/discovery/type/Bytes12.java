/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.ethereum.beacon.discovery.type;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Random;
import org.apache.tuweni.v2.bytes.Bytes;
import org.apache.tuweni.v2.bytes.DelegatingBytes;

public class Bytes12 extends DelegatingBytes {

  public static Bytes12 fromHexString(String hex) {
    return new Bytes12(Bytes.fromHexString(hex));
  }

  public static Bytes12 wrap(Bytes bytes) {
    return new Bytes12(bytes);
  }

  public static Bytes12 wrap(byte[] bytes) {
    return new Bytes12(Bytes.wrap(bytes));
  }

  public static Bytes12 wrap(Bytes bytes, int off) {
    return new Bytes12(bytes.slice(off, 12));
  }

  public static Bytes12 random(Random random) {
    return new Bytes12(Bytes.random(12, random));
  }

  private Bytes12(Bytes delegate) {
    super(delegate, 12);
    checkArgument(delegate.size() == 12, "Expected Bytes of size 12");
  }
}
