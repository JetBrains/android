/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
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
package com.google.idea.common.ui.properties;

import javax.annotation.Nullable;

/**
 * A listener reacting only to actual changes of the tracked {@link ObservableValue}. Contrary than
 * an {@link InvalidationListener}, a {@code ChangeListener} is only triggered if an update sets a
 * different value than the one the {@link ObservableValue} had.
 *
 * <p>To start listening, use {@link ObservableValue#addListener(ChangeListener)}, to stop, use
 * {@link ObservableValue#removeListener(ChangeListener)}.
 *
 * @param <T> the type of the tracked {@link ObservableValue}
 */
@FunctionalInterface
public interface ChangeListener<T> {

  /**
   * Indicates that the {@link ObservableValue} changed (= updated to a new value). The new value is
   * guaranteed to be different than the old value (regarding {@link Object#equals(Object)}).
   * Otherwise, this method wouldn't be called.
   *
   * <p>Note: Don't modify the passed {@link ObservableValue} or the oldValue/newValue within this
   * method.
   *
   * @param observable the tracked {@link ObservableValue} after the update
   * @param oldValue the value of the {@link ObservableValue} before the update
   * @param newValue the value of the {@link ObservableValue} after the update
   */
  void changed(ObservableValue<? extends T> observable, @Nullable T oldValue, @Nullable T newValue);
}
