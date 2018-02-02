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
package com.android.tools.idea.gradle.dsl.parser.dependencies;

import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslClosure;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElementList;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DependencyConfigurationDslElement extends GradleDslClosure {
  public DependencyConfigurationDslElement(@Nullable GradleDslElement parent,
                                           @Nullable PsiElement psiElement,
                                           @NotNull GradleNameElement name) {
    super(parent, psiElement, name);
  }

  @Override
  public void addParsedElement(@NotNull String property, @NotNull GradleDslElement element) {
    if (property.equals("exclude")) {
      GradleDslElementList elementList = getPropertyElement(property, GradleDslElementList.class);
      if (elementList == null) {
        elementList = new GradleDslElementList(this, GradleNameElement.create(property));
        super.addParsedElement(property, elementList);
      }
      elementList.addParsedElement(element);
      return;
    }
    super.addParsedElement(property, element);
  }
}
