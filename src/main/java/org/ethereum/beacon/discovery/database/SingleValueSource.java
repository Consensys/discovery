/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.database;

import java.util.Optional;

/** Created by Anton Nashatyrev on 19.11.2018. */
public interface SingleValueSource<V> {

  Optional<V> get();

  void set(V value);

  void remove();

  static <ValueType> SingleValueSource<ValueType> memSource() {
    return new SingleValueSource<>() {
      ValueType value;

      @Override
      public Optional<ValueType> get() {
        return Optional.ofNullable(value);
      }

      @Override
      public void set(ValueType value) {
        this.value = value;
      }

      @Override
      public void remove() {
        this.value = null;
      }
    };
  }
}
