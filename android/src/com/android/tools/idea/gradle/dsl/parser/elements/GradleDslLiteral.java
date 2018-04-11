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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.iStr;

/**
 * Represents a literal element.
 */
public final class GradleDslLiteral extends GradleDslSettableExpression {
  @Nullable private PsiElement myUnsavedConfigBlock;

  public GradleDslLiteral(@NotNull GradleDslElement parent, @NotNull GradleNameElement name) {
    super(parent, null, name, null);
  }

  public GradleDslLiteral(@NotNull GradleDslElement parent,
                          @NotNull PsiElement psiElement,
                          @NotNull GradleNameElement name,
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

  @Override
  @Nullable
  public Object getValue() {
    PsiElement element = getCurrentElement();
    if (element == null) {
      return null;
    }
    return ApplicationManager.getApplication()
                             .runReadAction((Computable<Object>)() -> getDslFile().getParser().extractValue(this, element, true));
  }

  @Override
  @Nullable
  public Object getUnresolvedValue() {
    PsiElement element = getCurrentElement();
    if (element == null) {
      return null;
    }
    return ApplicationManager.getApplication()
                             .runReadAction((Computable<Object>)() -> getDslFile().getParser().extractValue(this, element, false));
  }

  @Nullable
  public PsiElement getLastCommittedValue() {
    return myExpression;
  }

  /**
   * Returns the value of type {@code clazz} when the literal contains the value of that type,
   * or {@code null} otherwise.
   */
  @Override
  @Nullable
  public <T> T getValue(@NotNull Class<T> clazz) {
    Object value = getValue();
    if (value != null && clazz.isAssignableFrom(value.getClass())) {
      return clazz.cast(value);
    }
    return null;
  }

  @Override
  @Nullable
  public <T> T getUnresolvedValue(@NotNull Class<T> clazz) {
    Object value = getUnresolvedValue();
    if (value != null && clazz.isAssignableFrom(value.getClass())) {
      return clazz.cast(value);
    }
    return null;
  }

  @Override
  public void setValue(@NotNull Object value) {
    checkForValidValue(value);
    PsiElement element =
      ApplicationManager.getApplication().runReadAction((Computable<PsiElement>)() -> getDslFile().getParser().convertToPsiElement(value));
    setUnsavedValue(element);
    valueChanged();
  }

  @Nullable
  @Override
  public Object getRawValue() {
    PsiElement currentElement = getCurrentElement();
    if (currentElement == null) {
      return null;
    }

    return ApplicationManager.getApplication()
                             .runReadAction((Computable<Object>)() -> {
                               boolean shouldInterpolate = getDslFile().getParser().shouldInterpolate(this);
                               Object val = getDslFile().getParser().extractValue(this, currentElement, false);
                               if (val instanceof String && shouldInterpolate) {
                                 return iStr((String)val);
                               }
                               return val;
                             });
  }

  @NotNull
  @Override
  public GradleDslLiteral copy() {
    assert myParent != null;
    GradleDslLiteral literal = new GradleDslLiteral(myParent, GradleNameElement.copy(myName));
    Object v = getRawValue();
    if (v != null) {
      literal.setValue(v);
    }
    return literal;
  }

  public void setConfigBlock(@NotNull PsiElement block) {
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
  public void delete() {
    getDslFile().getWriter().deleteDslLiteral(this);
  }

  @Override
  protected void apply() {
    getDslFile().getWriter().applyDslLiteral(this);
  }
}
