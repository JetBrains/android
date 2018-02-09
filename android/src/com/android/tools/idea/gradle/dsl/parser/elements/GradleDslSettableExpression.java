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

import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a GradleExpression that has a value that can be set/reset. This class stores a tentative value. Subclasses should
 * use {@link #getCurrentElement()} to get the currently tentative or set element or {@link #reset()} return to the previously saved state.
 */
public abstract class GradleDslSettableExpression extends GradleDslExpression {
  @Nullable private PsiElement myUnsavedValue;

  protected GradleDslSettableExpression(@Nullable GradleDslElement parent,
                                        @Nullable PsiElement psiElement,
                                        @NotNull String name,
                                        @Nullable PsiElement expression) {
    super(parent, psiElement, name, expression);
  }

  @Nullable
  public PsiElement getUnsavedValue() {
    return myUnsavedValue;
  }

  @Override
  public void reset() {
    myUnsavedValue = null;
    // Resetting setModified is done by GradleDslElement#resetState.
  }

  @Nullable
  protected PsiElement getCurrentElement() {
    return myUnsavedValue != null ? myUnsavedValue : myExpression;
  }

  protected void setUnsavedValue(@Nullable PsiElement element) {
    myUnsavedValue = element;
    setModified(true);
  }

  protected void checkForValidValue(@NotNull Object value) {
    if (!(value instanceof String || value instanceof Integer || value instanceof Boolean || value instanceof ReferenceTo)) {
      throw new IllegalArgumentException(
        "Can't set a property value with: " + value.getClass() + " type must be one of [Boolean, Integer, String, ReferenceTo]");
    }
  }
}
