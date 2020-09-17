package org.ethereum.beacon.discovery.packet5_1.impl;

import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.packet5_1.BytesSerializable;
import org.ethereum.beacon.discovery.packet5_1.DecodeException;

public abstract class AbstractBytes implements BytesSerializable {

  private final Bytes bytes;

  public static Bytes checkStrictSize(Bytes bytes, int expectedSize) throws DecodeException {
    if (bytes.size() != expectedSize) {
      throw new DecodeException(
          "Data size (" + bytes.size() + ") doesn't match expected: " + expectedSize);
    }
    return bytes;
  }
  public static Bytes checkMinSize(Bytes bytes, int minimalSize) throws DecodeException {
    if (bytes.size() < minimalSize) {
      throw new DecodeException(
          "Data is too small: " + bytes.size() + ", (expected at least " + minimalSize
              + " bytes)");
    }
    return bytes;
  }

  public AbstractBytes(Bytes bytes) {
    this.bytes = bytes;
  }

  public Bytes getBytes() {
    return bytes;
  }
}
