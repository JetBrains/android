/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static com.android.tools.idea.gradle.dsl.model.android.TestCoverageModelImpl.JACOCO_VERSION;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.exactly;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.property;
import static com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription.SET;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ModelMapCollector.toModelMap;
import static com.android.tools.idea.gradle.dsl.parser.semantics.PropertySemanticsDescription.VAR;

import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslBlockElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElementSchema;
import com.android.tools.idea.gradle.dsl.parser.semantics.ExternalToModelMap;
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;

public class TestCoverageDslElement extends GradleDslBlockElement {
  public static final PropertiesElementDescription<TestCoverageDslElement> TEST_COVERAGE =
    new PropertiesElementDescription<>("testCoverage",
                                       TestCoverageDslElement.class,
                                       TestCoverageDslElement::new,
                                       TestCoverageDslElementSchema::new);

  public static final ExternalToModelMap ktsToModelMap = Stream.of(new Object[][]{
    {"jacocoVersion", property, JACOCO_VERSION, VAR},
  }).collect(toModelMap());

  public static final ExternalToModelMap groovyToModelMap = Stream.of(new Object[][]{
    {"jacocoVersion", property, JACOCO_VERSION, VAR},
    {"jacocoVersion", exactly(1), JACOCO_VERSION, SET},
  }).collect(toModelMap());

  public static final ExternalToModelMap declarativeToModelMap = Stream.of(new Object[][]{
    {"jacocoVersion", property, JACOCO_VERSION, VAR},
  }).collect(toModelMap());

  @Override
  public @NotNull ExternalToModelMap getExternalToModelMap(@NotNull GradleDslNameConverter converter) {
    return getExternalToModelMap(converter, groovyToModelMap, ktsToModelMap, declarativeToModelMap);
  }

  public TestCoverageDslElement(@NotNull GradleDslElement parent, @NotNull GradleNameElement name) {
    super(parent, name);
  }

  public static final class TestCoverageDslElementSchema extends GradlePropertiesDslElementSchema {
    @NotNull
    @Override
    public ExternalToModelMap getPropertiesInfo(GradleDslNameConverter.Kind kind) {
      return getExternalProperties(kind, groovyToModelMap, ktsToModelMap, declarativeToModelMap);
    }

    @NotNull
    @Override
    public String getAgpDocClass() {
      return "com.android.build.api.dsl.TestCoverage";
    }
  }
}
