/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.observable.core;

import com.android.tools.idea.observable.AbstractProperty;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An {@link AbstractProperty} backed by an optional value.
 */
public final class OptionalValueProperty<T> extends OptionalProperty<T> {

  @NotNull private Optional<T> myOptional;

  public OptionalValueProperty() {
    myOptional = Optional.empty();
  }

  public OptionalValueProperty(@NotNull T value) {
    myOptional = Optional.of(value);
  }

  public static <T> OptionalValueProperty<T> of(@NotNull T value) {
    return new OptionalValueProperty<>(value);
  }

  public static <T> OptionalValueProperty<T> fromNullable(@Nullable T value) {
    if (value != null) {
      return of(value);
    }
    else {
      return absent();
    }
  }

  public static <T> OptionalValueProperty<T> absent() {
    return new OptionalValueProperty<>();
  }

  @NotNull
  @Override
  public Optional<T> get() {
    return myOptional;
  }

  @Override
  protected void setDirectly(@NotNull Optional<T> value) {
    myOptional = value;
  }
}
