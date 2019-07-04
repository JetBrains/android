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
package com.android.tools.idea.gradle.dsl.parser.dependencies;

import com.android.tools.idea.gradle.dsl.parser.elements.*;
import com.android.tools.idea.templates.GradleFileMergers;
import com.intellij.openapi.util.text.StringUtil;
import java.util.Comparator;
import java.util.List;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class DependenciesDslElement extends GradleDslBlockElement {
  @NonNls public static final String DEPENDENCIES_BLOCK_NAME = "dependencies";

  public static final Comparator comparator = Comparator.comparing(GradleDslElement::getName, GradleFileMergers.CONFIGURATION_ORDERING);

  public DependenciesDslElement(@NotNull GradleDslElement parent) {
    super(parent, GradleNameElement.create(DEPENDENCIES_BLOCK_NAME));
  }

  @Override
  public void addParsedElement(@NotNull GradleDslElement dependency) {
    // Treat all expressions and expression maps as dependencies
    if (dependency instanceof GradleDslSimpleExpression ||
        dependency instanceof GradleDslExpressionMap ||
        dependency instanceof GradleDslExpressionList) {
      super.addParsedElement(dependency);
    }
  }

  @Override
  @NotNull
  public GradleDslElement setNewElement(@NotNull GradleDslElement newElement) {
    List<GradleDslElement> es = getAllElements();
    int i = 0;
    for (; i < es.size(); i++) {
      if (comparator.compare(es.get(i), newElement) > 0) {
        break;
      }
    }
    addNewElementAt(i, newElement);
    return newElement;
  }
}
