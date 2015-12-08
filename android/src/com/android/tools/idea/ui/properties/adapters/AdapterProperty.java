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

import com.android.tools.idea.ui.properties.InvalidationListener;
import com.android.tools.idea.ui.properties.ObservableProperty;
import com.android.tools.idea.ui.properties.ObservableValue;
import org.jetbrains.annotations.NotNull;

/**
 * Sometimes you have a property of one type that you want to cast to another type (a common case
 * being Strings to numeric types). Adapter properties wrap other properties to allow this explicit
 * conversion when needed, which is especially useful to allow properties of different types to be
 * two-way bound together.
 *
 * @param <S> The source type we're wrapping
 * @param <D> The destination type we're converting to
 */
public abstract class AdapterProperty<S, D> extends ObservableProperty<D> implements InvalidationListener {
  @NotNull private final ObservableProperty<S> myWrappedProperty;

  public AdapterProperty(@NotNull ObservableProperty<S> wrappedProperty) {
    myWrappedProperty = wrappedProperty;
    myWrappedProperty.addWeakListener(this);
  }

  @Override
  protected final void setDirectly(@NotNull D value) {
    myWrappedProperty.set(convertFromDestType(value));
  }

  @NotNull
  @Override
  public final D get() {
    return convertFromSourceType(myWrappedProperty.get());
  }

  @Override
  public final void onInvalidated(@NotNull ObservableValue<?> sender) {
    notifyInvalidated(); // When our wrapped observable gets invalidated, we should too
  }

  @NotNull
  protected abstract D convertFromSourceType(@NotNull S value);

  @NotNull
  protected abstract S convertFromDestType(@NotNull D value);
}

