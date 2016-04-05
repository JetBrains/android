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
package com.android.tools.idea.ui.properties.adapters;

import com.android.tools.idea.ui.properties.AbstractProperty;
import com.google.common.base.Optional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Adapter property that wraps an optional type and provides non-null access to its contents. This
 * is a very useful adapter for Swing properties, many of which are optional because Swing
 * components could technically return null, but in practice never do for some forms.
 */
public final class OptionalToValuePropertyAdapter<T> extends AdapterProperty<Optional<T>, T> {
  public OptionalToValuePropertyAdapter(@NotNull AbstractProperty<Optional<T>> wrappedProperty, @NotNull T initialValue) {
    super(wrappedProperty, initialValue);
  }

  /**
   * Constructor which extracts an initial value from the optional property's current value. If the
   * optional property is not set, this will throw an exception.
   */
  public OptionalToValuePropertyAdapter(@NotNull AbstractProperty<Optional<T>> wrappedProperty) {
    this(wrappedProperty, wrappedProperty.get().get());
  }

  @Nullable
  @Override
  protected T convertFromSourceType(@NotNull Optional<T> value) {
    return value.orNull();
  }

  @NotNull
  @Override
  protected Optional<T> convertFromDestType(@NotNull T value) {
    return Optional.of(value);
  }
}

