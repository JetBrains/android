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

import static com.android.tools.idea.gradle.dsl.api.ext.PropertyType.REGULAR;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ModelMapCollector.toModelMap;
import static com.android.tools.idea.gradle.dsl.model.android.FlavorTypeModelImpl.*;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.*;
import static com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription.*;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ModelSemanticsDescription.CREATE_WITH_VALUE;
import static com.android.tools.idea.gradle.dsl.parser.semantics.PropertySemanticsDescription.*;

import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.elements.*;
import com.android.tools.idea.gradle.dsl.parser.semantics.ExternalToModelMap;
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelEffectDescription;
import com.android.tools.idea.gradle.dsl.parser.semantics.VersionConstraint;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;

/**
 * Common base class for {@link BuildTypeDslElement} and {@link AbstractProductFlavorDslElement}.
 */
public abstract class AbstractFlavorTypeDslElement extends GradleDslBlockElement {
  @NotNull
  public static final ExternalToModelMap ktsToModelNameMap = Stream.of(new Object[][]{
    {"applicationIdSuffix", property, APPLICATION_ID_SUFFIX, VAR},
    {"setApplicationIdSuffix", exactly(1), APPLICATION_ID_SUFFIX, SET},
    {"buildConfigField", exactly(3), BUILD_CONFIG_FIELD, OTHER}, // ADD: add argument list as property to Dsl
    {"consumerProguardFiles", atLeast(0), CONSUMER_PROGUARD_FILES, AUGMENT_LIST},
    {"consumerProguardFile", exactly(1), CONSUMER_PROGUARD_FILES, AUGMENT_LIST},
    {"setConsumerProguardFiles", exactly(1), CONSUMER_PROGUARD_FILES, CLEAR_AND_AUGMENT_LIST},
    {"manifestPlaceholders", property, MANIFEST_PLACEHOLDERS, VAR_BUT_DO_NOT_USE_FOR_WRITING_IN_KTS, VersionConstraint.agpBefore("4.1.0")},
    {"manifestPlaceholders", property, MANIFEST_PLACEHOLDERS, VAL, VersionConstraint.agpFrom("4.1.0")},
    {"setManifestPlaceholders", exactly(1), MANIFEST_PLACEHOLDERS, SET, VersionConstraint.agpBefore("8.0.0")},
    {"matchingFallbacks", property, MATCHING_FALLBACKS, VAL},
    {"setMatchingFallbacks", atLeast(1), MATCHING_FALLBACKS, CLEAR_AND_AUGMENT_LIST, VersionConstraint.agpBefore("8.0.0")},
    {"multiDexEnabled", property, MULTI_DEX_ENABLED, VAR},
    {"setMultiDexEnabled", exactly(1), MULTI_DEX_ENABLED, SET},
    {"multiDexKeepFile", property, MULTI_DEX_KEEP_FILE, VAR},
    {"multiDexKeepProguard", property, MULTI_DEX_KEEP_PROGUARD, VAR},
    {"proguardFiles", atLeast(0), PROGUARD_FILES, AUGMENT_LIST},
    {"proguardFile", exactly(1), PROGUARD_FILES, AUGMENT_LIST},
    {"setProguardFiles", exactly(1), PROGUARD_FILES, CLEAR_AND_AUGMENT_LIST},
    {"resValue", exactly(3), RES_VALUE, OTHER},
    {"signingConfig", property, SIGNING_CONFIG, VAR},
    {"useJack", property, USE_JACK, VAR}, // actually deprecated / nonexistent
    {"useJack", exactly(1), USE_JACK, SET}, // see above
    {"versionNameSuffix", property, VERSION_NAME_SUFFIX, VAR},
    {"setVersionNameSuffix", exactly(1), VERSION_NAME_SUFFIX, SET}
  })
    .collect(toModelMap());

  @NotNull
  public static final ExternalToModelMap groovyToModelNameMap = Stream.of(new Object[][]{
    {"applicationIdSuffix", property, APPLICATION_ID_SUFFIX, VAR},
    {"applicationIdSuffix", exactly(1), APPLICATION_ID_SUFFIX, SET},
    {"buildConfigField", exactly(3), BUILD_CONFIG_FIELD, OTHER},
    {"consumerProguardFiles", atLeast(0), CONSUMER_PROGUARD_FILES, AUGMENT_LIST},
    {"consumerProguardFiles", property, CONSUMER_PROGUARD_FILES, VAR},
    {"consumerProguardFile", exactly(1), CONSUMER_PROGUARD_FILES, AUGMENT_LIST},
    {"setConsumerProguardFiles", exactly(1), CONSUMER_PROGUARD_FILES, CLEAR_AND_AUGMENT_LIST},
    {"manifestPlaceholders", property, MANIFEST_PLACEHOLDERS, VAR},
    {"manifestPlaceholders", exactly(1), MANIFEST_PLACEHOLDERS, SET},
    // TODO(xof): if we want to support the method call syntax (as opposed to the application statement syntax) of
    //  setManifestPlaceholders([a: 'b']) more properly, this SET could become CLEAR_AND_AUGMENT_MAP (and we would need something a bit
    //  like mungeElementsForAddToParsedElementList() in addToParsedElementMap).
    {"setManifestPlaceholders", exactly(1), MANIFEST_PLACEHOLDERS, SET, VersionConstraint.agpBefore("8.0.0")},
    {"matchingFallbacks", property, MATCHING_FALLBACKS, VAR},
    {"setMatchingFallbacks", atLeast(1), MATCHING_FALLBACKS, CLEAR_AND_AUGMENT_LIST, VersionConstraint.agpBefore("8.0.0")},
    {"multiDexEnabled", property, MULTI_DEX_ENABLED, VAR},
    {"multiDexEnabled", exactly(1), MULTI_DEX_ENABLED, SET},
    {"multiDexKeepFile", exactly(1), MULTI_DEX_KEEP_FILE, SET},
    {"multiDexKeepProguard", exactly(1), MULTI_DEX_KEEP_PROGUARD, SET},
    {"proguardFiles", atLeast(0), PROGUARD_FILES, AUGMENT_LIST},
    {"proguardFiles", property, PROGUARD_FILES, VAR},
    {"proguardFile", exactly(1), PROGUARD_FILES, AUGMENT_LIST},
    {"setProguardFiles", exactly(1), PROGUARD_FILES, CLEAR_AND_AUGMENT_LIST},
    {"resValue", exactly(3), RES_VALUE, OTHER},
    {"signingConfig", property, SIGNING_CONFIG, VAR},
    {"signingConfig", exactly(1), SIGNING_CONFIG, SET},
    {"useJack", property, USE_JACK, VAR},
    {"useJack", exactly(1), USE_JACK, SET},
    {"versionNameSuffix", property, VERSION_NAME_SUFFIX, VAR},
    {"versionNameSuffix", exactly(1), VERSION_NAME_SUFFIX, SET}
  })
    .collect(toModelMap());

  // TODO still need API review
  @NotNull
  public static final ExternalToModelMap declarativeToModelNameMap = Stream.of(new Object[][]{
      {"applicationIdSuffix", property, APPLICATION_ID_SUFFIX, VAR},
      {"buildConfigField", exactly(3), BUILD_CONFIG_FIELD, AUGMENT_LIST},
      {"consumerProguardFiles", property, CONSUMER_PROGUARD_FILES, VAR},
      {"manifestPlaceholders", property, MANIFEST_PLACEHOLDERS, VAR},
      {"matchingFallbacks", property, MATCHING_FALLBACKS, VAR},
      {"multiDexEnabled", property, MULTI_DEX_ENABLED, VAR},
      {"multiDexKeepFile", property, MULTI_DEX_KEEP_FILE, VAR},
      {"multiDexKeepProguard", property, MULTI_DEX_KEEP_PROGUARD, VAR},
      {"proguardFiles", property, PROGUARD_FILES, VAR},
      {"resValue", exactly(3), RES_VALUE, AUGMENT_LIST},
      {"signingConfig", property, SIGNING_CONFIG, VAR},
      {"useJack", property, USE_JACK, VAR},
      {"versionNameSuffix", property, VERSION_NAME_SUFFIX, VAR},
    })
    .collect(toModelMap());

  @Override
  public @NotNull ExternalToModelMap getExternalToModelMap(@NotNull GradleDslNameConverter converter) {
    return getExternalToModelMap(converter, groovyToModelNameMap, ktsToModelNameMap, declarativeToModelNameMap);
  }

  protected AbstractFlavorTypeDslElement(@NotNull GradleDslElement parent, @NotNull GradleNameElement name) {
    super(parent, name);
    GradleDslExpressionMap manifestPlaceholders = new GradleDslExpressionMap(this, GradleNameElement.fake(MANIFEST_PLACEHOLDERS.name));
    ModelEffectDescription effect = new ModelEffectDescription(MANIFEST_PLACEHOLDERS, CREATE_WITH_VALUE);
    manifestPlaceholders.setModelEffect(effect);
    manifestPlaceholders.setElementType(REGULAR);
    addDefaultProperty(manifestPlaceholders);
  }
}
