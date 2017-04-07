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

import com.android.tools.idea.ui.properties.AbstractProperty;
import org.jetbrains.annotations.NotNull;

/**
 * An {@link AbstractProperty} which is backed by a non-null object instance.
 */
public final class ObjectValueProperty<T> extends ObjectProperty<T> {

  @NotNull private T myValue;

  public ObjectValueProperty(@NotNull T value) {
    myValue = value;
  }

  @Override
  protected void setDirectly(@NotNull T value) {
    myValue = value;
  }

  @NotNull
  @Override
  public T get() {
    return myValue;
  }
}
