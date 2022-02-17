/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.util;

import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.rlp.RLP;
import org.apache.tuweni.rlp.RLPReader;

/** Handy utilities used for RLP encoding and decoding. */
public class RlpUtil {
  public static final int UINT64_MAX_SIZE = 8;
  public static final int MAX_NESTED_LIST_LEVELS = 3;

  public static Bytes checkMaxSize(final Bytes value, final int maxSize) {
    if (value.size() > maxSize) {
      throw new RlpDecodeException(
          "Value of size " + value.size() + " exceeds max size " + maxSize);
    }
    return value;
  }

  public static Bytes checkSizeEither(final Bytes value, final int size1, final int size2) {
    if (value.size() != size1 && value.size() != size2) {
      throw new RlpDecodeException(
          "Value of size " + value.size() + " not one of " + size1 + " or " + size2);
    }
    return value;
  }

  public static void checkComplete(final RLPReader reader) {
    if (!reader.isComplete()) {
      throw new RlpDecodeException("Unexpected trailing data detected");
    }
  }

  public static <T> T readRlpList(final Bytes rlp, final Function<RLPReader, T> fn) {
    return RLP.decode(
        rlp,
        reader -> {
          final T result = reader.readList(fn);
          checkComplete(reader);
          return result;
        });
  }
}
