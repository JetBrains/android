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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * A modifiable property whose changes of value can be observed through listeners.
 *
 * @param <T> the type of the contained value
 */
public class Property<T> implements ObservableValue<T> {

  @Nullable private T value;
  private final List<InvalidationListener<? super T>> invalidationListeners = new ArrayList<>();
  private final List<ChangeListener<? super T>> changeListeners = new ArrayList<>();

  public Property() {
    this(null);
  }

  public Property(@Nullable T value) {
    this.value = value;
  }

  @Override
  public void addListener(InvalidationListener<? super T> listener) {
    invalidationListeners.add(listener);
  }

  @Override
  public void addListener(ChangeListener<? super T> listener) {
    changeListeners.add(listener);
  }

  @Override
  public void removeListener(InvalidationListener<? super T> listener) {
    invalidationListeners.remove(listener);
  }

  @Override
  public void removeListener(ChangeListener<? super T> listener) {
    changeListeners.remove(listener);
  }

  @Override
  @Nullable
  public T getValue() {
    return value;
  }

  /**
   * Updates the value of this {@code Property}. Use {@code null} to unset the value.
   *
   * <p>Listeners subscribed to this {@code Property} will be notified about the change.
   *
   * @param value the new value
   */
  public void setValue(@Nullable T value) {
    T oldValue = this.value;
    this.value = value;

    triggerInvalidationListeners();
    triggerChangeListeners(oldValue);
  }

  private void triggerInvalidationListeners() {
    for (InvalidationListener<? super T> invalidationListener : invalidationListeners) {
      invalidationListener.invalidated(this);
    }
  }

  private void triggerChangeListeners(@Nullable T oldValue) {
    // Avoid potentially expensive equals() computation if no ChangeListener is registered.
    if (changeListeners.isEmpty()) {
      return;
    }

    if (!Objects.equals(oldValue, this.value)) {
      for (ChangeListener<? super T> changeListener : changeListeners) {
        changeListener.changed(this, oldValue, this.value);
      }
    }
  }
}
