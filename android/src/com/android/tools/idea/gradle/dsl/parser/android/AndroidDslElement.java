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

import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.android.tools.idea.gradle.dsl.model.android.AndroidModelImpl;
import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslBlockElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslSimpleExpression;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.groovy.GroovyDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.kotlin.KotlinDslNameConverter;
import com.google.common.collect.ImmutableMap;
import java.util.stream.Stream;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public final class AndroidDslElement extends GradleDslBlockElement {
  @NonNls public static final String ANDROID_BLOCK_NAME = "android";

  @NotNull
  private static final ImmutableMap<String, String> ktsToModelNameMap = Stream.of(new String[][]{
    {"buildToolsVersion", AndroidModelImpl.BUILD_TOOLS_VERSION},
    {"compileSdkVersion", AndroidModelImpl.COMPILE_SDK_VERSION},
    {"defaultPublishConfig", AndroidModelImpl.DEFAULT_PUBLISH_CONFIG},
    {"dynamicFeatures", AndroidModelImpl.DYNAMIC_FEATURES},
    {"flavorDimensions", AndroidModelImpl.FLAVOR_DIMENSIONS},
    {"generatePureSplits", AndroidModelImpl.GENERATE_PURE_SPLITS},
    {"ndkVersion", AndroidModelImpl.NDK_VERSION},
    {"publishNonDefault", AndroidModelImpl.PUBLISH_NON_DEFAULT},
    // TODO(b/142111082): this works to handle the fact that Kotlin does not provide a writeable resourePrefix property, but means that
    //  resolution of the property would fail.
    {"setResourcePrefix", AndroidModelImpl.RESOURCE_PREFIX}
  }).collect(toImmutableMap(data -> data[0], data -> data[1]));

  @NotNull
  private static final ImmutableMap<String, String> groovyToModelNameMap = Stream.of(new String[][]{
    {"buildToolsVersion", AndroidModelImpl.BUILD_TOOLS_VERSION},
    {"compileSdkVersion", AndroidModelImpl.COMPILE_SDK_VERSION},
    {"defaultPublishConfig", AndroidModelImpl.DEFAULT_PUBLISH_CONFIG},
    {"dynamicFeatures", AndroidModelImpl.DYNAMIC_FEATURES},
    {"flavorDimensions", AndroidModelImpl.FLAVOR_DIMENSIONS},
    {"generatePureSplits", AndroidModelImpl.GENERATE_PURE_SPLITS},
    {"ndkVersion", AndroidModelImpl.NDK_VERSION},
    {"publishNonDefault", AndroidModelImpl.PUBLISH_NON_DEFAULT},
    {"resourcePrefix", AndroidModelImpl.RESOURCE_PREFIX}
  }).collect(toImmutableMap(data -> data[0], data -> data[1]));

  @Override
  @NotNull
  public ImmutableMap<String, String> getExternalToModelMap(@NotNull GradleDslNameConverter converter) {
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
      addAsParsedDslExpressionList(AndroidModelImpl.FLAVOR_DIMENSIONS, (GradleDslSimpleExpression)element);
      return;
    }
    super.addParsedElement(element);
    maybeRenameElement(element);
  }

  @Override
  public void setParsedElement(@NotNull GradleDslElement element) {
    super.setParsedElement(element);
    maybeRenameElement(element);
  }
}
