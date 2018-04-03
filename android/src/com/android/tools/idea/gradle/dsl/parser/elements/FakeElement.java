/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * A {@link FakeElement} that is used to represent models that are derived from part of a {@link GradleDslElement}.
 * This is needed since a {@link GradlePropertyModel} requires a backing element, these elements are not
 * fully part of the tree, they have parents but are not visible from or attached to their parent.
 *
 * Subclasses of {@link FakeElement} can decide whether or not they should be able to be renamed or deleted. By default
 * {@link FakeElement}s can't be renamed but can be deleted if constructed with canDelete being {@code true}.
 *
 * {@link #produceValue()} and {@link #consumeValue(Object)} should be overridden by each subclass to provide getting
 * and setting the derived value from and to its real element.
 */
public abstract class FakeElement extends GradleDslSettableExpression {
  @NotNull protected final GradleDslExpression myRealExpression;
  private final boolean myCanDelete;

  public FakeElement(@Nullable GradleDslElement parent,
                     @NotNull GradleNameElement name,
                     @NotNull GradleDslExpression originExpression,
                     boolean canDelete) {
    super(parent, null, name, null);
    myRealExpression = originExpression;
    myCanDelete = canDelete;
  }

  @Nullable
  private PsiElement createPsiElement() {
    Object s = produceValue();
    PsiElement element = s == null ? null : getDslFile().getParser().convertToPsiElement(s);
    // Note: Even though we use static dependencies for everything else, we are required to update them here.
    setupDependencies(element);
    return element;
  }

  @Override
  public void rename(@NotNull String newName) {
    throw new UnsupportedOperationException("Renaming of this fake element is not possible.");
  }

  @Override
  public final void delete() {
    if (myCanDelete) {
      consumeValue(null);
    } else {
      throw new UnsupportedOperationException("Deleting this element is not supported.");
    }
  }

  @NotNull
  @Override
  public final Collection<GradleDslElement> getChildren() {
    PsiElement element = createPsiElement();
    if (element == null) {
      return ImmutableList.of();
    }
    return getDslFile().getParser().getResolvedInjections(this, element).stream().map(e -> e.getToBeInjected()).collect(
      Collectors.toList());
  }

  @Override
  protected final void apply() {
    // Do nothing, this is a fake element
  }

  @Override
  public final void reset() {
    // Do nothing, this is a fake element and has no state
  }

  @Nullable
  @Override
  public final Object getValue() {
    PsiElement element = createPsiElement();
    if (element == null) {
      return null;
    }

    return ApplicationManager.getApplication()
      .runReadAction((Computable<Object>)() -> getDslFile().getParser().extractValue(this, element, true));
  }

  @Nullable
  @Override
  public final Object getUnresolvedValue() {
    PsiElement element = createPsiElement();
    if (element == null) {
      return null;
    }

    return ApplicationManager.getApplication()
      .runReadAction((Computable<Object>)() -> getDslFile().getParser().extractValue(this, element, false));
  }

  @Nullable
  @Override
  public final <T> T getValue(@NotNull Class<T> clazz) {
    Object value = getValue();
    if (value != null && clazz.isAssignableFrom(value.getClass())) {
      return clazz.cast(value);
    }
    return null;
  }

  @Nullable
  @Override
  public final <T> T getUnresolvedValue(@NotNull Class<T> clazz) {
    return getValue(clazz);
  }

  @Override
  public final void setValue(@NotNull Object value) {
    consumeValue(value);
  }

  /**
   * @return returns the value that has been derived from the real element.
   */
  @Nullable
  protected abstract Object produceValue();

  /**
   * Set the derived element to a given value.
   *
   * @param value the value to set.
   */
  protected abstract void consumeValue(@Nullable Object value);
}
