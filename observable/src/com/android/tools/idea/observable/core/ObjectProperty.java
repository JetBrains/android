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
import com.android.tools.idea.observable.InvalidationListener;
import com.android.tools.idea.observable.ObservableValue;
import com.android.tools.idea.observable.expressions.value.TransformOptionalExpression;
import org.jetbrains.annotations.NotNull;

/**
 * Base class for all properties that return a generic (e.g. not int, String, bool, etc.), non-null
 * object instance.
 *
 * If you need to support null values, use {@link OptionalProperty} instead.
 *
 * Note: although this class is essentially the same as AbstractProperty, its purpose is to express
 * intention and provide a consistent API. That is, the pattern
 *
 * {@code ObjectProperty<File> myFile = new ObjectValueProperty<File>(targetFile)}
 *
 * is technically equivalent to
 *
 * {@code AbstractProperty<File> myFile = new ObjectValueProperty<File>(targetFile)}
 *
 * but that doesn't match the convention that other property types in this package follow, e.g.
 *
 * {@code
 *   IntProperty myCount = new IntValueProperty(0);
 *   StringProperty myName = new StringValueProperty("John Doe");
 * }
 */
public abstract class ObjectProperty<T> extends AbstractProperty<T> implements ObservableObject<T> {
  /**
   * Convenience method for converting a target optional value (that you know will always be
   * present) into a concrete value. For example, this is a useful way for wrapping Swing properties,
   * which often represent UI elements that technically return {@code null} but in practice never do.
   *
   * If the optional property you wrap is ever absent, this expression will throw an exception, so
   * be sure this is what you want to do. If you need more robust optional -> concrete handling,
   * consider using {@link TransformOptionalExpression} instead.
   */
  public static <T> ObjectProperty<T> wrap(@NotNull OptionalProperty<T> optionalProperty) {
    return new OptionalWrapper<>(optionalProperty);
  }

  private static final class OptionalWrapper<U> extends ObjectProperty<U> implements InvalidationListener {
    private final OptionalProperty<U> myOptionalProperty;

    public OptionalWrapper(@NotNull OptionalProperty<U> optionalProperty) {
      myOptionalProperty = optionalProperty;
      myOptionalProperty.addWeakListener(this);
    }

    @Override
    protected void setDirectly(@NotNull U value) {
      myOptionalProperty.setValue(value);
    }

    @NotNull
    @Override
    public U get() {
      return myOptionalProperty.getValue();
    }

    @Override
    public void onInvalidated(@NotNull ObservableValue<?> sender) {
      notifyInvalidated();
    }
  }
}
