/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.parser.include;

import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionList;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslSimpleExpression;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class IncludeDslElement extends GradlePropertiesDslElement {
  public static final String INCLUDE = "include";

  public IncludeDslElement(@Nullable GradleDslElement parent) {
    super(parent, null, GradleNameElement.create(INCLUDE));
  }

  @Override
  @Nullable
  public PsiElement create() {
    return myParent != null ? myParent.create() : null;
  }

  public void removeModule(@NotNull Object value) {
    for (GradleDslElement module : getPropertyElements(GradleDslElement.class)) {
      if (module instanceof GradleDslSimpleExpression) {
        GradleDslSimpleExpression simpleModulePath = (GradleDslSimpleExpression)module;
        if (value.equals(simpleModulePath.getValue())) {
          super.removeProperty(simpleModulePath);
          updateDependenciesOnRemoveElement(simpleModulePath);
          return;
        }
      }
      else if (module instanceof GradleDslExpressionList) {
        GradleDslExpressionList listIncludePaths = (GradleDslExpressionList)module;
        for (GradleDslSimpleExpression simpleModulePath : listIncludePaths.getSimpleExpressions()) {
          if (value.equals(simpleModulePath.getValue())) {
            listIncludePaths.removeProperty(simpleModulePath);
            updateDependenciesOnRemoveElement(simpleModulePath);
            return;
          }
        }
      }

    }
  }

  public void replaceModulePath(@NotNull Object oldValue, @NotNull Object newValue) {
    for (GradleDslElement module : getPropertyElements(GradleDslElement.class)) {
      if (module instanceof GradleDslSimpleExpression) {
        GradleDslSimpleExpression simpleModulePath = (GradleDslSimpleExpression)module;
        if (oldValue.equals(simpleModulePath.getValue())) {
          simpleModulePath.setValue(newValue);
          return;
        }
      }
      else if (module instanceof GradleDslExpressionList) {
        GradleDslExpressionList listIncludePaths = (GradleDslExpressionList)module;
        for (GradleDslSimpleExpression simpleModulePath : listIncludePaths.getSimpleExpressions()) {
          if (oldValue.equals(simpleModulePath.getValue())) {
            simpleModulePath.setValue(newValue);
            return;
          }
        }
      }
    }
  }
}
