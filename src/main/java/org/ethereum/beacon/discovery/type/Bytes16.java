package org.ethereum.beacon.discovery.type;

import org.apache.tuweni.bytes.Bytes;

public class Bytes16 extends DelegateBytes {

  public static Bytes16 wrap(Bytes bytes) {
    return new Bytes16(bytes);
  }

  private Bytes16(Bytes delegate) {
    super(delegate, 16);
  }
}
