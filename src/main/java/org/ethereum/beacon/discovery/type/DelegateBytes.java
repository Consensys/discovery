/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.ethereum.beacon.discovery.type;

import com.google.common.base.Preconditions;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.MutableBytes;

class DelegateBytes implements Bytes {
  private final Bytes delegate;
  protected final int size;

  protected DelegateBytes(Bytes delegate, int size) {
    Preconditions.checkArgument(delegate.size() == size, "Expected Bytes of size " + size);
    this.delegate = delegate;
    this.size = size;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    DelegateBytes that = (DelegateBytes) o;
    return Objects.equals(delegate, that.delegate);
  }

  @Override
  public int hashCode() {
    return Objects.hash(delegate);
  }

  @Override
  public int size() {
    return delegate.size();
  }

  @Override
  public byte get(int i) {
    return delegate.get(i);
  }

  @Override
  public int getInt(int i) {
    return delegate.getInt(i);
  }

  @Override
  public int getInt(int i, ByteOrder order) {
    return delegate.getInt(i, order);
  }

  @Override
  public int toInt() {
    return delegate.toInt();
  }

  @Override
  public int toInt(ByteOrder order) {
    return delegate.toInt(order);
  }

  @Override
  public boolean isEmpty() {
    return delegate.isEmpty();
  }

  @Override
  public long getLong(int i) {
    return delegate.getLong(i);
  }

  @Override
  public long getLong(int i, ByteOrder order) {
    return delegate.getLong(i, order);
  }

  @Override
  public long toLong() {
    return delegate.toLong();
  }

  @Override
  public long toLong(ByteOrder order) {
    return delegate.toLong(order);
  }

  @Override
  public BigInteger toBigInteger() {
    return delegate.toBigInteger();
  }

  @Override
  public BigInteger toBigInteger(ByteOrder order) {
    return delegate.toBigInteger(order);
  }

  @Override
  public BigInteger toUnsignedBigInteger() {
    return delegate.toUnsignedBigInteger();
  }

  @Override
  public BigInteger toUnsignedBigInteger(ByteOrder order) {
    return delegate.toUnsignedBigInteger(order);
  }

  @Override
  public boolean isZero() {
    return delegate.isZero();
  }

  @Override
  public boolean hasLeadingZero() {
    return delegate.hasLeadingZero();
  }

  @Override
  public int numberOfLeadingZeros() {
    return delegate.numberOfLeadingZeros();
  }

  @Override
  public boolean hasLeadingZeroByte() {
    return delegate.hasLeadingZeroByte();
  }

  @Override
  public int numberOfLeadingZeroBytes() {
    return delegate.numberOfLeadingZeroBytes();
  }

  @Override
  public int numberOfTrailingZeroBytes() {
    return delegate.numberOfTrailingZeroBytes();
  }

  @Override
  public int bitLength() {
    return delegate.bitLength();
  }

  @Override
  public Bytes and(Bytes other) {
    return delegate.and(other);
  }

  @Override
  public <T extends MutableBytes> T and(Bytes other, T result) {
    return delegate.and(other, result);
  }

  @Override
  public Bytes or(Bytes other) {
    return delegate.or(other);
  }

  @Override
  public <T extends MutableBytes> T or(Bytes other, T result) {
    return delegate.or(other, result);
  }

  @Override
  public Bytes xor(Bytes other) {
    return delegate.xor(other);
  }

  @Override
  public <T extends MutableBytes> T xor(Bytes other, T result) {
    return delegate.xor(other, result);
  }

  @Override
  public Bytes not() {
    return delegate.not();
  }

  @Override
  public <T extends MutableBytes> T not(T result) {
    return delegate.not(result);
  }

  @Override
  public Bytes shiftRight(int distance) {
    return delegate.shiftRight(distance);
  }

  @Override
  public <T extends MutableBytes> T shiftRight(int distance, T result) {
    return delegate.shiftRight(distance, result);
  }

  @Override
  public Bytes shiftLeft(int distance) {
    return delegate.shiftLeft(distance);
  }

  @Override
  public <T extends MutableBytes> T shiftLeft(int distance, T result) {
    return delegate.shiftLeft(distance, result);
  }

  @Override
  public Bytes slice(int i) {
    return delegate.slice(i);
  }

  @Override
  public Bytes slice(int i, int length) {
    return delegate.slice(i, length);
  }

  @Override
  public Bytes copy() {
    return delegate.copy();
  }

  @Override
  public MutableBytes mutableCopy() {
    return delegate.mutableCopy();
  }

  @Override
  public void copyTo(MutableBytes destination) {
    delegate.copyTo(destination);
  }

  @Override
  public void copyTo(MutableBytes destination, int destinationOffset) {
    delegate.copyTo(destination, destinationOffset);
  }

  @Override
  public void appendTo(ByteBuffer byteBuffer) {
    delegate.appendTo(byteBuffer);
  }

  @Override
  public <T extends Appendable> T appendHexTo(T appendable) {
    return delegate.appendHexTo(appendable);
  }

  @Override
  public int commonPrefixLength(Bytes other) {
    return delegate.commonPrefixLength(other);
  }

  @Override
  public Bytes commonPrefix(Bytes other) {
    return delegate.commonPrefix(other);
  }

  @Override
  public Bytes trimLeadingZeros() {
    return delegate.trimLeadingZeros();
  }

  @Override
  public void update(MessageDigest digest) {
    delegate.update(digest);
  }

  @Override
  public Bytes reverse() {
    return delegate.reverse();
  }

  @Override
  public byte[] toArray() {
    return delegate.toArray();
  }

  @Override
  public byte[] toArrayUnsafe() {
    return delegate.toArrayUnsafe();
  }

  @Override
  public String toString() {
    return delegate.toString();
  }

  @Override
  public String toHexString() {
    return delegate.toHexString();
  }

  @Override
  public String toShortHexString() {
    return delegate.toShortHexString();
  }

  @Override
  public String toBase64String() {
    return delegate.toBase64String();
  }
}
