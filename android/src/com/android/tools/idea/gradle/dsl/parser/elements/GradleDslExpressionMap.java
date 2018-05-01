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

import com.android.tools.idea.gradle.dsl.parser.GradleReferenceInjection;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Represents an element which consists of a map from properties of type {@link String} and values of type {@link GradleDslSimpleExpression}.
 */
public final class GradleDslExpressionMap extends GradlePropertiesDslElement implements GradleDslExpression {
  // This boolean controls whether of not the empty map element should be deleted on a call to delete in one of
  // its children. For non-literal maps (e.g func key: 'val', key1: 'val') #shouldBeDeleted() always returns true since we
  // never want to preserve these maps. However literal maps (e.g prop = [key: 'merge1', key1: 'merge2']) should only be deleted
  // if the #delete() method on the map element is called, not when there are no more elements left. This is due to
  // prop = [:] possibly having important semantic meaning.
  private boolean myShouldBeDeleted;

  public GradleDslExpressionMap(@Nullable GradleDslElement parent, @NotNull GradleNameElement name) {
    super(parent, null, name);
  }

  public GradleDslExpressionMap(@Nullable GradleDslElement parent, @NotNull GradleNameElement name, boolean isLiteralMap) {
    super(parent, null, name);
    myUseAssignment = isLiteralMap;
  }

  public GradleDslExpressionMap(@Nullable GradleDslElement parent,
                                @Nullable PsiElement psiElement,
                                @NotNull GradleNameElement name,
                                boolean isLiteralMap) {
    super(parent, psiElement, name);
    myUseAssignment = isLiteralMap;
  }

  public void addNewLiteral(String key, Object value) {
    GradleDslElement propertyElement = getPropertyElement(key);
    if (propertyElement instanceof GradleDslLiteral) {
      ((GradleDslLiteral)propertyElement).setValue(value);
      return;
    }
    GradleNameElement name = GradleNameElement.create(key);
    GradleDslLiteral gradleDslLiteral = new GradleDslLiteral(this, name);
    setNewElement(gradleDslLiteral);
    gradleDslLiteral.setValue(value);
  }

  @Override
  @Nullable
  public PsiElement create() {
    return getDslFile().getWriter().createDslExpressionMap(this);
  }

  @Override
  public void delete() {
    myShouldBeDeleted = true;
    super.delete();
  }

  @Override
  public void apply() {
    getDslFile().getWriter().applyDslExpressionMap(this);
    super.apply();
  }

  public boolean isLiteralMap() {
    return myUseAssignment;
  }

  @Override
  @Nullable
  public PsiElement getExpression() {
    return getPsiElement();
  }

  public boolean shouldBeDeleted() {
    return !isLiteralMap() || myShouldBeDeleted;
  }

  @Override
  @NotNull
  public List<GradleReferenceInjection> getResolvedVariables() {
    return getDependencies().stream().filter(e -> e.isResolved()).collect(Collectors.toList());
  }
}
