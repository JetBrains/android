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
 * A wrapped value for which updates can be observed by listeners. The contained value can change
 * over time and also be {@code null}, which signifies an unset value.
 *
 * @param <T> the type of the contained value
 */
public interface ObservableValue<T> {

  /**
   * Subscribes an {@link InvalidationListener} to receive notifications on updates of this {@code
   * ObservableValue}.
   *
   * <p>The {@code ObservableValue} keeps a strong reference to this listener (and anything
   * referenced by it). Hence, don't forget to use {@link #removeListener(InvalidationListener)} to
   * allow GC to kick in when the listener is not needed anymore.
   *
   * <p>The same listener instance can be attached to multiple {@code ObservableValue}s.
   *
   * <p>It's also possible to attach the same listener instance multiple times to the same {@code
   * ObservableValue} instance. However, that would result in that listener instance being triggered
   * multiple times upon an update of the {@code ObservableValue}. In addition, {@link
   * #removeListener(InvalidationListener)} will only remove one of these instances, which might
   * lead to confusing behavior. Hence, it's recommended to avoid this.
   *
   * @param listener the listener which should be notified on updates
   */
  void addListener(InvalidationListener<? super T> listener);

  /**
   * Subscribes a {@link ChangeListener} to receive notifications on updates of this {@code
   * ObservableValue}.
   *
   * <p>The {@code ObservableValue} keeps a strong reference to this listener (and anything
   * referenced by it). Hence, don't forget to use {@link #removeListener(ChangeListener)} to allow
   * GC to kick in when the listener is not needed anymore.
   *
   * <p>The same listener instance can be attached to multiple {@code ObservableValue}s.
   *
   * <p>It's also possible to attach the same listener instance multiple times to the same {@code
   * ObservableValue} instance. However, that would result in that listener instance being triggered
   * multiple times upon an update of the {@code ObservableValue}. In addition, {@link
   * #removeListener(ChangeListener)} will only remove one of these instances, which might lead to
   * confusing behavior. Hence, it's recommended to avoid this.
   *
   * @param listener the listener which should be notified on updates
   */
  void addListener(ChangeListener<? super T> listener);

  /**
   * Unsubscribes an {@link InvalidationListener} from receiving notifications for this {@code
   * ObservableValue}.
   *
   * <p>Also see {@link #addListener(InvalidationListener)} for further details.
   *
   * @param listener the listener which should not receive further notifications
   */
  void removeListener(InvalidationListener<? super T> listener);

  /**
   * Unsubscribes a {@link ChangeListener} from receiving notifications for this {@code
   * ObservableValue}.
   *
   * <p>Also see {@link #addListener(ChangeListener)} for further details.
   *
   * @param listener the listener which should not receive further notifications
   */
  void removeListener(ChangeListener<? super T> listener);

  /**
   * Returns the value which is currently wrapped by this {@code ObservableValue}. The returned
   * value is {@code null} if the value is unset.
   */
  @Nullable
  T getValue();
}
