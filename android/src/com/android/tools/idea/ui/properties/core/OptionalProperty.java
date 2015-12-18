/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.ui.properties.core;

import com.android.tools.idea.ui.properties.ObservableProperty;
import com.google.common.base.Optional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Properties don't allow {@code null} values by design, but if the concept is still needed, this
 * convenience class provides an interface that mirrors that of guava's {@link Optional} interface.
 */
public final class OptionalProperty<T> extends ObservableProperty<Optional<T>> {

  @NotNull private Optional<T> myOptional;

  public static <T> OptionalProperty<T> of(@NotNull T value) {
    return new OptionalProperty<T>(value);
  }

  public static <T> OptionalProperty<T> absent() {
    return new OptionalProperty<T>();
  }

  public OptionalProperty() {
    myOptional = Optional.absent();
  }

  public OptionalProperty(@NotNull T value) {
    myOptional = Optional.of(value);
  }

  @NotNull
  @Override
  public Optional<T> get() {
    return myOptional;
  }

  @NotNull
  public T getValue() {
    return myOptional.get();
  }

  public void setValue(@NotNull T value) {
    if (!myOptional.isPresent() || !myOptional.get().equals(value)) {
      set(Optional.of(value));
    }
  }

  public void clear() {
    if (myOptional.isPresent()) {
      set(Optional.<T>absent());
    }
  }

  @NotNull
  public T getValueOr(@NotNull T defaultValue) {
    return myOptional.or(defaultValue);
  }

  @Nullable
  public T getValueOrNull() {
    return myOptional.orNull();
  }

  public boolean isPresent() {
    return myOptional.isPresent();
  }

  @Override
  protected void setDirectly(@NotNull Optional<T> value) {
    myOptional = value;
  }
}
