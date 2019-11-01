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
package com.android.tools.idea.gradle.dsl.parser.configurations;

import static com.android.tools.idea.gradle.dsl.model.configurations.ConfigurationModelImpl.*;
import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslBlockElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslNamedDomainElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.groovy.GroovyDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.kotlin.KotlinDslNameConverter;
import com.google.common.collect.ImmutableMap;
import com.intellij.psi.PsiElement;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ConfigurationDslElement extends GradleDslBlockElement implements GradleDslNamedDomainElement {
  @NotNull
  public static final ImmutableMap<String, String> ktsToModelNameMap = Stream.of(new String[][]{
    {"isTransitive", TRANSITIVE},
    {"isVisible", VISIBLE}
  }).collect(toImmutableMap(data -> data[0], data -> data[1]));

  @NotNull
  public static final ImmutableMap<String, String> groovyToModelNameMap = Stream.of(new String[][]{
    {"transitive", TRANSITIVE},
    {"visible", VISIBLE}
  }).collect(toImmutableMap(data -> data[0], data -> data[1]));

  @Override
  @NotNull
  public ImmutableMap<String, String> getExternalToModelMap(@NotNull GradleDslNameConverter converter) {
    if (converter instanceof KotlinDslNameConverter) {
      return ktsToModelNameMap;
    }
    else if (converter instanceof GroovyDslNameConverter) {
      return groovyToModelNameMap;
    }
    else {
      return super.getExternalToModelMap(converter);
    }
  }

  private final boolean myHasBraces;

  public ConfigurationDslElement(@NotNull GradleDslElement parent, @NotNull GradleNameElement name) {
    super(parent, name);
    myHasBraces = true;
  }

  public ConfigurationDslElement(@NotNull GradleDslElement parent, @NotNull PsiElement element, @NotNull GradleNameElement name, boolean hasBraces) {
    super(parent, name);
    setPsiElement(element);
    myHasBraces = hasBraces;
  }

  private String methodName;

  @Nullable
  @Override
  public String getMethodName() {
    return methodName;
  }

  @Nullable
  @Override
  public void setMethodName(@Nullable String value) {
    methodName = value;
  }

  @Nullable
  @Override
  public PsiElement create() {
    // Delete and re-create
    if (!myHasBraces) {
      delete();
    }
    return super.create();
  }

  @Override
  public boolean isInsignificantIfEmpty() {
    return false;
  }

  @Override
  public void addParsedElement(@NotNull GradleDslElement element) {
    super.addParsedElement(element);
    maybeRenameElement(element);
  }

  @Override
  public void setParsedElement(@NotNull GradleDslElement element) {
    super.setParsedElement(element);
    maybeRenameElement(element);
  }
}
