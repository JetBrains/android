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
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrString;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrStringInjection;
import org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil;

import java.util.Collection;

import static com.intellij.openapi.util.text.StringUtil.*;
import static com.intellij.psi.util.PsiTreeUtil.getChildOfType;

/**
 * Represents a {@link GrLiteral} element.
 */
public final class GradleDslLiteral extends GradleDslExpression {
  @Nullable private Object myUnsavedValue;

  public GradleDslLiteral(@NotNull GradleDslElement parent, @NotNull String name) {
    super(parent, null, name, null);
  }

  public GradleDslLiteral(@NotNull GradleDslElement parent,
                          @NotNull GroovyPsiElement psiElement,
                          @NotNull String name,
                          @NotNull GrLiteral literal) {
    super(parent, psiElement, name, literal);
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

      GrStringInjection[] injections = ((GrString)myExpression).getInjections();
      for (GrStringInjection injection : injections) {
        String variableName = null;

        GrClosableBlock closableBlock = injection.getClosableBlock();
        if (closableBlock != null) {
          String blockText  = closableBlock.getText();
          variableName = blockText.substring(1, blockText.length() - 1);
        }
        else {
          GrExpression expression = injection.getExpression();
          if (expression != null) {
            variableName = expression.getText();
          }
        }

        if (!isEmpty(variableName)) {
          GradleDslExpression resolvedLiteral = resolveReference(variableName, GradleDslExpression.class);
          if (resolvedLiteral != null) {
            Object resolvedValue = resolvedLiteral.getValue();
            if (resolvedValue != null) {
              literalText = literalText.replace(injection.getText(), resolvedValue.toString());
            }
          }
        }
      }
      return literalText;
    }
    return null;
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

  @Override
  public String toString() {
    Object value = getValue();
    return value != null ? value.toString() : super.toString();
  }

  @Override
  @NotNull
  protected Collection<GradleDslElement> getChildren() {
    return ImmutableList.of();
  }

  @Override
  @Nullable
  public GroovyPsiElement create() {
    if (!(myParent instanceof GradleDslExpressionMap)) {
      return super.create();
    }
    // This is a value in the map element we need to create a named argument for it.
    GroovyPsiElement parentPsiElement = myParent.create();
    if (parentPsiElement == null) {
      return null;
    }

    setPsiElement(parentPsiElement);
    GrLiteral newLiteral = createLiteral();
    if (newLiteral == null) {
      return null;
    }

    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(newLiteral.getProject());
    GrNamedArgument namedArgument = factory.createNamedArgument(myName, newLiteral);
    PsiElement added;
    if (parentPsiElement instanceof GrArgumentList) {
      added = ((GrArgumentList)parentPsiElement).addNamedArgument(namedArgument);
    }
    else {
      added = parentPsiElement.addAfter(namedArgument, parentPsiElement.getLastChild());
    }
    if (added instanceof GrNamedArgument) {
      GrNamedArgument addedNameArgument = (GrNamedArgument)added;
      GrLiteral literal = getChildOfType(addedNameArgument, GrLiteral.class);
      if (literal != null) {
        myExpression = literal;
        setModified(false);
        return getPsiElement();
      }
    }
    return null;
  }

  @Override
  protected void delete() {
    if(myExpression == null) {
      return;
    }
    PsiElement parent = myExpression.getParent();
    myExpression.delete();
    deleteIfEmpty(parent);
  }

  @Override
  protected void apply() {
    GroovyPsiElement psiElement = getPsiElement();
    if (psiElement == null) {
      return;
    }

    GrLiteral newLiteral = createLiteral();
    if (newLiteral == null) {
      return;
    }
    if (myExpression != null) {
      PsiElement replace = myExpression.replace(newLiteral);
      if (replace instanceof GrLiteral) {
        myExpression = (GrLiteral)replace;
      }
    }
    else {
      PsiElement added = psiElement.addAfter(newLiteral, psiElement.getLastChild());
      if (added instanceof GrLiteral) {
        myExpression = (GrLiteral)added;
      }
    }
  }

  @Override
  protected void reset() {
    myUnsavedValue = null;
  }

  @Nullable
  private GrLiteral createLiteral() {
    if(myUnsavedValue == null) {
      return null;
    }

    CharSequence unsavedValueText = null;
    if (myUnsavedValue instanceof String) {
      unsavedValueText = GrStringUtil.getLiteralTextByValue((String)myUnsavedValue);
    } else if (myUnsavedValue instanceof Integer || myUnsavedValue instanceof Boolean) {
      unsavedValueText = myUnsavedValue.toString();
    }
    myUnsavedValue = null;

    if(unsavedValueText == null) {
      return null;
    }

    GroovyPsiElement psiElement = getPsiElement();
    if (psiElement == null) {
      return null;
    }

    Project project = psiElement.getProject();
    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(project);
    GrExpression newExpression = factory.createExpressionFromText(unsavedValueText);

    if (!(newExpression instanceof GrLiteral)) {
      return null;
    }
    return (GrLiteral)newExpression;
  }
}
