/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.database;

import javax.annotation.Nonnull;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.MutableBytes;

public class XorDataSource<TValue> extends CodecSource.KeyOnly<Bytes, TValue, Bytes> {

  public XorDataSource(@Nonnull DataSource<Bytes, TValue> upstreamSource, Bytes keyXorModifier) {
    super(upstreamSource, key -> xorLongest(key, keyXorModifier));
  }

  private static Bytes xorLongest(Bytes v1, Bytes v2) {
    Bytes longVal = v1.size() >= v2.size() ? v1 : v2;
    Bytes shortVal = v1.size() < v2.size() ? v1 : v2;
    MutableBytes ret = longVal.mutableCopy();
    int longLen = longVal.size();
    int shortLen = shortVal.size();
    for (int i = 0; i < shortLen; i++) {
      ret.set(longLen - i - 1, (byte) (ret.get(longLen - i - 1) ^ shortVal.get(shortLen - i - 1)));
    }
    return ret;
  }
}
