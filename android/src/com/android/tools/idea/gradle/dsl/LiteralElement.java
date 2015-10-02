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
package com.android.tools.idea.gradle.dsl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;

/**
 * Represents a {@link GrLiteral} element.
 */
final class LiteralElement extends GradleDslElement {
  @NotNull private final String myName;

  @Nullable private GrLiteral myLiteral;
  @Nullable private Object myUnsavedValue;

  LiteralElement(@Nullable GradleDslElement parent, @NotNull String name) {
    this(parent, name, null);
  }

  LiteralElement(@Nullable GradleDslElement parent, @NotNull String name, @Nullable GrLiteral literal) {
    super(parent);
    myName = name;
    myLiteral = literal;
  }

  @NotNull
  String getName() {
    return myName;
  }

  @Nullable
  GrLiteral getLiteral() {
    return myLiteral;
  }

  @Nullable
  Object getValue() {
    if (myUnsavedValue != null) {
      return myUnsavedValue;
    }

    if (myLiteral != null) {
      return myLiteral.getValue();
    }
    return null;
  }

  /**
   * Returns the value of type {@code clazz} when the the {@link GrLiteral} element contains the value of that type,
   * or {@code null} otherwise.
   */
  @Nullable
  <T> T getValue(@NotNull Class<T> clazz) {
    Object value = getValue();
    if (value != null && clazz.isInstance(value)) {
      return clazz.cast(value);
    }
    return null;
  }

  void setValue(@NotNull Object value) {
    myUnsavedValue = value;
    setModified(true);
  }

  @Override
  public String toString() {
    Object value = getValue();
    return value != null ? value.toString() : super.toString();
  }

  @Override
  protected void apply() {
  }

  @Override
  protected void reset() {
    myUnsavedValue = null;
  }
}
