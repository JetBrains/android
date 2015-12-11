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
package com.android.tools.idea.ui.properties;

import com.android.tools.idea.ui.properties.core.ObservableBool;
import com.android.tools.idea.ui.properties.expressions.bool.IsEqualToExpression;
import org.jetbrains.annotations.NotNull;

/**
 * A class that represents a value which, when modified, notifies all listeners.
 */
public interface ObservableValue<T> {
  @NotNull
  T get();

  /**
   * Add a listener which will be notified any time this observable value is invalidated (either
   * changed or depending on another value that has changed).
   * <p/>
   * This method adds a strong reference to the listener, keeping it alive. This is useful for
   * one-off lambda methods that should be associated with this observable value for its whole
   * lifetime. However, if you have a listener bound to a variable name, you should always prefer
   * to use {@link #addWeakListener(InvalidationListener)}, instead.
   */
  void addListener(@NotNull InvalidationListener listener);

  void removeListener(@NotNull InvalidationListener listener);

  /**
   * Add a listener which should remove itself automatically after going out of scope.
   * <p/>
   * This is useful if the lifetime of the listener is likely shorter than the observable itself,
   * and using this may help prevent memory links. You may still call
   * {@link #removeListener(InvalidationListener)} to remove weakly added listeners.
   */
  void addWeakListener(@NotNull InvalidationListener listener);

  @NotNull
  ObservableBool isEqualTo(@NotNull T value);
}
