/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.parser.android.testOptions.testSuites;

import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.atLeast;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.property;
import static com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription.ADD_AS_LIST;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ModelMapCollector.toModelMap;
import static com.android.tools.idea.gradle.dsl.parser.semantics.PropertySemanticsDescription.VAL;
import static com.android.tools.idea.gradle.dsl.parser.semantics.PropertySemanticsDescription.VAR;

import com.android.tools.idea.gradle.dsl.model.android.testOptions.testSuites.UseJunitEngineModelImpl;
import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslBlockElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.semantics.ExternalToModelMap;
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;

public class UseJunitEngineDslElement extends GradleDslBlockElement {
  public static final PropertiesElementDescription<UseJunitEngineDslElement> USE_JUNIT_ENGINE =
    new PropertiesElementDescription<>("useJunitEngine",
                                       UseJunitEngineDslElement.class,
                                       UseJunitEngineDslElement::new);

  public static final ExternalToModelMap ktsToModelNameMap = Stream.of(new Object[][]{
    {"inputs", property, UseJunitEngineModelImpl.INPUTS, VAL},
    {"inputs", atLeast(0), UseJunitEngineModelImpl.INPUTS, ADD_AS_LIST},
    {"includeEngines", property, UseJunitEngineModelImpl.INCLUDED_ENGINES, VAL},
    {"includeEngines", atLeast(0), UseJunitEngineModelImpl.INCLUDED_ENGINES, ADD_AS_LIST},
  }).collect(toModelMap());

  public static final ExternalToModelMap groovyToModelNameMap = Stream.of(new Object[][]{
    {"inputs", atLeast(0), UseJunitEngineModelImpl.INPUTS, ADD_AS_LIST},
    {"inputs", property, UseJunitEngineModelImpl.INPUTS, VAR},
    {"includeEngines", atLeast(0), UseJunitEngineModelImpl.INCLUDED_ENGINES, ADD_AS_LIST},
    {"includeEngines", property, UseJunitEngineModelImpl.INCLUDED_ENGINES, VAR},
  }).collect(toModelMap());

  public static final ExternalToModelMap declarativeToModelNameMap = Stream.of(new Object[][]{
    {"inputs", property, UseJunitEngineModelImpl.INPUTS, VAR},
    {"includeEngines", property, UseJunitEngineModelImpl.INCLUDED_ENGINES, VAR},
  }).collect(toModelMap());

  public UseJunitEngineDslElement(@NotNull GradleDslElement parent, @NotNull GradleNameElement name) {
    super(parent, name);
  }

  @Override
  public @NotNull ExternalToModelMap getExternalToModelMap(@NotNull GradleDslNameConverter converter) {
    return getExternalToModelMap(converter, groovyToModelNameMap, ktsToModelNameMap, declarativeToModelNameMap);
  }

  @Override
  public boolean isInsignificantIfEmpty() {
    return false;
  }
}