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
import com.android.tools.idea.ui.properties.InvalidationListener;
import com.android.tools.idea.ui.properties.ObservableValue;
import com.android.tools.idea.ui.properties.core.BoolProperty;
import com.android.tools.idea.ui.properties.core.BoolValueProperty;
import com.android.tools.idea.ui.properties.core.ObservableBool;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Sometimes you have a property of one type that you want to cast to another type (a common case
 * being Strings to numeric types). Adapter properties wrap other properties to allow this explicit
 * conversion when needed, which is especially useful to allow properties of different types to be
 * two-way bound together.
 *
 * Adapter properties will try to stay in sync as much as they can, but if for any reason a
 * conversion can't happen, adapter properties will retain their last good value. This is so that
 * temporary errors made by a user typing in input, for example, won't suddenly drop a value to 0
 * (or some other obvious default value). However, the {@link #inSync()} property is exposed so you
 * can register a validator against such bad input cases.
 *
 * @param <S> The source type we're wrapping
 * @param <D> The destination type we're converting to
 */
public abstract class AdapterProperty<S, D> extends AbstractProperty<D> implements InvalidationListener {

  @NotNull private final AbstractProperty<S> myWrappedProperty;
  private final BoolProperty myInSync = new BoolValueProperty();
  @NotNull private D myLastValue;

  /**
   * We can't simply call trySync in our constructor because some child classes initialize fields
   * in their own constructor which are then used in the syncing. Instead, we sync lazily at the
   * first chance we get.
   */
  private boolean myNeedsInitialSync = true;

  public AdapterProperty(@NotNull AbstractProperty<S> wrappedProperty, @NotNull D initialValue) {
    myLastValue = initialValue;
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
    doInitialSync();
    return myLastValue;
  }

  @NotNull
  public ObservableBool inSync() {
    doInitialSync();
    return myInSync;
  }

  @Override
  public final void onInvalidated(@NotNull ObservableValue<?> sender) {
    trySync();
    notifyInvalidated(); // When our wrapped observable gets invalidated, we should too
  }

  /**
   * Try to convert from the source type to the dest type, returning {@code null} if the conversion
   * doesn't work. In that case, this property adapter will use its last known good value, but
   * {@link #inSync()} will be set to false.
   */
  @Nullable
  protected abstract D convertFromSourceType(@NotNull S value);

  @NotNull
  protected abstract S convertFromDestType(@NotNull D value);

  private void doInitialSync() {
    if (myNeedsInitialSync) {
      trySync(); // Sets myNeedsInitialSync to false
    }
  }

  private void trySync() {
    D result = convertFromSourceType(myWrappedProperty.get());
    myInSync.set(result != null);
    if (result != null) {
      myLastValue = result;
    }
    myNeedsInitialSync = false;
  }
}

