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

/**
 * A listener reacting to any updates on a tracked {@link ObservableValue}. If such an update
 * doesn't set a new value but uses the same value the {@link ObservableValue} already had, this
 * listener is still triggered.
 *
 * <p>To start listening, use {@link ObservableValue#addListener(InvalidationListener)}, to stop,
 * use {@link ObservableValue#removeListener(InvalidationListener)}.
 *
 * @param <T> the type of the tracked {@link ObservableValue}
 */
@FunctionalInterface
public interface InvalidationListener<T> {

  /**
   * Indicates that the {@link ObservableValue} was invalidated (= its value was updated). The new
   * value may be the same as the old value. To retrieve the new value, call {@link
   * ObservableValue#getValue()} on the passed instance.
   *
   * <p>Note: Don't modify the passed {@link ObservableValue} within this method.
   *
   * @param observable the tracked {@link ObservableValue} after the update
   */
  void invalidated(ObservableValue<? extends T> observable);
}
