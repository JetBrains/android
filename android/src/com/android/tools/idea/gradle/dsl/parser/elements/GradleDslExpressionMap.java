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

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrApplicationStatement;

import java.util.List;
import java.util.Map;

/**
 * Represents an element which consists of a map from properties of type {@link String} and values of type {@link GradleDslExpression}.
 */
public final class GradleDslExpressionMap extends GradlePropertiesDslElement {
  public GradleDslExpressionMap(@Nullable GradleDslElement parent, @NotNull String name) {
    super(parent, null, name);
  }

  public GradleDslExpressionMap(@Nullable GradleDslElement parent,
                                @NotNull GroovyPsiElement psiElement,
                                @NotNull String name) {
    super(parent, psiElement, name);
  }

  public void addNewLiteral(String key, Object value) {
    GradleDslElement propertyElement = getPropertyElement(key);
    if (propertyElement instanceof GradleDslLiteral) {
      ((GradleDslLiteral)propertyElement).setValue(value);
      return;
    }
    GradleDslLiteral gradleDslLiteral = new GradleDslLiteral(this, key);
    setNewElement(key, gradleDslLiteral);
    gradleDslLiteral.setValue(value);
  }

  /**
   * Returns the map from properties of the type {@link String} and values of the type {@code clazz}.
   *
   * <p>Returns an empty map when the given there are no values of type {@code clazz}.
   */
  @NotNull
  public <V> Map<String, V> getValues(@NotNull Class<V> clazz) {
    Map<String, V> result = Maps.newHashMap();
    for (Map.Entry<String, GradleDslElement> entry : getPropertyElements().entrySet()) {
      GradleDslElement propertyElement = entry.getValue();
      if (propertyElement instanceof GradleDslExpression) {
        V value = ((GradleDslExpression)propertyElement).getValue(clazz);
        if (value != null) {
          result.put(entry.getKey(), value);
        }
      }
    }
    return result;
  }

  @Override
  @Nullable
  public GroovyPsiElement create() {
    GroovyPsiElement psiElement = super.create();
    if (psiElement == null) {
      return null;
    }

    if (psiElement instanceof GrListOrMap || psiElement instanceof GrArgumentList) {
      return psiElement;
    }

    if (psiElement instanceof GrApplicationStatement) {
      GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(psiElement.getProject());
      GrArgumentList argumentList = factory.createArgumentListFromText("xyz");
      argumentList.getFirstChild().delete(); // Workaround to get an empty argument list.
      PsiElement added = psiElement.addAfter(argumentList, psiElement.getLastChild());
      if (added instanceof GrArgumentList) {
        GrArgumentList addedArgumentList = (GrArgumentList)added;
        setPsiElement(addedArgumentList);
        return addedArgumentList;
      }
    }

    return null;
  }
}
