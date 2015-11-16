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
import com.android.tools.idea.ui.properties.expressions.bool.BooleanExpression;
import com.google.common.base.Optional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base class for all properties that need to support being set to or returning a {@code null}
 * value.
 * <p/>
 * Designed with an interface that emulates Guava's {@link Optional}.
 */
public abstract class OptionalProperty<T> extends ObservableProperty<Optional<T>> implements ObservableOptional<T> {

  public final void setValue(@NotNull T value) {
    Optional<T> opt = get();
    if (!opt.isPresent() || !opt.get().equals(value)) {
      set(Optional.of(value));
    }
  }

  public final void clear() {
    if (get().isPresent()) {
      set(Optional.<T>absent());
    }
  }

  public final void setNullableValue(@Nullable T value) {
    if (value != null) {
      setValue(value);
    }
    else {
      clear();
    }
  }

  @NotNull
  @Override
  public final ObservableBool isPresent() {
    return new BooleanExpression(this) {
      @NotNull
      @Override
      public Boolean get() {
        return OptionalProperty.this.get().isPresent();
      }
    };
  }

  @Override
  @NotNull
  public final T getValue() {
    Optional<T> opt = get();
    return opt.get();
  }

  @Override
  @NotNull
  public final T getValueOr(@NotNull T defaultValue) {
    Optional<T> opt = get();
    return opt.or(defaultValue);
  }

  @Override
  @Nullable
  public final T getValueOrNull() {
    Optional<T> opt = get();
    return opt.orNull();
  }

}
