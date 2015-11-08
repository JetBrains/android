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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;

import java.util.Collection;
import java.util.List;

/**
 * Represents a {@link GrMethodCallExpression} element.
 */
public final class GradleDslMethodCall extends GradleDslExpression {
  private final @NotNull List<GradleDslElement> myArguments = Lists.newArrayList();
  private final @NotNull List<GradleDslElement> myToBeRemovedArguments = Lists.newArrayList();

  public GradleDslMethodCall(@NotNull GradleDslElement parent,
                             @NotNull GrMethodCallExpression methodCall,
                             @NotNull String name) {
    super(parent, methodCall, name);
  }

  public void addParsedExpression(@NotNull GradleDslExpression expression) {
    expression.myParent = this;
    myArguments.add(expression);
  }

  public void addParsedExpressionMap(@NotNull GradleDslExpressionMap expressionMap) {
    expressionMap.myParent = this;
    myArguments.add(expressionMap);
  }

  @NotNull
  public List<GradleDslElement> getArguments() {
    if (myToBeRemovedArguments.isEmpty()) {
      return ImmutableList.copyOf(myArguments);
    }

    List<GradleDslElement> result = Lists.newArrayList();
    result.addAll(myArguments);
    for (GradleDslElement argument : myToBeRemovedArguments) {
      result.remove(argument);
    }
    return result;
  }

  @Override
  @NotNull
  protected Collection<GradleDslElement> getChildren() {
    return getArguments();
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
    // TODO: Add support to set a method value.
  }

  public void remove(GradleDslElement argument) {
    if (myArguments.contains(argument)) {
      myToBeRemovedArguments.add(argument);
      setModified(true);
    }
  }

  @Override
  protected void apply() {
    for (GradleDslElement argument : myToBeRemovedArguments) {
      if (myArguments.remove(argument)) {
        argument.delete();
      }
    }

    for (GradleDslElement argument : myArguments) {
      if (argument.isModified()) {
        argument.applyChanges();
      }
    }
  }

  @Override
  protected void reset() {
    myToBeRemovedArguments.clear();
    for (GradleDslElement argument : myArguments) {
      if (argument.isModified()) {
        argument.resetState();
      }
    }
  }

  @Override
  @Nullable
  public GroovyPsiElement create() {
    // TODO: Add support to create a new method element, if there is a use case.
    return getPsiElement();
  }
}
