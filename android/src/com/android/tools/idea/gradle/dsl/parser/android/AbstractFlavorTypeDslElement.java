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

import com.android.tools.idea.gradle.dsl.model.android.AndroidModelImpl;
import com.android.tools.idea.gradle.dsl.model.android.FlavorTypeModelImpl;
import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.elements.*;
import com.android.tools.idea.gradle.dsl.parser.groovy.GroovyDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.kotlin.KotlinDslNameConverter;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Common base class for {@link BuildTypeDslElement} and {@link ProductFlavorDslElement}.
 */
public abstract class AbstractFlavorTypeDslElement extends GradleDslBlockElement {
  @NotNull
  public static final ImmutableMap<String, String> ktsToModelNameMap = Stream.of(new String[][]{
    {"applicationIdSuffix", FlavorTypeModelImpl.APPLICATION_ID_SUFFIX},
    {"buildConfigField", FlavorTypeModelImpl.BUILD_CONFIG_FIELD},
    {"consumerProguardFiles", FlavorTypeModelImpl.CONSUMER_PROGUARD_FILES},
    {"manifestPlaceholders", FlavorTypeModelImpl.MANIFEST_PLACEHOLDERS},
    {"matchingFallbacks", FlavorTypeModelImpl.MATCHING_FALLBACKS},
    {"multiDexEnabled", FlavorTypeModelImpl.MULTI_DEX_ENABLED},
    {"multiDexKeepFile", FlavorTypeModelImpl.MULTI_DEX_KEEP_FILE},
    {"multiDexKeepProguard", FlavorTypeModelImpl.MULTI_DEX_KEEP_PROGUARD},
    {"proguardFiles", FlavorTypeModelImpl.PROGUARD_FILES},
    {"resValue", FlavorTypeModelImpl.RES_VALUE},
    {"signingConfig", FlavorTypeModelImpl.SIGNING_CONFIG},
    {"useJack", FlavorTypeModelImpl.USE_JACK},
    {"versionNameSuffix", FlavorTypeModelImpl.VERSION_NAME_SUFFIX}
  })
    .collect(toImmutableMap(data -> data[0], data-> data[1]));

  @NotNull
  public static final ImmutableMap<String, String> groovyToModelNameMap = Stream.of(new String[][]{
    {"applicationIdSuffix", FlavorTypeModelImpl.APPLICATION_ID_SUFFIX},
    {"buildConfigField", FlavorTypeModelImpl.BUILD_CONFIG_FIELD},
    {"consumerProguardFiles", FlavorTypeModelImpl.CONSUMER_PROGUARD_FILES},
    {"manifestPlaceholders", FlavorTypeModelImpl.MANIFEST_PLACEHOLDERS},
    {"matchingFallbacks", FlavorTypeModelImpl.MATCHING_FALLBACKS},
    {"multiDexEnabled", FlavorTypeModelImpl.MULTI_DEX_ENABLED},
    {"multiDexKeepFile", FlavorTypeModelImpl.MULTI_DEX_KEEP_FILE},
    {"multiDexKeepProguard", FlavorTypeModelImpl.MULTI_DEX_KEEP_PROGUARD},
    {"proguardFiles", FlavorTypeModelImpl.PROGUARD_FILES},
    {"resValue", FlavorTypeModelImpl.RES_VALUE},
    {"signingConfig", FlavorTypeModelImpl.SIGNING_CONFIG},
    {"useJack", FlavorTypeModelImpl.USE_JACK},
    {"versionNameSuffix", FlavorTypeModelImpl.VERSION_NAME_SUFFIX}
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

  // Stores the method name of the block used in the KTS file. Ex: for the block with the name getByName("release"), methodName will be
  // getByName.
  @Nullable
  private String methodName;

  protected AbstractFlavorTypeDslElement(@NotNull GradleDslElement parent, @NotNull GradleNameElement name) {
    super(parent, name);
  }

  protected AbstractFlavorTypeDslElement(@NotNull GradleDslElement parent, @NotNull GradleNameElement name, @NotNull String methodName) {
    super(parent, name);
    this.methodName = methodName;
  }

  public void setMethodName(String methodName) {
    this.methodName = methodName;
  }

  @Nullable
  public String getMethodName() {
    return  methodName;
  }

  protected void maybeRenameElement(@NotNull GradleDslElement element) {
    String name = element.getName();
    Map<String,String> nameMapper = getExternalToModelMap(element.getDslFile().getParser());
    if (nameMapper.containsKey(name)) {
      String newName = nameMapper.get(name);
      // we rename the GradleNameElement, and not the element directly, because this renaming is not about renaming the property
      // but about providing a canonical model name for a thing.
      element.getNameElement().canonize(newName); // NOTYPO
    }
  }

  @Override
  public void addParsedElement(@NotNull GradleDslElement element) {
    String property = element.getName();

    // setProguardFiles has the same name in Groovy and Kotlin
    if (property.equals("setProguardFiles")) {
      // Clear the property since setProguardFiles overwrites these.
      removeProperty(FlavorTypeModelImpl.PROGUARD_FILES);
      addToParsedExpressionList(FlavorTypeModelImpl.PROGUARD_FILES, element);
      return;
    }

    // setConsumerProguardFiles has the same name in Groovy and Kotlin
    if (property.equals("setConsumerProguardFiles")) {
      removeProperty(FlavorTypeModelImpl.CONSUMER_PROGUARD_FILES);
      addToParsedExpressionList(FlavorTypeModelImpl.CONSUMER_PROGUARD_FILES, element);
      return;
    }

    // proguardFiles and proguardFile have the same name in Groovy and Kotlin
    if (property.equals("proguardFiles") || property.equals("proguardFile")) {
      addToParsedExpressionList(FlavorTypeModelImpl.PROGUARD_FILES, element);
      return;
    }

    // consumerProguardFiles and consumerProguardFile have the same name in Groovy and Kotlin
    if (property.equals("consumerProguardFiles") || property.equals("consumerProguardFile")) {
      addToParsedExpressionList(FlavorTypeModelImpl.CONSUMER_PROGUARD_FILES, element);
      return;
    }

    super.addParsedElement(element);
  }

  @Override
  public boolean isInsignificantIfEmpty() {
    // defaultConfig is special in that is can be deleted if it is empty.
    return myName.name().equals(AndroidModelImpl.DEFAULT_CONFIG);
  }
}
