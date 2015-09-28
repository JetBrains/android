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
package com.android.tools.idea.gradle.dsl.parser;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;

/**
 * Represents a {@link GrLiteral} element.
 */
public class LiteralElement extends GradleDslElement {
  @NotNull private final String myName;
  @NotNull private final GrLiteral myLiteral;

  public LiteralElement(@Nullable GradleDslElement parent, @NotNull String name, @NotNull GrLiteral literal) {
    super(parent);
    myName = name;
    myLiteral = literal;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @NotNull
  public GrLiteral getLiteral() {
    return myLiteral;
  }

  @Nullable
  public Object getValue() {
    return myLiteral.getValue();
  }

  /**
   * Returns the value of type {@code clazz} when the the {@link GrLiteral} element contains the value of that type,
   * or {@code null} otherwise.
   */
  @Nullable
  public <T> T getValue(@NotNull Class<T> clazz) {
    Object value = getValue();
    if (value != null && clazz.isInstance(value)) {
      return clazz.cast(value);
    }
    return null;
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
  }
}
