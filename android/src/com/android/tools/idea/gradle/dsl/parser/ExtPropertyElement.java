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
 * An extra user-defined property.
 * <p>
 * For more details please read
 * <a href="https://docs.gradle.org/current/userguide/writing_build_scripts.html#sec:extra_properties">Extra Properties</a>.
 * </p>
 */
public class ExtPropertyElement implements GradleDslElement {
  @NotNull private final String myName;
  @NotNull private final GrLiteral myValueExpression;

  public ExtPropertyElement(@NotNull String name, @NotNull GrLiteral valueExpression) {
    myName = name;
    myValueExpression = valueExpression;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  /**
   * @return the value of this property, using the appropriate data type as defined in the build.gradle file.
   */
  @Nullable
  public Object getValue() {
    return myValueExpression.getValue();
  }
}
