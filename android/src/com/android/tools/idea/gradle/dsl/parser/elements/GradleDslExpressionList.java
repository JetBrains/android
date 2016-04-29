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
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrApplicationStatement;

import java.util.Collection;
import java.util.List;

/**
 * Represents an element which consists a list of {@link GradleDslExpression}s.
 */
public final class GradleDslExpressionList extends GradleDslElement {
  @NotNull private final List<GradleDslExpression> myExpressions = Lists.newArrayList();
  @NotNull private final List<GradleDslExpression> myToBeAddedExpressions = Lists.newArrayList();
  @NotNull private final List<GradleDslExpression> myToBeRemovedExpressions = Lists.newArrayList();

  public GradleDslExpressionList(@Nullable GradleDslElement parent, @NotNull String name) {
    super(parent, null, name);
  }

  public GradleDslExpressionList(@NotNull GradleDslElement parent, @NotNull GroovyPsiElement psiElement, @NotNull String name) {
    super(parent, psiElement, name);
  }

  public void addParsedExpression(@NotNull GradleDslExpression expression) {
    expression.myParent = this;
    myExpressions.add(expression);
  }

  public void addNewExpression(@NotNull GradleDslExpression expression) {
    expression.myParent = this;
    myToBeAddedExpressions.add(expression);
    setModified(true);
  }

  void addNewLiteral(@NotNull Object value) {
    GradleDslLiteral literal = new GradleDslLiteral(this, myName);
    literal.setValue(value);
    myToBeAddedExpressions.add(literal);
  }

  void removeExpression(@NotNull Object value) {
    for (GradleDslExpression expression : getExpressions()) {
      if (value.equals(expression.getValue())) {
        myToBeRemovedExpressions.add(expression);
        setModified(true);
        return;
      }
    }
  }

  void replaceExpression(@NotNull Object oldValue, @NotNull Object newValue) {
    for (GradleDslExpression expression : getExpressions()) {
      if (oldValue.equals(expression.getValue())) {
        expression.setValue(newValue);
        return;
      }
    }
  }

  @NotNull
  public List<GradleDslExpression> getExpressions() {
    if (myToBeAddedExpressions.isEmpty() && myToBeRemovedExpressions.isEmpty()) {
      return ImmutableList.copyOf(myExpressions);
    }

    List<GradleDslExpression> result = Lists.newArrayList();
    result.addAll(myExpressions);
    result.addAll(myToBeAddedExpressions);
    for (GradleDslExpression expression : myToBeRemovedExpressions) {
      result.remove(expression);
    }
    return result;
  }

  /**
   * Returns the list of values of type {@code clazz}.
   *
   * <p>Returns an empty list when there are no elements of type {@code clazz}.
   */
  @NotNull
  public <E> List<E> getValues(Class<E> clazz) {
    List<E> result = Lists.newArrayList();
    for (GradleDslExpression expression : getExpressions()) {
      if (expression instanceof GradleDslReference) {
        // See if the reference itself is pointing to a list.
        GradleDslExpressionList referenceList = expression.getValue(GradleDslExpressionList.class);
        if (referenceList != null) {
          result.addAll(referenceList.getValues(clazz));
          continue;
        }
      }
      E value = expression.getValue(clazz);
      if (value != null) {
        result.add(value);
      }
    }
    return result;
  }

  @Override
  @Nullable
  public GroovyPsiElement create() {
    GroovyPsiElement psiElement = getPsiElement();
    if (psiElement != null) {
      return psiElement;
    }

    psiElement = super.create();
    if (psiElement == null) {
      return null;
    }

    if (psiElement instanceof GrListOrMap) {
      return psiElement;
    }

    if (psiElement instanceof GrArgumentList) {
      if (((GrArgumentList)psiElement).getAllArguments().length == 1) {
        // Sometimes it's not possible to append to the arguments list with one item. eg. proguardFile "xyz".
        // Set the psiElement to null and create a new psiElement of an empty application statement.
        setPsiElement(null);
        psiElement = super.create();
      }
      else {
        return psiElement;
      }
    }

    if (psiElement instanceof GrApplicationStatement) {
      GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(psiElement.getProject());
      GrArgumentList argumentList = factory.createArgumentListFromText("xyz");
      argumentList.getFirstChild().delete(); // Workaround to get an empty argument list.
      PsiElement added = psiElement.addAfter(argumentList, psiElement.getLastChild());
      if (added instanceof GrArgumentList) {
        GrArgumentList addedArgumentList = (GrArgumentList)added;
        setPsiElement(addedArgumentList);
        return addedArgumentList;
      }
    }

    return null;
  }

  @Override
  protected void apply() {
    GroovyPsiElement psiElement = create();
    if (psiElement != null) {
      for (GradleDslExpression expression : myToBeAddedExpressions) {
        expression.setPsiElement(psiElement);
        expression.applyChanges();
        myExpressions.add(expression);
      }
    }
    myToBeAddedExpressions.clear();

    for (GradleDslExpression expression : myToBeRemovedExpressions) {
      if (myExpressions.remove(expression)) {
        expression.delete();
      }
    }
    myToBeRemovedExpressions.clear();

    for (GradleDslExpression expression : myExpressions) {
      if (expression.isModified()) {
        expression.applyChanges();
      }
    }
  }

  @Override
  protected void reset() {
    myToBeAddedExpressions.clear();
    myToBeRemovedExpressions.clear();
    for (GradleDslExpression expression : myExpressions) {
      if (expression.isModified()) {
        expression.resetState();
      }
    }
  }

  @Override
  @NotNull
  protected Collection<GradleDslElement> getChildren() {
    return ImmutableList.of();
  }
}
