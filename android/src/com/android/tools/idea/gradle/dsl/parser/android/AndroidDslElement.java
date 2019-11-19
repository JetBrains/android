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

import static com.android.tools.idea.gradle.dsl.model.android.AndroidModelImpl.*;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.*;
import static com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription.*;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ModelMapCollector.toModelMap;
import static com.android.tools.idea.gradle.dsl.parser.semantics.PropertySemanticsDescription.*;

import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslBlockElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslSimpleExpression;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.groovy.GroovyDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.kotlin.KotlinDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.semantics.SemanticsDescription;
import com.google.common.collect.ImmutableMap;
import java.util.stream.Stream;
import kotlin.Pair;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public final class AndroidDslElement extends GradleDslBlockElement {
  @NonNls public static final String ANDROID_BLOCK_NAME = "android";

  @NotNull
  private static final ImmutableMap<Pair<String,Integer>, Pair<String, SemanticsDescription>> ktsToModelNameMap = Stream.of(new Object[][]{
    {"buildToolsVersion", property, BUILD_TOOLS_VERSION, VAR},
    {"buildToolsVersion", exactly(1), BUILD_TOOLS_VERSION, SET},
    {"compileSdkVersion", property, COMPILE_SDK_VERSION, VAR}, // TODO(xof): type handling of this is tricky
    {"compileSdkVersion", exactly(1), COMPILE_SDK_VERSION, SET},
    {"defaultPublishConfig", property, DEFAULT_PUBLISH_CONFIG, VAR},
    {"defaultPublishConfig", exactly(1), DEFAULT_PUBLISH_CONFIG, SET},
    {"dynamicFeatures", property, DYNAMIC_FEATURES, VAR},
    {"flavorDimensions", atLeast(0), FLAVOR_DIMENSIONS, OTHER}, // SETN: sets the property to the list of varargs arguments
    {"generatePureSplits", property, GENERATE_PURE_SPLITS, VAR},
    {"generatePureSplits", exactly(1), GENERATE_PURE_SPLITS, SET},
    {"ndkVersion", property, NDK_VERSION, VAR},
    {"setPublishNonDefault", exactly(1), PUBLISH_NON_DEFAULT, SET},
    {"resourcePrefix", property, RESOURCE_PREFIX, VAL}, // no setResourcePrefix: not a VAR
    {"resourcePrefix", exactly(1), RESOURCE_PREFIX, SET}
  }).collect(toModelMap());

  @NotNull
  private static final ImmutableMap<Pair<String,Integer>, Pair<String,SemanticsDescription>> groovyToModelNameMap = Stream.of(new Object[][]{
    {"buildToolsVersion", property, BUILD_TOOLS_VERSION, VAR},
    {"buildToolsVersion", exactly(1), BUILD_TOOLS_VERSION, SET},
    {"compileSdkVersion", property, COMPILE_SDK_VERSION, VAR},
    {"compileSdkVersion", exactly(1), COMPILE_SDK_VERSION, SET},
    {"defaultPublishConfig", property, DEFAULT_PUBLISH_CONFIG, VAR},
    {"defaultPublishConfig", exactly(1), DEFAULT_PUBLISH_CONFIG, SET},
    {"dynamicFeatures", property, DYNAMIC_FEATURES, VAR},
    {"flavorDimensions", atLeast(0), FLAVOR_DIMENSIONS, OTHER},
    {"generatePureSplits", property, GENERATE_PURE_SPLITS, VAR},
    {"generatePureSplits", exactly(1), GENERATE_PURE_SPLITS, SET},
    {"ndkVersion", property, NDK_VERSION, VAR},
    {"ndkVersion", exactly(1), NDK_VERSION, SET},
    {"publishNonDefault", property, PUBLISH_NON_DEFAULT, VAR},
    {"publishNonDefault", exactly(1), PUBLISH_NON_DEFAULT, SET},
    {"resourcePrefix", property, RESOURCE_PREFIX, VAL},
    {"resourcePrefix", exactly(1), RESOURCE_PREFIX, SET}
  }).collect(toModelMap());

  @Override
  @NotNull
  public ImmutableMap<Pair<String,Integer>, Pair<String,SemanticsDescription>> getExternalToModelMap(@NotNull GradleDslNameConverter converter) {
    if (converter instanceof KotlinDslNameConverter) {
      return ktsToModelNameMap;
    }
    else if (converter instanceof GroovyDslNameConverter) {
      return groovyToModelNameMap;
    }
    else {
      return super.getExternalToModelMap(converter);
    }
  }

  public AndroidDslElement(@NotNull GradleDslElement parent) {
    super(parent, GradleNameElement.create(ANDROID_BLOCK_NAME));
  }

  @Override
  public void addParsedElement(@NotNull GradleDslElement element) {
    if (element.getName().equals("flavorDimensions") && element instanceof GradleDslSimpleExpression) {
      addAsParsedDslExpressionList(FLAVOR_DIMENSIONS, (GradleDslSimpleExpression)element);
      return;
    }
    super.addParsedElement(element);
  }
}
