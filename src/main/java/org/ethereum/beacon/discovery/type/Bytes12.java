package org.ethereum.beacon.discovery.type;

import org.apache.tuweni.bytes.Bytes;

public class Bytes12 extends DelegateBytes {

  public static Bytes12 wrap(Bytes bytes) {
    return new Bytes12(bytes);
  }

  public static Bytes12 wrap(Bytes bytes, int off) {
    return new Bytes12(bytes.slice(off, 12));
  }

  private Bytes12(Bytes delegate) {
    super(delegate, 12);
  }
}
