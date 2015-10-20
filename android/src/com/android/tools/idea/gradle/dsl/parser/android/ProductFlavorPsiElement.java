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

public final class ProductFlavorPsiElement extends GradlePropertiesPsiElement {

  public ProductFlavorPsiElement(@NotNull GradlePsiElement parent, @NotNull String name) {
    super(parent, null, name);
  }

  @Override
  protected boolean isBlockElement() {
    return true;
  }

  @Override
  public void addPsiElement(@NotNull String property, @NotNull GradlePsiElement element) {
    if (property.equals("consumerProguardFiles") && element instanceof GradlePsiLiteral) {
      addAsPsiLiteralList(property, (GradlePsiLiteral)element);
      return;
    }

    if (property.equals("proguardFiles") || property.equals("proguardFile")) {
      addToPsiLiteralList("proguardFiles", element);
      return;
    }

    if (property.equals("resConfigs") || property.equals("resConfig")) {
      addToPsiLiteralList("resConfigs", element);
      return;
    }

    if (property.equals("resValue")) {
      if (!(element instanceof GradlePsiLiteralList)) {
        return;
      }
      GradlePsiLiteralList listElement = (GradlePsiLiteralList)element;
      if (listElement.getElements().size() != 3 || listElement.getValues(String.class).size() != 3) {
        return;
      }

      GradlePsiElementList elementList = getProperty("resValues", GradlePsiElementList.class);
      if (elementList == null) {
        elementList = new GradlePsiElementList(this, "resValues");
        setPsiElement("resValues", elementList);
      }
      elementList.addParsedElement(element);
    }

    if (property.equals("testInstrumentationRunnerArguments")) {
      if (!(element instanceof GradlePsiLiteralMap)) {
        return;
      }
      GradlePsiLiteralMap testInstrumentationRunnerArgumentsElement =
        getProperty("testInstrumentationRunnerArguments", GradlePsiLiteralMap.class);
      if (testInstrumentationRunnerArgumentsElement == null) {
        setPsiElement("testInstrumentationRunnerArguments", element);
      } else {
        GradlePsiLiteralMap elementsToAdd = (GradlePsiLiteralMap)element;
        for (String key : elementsToAdd.getProperties()) {
          GradlePsiElement elementToAdd = elementsToAdd.getPropertyElement(key);
          if (elementToAdd != null) {
            testInstrumentationRunnerArgumentsElement.setPsiElement(key, elementToAdd);
          }
        }
      }
      return;
    }

    if (property.equals("testInstrumentationRunnerArgument")) {
      if (!(element instanceof GradlePsiLiteralList)) {
        return;
      }
      GradlePsiLiteralList gradlePsiLiteralList = (GradlePsiLiteralList)element;
      List<GradlePsiLiteral> elements = gradlePsiLiteralList.getElements();
      if (elements.size() != 2) {
        return;
      }

      String key = elements.get(0).getValue(String.class);
      if (key == null) {
        return;
      }
      GradlePsiLiteral value = elements.get(1);

      GradlePsiLiteralMap testInstrumentationRunnerArgumentsElement =
        getProperty("testInstrumentationRunnerArguments", GradlePsiLiteralMap.class);
      if (testInstrumentationRunnerArgumentsElement == null) {
        testInstrumentationRunnerArgumentsElement = new GradlePsiLiteralMap(this, "testInstrumentationRunnerArguments");
      }
      testInstrumentationRunnerArgumentsElement.setPsiElement(key, value);
      return;
    }

    super.addPsiElement(property, element);
  }
}
