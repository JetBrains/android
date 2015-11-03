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

import com.android.tools.idea.gradle.dsl.parser.GradleDslFile;
import com.android.tools.idea.gradle.dsl.parser.ext.ExtDslElement;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

import java.util.Collection;

/**
 * Represents a {@link GrReferenceExpression} element.
 */
public final class GradleDslReference extends GradleDslElement {
  @NotNull private GrReferenceExpression myReference;

  public GradleDslReference(@NotNull GradleDslElement parent,
                            @NotNull GroovyPsiElement psiElement,
                            @NotNull String name,
                            @NotNull GrReferenceExpression reference) {
    super(parent, psiElement, name);
    myReference = reference;
  }

  @Nullable
  public String getReferenceText() {
    return myReference.getText();
  }

  /**
   * Returns the value of type {@code clazz} when the {@link GrReferenceExpression} element is referring to an element with the value
   * of that type, or {@code null} otherwise.
   */
  @Nullable
  public <T> T getResolvedValue(@NotNull Class<T> clazz) {
    String referenceText = getReferenceText();
    if (referenceText == null) {
      return null;
    }

    GradleDslFile dslFile = null;
    GradleDslElement parent = myParent;
    while(parent != null) {
      if (parent instanceof GradleDslFile) {
        dslFile = (GradleDslFile)parent;
        break;
      }
      parent = parent.getParent();
    }

    if (dslFile == null) {
      return null;
    }

    ExtDslElement extDslElement = dslFile.getProperty(ExtDslElement.NAME, ExtDslElement.class);
    if (extDslElement == null) {
      return null;
    }

    // TODO: Also expand to look at other places like rootProject etc.
    T extPropertyValue = extDslElement.getProperty(referenceText, clazz);
    if (extPropertyValue != null) {
      return extPropertyValue;
    }

    if (clazz.isAssignableFrom(String.class)) {
      return clazz.cast(referenceText);
    }

    return null;
  }

  @Override
  @NotNull
  protected Collection<GradleDslElement> getChildren() {
    return ImmutableList.of();
  }

  @Override
  protected void apply() {
    // TODO: Add support to update a reference element.
  }

  @Override
  protected void reset() {
    // TODO: Add support to update a reference element.
  }

  @Override
  @Nullable
  public GroovyPsiElement create() {
    // TODO: Add support to create a new reference element.
    return getPsiElement();
  }

  @Override
  protected void delete() {
    // TODO: Add support to delete a reference element.
  }
}
