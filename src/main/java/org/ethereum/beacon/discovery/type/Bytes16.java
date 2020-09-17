package org.ethereum.beacon.discovery.type;

import org.apache.tuweni.bytes.Bytes;

public class Bytes16 extends DelegateBytes {

  public static Bytes16 fromHexString(String hex) {
    return new Bytes16(Bytes.fromHexString(hex));
  }

  public static Bytes16 wrap(Bytes bytes) {
    return new Bytes16(bytes);
  }

  public static Bytes16 wrap(Bytes bytes, int off) {
    return new Bytes16(bytes.slice(off, 16));
  }

  private Bytes16(Bytes delegate) {
    super(delegate, 16);
  }
}
