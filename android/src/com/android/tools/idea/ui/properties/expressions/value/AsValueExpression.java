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
package com.android.tools.idea.ui.properties.expressions.value;

import com.android.tools.idea.ui.properties.core.OptionalProperty;
import com.android.tools.idea.ui.properties.expressions.Expression;
import org.jetbrains.annotations.NotNull;

/**
 * Expression for converting a target optional value (that you know will always be present) into a
 * concrete value. This is a useful expression for wrapping Swing properties, which often represent
 * UI elements that technically return {@code null} but in practice never do.
 *
 * If the optional property you wrap is ever absent, this expression will throw an exception, so
 * be sure this is what you want to do. If you need more robust optional -> concrete handling,
 * consider using {@link TransformOptionalExpression} instead.
 */
public final class AsValueExpression<T> extends Expression<T> {
  private final OptionalProperty<T> myValue;

  public AsValueExpression(OptionalProperty<T> value) {
    super(value);
    myValue = value;
  }

  @NotNull
  @Override
  public T get() {
    return myValue.getValue();
  }
}
