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
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;

import java.util.Collection;
import java.util.List;

/**
 * Represents a {@link GrMethodCallExpression} element.
 */
public final class GradleDslMethodCall extends GradleDslElement {
  private final @NotNull List<GradleDslLiteral> myLiteralArguments;
  private final @NotNull List<GradleDslLiteralMap> myMapArguments;
  private final @NotNull List<GradleDslElement> myAllArguments;

  public GradleDslMethodCall(@NotNull GradleDslElement parent,
                             @NotNull String name,
                             @NotNull GrMethodCallExpression methodCall) {
    super(parent, methodCall, name);
    GrArgumentList argumentList = methodCall.getArgumentList();
    if (argumentList != null) {
      List<GradleDslLiteral> literalArguments = Lists.newArrayList();
      List<GradleDslLiteralMap> mapArguments = Lists.newArrayList();
      GrExpression[] expressionArguments = argumentList.getExpressionArguments();
      for (GrExpression expression : expressionArguments) {
        if (expression instanceof GrLiteral) {
          literalArguments.add(new GradleDslLiteral(this, argumentList, name, (GrLiteral)expression));
        }
        else if (expression instanceof GrListOrMap) {
          GrListOrMap listOrMap = (GrListOrMap)expression;
          if (listOrMap.isMap()) {
            mapArguments.add(new GradleDslLiteralMap(this, name, listOrMap));
          }
          else {
            literalArguments.addAll(new GradleDslLiteralList(this, name, listOrMap).getElements());
          }
        }
      }

      GrNamedArgument[] namedArguments = argumentList.getNamedArguments();
      if (namedArguments.length > 0) {
        mapArguments.add(new GradleDslLiteralMap(this, argumentList, name, namedArguments));
      }

      myLiteralArguments = ImmutableList.copyOf(literalArguments);
      myMapArguments = ImmutableList.copyOf(mapArguments);
      myAllArguments = ImmutableList.<GradleDslElement>builder().addAll(myLiteralArguments).addAll(myMapArguments).build();
    }
    else {
      myLiteralArguments = ImmutableList.of();
      myMapArguments = ImmutableList.of();
      myAllArguments = ImmutableList.of();
    }
  }

  @NotNull
  public List<GradleDslLiteral> getLiteralArguments() {
    return myLiteralArguments;
  }

  @NotNull
  public List<GradleDslLiteralMap> getMapArguments() {
    return myMapArguments;
  }

  @NotNull
  public List<GradleDslElement> getAllArguments() {
    return myAllArguments;
  }

  @Override
  @NotNull
  protected Collection<GradleDslElement> getChildren() {
    return ImmutableList.of();
  }

  @Override
  protected void apply() {
    for (GradleDslElement element : getAllArguments()) {
      if (element.isModified()) {
        element.applyChanges();
      }
    }
  }

  @Override
  protected void reset() {
    for (GradleDslElement element : getAllArguments()) {
      if (element.isModified()) {
        element.resetState();
      }
    }
  }

  @Override
  @Nullable
  public GroovyPsiElement create() {
    // TODO: Add support to create a new method element, if there is a use case.
    return getPsiElement();
  }

  @Override
  protected void delete() {
    // TODO: Add support to delete a method element, if there is a use case.
  }
}
