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

public final class ProductFlavorDslElement extends GradlePropertiesDslElement {

  public ProductFlavorDslElement(@NotNull GradleDslElement parent, @NotNull String name) {
    super(parent, null, name);
  }

  @Override
  protected boolean isBlockElement() {
    return true;
  }

  @Override
  public void addDslElement(@NotNull String property, @NotNull GradleDslElement element) {
    if (property.equals("consumerProguardFiles") && element instanceof GradleDslLiteral) {
      addAsDslLiteralList(property, (GradleDslLiteral)element);
      return;
    }

    if (property.equals("proguardFiles") || property.equals("proguardFile")) {
      addToDslLiteralList("proguardFiles", element);
      return;
    }

    if (property.equals("resConfigs") || property.equals("resConfig")) {
      addToDslLiteralList("resConfigs", element);
      return;
    }

    if (property.equals("resValue")) {
      if (!(element instanceof GradleDslLiteralList)) {
        return;
      }
      GradleDslLiteralList listElement = (GradleDslLiteralList)element;
      if (listElement.getElements().size() != 3 || listElement.getValues(String.class).size() != 3) {
        return;
      }

      GradleDslElementList elementList = getProperty("resValues", GradleDslElementList.class);
      if (elementList == null) {
        elementList = new GradleDslElementList(this, "resValues");
        setDslElement("resValues", elementList);
      }
      elementList.addParsedElement(element);
    }

    if (property.equals("testInstrumentationRunnerArguments")) {
      if (!(element instanceof GradleDslLiteralMap)) {
        return;
      }
      GradleDslLiteralMap testInstrumentationRunnerArgumentsElement =
        getProperty("testInstrumentationRunnerArguments", GradleDslLiteralMap.class);
      if (testInstrumentationRunnerArgumentsElement == null) {
        setDslElement("testInstrumentationRunnerArguments", element);
      } else {
        GradleDslLiteralMap elementsToAdd = (GradleDslLiteralMap)element;
        for (String key : elementsToAdd.getProperties()) {
          GradleDslElement elementToAdd = elementsToAdd.getPropertyElement(key);
          if (elementToAdd != null) {
            testInstrumentationRunnerArgumentsElement.setDslElement(key, elementToAdd);
          }
        }
      }
      return;
    }

    if (property.equals("testInstrumentationRunnerArgument")) {
      if (!(element instanceof GradleDslLiteralList)) {
        return;
      }
      GradleDslLiteralList gradleDslLiteralList = (GradleDslLiteralList)element;
      List<GradleDslLiteral> elements = gradleDslLiteralList.getElements();
      if (elements.size() != 2) {
        return;
      }

      String key = elements.get(0).getValue(String.class);
      if (key == null) {
        return;
      }
      GradleDslLiteral value = elements.get(1);

      GradleDslLiteralMap testInstrumentationRunnerArgumentsElement =
        getProperty("testInstrumentationRunnerArguments", GradleDslLiteralMap.class);
      if (testInstrumentationRunnerArgumentsElement == null) {
        testInstrumentationRunnerArgumentsElement = new GradleDslLiteralMap(this, "testInstrumentationRunnerArguments");
      }
      testInstrumentationRunnerArgumentsElement.setDslElement(key, value);
      return;
    }

    super.addDslElement(property, element);
  }
}
