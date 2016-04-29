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

/**
 * Base class for all properties that return a generic (e.g. not int, String, bool, etc.), non-null
 * object instance.
 * <p/>
 * If you need to support null values, use {@link OptionalProperty} instead.
 */
public abstract class ObjectProperty<T> extends ObservableProperty<T> implements ObservableObject<T> {
  /**
   * At the moment, although this class doesn't have any methods, someday it may. For now, it's
   * purpose is to express intention and provide a consistent API. That is, the pattern
   *
   * {@code ObjectProperty<File> myFile = new ObjectValueProperty<File>(targetFile)}
   *
   * while currently equivalent to
   *
   * {@code ObservableProperty<File> myFile = new ObjectValueProperty<File>(targetFile)}
   *
   * doesn't match the convention that other property types in this package follow, e.g.
   *
   * {@code
   *   IntProperty myCount = new IntValueProperty(0);
   *   StringProperty myName = new StringValueProperty("John Doe");
   * }
   */
}
