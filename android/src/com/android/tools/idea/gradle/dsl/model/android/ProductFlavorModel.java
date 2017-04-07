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
package com.android.tools.idea.gradle.dsl.model.android;

import com.android.annotations.NonNull;
import com.android.tools.idea.gradle.dsl.model.android.productFlavors.ExternalNativeBuildOptionsModel;
import com.android.tools.idea.gradle.dsl.model.android.productFlavors.NdkOptionsModel;
import com.android.tools.idea.gradle.dsl.model.values.GradleNotNullValue;
import com.android.tools.idea.gradle.dsl.model.values.GradleNullableValue;
import com.android.tools.idea.gradle.dsl.parser.android.ProductFlavorDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.productFlavors.ExternalNativeBuildOptionsDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.productFlavors.NdkOptionsDslElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

import static com.android.tools.idea.gradle.dsl.parser.android.ExternalNativeBuildDslElement.EXTERNAL_NATIVE_BUILD_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.android.productFlavors.NdkOptionsDslElement.NDK_BLOCK_NAME;

public final class ProductFlavorModel extends AbstractFlavorTypeModel {
  @NonNls private static final String APPLICATION_ID = "applicationId";
  @NonNls private static final String DIMENSION = "dimension";
  @NonNls private static final String MAX_SDK_VERSION = "maxSdkVersion";
  @NonNls private static final String MIN_SDK_VERSION = "minSdkVersion";
  @NonNls private static final String RES_CONFIGS = "resConfigs";
  @NonNls private static final String TARGET_SDK_VERSION = "targetSdkVersion";
  @NonNls private static final String TEST_APPLICATION_ID = "testApplicationId";
  @NonNls private static final String TEST_FUNCTIONAL_TEST = "testFunctionalTest";
  @NonNls private static final String TEST_HANDLE_PROFILING = "testHandleProfiling";
  @NonNls private static final String TEST_INSTRUMENTATION_RUNNER = "testInstrumentationRunner";
  @NonNls private static final String TEST_INSTRUMENTATION_RUNNER_ARGUMENTS = "testInstrumentationRunnerArguments";
  @NonNls private static final String VERSION_CODE = "versionCode";
  @NonNls private static final String VERSION_NAME = "versionName";

  public ProductFlavorModel(@NotNull ProductFlavorDslElement dslElement) {
    super(dslElement);
  }

  @NotNull
  public GradleNullableValue<String> applicationId() {
    return myDslElement.getLiteralProperty(APPLICATION_ID, String.class);
  }

  @NotNull
  public ProductFlavorModel setApplicationId(@NotNull String applicationId) {
    myDslElement.setNewLiteral(APPLICATION_ID, applicationId);
    return this;
  }

  @NotNull
  public ProductFlavorModel removeApplicationId() {
    myDslElement.removeProperty(APPLICATION_ID);
    return this;
  }

  @NotNull
  public GradleNullableValue<String> dimension() {
    return myDslElement.getLiteralProperty(DIMENSION, String.class);
  }

  @NotNull
  public ProductFlavorModel setDimension(@NotNull String dimension) {
    myDslElement.setNewLiteral(DIMENSION, dimension);
    return this;
  }

  @NotNull
  public ProductFlavorModel removeDimension() {
    myDslElement.removeProperty(DIMENSION);
    return this;
  }

  @NotNull
  public ExternalNativeBuildOptionsModel externalNativeBuild() {
    ExternalNativeBuildOptionsDslElement externalNativeBuildOptionsDslElement =
      myDslElement.getPropertyElement(EXTERNAL_NATIVE_BUILD_BLOCK_NAME,
                                      ExternalNativeBuildOptionsDslElement.class);
    if (externalNativeBuildOptionsDslElement == null) {
      externalNativeBuildOptionsDslElement = new ExternalNativeBuildOptionsDslElement(myDslElement);
      myDslElement.setNewElement(EXTERNAL_NATIVE_BUILD_BLOCK_NAME, externalNativeBuildOptionsDslElement);
    }
    return new ExternalNativeBuildOptionsModel(externalNativeBuildOptionsDslElement);
  }

  @NonNull
  public ProductFlavorModel removeExternalNativeBuild() {
    myDslElement.removeProperty(EXTERNAL_NATIVE_BUILD_BLOCK_NAME);
    return this;
  }

  @NotNull
  public GradleNullableValue<Integer> maxSdkVersion() {
    return myDslElement.getLiteralProperty(MAX_SDK_VERSION, Integer.class);
  }

  @NotNull
  public ProductFlavorModel setMaxSdkVersion(int maxSdkVersion) {
    myDslElement.setNewLiteral(MAX_SDK_VERSION, maxSdkVersion);
    return this;
  }

  @NotNull
  public ProductFlavorModel removeMaxSdkVersion() {
    myDslElement.removeProperty(MAX_SDK_VERSION);
    return this;
  }

  @NotNull
  public GradleNullableValue<String> minSdkVersion() {
    return getIntOrStringValue(MIN_SDK_VERSION);
  }

  @NotNull
  public ProductFlavorModel setMinSdkVersion(int minSdkVersion) {
    myDslElement.setNewLiteral(MIN_SDK_VERSION, minSdkVersion);
    return this;
  }

  @NotNull
  public ProductFlavorModel setMinSdkVersion(@NotNull String minSdkVersion) {
    myDslElement.setNewLiteral(MIN_SDK_VERSION, minSdkVersion);
    return this;
  }

  @NotNull
  public ProductFlavorModel removeMinSdkVersion() {
    myDslElement.removeProperty(MIN_SDK_VERSION);
    return this;
  }

  @NotNull
  public NdkOptionsModel ndk() {
    NdkOptionsDslElement ndkOptionsDslElement = myDslElement.getPropertyElement(NDK_BLOCK_NAME, NdkOptionsDslElement.class);
    if (ndkOptionsDslElement == null) {
      ndkOptionsDslElement = new NdkOptionsDslElement(myDslElement);
      myDslElement.setNewElement(NDK_BLOCK_NAME, ndkOptionsDslElement);
    }
    return new NdkOptionsModel(ndkOptionsDslElement);
  }

  @NonNull
  public ProductFlavorModel removeNdk() {
    myDslElement.removeProperty(NDK_BLOCK_NAME);
    return this;
  }

  @Nullable
  public List<GradleNotNullValue<String>> resConfigs() {
    return myDslElement.getListProperty(RES_CONFIGS, String.class);
  }

  @NotNull
  public ProductFlavorModel addResConfig(@NotNull String resConfig) {
    myDslElement.addToNewLiteralList(RES_CONFIGS, resConfig);
    return this;
  }

  @NotNull
  public ProductFlavorModel removeResConfig(@NotNull String resConfig) {
    myDslElement.removeFromExpressionList(RES_CONFIGS, resConfig);
    return this;
  }

  @NotNull
  public ProductFlavorModel removeAllResConfigs() {
    myDslElement.removeProperty(RES_CONFIGS);
    return this;
  }

  @NotNull
  public ProductFlavorModel replaceResConfig(@NotNull String oldResConfig, @NotNull String newResConfig) {
    myDslElement.replaceInExpressionList(RES_CONFIGS, oldResConfig, newResConfig);
    return this;
  }

  @NotNull
  public GradleNullableValue<String> targetSdkVersion() {
    return getIntOrStringValue(TARGET_SDK_VERSION);
  }

  @NotNull
  public ProductFlavorModel setTargetSdkVersion(int targetSdkVersion) {
    myDslElement.setNewLiteral(TARGET_SDK_VERSION, targetSdkVersion);
    return this;
  }

  @NotNull
  public ProductFlavorModel setTargetSdkVersion(@NotNull String targetSdkVersion) {
    myDslElement.setNewLiteral(TARGET_SDK_VERSION, targetSdkVersion);
    return this;
  }

  @NotNull
  public ProductFlavorModel removeTargetSdkVersion() {
    myDslElement.removeProperty(TARGET_SDK_VERSION);
    return this;
  }

  @NotNull
  public GradleNullableValue<String> testApplicationId() {
    return myDslElement.getLiteralProperty(TEST_APPLICATION_ID, String.class);
  }

  @NotNull
  public ProductFlavorModel setTestApplicationId(@NotNull String testApplicationId) {
    myDslElement.setNewLiteral(TEST_APPLICATION_ID, testApplicationId);
    return this;
  }

  @NotNull
  public ProductFlavorModel removeTestApplicationId() {
    myDslElement.removeProperty(TEST_APPLICATION_ID);
    return this;
  }

  @NotNull
  public GradleNullableValue<Boolean> testFunctionalTest() {
    return myDslElement.getLiteralProperty(TEST_FUNCTIONAL_TEST, Boolean.class);
  }

  @NotNull
  public ProductFlavorModel setTestFunctionalTest(boolean testFunctionalTest) {
    myDslElement.setNewLiteral(TEST_FUNCTIONAL_TEST, testFunctionalTest);
    return this;
  }

  @NotNull
  public ProductFlavorModel removeTestFunctionalTest() {
    myDslElement.removeProperty(TEST_FUNCTIONAL_TEST);
    return this;
  }

  @NotNull
  public GradleNullableValue<Boolean> testHandleProfiling() {
    return myDslElement.getLiteralProperty(TEST_HANDLE_PROFILING, Boolean.class);
  }

  @NotNull
  public ProductFlavorModel setTestHandleProfiling(boolean testHandleProfiling) {
    myDslElement.setNewLiteral(TEST_HANDLE_PROFILING, testHandleProfiling);
    return this;
  }

  @NotNull
  public ProductFlavorModel removeTestHandleProfiling() {
    myDslElement.removeProperty(TEST_HANDLE_PROFILING);
    return this;
  }

  @NotNull
  public GradleNullableValue<String> testInstrumentationRunner() {
    return myDslElement.getLiteralProperty(TEST_INSTRUMENTATION_RUNNER, String.class);
  }

  @NotNull
  public ProductFlavorModel setTestInstrumentationRunner(@NotNull String testInstrumentationRunner) {
    myDslElement.setNewLiteral(TEST_INSTRUMENTATION_RUNNER, testInstrumentationRunner);
    return this;
  }

  @NotNull
  public ProductFlavorModel removeTestInstrumentationRunner() {
    myDslElement.removeProperty(TEST_INSTRUMENTATION_RUNNER);
    return this;
  }

  @Nullable
  public Map<String, GradleNotNullValue<String>> testInstrumentationRunnerArguments() {
    return myDslElement.getMapProperty(TEST_INSTRUMENTATION_RUNNER_ARGUMENTS, String.class);
  }

  @NotNull
  public ProductFlavorModel setTestInstrumentationRunnerArgument(@NotNull String name, @NotNull String value) {
    myDslElement.setInNewLiteralMap(TEST_INSTRUMENTATION_RUNNER_ARGUMENTS, name, value);
    return this;
  }

  @NotNull
  public ProductFlavorModel removeTestInstrumentationRunnerArgument(@NotNull String name) {
    myDslElement.removeFromExpressionMap(TEST_INSTRUMENTATION_RUNNER_ARGUMENTS, name);
    return this;
  }

  @NotNull
  public ProductFlavorModel removeAllTestInstrumentationRunnerArguments() {
    myDslElement.removeProperty(TEST_INSTRUMENTATION_RUNNER_ARGUMENTS);
    return this;
  }

  @NotNull
  public GradleNullableValue<String> versionCode() {
    return getIntOrStringValue(VERSION_CODE);
  }

  @NotNull
  public ProductFlavorModel setVersionCode(int versionCode) {
    myDslElement.setNewLiteral(VERSION_CODE, versionCode);
    return this;
  }

  @NotNull
  public ProductFlavorModel setVersionCode(@NotNull String versionCode) {
    myDslElement.setNewLiteral(VERSION_CODE, versionCode);
    return this;
  }

  @NotNull
  public ProductFlavorModel removeVersionCode() {
    myDslElement.removeProperty(VERSION_CODE);
    return this;
  }

  @NotNull
  public GradleNullableValue<String> versionName() {
    return myDslElement.getLiteralProperty(VERSION_NAME, String.class);
  }

  @NotNull
  public ProductFlavorModel setVersionName(@NotNull String versionName) {
    myDslElement.setNewLiteral(VERSION_NAME, versionName);
    return this;
  }

  @NotNull
  public ProductFlavorModel removeVersionName() {
    myDslElement.removeProperty(VERSION_NAME);
    return this;
  }

  // Overriding the super class method to make them chainable along with the other methods in this class.

  @Override
  @NotNull
  public ProductFlavorModel addConsumerProguardFile(@NotNull String consumerProguardFile) {
    return (ProductFlavorModel)super.addConsumerProguardFile(consumerProguardFile);
  }

  @Override
  @NotNull
  public ProductFlavorModel removeConsumerProguardFile(@NotNull String consumerProguardFile) {
    return (ProductFlavorModel)super.removeConsumerProguardFile(consumerProguardFile);
  }

  @Override
  @NotNull
  public ProductFlavorModel removeAllConsumerProguardFiles() {
    return (ProductFlavorModel)super.removeAllConsumerProguardFiles();
  }

  @Override
  @NotNull
  public ProductFlavorModel replaceConsumerProguardFile(@NotNull String oldConsumerProguardFile, @NotNull String newConsumerProguardFile) {
    return (ProductFlavorModel)super.replaceConsumerProguardFile(oldConsumerProguardFile, newConsumerProguardFile);
  }

  @Override
  @NotNull
  public ProductFlavorModel setManifestPlaceholder(@NotNull String name, @NotNull String value) {
    return (ProductFlavorModel)super.setManifestPlaceholder(name, value);
  }

  @Override
  @NotNull
  public ProductFlavorModel setManifestPlaceholder(@NotNull String name, int value) {
    return (ProductFlavorModel)super.setManifestPlaceholder(name, value);
  }

  @Override
  @NotNull
  public ProductFlavorModel setManifestPlaceholder(@NotNull String name, boolean value) {
    return (ProductFlavorModel)super.setManifestPlaceholder(name, value);
  }

  @Override
  @NotNull
  public ProductFlavorModel removeManifestPlaceholder(@NotNull String name) {
    return (ProductFlavorModel)super.removeManifestPlaceholder(name);
  }

  @Override
  @NotNull
  public ProductFlavorModel removeAllManifestPlaceholders() {
    return (ProductFlavorModel)super.removeAllManifestPlaceholders();
  }

  @Override
  @NotNull
  public ProductFlavorModel setMultiDexEnabled(boolean multiDexEnabled) {
    return (ProductFlavorModel)super.setMultiDexEnabled(multiDexEnabled);
  }

  @Override
  @NotNull
  public ProductFlavorModel removeMultiDexEnabled() {
    return (ProductFlavorModel)super.removeMultiDexEnabled();
  }

  @Override
  @NotNull
  public ProductFlavorModel addProguardFile(@NotNull String proguardFile) {
    return (ProductFlavorModel)super.addProguardFile(proguardFile);
  }

  @Override
  @NotNull
  public ProductFlavorModel removeProguardFile(@NotNull String proguardFile) {
    return (ProductFlavorModel)super.removeProguardFile(proguardFile);
  }

  @Override
  @NotNull
  public ProductFlavorModel removeAllProguardFiles() {
    return (ProductFlavorModel)super.removeAllProguardFiles();
  }

  @Override
  @NotNull
  public ProductFlavorModel replaceProguardFile(@NotNull String oldProguardFile, @NotNull String newProguardFile) {
    return (ProductFlavorModel)super.replaceProguardFile(oldProguardFile, newProguardFile);
  }

  @Override
  @NotNull
  public ProductFlavorModel addResValue(@NotNull ResValue resValue) {
    return (ProductFlavorModel)super.addResValue(resValue);
  }

  @Override
  @NotNull
  public ProductFlavorModel removeResValue(@NotNull ResValue resValue) {
    return (ProductFlavorModel)super.removeResValue(resValue);
  }

  @Override
  @NotNull
  public ProductFlavorModel removeAllResValues() {
    return (ProductFlavorModel)super.removeAllResValues();
  }

  @Override
  @NotNull
  public ProductFlavorModel replaceResValue(@NotNull ResValue oldResValue, @NotNull ResValue newResValue) {
    return (ProductFlavorModel)super.replaceResValue(oldResValue, newResValue);
  }

  @Override
  @NotNull
  public ProductFlavorModel setUseJack(boolean useJack) {
    return (ProductFlavorModel)super.setUseJack(useJack);
  }

  @Override
  @NotNull
  public ProductFlavorModel removeUseJack() {
    return (ProductFlavorModel)super.removeUseJack();
  }
}
