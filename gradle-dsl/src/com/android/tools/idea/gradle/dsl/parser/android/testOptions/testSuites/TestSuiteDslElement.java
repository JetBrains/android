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
import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.android.tools.idea.gradle.dsl.model.android.testOptions.testSuites.TestSuiteModelImpl;
import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.android.AbstractProductFlavorDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslBlockElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslNamedDomainElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.semantics.ExternalToModelMap;
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TestSuiteDslElement extends GradleDslBlockElement implements GradleDslNamedDomainElement {
  public static final PropertiesElementDescription<TestSuiteDslElement> TEST_SUITE =
    new PropertiesElementDescription<>(null,
                                       TestSuiteDslElement.class,
                                       TestSuiteDslElement::new,
                                       "testSuite");

  public static final ImmutableMap<String, PropertiesElementDescription<?>> CHILD_PROPERTIES_ELEMENTS_MAP = Stream.of(new Object[][]{
    {"useJunitEngine", UseJunitEngineDslElement.USE_JUNIT_ENGINE},
    {"targets", TargetsDslElement.TARGETS},
    {"assets", AssetsDslElement.ASSETS},
  }).collect(toImmutableMap(data -> (String)data[0], data -> (PropertiesElementDescription)data[1]));

  @Override
  @NotNull
  public Map<String, PropertiesElementDescription<?>> getChildPropertiesElementsDescriptionMap(
    @NotNull GradleDslNameConverter.Kind kind
  ) {
    return CHILD_PROPERTIES_ELEMENTS_MAP;
  }

  private static final ExternalToModelMap ktsToModelNameMap = Stream.of(new Object[][]{
    {"targetVariants", property, TestSuiteModelImpl.getTARGET_VARIANTS(), VAL},
    {"targetVariants", atLeast(0), TestSuiteModelImpl.getTARGET_VARIANTS(), ADD_AS_LIST},
  }).collect(toModelMap(AbstractProductFlavorDslElement.ktsToModelNameMap));

  private static final ExternalToModelMap groovyToModelNameMap = Stream.of(new Object[][]{
    {"targetVariants", atLeast(0), TestSuiteModelImpl.getTARGET_VARIANTS(), ADD_AS_LIST},
    {"targetVariants", property, TestSuiteModelImpl.getTARGET_VARIANTS(), VAR},
  }).collect(toModelMap(AbstractProductFlavorDslElement.groovyToModelNameMap));

  private static final ExternalToModelMap declarativeToModelNameMap = Stream.of(new Object[][]{
    {"targetVariants", property, TestSuiteModelImpl.getTARGET_VARIANTS(), VAR},
  }).collect(toModelMap(AbstractProductFlavorDslElement.declarativeToModelNameMap));

  @Nullable
  private String methodName;

  public TestSuiteDslElement(@NotNull GradleDslElement parent, @NotNull GradleNameElement name) {
    super(parent, name);
  }

  @Override
  public @NotNull ExternalToModelMap getExternalToModelMap(@NotNull GradleDslNameConverter converter) {
    return getExternalToModelMap(converter, groovyToModelNameMap, ktsToModelNameMap, declarativeToModelNameMap);
  }

  @Override
  public void setMethodName(@Nullable String methodName) {
    this.methodName = methodName;
  }

  @Nullable
  @Override
  public String getMethodName() {
    return methodName;
  }

  @Override
  @Nullable
  public String getAccessMethodName() { return TEST_SUITE.namedObjectAssociatedName; }

  @Override
  public boolean isInsignificantIfEmpty() {
    return false;
  }
}
