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


import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

public class GradleDslUnknownElement extends GradleDslSimpleExpression {
  public static final Logger LOG = Logger.getInstance(GradleDslUnknownElement.class);

  public GradleDslUnknownElement(@NotNull GradleDslElement parent, @NotNull PsiElement expression, @NotNull GradleNameElement name) {
    super(parent, expression, name, expression);
  }

  @NotNull
  @Override
  public Collection<GradleDslElement> getChildren() {
    return Collections.emptyList();
  }

  @Override
  protected void apply() {
    // This element can't be changed.
  }

  @Nullable
  @Override
  public Object produceValue() {
    PsiElement element = getExpression();
    if (element == null) {
      return null;
    }
    return getPsiText(element);
  }

  @Nullable
  @Override
  public Object produceUnresolvedValue() {
    return getValue();
  }

  @Override
  public void setValue(@NotNull Object value) {
    LOG.warn(new UnsupportedOperationException("Can't set the value of an unknown element: " + this));
  }

  @Nullable
  @Override
  public Object produceRawValue() {
    return getUnresolvedValue();
  }

  @NotNull
  @Override
  public GradleDslUnknownElement copy() {
    assert myParent != null && myExpression != null;
    return new GradleDslUnknownElement(myParent, myExpression, GradleNameElement.copy(myName));
  }
}
