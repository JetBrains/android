/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.model.values;

import com.android.tools.idea.gradle.dsl.api.values.GradleNotNullValue;
import com.android.tools.idea.gradle.dsl.api.values.GradleValue;
import com.android.tools.idea.gradle.dsl.parser.GradleReferenceInjection;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpression;
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Represents a value returned by Gradle Dsl Model.
 *
 * @param <T> the type of the returned value.
 */
public abstract class GradleValueImpl<T> implements GradleValue<T> {
  @Nullable private final GradleDslElement myDslElement;
  @Nullable private final T myValue;

  protected GradleValueImpl(@Nullable GradleDslElement dslElement, @Nullable T value) {
    myDslElement = dslElement;
    myValue = value;
  }

  @Nullable
  @Override
  public T value() {
    return myValue;
  }

  @Override
  @Nullable
  public PsiElement getPsiElement() {
    if (myDslElement == null) {
      return null;
    }
    return myDslElement instanceof GradleDslExpression ? ((GradleDslExpression)myDslElement).getExpression() : myDslElement.getPsiElement();
  }

  @Nullable
  @Override
  public VirtualFile getFile() {
    return myDslElement != null ? myDslElement.getDslFile().getFile() : null;
  }

  @Nullable
  @Override
  public String getPropertyName() {
    return myDslElement != null ? myDslElement.getQualifiedName() : null;
  }

  @Nullable
  @Override
  public String getDslText() {
    PsiElement psiElement = getPsiElement();
    return psiElement != null ? psiElement.getText() : null;
  }

  @Override
  @NotNull
  public Map<String, GradleNotNullValue<Object>> getResolvedVariables() {
    if (myDslElement == null) {
      return ImmutableMap.of();
    }

    ImmutableMap.Builder<String, GradleNotNullValue<Object>> builder = ImmutableMap.builder();
    for (GradleReferenceInjection injection : myDslElement.getResolvedVariables()) {
      String variableName = injection.getName();

      // Skip any lists or maps that we might possibly get here.
      GradleDslExpression expression = injection.getToBeInjectedExpression();
      if (expression == null) {
        continue;
      }

      Object resolvedValue = expression.getValue();
      // No values here should be null
      if (resolvedValue == null) {
        Logger.getInstance(GradleValueImpl.class).warn("Reference to a null value was found, variable: " + variableName);
        continue;
      }

      builder.put(variableName, new GradleNotNullValueImpl<>(expression, resolvedValue));
    }
    return builder.build();
  }
}
