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

import com.intellij.psi.PsiType;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;

import static com.intellij.openapi.util.text.StringUtil.unquoteString;

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
   * @return the value of this property, using the appropriate data type as defined in the build.gradle file. If the value type is not
   * supported, this method will return the value as {@code String}.
   */
  @NotNull
  public Object getValue() {
    String valueAsText = myValueExpression.getText();

    PsiType type = myValueExpression.getType();
    if (type instanceof PsiClassReferenceType) {
      String qualifiedName = ((PsiClassReferenceType)type).getReference().getQualifiedName();
      if (isMatchingType(qualifiedName, String.class)) {
        return unquoteString(valueAsText);
      }
      if (isMatchingType(qualifiedName, Integer.class)) {
        return Integer.parseInt(valueAsText);
      }
      // TODO support more data types.
    }
    return valueAsText;
  }

  private static boolean isMatchingType(@NotNull String parsedType, @NotNull Class<?> realType) {
    return realType.getCanonicalName().equals(parsedType);
  }
}
