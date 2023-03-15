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
import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslBlockElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.semantics.ExternalToModelMap;
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription;
import com.android.tools.idea.gradle.dsl.parser.semantics.VersionConstraint;
import com.google.common.collect.ImmutableMap;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;

public final class AndroidDslElement extends GradleDslBlockElement {
  public static final PropertiesElementDescription<AndroidDslElement> ANDROID =
    new PropertiesElementDescription<>("android", AndroidDslElement.class, AndroidDslElement::new);

  public static final ImmutableMap<String,PropertiesElementDescription> CHILD_PROPERTIES_ELEMENTS_MAP = Stream.of(new Object[][]{
    {"aaptOptions", AaptOptionsDslElement.AAPT_OPTIONS},
    {"androidResources", AndroidResourcesDslElement.ANDROID_RESOURCES},
    {"adbOptions", AdbOptionsDslElement.ADB_OPTIONS},
    {"buildFeatures", BuildFeaturesDslElement.BUILD_FEATURES},
    {"buildTypes", BuildTypesDslElement.BUILD_TYPES},
    {"compileOptions", CompileOptionsDslElement.COMPILE_OPTIONS},
    {"composeOptions", ComposeOptionsDslElement.COMPOSE_OPTIONS},
    {"dataBinding", DataBindingDslElement.DATA_BINDING},
    {"defaultConfig", DefaultConfigDslElement.DEFAULT_CONFIG},
    {"dependenciesInfo", DependenciesInfoDslElement.DEPENDENCIES_INFO},
    {"dexOptions", DexOptionsDslElement.DEX_OPTIONS},
    {"externalNativeBuild", ExternalNativeBuildDslElement.EXTERNAL_NATIVE_BUILD},
    {"installation", InstallationDslElement.INSTALLATION},
    {"jacoco", JacocoDslElement.JACOCO},
    {"kotlinOptions", KotlinOptionsDslElement.KOTLIN_OPTIONS},
    {"lint", LintDslElement.LINT},
    {"lintOptions", LintOptionsDslElement.LINT_OPTIONS},
    {"packaging", PackagingOptionsDslElement.PACKAGING}, // TODD(xof): since AGP version ? (needs infrastructure)
    {"packagingOptions", PackagingOptionsDslElement.PACKAGING_OPTIONS},
    {"productFlavors", ProductFlavorsDslElement.PRODUCT_FLAVORS},
    {"signingConfigs", SigningConfigsDslElement.SIGNING_CONFIGS},
    {"sourceSets", SourceSetsDslElement.SOURCE_SETS},
    {"splits", SplitsDslElement.SPLITS},
    {"testCoverage", TestCoverageDslElement.TEST_COVERAGE},
    {"testOptions", TestOptionsDslElement.TEST_OPTIONS},
    {"viewBinding", ViewBindingDslElement.VIEW_BINDING}
  }).collect(toImmutableMap(data -> (String) data[0], data -> (PropertiesElementDescription) data[1]));

  @Override
  @NotNull
  protected ImmutableMap<String,PropertiesElementDescription> getChildPropertiesElementsDescriptionMap() {
    return CHILD_PROPERTIES_ELEMENTS_MAP;
  }

  private static final ExternalToModelMap ktsToModelNameMap = Stream.of(new Object[][]{
    {"aidlPackagedList", property, AIDL_PACKAGED_LIST, VAL}, // TODO(xof): was aidlPackageW***eL**t, add support for old version
    {"assetPacks", property, ASSET_PACKS, VAL}, // TODO(xof): was VAR some time ago
    {"buildToolsVersion", property, BUILD_TOOLS_VERSION, VAR},
    {"buildToolsVersion", exactly(1), BUILD_TOOLS_VERSION, SET, VersionConstraint.agpBefore("8.0.0")},
    {"compileSdk", property, COMPILE_SDK_VERSION, VAR, VersionConstraint.agpFrom("4.1.0")},
    {"compileSdkPreview", property, COMPILE_SDK_VERSION, VAR, VersionConstraint.agpFrom("4.1.0")},
    {"compileSdkVersion", property, COMPILE_SDK_VERSION, VAR_BUT_DO_NOT_USE_FOR_WRITING_IN_KTS, VersionConstraint.agpBefore("8.0.0")},
    {"compileSdkVersion", exactly(1), COMPILE_SDK_VERSION, SET, VersionConstraint.agpBefore("8.0.0")},
    {"defaultPublishConfig", property, DEFAULT_PUBLISH_CONFIG, VAR},
    {"defaultPublishConfig", exactly(1), DEFAULT_PUBLISH_CONFIG, SET},
    {"dynamicFeatures", property, DYNAMIC_FEATURES, VAR, VersionConstraint.agpBefore("4.1.0")},
    {"dynamicFeatures", property, DYNAMIC_FEATURES, VAL, VersionConstraint.agpFrom("4.1.0")},
    {"flavorDimensions", property, FLAVOR_DIMENSIONS, VAL, VersionConstraint.agpFrom("4.1.0")},
    {"flavorDimensions", atLeast(0), FLAVOR_DIMENSIONS, ADD_AS_LIST, VersionConstraint.agpBefore("8.0.0")},
    {"generatePureSplits", property, GENERATE_PURE_SPLITS, VAR},
    {"generatePureSplits", exactly(1), GENERATE_PURE_SPLITS, SET},
    {"namespace", property, NAMESPACE, VAR},
    {"ndkVersion", property, NDK_VERSION, VAR},
    {"setPublishNonDefault", exactly(1), PUBLISH_NON_DEFAULT, SET},
    {"resourcePrefix", property, RESOURCE_PREFIX, VAL}, // no setResourcePrefix: not a VAR
    {"resourcePrefix", exactly(1), RESOURCE_PREFIX, SET},
    {"targetProjectPath", property, TARGET_PROJECT_PATH, VAR},
    {"targetProjectPath", exactly(1), TARGET_PROJECT_PATH, SET},
    {"testNamespace", property, TEST_NAMESPACE, VAR},
  }).collect(toModelMap());

  private static final ExternalToModelMap groovyToModelNameMap = Stream.of(new Object[][]{
    {"aidlPackagedList", property, AIDL_PACKAGED_LIST, VAL},
    {"assetPacks", property, ASSET_PACKS, VAR},
    {"buildToolsVersion", property, BUILD_TOOLS_VERSION, VAR},
    {"buildToolsVersion", exactly(1), BUILD_TOOLS_VERSION, SET},
    {"compileSdk", property, COMPILE_SDK_VERSION, VAR, VersionConstraint.agpFrom("4.1.0")},
    {"compileSdk", exactly(1), COMPILE_SDK_VERSION, SET, VersionConstraint.agpFrom("4.1.0")},
    {"compileSdkPreview", property, COMPILE_SDK_VERSION, VAR, VersionConstraint.agpFrom("4.1.0")},
    {"compileSdkPreview", exactly(1), COMPILE_SDK_VERSION, SET, VersionConstraint.agpFrom("4.1.0")},
    {"compileSdkVersion", property, COMPILE_SDK_VERSION, VAR, VersionConstraint.agpBefore("8.0.0")},
    {"compileSdkVersion", exactly(1), COMPILE_SDK_VERSION, SET, VersionConstraint.agpBefore("8.0.0")},
    {"defaultPublishConfig", property, DEFAULT_PUBLISH_CONFIG, VAR},
    {"defaultPublishConfig", exactly(1), DEFAULT_PUBLISH_CONFIG, SET},
    {"dynamicFeatures", property, DYNAMIC_FEATURES, VAR},
    {"flavorDimensions", atLeast(0), FLAVOR_DIMENSIONS, ADD_AS_LIST},
    {"flavorDimensions", property, FLAVOR_DIMENSIONS, VAR, VersionConstraint.agpFrom("4.1.0")},
    {"generatePureSplits", property, GENERATE_PURE_SPLITS, VAR},
    {"generatePureSplits", exactly(1), GENERATE_PURE_SPLITS, SET},
    {"namespace", property, NAMESPACE, VAR},
    {"namespace", exactly(1), NAMESPACE, SET},
    {"ndkVersion", property, NDK_VERSION, VAR},
    {"ndkVersion", exactly(1), NDK_VERSION, SET},
    {"publishNonDefault", property, PUBLISH_NON_DEFAULT, VAR},
    {"publishNonDefault", exactly(1), PUBLISH_NON_DEFAULT, SET},
    {"resourcePrefix", property, RESOURCE_PREFIX, VAL},
    {"resourcePrefix", exactly(1), RESOURCE_PREFIX, SET},
    {"targetProjectPath", property, TARGET_PROJECT_PATH, VAR},
    {"targetProjectPath", exactly(1), TARGET_PROJECT_PATH, SET},
    {"testNamespace", property, TEST_NAMESPACE, VAR},
    {"testNamespace", exactly(1), TEST_NAMESPACE, SET},
  }).collect(toModelMap());

  @Override
  public @NotNull ExternalToModelMap getExternalToModelMap(@NotNull GradleDslNameConverter converter) {
    return getExternalToModelMap(converter, groovyToModelNameMap, ktsToModelNameMap);
  }

  public AndroidDslElement(@NotNull GradleDslElement parent, @NotNull GradleNameElement name) {
    super(parent, name);
  }
}
