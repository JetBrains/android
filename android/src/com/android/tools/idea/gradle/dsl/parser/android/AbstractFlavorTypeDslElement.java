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
package com.android.tools.idea.gradle.dsl.parser.android;

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.android.tools.idea.gradle.dsl.model.android.FlavorTypeModelImpl.*;

import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.elements.*;
import com.android.tools.idea.gradle.dsl.parser.groovy.GroovyDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.kotlin.KotlinDslNameConverter;
import com.google.common.collect.ImmutableMap;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;

/**
 * Common base class for {@link BuildTypeDslElement} and {@link AbstractProductFlavorDslElement}.
 */
public abstract class AbstractFlavorTypeDslElement extends GradleDslBlockElement {
  @NotNull
  public static final ImmutableMap<String, String> ktsToModelNameMap = Stream.of(new String[][]{
    {"applicationIdSuffix", APPLICATION_ID_SUFFIX},
    {"buildConfigField", BUILD_CONFIG_FIELD},
    {"consumerProguardFiles", CONSUMER_PROGUARD_FILES},
    {"manifestPlaceholders", MANIFEST_PLACEHOLDERS},
    {"matchingFallbacks", MATCHING_FALLBACKS},
    {"multiDexEnabled", MULTI_DEX_ENABLED},
    {"multiDexKeepFile", MULTI_DEX_KEEP_FILE},
    {"multiDexKeepProguard", MULTI_DEX_KEEP_PROGUARD},
    {"proguardFiles", PROGUARD_FILES},
    {"resValue", RES_VALUE},
    {"signingConfig", SIGNING_CONFIG},
    {"useJack", USE_JACK},
    {"versionNameSuffix", VERSION_NAME_SUFFIX}
  })
    .collect(toImmutableMap(data -> data[0], data-> data[1]));

  @NotNull
  public static final ImmutableMap<String, String> groovyToModelNameMap = Stream.of(new String[][]{
    {"applicationIdSuffix", APPLICATION_ID_SUFFIX},
    {"buildConfigField", BUILD_CONFIG_FIELD},
    {"consumerProguardFiles", CONSUMER_PROGUARD_FILES},
    {"manifestPlaceholders", MANIFEST_PLACEHOLDERS},
    {"matchingFallbacks", MATCHING_FALLBACKS},
    {"multiDexEnabled", MULTI_DEX_ENABLED},
    {"multiDexKeepFile", MULTI_DEX_KEEP_FILE},
    {"multiDexKeepProguard", MULTI_DEX_KEEP_PROGUARD},
    {"proguardFiles", PROGUARD_FILES},
    {"resValue", RES_VALUE},
    {"signingConfig", SIGNING_CONFIG},
    {"useJack", USE_JACK},
    {"versionNameSuffix", VERSION_NAME_SUFFIX}
  })
    .collect(toImmutableMap(data -> data[0], data-> data[1]));

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


  protected AbstractFlavorTypeDslElement(@NotNull GradleDslElement parent, @NotNull GradleNameElement name) {
    super(parent, name);
  }

  @Override
  public void addParsedElement(@NotNull GradleDslElement element) {
    String property = element.getName();

    // setProguardFiles has the same name in Groovy and Kotlin
    if (property.equals("setProguardFiles")) {
      // Clear the property since setProguardFiles overwrites these.
      removeProperty(PROGUARD_FILES);
      addToParsedExpressionList(PROGUARD_FILES, element);
      return;
    }

    // setConsumerProguardFiles has the same name in Groovy and Kotlin
    if (property.equals("setConsumerProguardFiles")) {
      removeProperty(CONSUMER_PROGUARD_FILES);
      addToParsedExpressionList(CONSUMER_PROGUARD_FILES, element);
      return;
    }

    // proguardFiles and proguardFile have the same name in Groovy and Kotlin
    if (property.equals("proguardFiles") || property.equals("proguardFile")) {
      addToParsedExpressionList(PROGUARD_FILES, element);
      return;
    }

    // consumerProguardFiles and consumerProguardFile have the same name in Groovy and Kotlin
    if (property.equals("consumerProguardFiles") || property.equals("consumerProguardFile")) {
      addToParsedExpressionList(CONSUMER_PROGUARD_FILES, element);
      return;
    }

    super.addParsedElement(element);
  }
}
