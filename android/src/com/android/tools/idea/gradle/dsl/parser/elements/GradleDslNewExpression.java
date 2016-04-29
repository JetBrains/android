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
package com.android.tools.idea.gradle.dsl.parser.elements;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrApplicationStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Represents a {@link GrNewExpression} element.
 */
public final class GradleDslNewExpression extends GradleDslExpression {
  private final @NotNull List<GradleDslExpression> myArguments = Lists.newArrayList();

  public GradleDslNewExpression(@NotNull GradleDslElement parent, @NotNull GrNewExpression newExpression, @NotNull String name) {
    super(parent, newExpression, name, newExpression);
  }

  public void addParsedExpression(@NotNull GradleDslExpression expression) {
    expression.myParent = this;
    myArguments.add(expression);
  }

  @NotNull
  public List<GradleDslExpression> getArguments() {
    List<GradleDslExpression> result = Lists.newArrayList();

    for (GradleDslExpression argument : myArguments) {
      if (argument instanceof GradleDslReference) {
        // See if the reference is pointing to a list.
        GradleDslExpressionList listValue = argument.getValue(GradleDslExpressionList.class);
        if (listValue != null) {
          result.addAll(listValue.getExpressions());
          continue;
        }
      }
      result.add(argument);
    }

    return result;
  }

  @Override
  @NotNull
  protected Collection<GradleDslElement> getChildren() {
    return Collections.emptyList();
  }

  @Nullable
  @Override
  public Object getValue() {
    GroovyPsiElement psiElement = getPsiElement();
    return psiElement != null ? psiElement.getText() : null;
  }

  @Nullable
  @Override
  public <T> T getValue(@NotNull Class<T> clazz) {
    Object value = getValue();
    if (clazz.isInstance(value)) {
      return clazz.cast(value);
    }
    return null;
  }

  @Override
  public void setValue(@NotNull Object value) {
    // TODO: Add support to set the full expression definition as a String.
  }

  @Override
  protected void apply() {
    // TODO: Add support to apply changes when there is a use case for it.
  }

  @Override
  protected void reset() {
    // TODO: Add support to reset changes when there is a use case for it.
  }

  @Override
  @Nullable
  public GroovyPsiElement create() {
    // TODO: Add support to create new element when there is a use case for it.
    return getPsiElement();
  }
}
