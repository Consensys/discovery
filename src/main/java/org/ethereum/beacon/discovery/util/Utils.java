/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.util;

import static com.google.common.base.Preconditions.checkArgument;

import java.math.BigInteger;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.apache.tuweni.v2.bytes.Bytes;
import org.apache.tuweni.v2.units.bigints.UInt64;
import reactor.core.Exceptions;

public class Utils {

  /**
   * Reactor treats a number of Throwable as 'fatal' by default including StackOverflowError and
   * OutOfMemoryError However the latter two errors could be pretty recoverable. This predicate
   * excludes the above two errors from the Reactor default fatal set
   */
  public static final Predicate<Throwable> RECOVERABLE_ERRORS_PREDICATE =
      t -> {
        if (t instanceof StackOverflowError || t instanceof OutOfMemoryError) {
          return true;
        } else {
          try {
            Exceptions.throwIfFatal(t);
            return true;
          } catch (Throwable e) {
            return false;
          }
        }
      };

  public static <A, B> Function<Optional<A>, Stream<B>> optionalFlatMap(Function<A, B> func) {
    return opt -> opt.stream().map(func);
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
   * Return byte array representation of BigInteger for unsigned numeric
   *
   * <p>{@link BigInteger#toByteArray()} adds a bit for the sign. If you work with unsigned numerics
   * it's always a 0. But if an integer uses exactly 8-some bits, sign bit will add an extra 0 byte
   * to the result, which could broke some things. This method removes this redundant prefix byte
   * when extracting byte array from BigInteger
   *
   * @param size required size, in bytes
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

  public static UInt64 toUInt64(Bytes bytes) throws IllegalArgumentException {
    checkArgument(bytes.size() <= 8);
    return UInt64.fromBytes(bytes.mutableCopy().leftPad(8));
  }

  public static int compareBytes(Bytes b1, Bytes b2) {
    for (int i = 0; i < b1.size(); i++) {
      int res = (b1.get(i) & 0xFF) - (b2.get(i) & 0xFF);
      if (res != 0) {
        return res;
      }
    }
    return 0;
  }

  public static boolean isPortValid(final int port) {
    return (port >= 0 && port <= 65535);
  }
}
