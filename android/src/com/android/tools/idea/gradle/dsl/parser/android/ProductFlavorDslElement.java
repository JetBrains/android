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

import com.android.tools.idea.gradle.dsl.parser.GradleReferenceInjection;
import com.android.tools.idea.gradle.dsl.parser.elements.*;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

import static com.android.tools.idea.gradle.dsl.model.ext.PropertyUtil.followElement;

public final class ProductFlavorDslElement extends AbstractFlavorTypeDslElement {

  public ProductFlavorDslElement(@NotNull GradleDslElement parent, @NotNull GradleNameElement name) {
    super(parent, name);
  }

  @Override
  public void addParsedElement(@NotNull GradleDslElement element) {
    String property = element.getName();
    if (property.equals("resConfigs") || property.equals("resConfig")) {
      addToParsedExpressionList("resConfigs", element);
      return;
    }

    if (property.equals("testInstrumentationRunnerArguments")) {
      // This deals with references to maps.
      GradleDslElement oldElement = element;
      if (element instanceof GradleDslLiteral && ((GradleDslLiteral)element).isReference()) {
        element = followElement((GradleDslLiteral) element);
      }
      if (!(element instanceof GradleDslExpressionMap)) {
        return;
      }

      GradleDslExpressionMap testInstrumentationRunnerArgumentsElement =
        getPropertyElement("testInstrumentationRunnerArguments", GradleDslExpressionMap.class);
      if (testInstrumentationRunnerArgumentsElement == null) {
        testInstrumentationRunnerArgumentsElement =
          new GradleDslExpressionMap(this, element.getPsiElement(), oldElement.getNameElement(), true);
        setParsedElement(testInstrumentationRunnerArgumentsElement);
      }


      testInstrumentationRunnerArgumentsElement.setPsiElement(element.getPsiElement());
      GradleDslExpressionMap elementsToAdd = (GradleDslExpressionMap)element;
      for (Map.Entry<String, GradleDslElement> entry : elementsToAdd.getPropertyElements().entrySet()) {
        testInstrumentationRunnerArgumentsElement.setParsedElement(entry.getValue());
      }
      return;
    }

    if (property.equals("testInstrumentationRunnerArgument")) {
      if (!(element instanceof GradleDslExpressionList)) {
        return;
      }
      GradleDslExpressionList gradleDslExpressionList = (GradleDslExpressionList)element;
      List<GradleDslSimpleExpression> elements = gradleDslExpressionList.getSimpleExpressions();
      if (elements.size() != 2) {
        return;
      }

      String key = elements.get(0).getValue(String.class);
      if (key == null) {
        return;
      }
      GradleDslSimpleExpression value = elements.get(1);
      // Set the name element of the value to be the previous element.
      value.getNameElement().commitNameChange(elements.get(0).getPsiElement(), this.getDslFile().getWriter());

      GradleDslExpressionMap testInstrumentationRunnerArgumentsElement =
        getPropertyElement("testInstrumentationRunnerArguments", GradleDslExpressionMap.class);
      if (testInstrumentationRunnerArgumentsElement == null) {
        testInstrumentationRunnerArgumentsElement =
          new GradleDslExpressionMap(this, GradleNameElement.create("testInstrumentationRunnerArguments"));
      }
      testInstrumentationRunnerArgumentsElement.setParsedElement(value);
      return;
    }

    super.addParsedElement(element);
  }
}
