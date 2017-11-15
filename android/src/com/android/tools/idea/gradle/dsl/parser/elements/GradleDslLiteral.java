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

import com.android.tools.idea.gradle.dsl.parser.GradleResolvedVariable;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrString;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrStringInjection;

import java.util.Collection;
import java.util.List;

import static com.intellij.openapi.util.text.StringUtil.*;

/**
 * Represents a {@link GrLiteral} element.
 */
public final class GradleDslLiteral extends GradleDslExpression {
  @Nullable private Object myUnsavedValue;
  @Nullable private PsiElement myUnsavedConfigBlock;

  public GradleDslLiteral(@NotNull GradleDslElement parent, @NotNull String name) {
    super(parent, null, name, null);
  }

  public GradleDslLiteral(@NotNull GradleDslElement parent,
                          @NotNull PsiElement psiElement,
                          @NotNull String name,
                          @NotNull PsiElement literal) {
    super(parent, psiElement, name, literal);
  }

  @Nullable
  public PsiElement getUnsavedConfigBlock() {
    return myUnsavedConfigBlock;
  }

  public void setUnsavedConfigBlock(@Nullable PsiElement configBlock) {
    myUnsavedConfigBlock = configBlock;
  }

  @Nullable
  public GrLiteral getLiteral() {
    return (GrLiteral)myExpression;
  }

  @Override
  @Nullable
  public Object getValue() {
    if (myUnsavedValue != null) {
      return myUnsavedValue;
    }

    if (myExpression == null) {
      return null;
    }

    Object value = ((GrLiteral)myExpression).getValue();
    if (value != null) {
      return value;
    }

    if (myExpression instanceof GrString) { // String literal with variables. ex: compileSdkVersion = "$ANDROID-${VERSION}"
      String literalText = myExpression.getText();
      if (isQuotedString(literalText)) {
        literalText = unquoteString(literalText);
      }

      List<GradleResolvedVariable> resolvedVariables = Lists.newArrayList();
      GrStringInjection[] injections = ((GrString)myExpression).getInjections();
      for (GrStringInjection injection : injections) {
        String variableName = null;

        GrClosableBlock closableBlock = injection.getClosableBlock();
        if (closableBlock != null) {
          String blockText = closableBlock.getText();
          variableName = blockText.substring(1, blockText.length() - 1);
        }
        else {
          GrExpression expression = injection.getExpression();
          if (expression != null) {
            variableName = expression.getText();
          }
        }

        if (!isEmpty(variableName)) {
          GradleDslExpression resolvedExpression = resolveReference(variableName, GradleDslExpression.class);
          if (resolvedExpression != null) {
            Object resolvedValue = resolvedExpression.getValue();
            if (resolvedValue != null) {
              resolvedVariables.add(new GradleResolvedVariable(variableName, resolvedValue, resolvedExpression));
              literalText = literalText.replace(injection.getText(), resolvedValue.toString());
            }
          }
        }
      }
      setResolvedVariables(resolvedVariables);
      return literalText;
    }
    return null;
  }

  @Nullable
  public Object getUnsavedValue() {
    return myUnsavedValue;
  }

  /**
   * Returns the value of type {@code clazz} when the the {@link GrLiteral} element contains the value of that type,
   * or {@code null} otherwise.
   */
  @Override
  @Nullable
  public <T> T getValue(@NotNull Class<T> clazz) {
    Object value = getValue();
    if (value != null && clazz.isInstance(value)) {
      return clazz.cast(value);
    }
    return null;
  }

  @Override
  public void setValue(@NotNull Object value) {
    myUnsavedValue = value;
    setModified(true);
  }

  public void setConfigBlock(@NotNull GrClosableBlock block) {
    // For now we only support setting the config block on literals for newly created dependencies.
    Preconditions.checkState(getPsiElement() == null, "Can't add configuration block to an existing DSL literal.");

    // TODO: Use com.android.tools.idea.gradle.dsl.parser.dependencies.DependencyConfigurationDslElement to add a dependency configuration.

    myUnsavedConfigBlock = block;
    setModified(true);
  }

  @Override
  public String toString() {
    Object value = getValue();
    return value != null ? value.toString() : super.toString();
  }

  @Override
  @NotNull
  public Collection<GradleDslElement> getChildren() {
    return ImmutableList.of();
  }

  @Override
  @Nullable
  public PsiElement create() {
    return getDslFile().getWriter().createDslLiteral(this);
  }

  @Override
  protected void delete() {
    getDslFile().getWriter().deleteDslLiteral(this);
  }

  @Override
  protected void apply() {
    getDslFile().getWriter().applyDslLiteral(this);
  }

  @Override
  public void reset() {
    myUnsavedValue = null;
  }
}
