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
package com.android.tools.idea.gradle.dsl.parser.android;

import com.android.tools.idea.gradle.dsl.parser.elements.*;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

public final class ProductFlavorDslElement extends GradlePropertiesDslElement {

  public ProductFlavorDslElement(@NotNull GradleDslElement parent, @NotNull String name) {
    super(parent, null, name);
  }

  @Override
  protected boolean isBlockElement() {
    return true;
  }

  @Override
  public void addParsedElement(@NotNull String property, @NotNull GradleDslElement element) {
    if (property.equals("consumerProguardFiles") && element instanceof GradleDslExpression) {
      addAsParsedDslExpressionList(property, (GradleDslExpression)element);
      return;
    }

    if (property.equals("proguardFiles") || property.equals("proguardFile")) {
      addToParsedExpressionList("proguardFiles", element);
      return;
    }

    if (property.equals("resConfigs") || property.equals("resConfig")) {
      addToParsedExpressionList("resConfigs", element);
      return;
    }

    if (property.equals("resValue")) {
      if (!(element instanceof GradleDslExpressionList)) {
        return;
      }
      GradleDslExpressionList listElement = (GradleDslExpressionList)element;
      if (listElement.getExpressions().size() != 3 || listElement.getValues(String.class).size() != 3) {
        return;
      }

      GradleDslElementList elementList = getProperty("resValues", GradleDslElementList.class);
      if (elementList == null) {
        elementList = new GradleDslElementList(this, "resValues");
        setParsedElement("resValues", elementList);
      }
      elementList.addParsedElement(element);
    }

    if (property.equals("testInstrumentationRunnerArguments")) {
      if (!(element instanceof GradleDslExpressionMap)) {
        return;
      }
      GradleDslExpressionMap testInstrumentationRunnerArgumentsElement =
        getProperty("testInstrumentationRunnerArguments", GradleDslExpressionMap.class);
      if (testInstrumentationRunnerArgumentsElement == null) {
        setParsedElement("testInstrumentationRunnerArguments", element);
      }
      else {
        testInstrumentationRunnerArgumentsElement.setPsiElement(element.getPsiElement());
        GradleDslExpressionMap elementsToAdd = (GradleDslExpressionMap)element;
        for (Map.Entry<String, GradleDslElement> entry : elementsToAdd.getPropertyElements().entrySet()) {
          testInstrumentationRunnerArgumentsElement.setParsedElement(entry.getKey(), entry.getValue());
        }
      }
      return;
    }

    if (property.equals("testInstrumentationRunnerArgument")) {
      if (!(element instanceof GradleDslExpressionList)) {
        return;
      }
      GradleDslExpressionList gradleDslExpressionList = (GradleDslExpressionList)element;
      List<GradleDslExpression> elements = gradleDslExpressionList.getExpressions();
      if (elements.size() != 2) {
        return;
      }

      String key = elements.get(0).getValue(String.class);
      if (key == null) {
        return;
      }
      GradleDslExpression value = elements.get(1);

      GradleDslExpressionMap testInstrumentationRunnerArgumentsElement =
        getProperty("testInstrumentationRunnerArguments", GradleDslExpressionMap.class);
      if (testInstrumentationRunnerArgumentsElement == null) {
        testInstrumentationRunnerArgumentsElement = new GradleDslExpressionMap(this, "testInstrumentationRunnerArguments");
      }
      testInstrumentationRunnerArgumentsElement.setParsedElement(key, value);
      return;
    }

    super.addParsedElement(property, element);
  }
}
