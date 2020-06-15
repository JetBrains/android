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

import org.jetbrains.annotations.NotNull;

/**
 * A specialized {@link ObjectProperty} that wraps an enumeration value.
 */
public final class EnumValueProperty<E extends Enum<E>> extends ObjectProperty<E> {
  @NotNull private E myValue;

  public EnumValueProperty(@NotNull E value) {
    myValue = value;
  }

  /**
   * Create a property which is initialized to the very first enum value.
   *
   * The enumeration class must be passed in explicitly due to the nature of Java generic-erasure.
   */
  public EnumValueProperty(Class<E> enumClass) { myValue = enumClass.getEnumConstants()[0]; }

  @Override
  protected void setDirectly(@NotNull E value) {
    myValue = value;
  }

  @NotNull
  @Override
  public E get() {
    return myValue;
  }
}
