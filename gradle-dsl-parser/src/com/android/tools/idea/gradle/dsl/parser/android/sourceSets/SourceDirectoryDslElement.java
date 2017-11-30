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
package com.android.tools.idea.gradle.dsl.parser.android.sourceSets;

import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslBlockElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import org.jetbrains.annotations.NotNull;


public class SourceDirectoryDslElement extends GradleDslBlockElement {
  public SourceDirectoryDslElement(@NotNull GradleDslElement parent, @NotNull String name) {
    super(parent, name);
  }

  @Override
  public void addParsedElement(@NotNull String property, @NotNull GradleDslElement element) {
    if (property.equals("srcDirs") || property.equals("srcDir")) {
      addToParsedExpressionList("srcDirs", element);
      return;
    }

    if (property.equals("include") || property.equals("exclude")) {
      addToParsedExpressionList(property, element);
      return;
    }

    super.addParsedElement(property, element);
  }
}
