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
package com.android.tools.idea.gradle.dsl.model;

import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel;
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.dsl.api.java.LanguageLevelPropertyModel;
import com.android.tools.idea.gradle.dsl.api.util.GradleDslModel;
import com.android.tools.idea.gradle.dsl.api.values.GradleNullableValue;
import com.android.tools.idea.gradle.dsl.model.ext.GradlePropertyModelBuilder;
import com.android.tools.idea.gradle.dsl.model.ext.GradlePropertyModelImpl;
import com.android.tools.idea.gradle.dsl.model.ext.PropertyTransform;
import com.android.tools.idea.gradle.dsl.model.ext.ResolvedPropertyModelImpl;
import com.android.tools.idea.gradle.dsl.model.java.LanguageLevelPropertyModelImpl;
import com.android.tools.idea.gradle.dsl.model.values.GradleNotNullValueImpl;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.stream.Collectors;

import static com.android.tools.idea.gradle.dsl.api.ext.PropertyType.REGULAR;

/**
 * Base class for the models representing block elements.
 */
public abstract class GradleDslBlockModel implements GradleDslModel {
  protected GradlePropertiesDslElement myDslElement;

  protected GradleDslBlockModel(@NotNull GradlePropertiesDslElement dslElement) {
    myDslElement = dslElement;
  }

  @Nullable
  public PsiElement getPsiElement() {
    return myDslElement.getPsiElement();
  }

  public boolean hasValidPsiElement() {
    PsiElement psiElement = getPsiElement();
    return psiElement != null && psiElement.isValid();
  }

  @NotNull
  protected GradleNullableValue<String> getIntOrStringValue(@NotNull String propertyName) {
    Integer intValue = myDslElement.getLiteralProperty(propertyName, Integer.class).value();
    if (intValue != null) {
      GradleDslElement propertyElement = myDslElement.getPropertyElement(propertyName);
      assert propertyElement != null;
      return new GradleNotNullValueImpl<>(propertyElement, intValue.toString());
    }
    return myDslElement.getLiteralProperty(propertyName, String.class);
  }

  @Override
  @NotNull
  public Map<String, GradlePropertyModel> getInScopeProperties() {
    return myDslElement.getInScopeElements().entrySet().stream()
      .collect(Collectors.toMap(e -> e.getKey(), e -> new GradlePropertyModelImpl(e.getValue())));
  }

  @NotNull
  protected ResolvedPropertyModel getModelForProperty(@NotNull String property) {
    return getModelForProperty(property, false);
  }

  @NotNull
  protected LanguageLevelPropertyModel getLanguageModelForProperty(@NotNull String property) {
    return GradlePropertyModelBuilder.create(myDslElement, property).buildLanguage();
  }

  @NotNull
  protected ResolvedPropertyModel getModelForProperty(@NotNull String property, boolean isMethod) {
    return GradlePropertyModelBuilder.create(myDslElement, property).asMethod(isMethod).buildResolved();
  }
}
