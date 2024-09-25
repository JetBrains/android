/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.common.settings;

import java.util.function.Supplier;

/** A mutable property. */
public interface Property<T> {

  /** Returns the value of the property. */
  T getValue();

  /** Assigns a value to the property. */
  void setValue(T value);

  /** A function which accesses the value of a property on the given object. */
  @FunctionalInterface
  interface Getter<ObjectT, ValueT> {
    ValueT getValue(ObjectT object);
  }

  /** A function which assigns a value to a property on the given object. */
  @FunctionalInterface
  interface Setter<ObjectT, ValueT> {
    void setValue(ObjectT object, ValueT value);
  }

  /**
   * Creates a {@link Property} from a getter and setter which operate on a property of the given
   * object.
   */
  static <ObjectT, ValueT> Property<ValueT> create(
      Supplier<ObjectT> objectSupplier,
      Getter<ObjectT, ValueT> getter,
      Setter<ObjectT, ValueT> setter) {

    return new Property<ValueT>() {
      @Override
      public ValueT getValue() {
        return getter.getValue(objectSupplier.get());
      }

      @Override
      public void setValue(ValueT value) {
        setter.setValue(objectSupplier.get(), value);
      }
    };
  }
}
