/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.ethereum.beacon.discovery.packet.impl;

import java.util.Objects;
import org.apache.tuweni.v2.bytes.Bytes;
import org.ethereum.beacon.discovery.packet.BytesSerializable;
import org.ethereum.beacon.discovery.util.DecodeException;

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
          "Data is too small: " + bytes.size() + ", (expected at least " + minimalSize + " bytes)");
    }
    return bytes;
  }

  protected AbstractBytes(Bytes bytes) {
    this.bytes = bytes;
  }

  @Override
  public Bytes getBytes() {
    return bytes;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    AbstractBytes that = (AbstractBytes) o;
    return Objects.equals(bytes, that.bytes);
  }

  @Override
  public int hashCode() {
    return Objects.hash(bytes);
  }
}
