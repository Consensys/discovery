package org.ethereum.beacon.discovery.type;

import org.apache.tuweni.bytes.Bytes;

public class Bytes52 extends DelegateBytes {

  public static Bytes52 wrap(Bytes bytes) {
    return new Bytes52(bytes);
  }

  private Bytes52(Bytes delegate) {
    super(delegate, 52);
  }
}
