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

import static com.android.tools.idea.gradle.dsl.model.ext.PropertyUtil.removeElement;

import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel;
import com.android.tools.idea.gradle.dsl.api.ext.PasswordPropertyModel;
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.dsl.api.java.LanguageLevelPropertyModel;
import com.android.tools.idea.gradle.dsl.api.util.GradleDslElementModel;
import com.android.tools.idea.gradle.dsl.api.util.GradleBlockModel;
import com.android.tools.idea.gradle.dsl.api.util.GradleDslModel;
import com.android.tools.idea.gradle.dsl.model.ext.GradlePropertyModelBuilder;
import com.android.tools.idea.gradle.dsl.model.ext.GradlePropertyModelImpl;
import com.android.tools.idea.gradle.dsl.model.ext.PropertyUtil;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpression;
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement;
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelPropertyDescription;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.HashSetQueue;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Base class for the models representing block elements.
 */
public abstract class GradleDslBlockModel implements GradleBlockModel, GradleDslElementModel {
  protected final @NotNull GradlePropertiesDslElement myDslElement;

  protected GradleDslBlockModel(@NotNull GradlePropertiesDslElement dslElement) {
    myDslElement = dslElement;
  }

  @Override
  public <T extends @NotNull GradleDslModel> @NotNull T getModel(@NotNull Class<T> klass) {
    return GradleBlockModelMap.get(myDslElement, getClass(), klass);
  }

  @Override
  @Nullable
  public PsiElement getPsiElement() {
    return myDslElement.getPsiElement();
  }

  @Override
  public @Nullable GradleDslElement getRawElement() {
    return myDslElement;
  }

  @Override
  public @NotNull GradleDslElement getHolder() {
    return getRawPropertyHolder();
  }

  @Override
  public @NotNull GradleDslElement getRawPropertyHolder() {
    GradleDslElement parent = myDslElement.getParent();
    if (parent != null) return parent;
    // GradleDslFile elements (which are GradlePropertiesDslElements) have null parents, though we should never
    // construct a GradleDslBlockModel with a GradleDslFile element.  Return myDslElement to satisfy the type
    // requirements.
    return myDslElement;
  }

  @Override
  public @NotNull String getFullyQualifiedName() {
    return myDslElement.getQualifiedName();
  }

  public boolean hasValidPsiElement() {
    PsiElement psiElement = getPsiElement();
    return psiElement != null && psiElement.isValid();
  }

  @Override
  @NotNull
  public Map<String, GradlePropertyModel> getInScopeProperties() {
    return myDslElement.getInScopeElements().entrySet().stream()
                       .collect(Collectors.toMap(e -> e.getKey(), e -> new GradlePropertyModelImpl(e.getValue())));
  }

  @Override
  @NotNull
  public List<GradlePropertyModel> getDeclaredProperties() {
    return myDslElement.getContainedElements(true).stream()
                       .filter(e -> e instanceof GradleDslExpression)
                       .map(e -> new GradlePropertyModelImpl(e)).collect(Collectors.toList());
  }

  @Override
  @Nullable
  public PsiElement getRepresentativeContainedPsiElement() {
    PsiElement psiElement = getPsiElement();
    if (psiElement != null) return psiElement;
    Queue<GradleDslElement> elementQueue = new HashSetQueue<>();
    Set<GradleDslElement> visitedSet = new HashSet<>();
    visitedSet.add(myDslElement);
    for (GradleDslElement newElement : myDslElement.getOriginalElements()) {
      if (!visitedSet.contains(newElement)) {
        elementQueue.add(newElement);
      }
    }
    while (!elementQueue.isEmpty()) {
      GradleDslElement element = elementQueue.remove();
      psiElement = element.getPsiElement();
      if (psiElement != null) return psiElement;
      visitedSet.add(element);
      if (element instanceof GradlePropertiesDslElement) {
        for (GradleDslElement newElement : ((GradlePropertiesDslElement)element).getOriginalElements()) {
          if (!visitedSet.contains(newElement)) {
            elementQueue.add(newElement);
          }
        }
      }
    }
    return null;
  }

  @Override
  public void delete() {
    removeElement(myDslElement);
  }

  @NotNull
  protected ResolvedPropertyModel getModelForProperty(@NotNull String property) {
    return GradlePropertyModelBuilder.create(myDslElement, property).buildResolved();
  }

  @NotNull
  protected ResolvedPropertyModel getModelForProperty(@NotNull ModelPropertyDescription property) {
    return GradlePropertyModelBuilder.create(myDslElement, property).buildResolved();
  }

  @NotNull
  protected LanguageLevelPropertyModel getLanguageModelForProperty(@NotNull String property) {
    return GradlePropertyModelBuilder.create(myDslElement, property).buildLanguage();
  }

  @NotNull
  protected LanguageLevelPropertyModel getJvmTargetModelForProperty(@NotNull String property) {
    return GradlePropertyModelBuilder.create(myDslElement, property).buildJvmTarget();
  }

  @NotNull
  protected ResolvedPropertyModel getFileModelForProperty(@NotNull String property) {
    return GradlePropertyModelBuilder.create(myDslElement, property).addTransform(PropertyUtil.FILE_TRANSFORM).buildResolved();
  }

  @NotNull
  protected PasswordPropertyModel getPasswordModelForProperty(@NotNull String property) {
    return GradlePropertyModelBuilder.create(myDslElement, property).buildPassword();
  }
}
