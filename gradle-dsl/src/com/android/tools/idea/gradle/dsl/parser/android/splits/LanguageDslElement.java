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
package com.android.tools.idea.gradle.dsl.parser.android.splits;

import static com.android.tools.idea.gradle.dsl.model.android.splits.LanguageModelImpl.ENABLE;
import static com.android.tools.idea.gradle.dsl.model.android.splits.LanguageModelImpl.INCLUDE;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.atLeast;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.exactly;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.property;
import static com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription.OTHER;
import static com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription.SET;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ModelMapCollector.toModelMap;
import static com.android.tools.idea.gradle.dsl.parser.semantics.PropertySemanticsDescription.VAL;
import static com.android.tools.idea.gradle.dsl.parser.semantics.PropertySemanticsDescription.VAR;

import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslBlockElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.semantics.ExternalToModelMap;
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;

public class LanguageDslElement extends GradleDslBlockElement {
  public static final PropertiesElementDescription<LanguageDslElement> LANGUAGE =
    new PropertiesElementDescription<>("language",
                                       LanguageDslElement.class,
                                       LanguageDslElement::new);

  public static final ExternalToModelMap ktsToModelNameMap = Stream.of(new Object[][]{
    {"isEnable", property, ENABLE, VAR},
    {"include", property, INCLUDE, VAL},
    {"include", atLeast(0), INCLUDE, OTHER},
    {"setInclude", exactly(1), INCLUDE, SET}
  }).collect(toModelMap());

  public static final ExternalToModelMap groovyToModelNameMap = Stream.of(new Object[][]{
    {"enable", property, ENABLE, VAR},
    {"enable", exactly(1), ENABLE, SET},
    {"include", property, INCLUDE, VAR},
    {"include", atLeast(0), INCLUDE, OTHER}
  }).collect(toModelMap());

  public static final ExternalToModelMap declarativeToModelNameMap = Stream.of(new Object[][]{
    {"enable", property, ENABLE, VAR},
    {"include", property, INCLUDE, VAR},
  }).collect(toModelMap());

  @Override
  public @NotNull ExternalToModelMap getExternalToModelMap(@NotNull GradleDslNameConverter converter) {
    return getExternalToModelMap(converter, groovyToModelNameMap, ktsToModelNameMap, declarativeToModelNameMap);
  }

  public LanguageDslElement(@NotNull GradleDslElement parent, @NotNull GradleNameElement name) {
    super(parent, name);
  }

  @Override
  public void addParsedElement(@NotNull GradleDslElement element) {
    String property = element.getName();
    if (property.equals("include")) {
      addToParsedExpressionList(INCLUDE, element);
      return;
    }

    super.addParsedElement(element);
  }
}
