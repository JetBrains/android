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
package com.android.tools.idea.gradle.dsl.model;

import com.android.tools.idea.gradle.dsl.parser.GradleResolvedVariable;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;

import java.util.Map;

/**
 * Represents a value returned by Gradle Dsl Model.
 *
 * Note: WIP. Please do no use.
 *
 * @param <T> the type of the returned value.
 */
public class GradleValue<T> {
  @NotNull private final T myValue;
  @NotNull private final GradleDslElement myDslElement;

  public GradleValue(@NotNull T value, @NotNull GradleDslElement dslElement) {
    myValue = value;
    myDslElement = dslElement;
  }

  @NotNull
  public T getValue() {
    return myValue;
  }

  @NotNull
  public VirtualFile getFile() {
    return myDslElement.getDslFile().getFile();
  }

  @NotNull
  public String getPropertyName() {
    return myDslElement.getQualifiedName();
  }

  @Nullable
  public String getDslText() {
    GroovyPsiElement psiElement = myDslElement.getPsiElement();
    return psiElement != null ? psiElement.getText() : null;
  }

  @NotNull
  public Map<String, GradleValue<Object>> getResolvedVariables() {
    ImmutableMap.Builder<String, GradleValue<Object>> builder = ImmutableMap.builder();
    for (GradleResolvedVariable variable : myDslElement.getResolvedVariables()) {
      String variableName = variable.getVariableName();
      Object resolvedValue = variable.getValue();
      GradleDslElement element = variable.getElement();
      builder.put(variableName, new GradleValue<Object>(resolvedValue, element));
    }
    return builder.build();
  }
}
