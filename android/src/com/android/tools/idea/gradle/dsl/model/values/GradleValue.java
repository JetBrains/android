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

import com.android.tools.idea.gradle.dsl.parser.GradleResolvedVariable;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpression;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.hash.LinkedHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Represents a value returned by Gradle Dsl Model.
 *
 * @param <T> the type of the returned value.
 */
public abstract class GradleValue<T> {
  @Nullable private final GradleDslElement myDslElement;
  @Nullable private final T myValue;

  protected GradleValue(@Nullable GradleDslElement dslElement, @Nullable T value) {
    myDslElement = dslElement;
    myValue = value;
  }

  @Nullable
  public T value() {
    return myValue;
  }

  @Nullable
  public GroovyPsiElement getPsiElement() {
    if (myDslElement == null) {
      return null;
    }
    return myDslElement instanceof GradleDslExpression ? ((GradleDslExpression)myDslElement).getExpression() : myDslElement.getPsiElement();
  }

  @Nullable
  public VirtualFile getFile() {
    return myDslElement != null ? myDslElement.getDslFile().getFile() : null;
  }

  @Nullable
  public String getPropertyName() {
    return myDslElement != null ? myDslElement.getQualifiedName() : null;
  }

  @Nullable
  public String getDslText() {
    GroovyPsiElement psiElement = getPsiElement();
    return psiElement != null ? psiElement.getText() : null;
  }

  @NotNull
  public Map<String, GradleNotNullValue<Object>> getResolvedVariables() {
    if (myDslElement == null) {
      return ImmutableMap.of();
    }

    ImmutableMap.Builder<String, GradleNotNullValue<Object>> builder = ImmutableMap.builder();
    for (GradleResolvedVariable variable : myDslElement.getResolvedVariables()) {
      String variableName = variable.getVariableName();
      Object resolvedValue = variable.getValue();
      GradleDslElement element = variable.getElement();
      builder.put(variableName, new GradleNotNullValue<>(element, resolvedValue));
    }
    return builder.build();
  }

  @NotNull
  public static <E> List<E> getValues(@Nullable List<? extends GradleValue<E>> gradleValues) {
    if (gradleValues == null) {
      return ImmutableList.of();
    }

    List<E> values = new ArrayList<>(gradleValues.size());
    for (GradleValue<E> gradleValue : gradleValues) {
      E value = gradleValue.value();
      if (value != null) {
        values.add(value);
      }
    }

    return values;
  }

  @NotNull
  public static <V> Map<String, V> getValues(@Nullable Map<String, ? extends GradleValue<V>> gradleValues) {
    if (gradleValues == null) {
      return ImmutableMap.of();
    }

    Map<String, V> values = new LinkedHashMap<>();
    for (Map.Entry<String, ? extends GradleValue<V>> gradleValueEntry : gradleValues.entrySet()) {
      V value = gradleValueEntry.getValue().value();
      if (value != null) {
        values.put(gradleValueEntry.getKey(), value);
      }
    }

    return values;
  }
}
