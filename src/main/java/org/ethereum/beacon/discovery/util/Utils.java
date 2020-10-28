/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.util;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes;
import org.apache.tuweni.units.bigints.UInt64;

public class Utils {

  public static <A, B> Function<Optional<A>, Stream<B>> optionalFlatMap(Function<A, B> func) {
    return opt -> opt.map(a -> Stream.of(func.apply(a))).orElseGet(Stream::empty);
  }

  public static <A, B> Function<A, Stream<B>> nullableFlatMap(Function<A, B> func) {
    return n -> n != null ? Stream.of(func.apply(n)) : Stream.empty();
  }

  public static <C> void futureForward(
      CompletableFuture<C> result, CompletableFuture<C> forwardToFuture) {
    result.whenComplete(
        (res, t) -> {
          if (t != null) {
            forwardToFuture.completeExceptionally(t);
          } else {
            forwardToFuture.complete(res);
          }
        });
  }

  /**
   * @param size required size, in bytes
   * @return byte array representation of BigInteger for unsigned numeric
   *     <p>{@link BigInteger#toByteArray()} adds a bit for the sign. If you work with unsigned
   *     numerics it's always a 0. But if an integer uses exactly 8-some bits, sign bit will add an
   *     extra 0 byte to the result, which could broke some things. This method removes this
   *     redundant prefix byte when extracting byte array from BigInteger
   */
  public static byte[] extractBytesFromUnsignedBigInt(BigInteger bigInteger, int size) {
    byte[] bigIntBytes = bigInteger.toByteArray();
    if (bigIntBytes.length == size) {
      return bigIntBytes;
    } else if (bigIntBytes.length == (size + 1)) {
      byte[] res = new byte[size];
      System.arraycopy(bigIntBytes, 1, res, 0, res.length);
      return res;
    } else if (bigIntBytes.length < size) {
      byte[] res = new byte[size];
      System.arraycopy(bigIntBytes, 0, res, size - bigIntBytes.length, bigIntBytes.length);
      return res;
    } else {
      throw new RuntimeException(
          String.format("Cannot extract bytes of size %s from BigInteger [%s]", size, bigInteger));
    }
  }

  /**
   * Left pad a {@link Bytes} value with zero bytes to create a {@link Bytes}.
   *
   * @param value The bytes value pad.
   * @return A {@link Bytes} that exposes the left-padded bytes of {@code value}.
   * @throws IllegalArgumentException if {@code value.size() &gt; 4}.
   */
  public static Bytes leftPad(Bytes value, int length) {
    checkNotNull(value);
    checkArgument(value.size() <= length, "Expected at most %s bytes but got %s", 4, value.size());
    MutableBytes result = MutableBytes.create(length);
    value.copyTo(result, length - value.size());
    return result;
  }

  public static UInt64 toUInt64(Bytes bytes) throws IllegalArgumentException {
    checkArgument(bytes.size() <= 8);
    return UInt64.fromBytes(Utils.leftPad(bytes, 8));
  }

  public static int compareBytes(Bytes32 b1, Bytes32 b2) {
    for (int i = 0; i < b1.size(); i++) {
      int res = (b1.get(i) & 0xFF) - (b2.get(i) & 0xFF);
      if (res != 0) {
        return res;
      }
    }
    return 0;
  }
}
