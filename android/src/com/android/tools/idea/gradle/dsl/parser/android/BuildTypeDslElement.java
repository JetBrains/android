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
package com.android.tools.idea.gradle.dsl.parser.android;

import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElementList;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionList;
import org.jetbrains.annotations.NotNull;

public final class BuildTypeDslElement extends AbstractFlavorTypeDslElement {

  public BuildTypeDslElement(@NotNull GradleDslElement parent, @NotNull String name) {
    super(parent, name);
  }

  @Override
  public void addParsedElement(@NotNull String property, @NotNull GradleDslElement element) {
    if (property.equals("buildConfigField")) {
      if (!(element instanceof GradleDslExpressionList)) {
        return;
      }
      GradleDslExpressionList listElement = (GradleDslExpressionList)element;
      if (listElement.getExpressions().size() != 3 || listElement.getValues(String.class).size() != 3) {
        return;
      }

      GradleDslElementList elementList = getPropertyElement("buildConfigField", GradleDslElementList.class);
      if (elementList == null) {
        elementList = new GradleDslElementList(this, "buildConfigField");
        setParsedElement("buildConfigField", elementList);
      }
      elementList.addParsedElement(element);
      return;
    }

    super.addParsedElement(property, element);
  }
}
