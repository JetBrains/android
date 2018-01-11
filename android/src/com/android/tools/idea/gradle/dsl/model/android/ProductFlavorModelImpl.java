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

import com.android.tools.idea.gradle.dsl.api.android.ProductFlavorModel;
import com.android.tools.idea.gradle.dsl.api.android.productFlavors.ExternalNativeBuildOptionsModel;
import com.android.tools.idea.gradle.dsl.api.android.productFlavors.NdkOptionsModel;
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.dsl.api.values.GradleNotNullValue;
import com.android.tools.idea.gradle.dsl.model.android.productFlavors.ExternalNativeBuildOptionsModelImpl;
import com.android.tools.idea.gradle.dsl.model.android.productFlavors.NdkOptionsModelImpl;
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

public final class ProductFlavorModelImpl extends FlavorTypeModelImpl implements
                                                                              ProductFlavorModel {
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

  public ProductFlavorModelImpl(@NotNull ProductFlavorDslElement dslElement) {
    super(dslElement);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel applicationId() {
    return getModelForProperty(APPLICATION_ID);
  }

  @Override
  public void removeApplicationId() {
    myDslElement.removeProperty(APPLICATION_ID);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel dimension() {
    return getModelForProperty(DIMENSION);
  }

  @Override
  public void removeDimension() {
    myDslElement.removeProperty(DIMENSION);
  }

  @Override
  @NotNull
  public ExternalNativeBuildOptionsModel externalNativeBuild() {
    ExternalNativeBuildOptionsDslElement externalNativeBuildOptionsDslElement =
      myDslElement.getPropertyElement(EXTERNAL_NATIVE_BUILD_BLOCK_NAME,
                                      ExternalNativeBuildOptionsDslElement.class);
    if (externalNativeBuildOptionsDslElement == null) {
      externalNativeBuildOptionsDslElement = new ExternalNativeBuildOptionsDslElement(myDslElement);
      myDslElement.setNewElement(EXTERNAL_NATIVE_BUILD_BLOCK_NAME, externalNativeBuildOptionsDslElement);
    }
    return new ExternalNativeBuildOptionsModelImpl(externalNativeBuildOptionsDslElement);
  }

  @Override
  public void removeExternalNativeBuild() {
    myDslElement.removeProperty(EXTERNAL_NATIVE_BUILD_BLOCK_NAME);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel maxSdkVersion() {
    return getModelForProperty(MAX_SDK_VERSION);
  }

  @Override
  public void removeMaxSdkVersion() {
    myDslElement.removeProperty(MAX_SDK_VERSION);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel minSdkVersion() {
    return getModelForProperty(MIN_SDK_VERSION);
  }

  @Override
  public void removeMinSdkVersion() {
    myDslElement.removeProperty(MIN_SDK_VERSION);
  }

  @Override
  @NotNull
  public NdkOptionsModel ndk() {
    NdkOptionsDslElement ndkOptionsDslElement = myDslElement.getPropertyElement(NDK_BLOCK_NAME, NdkOptionsDslElement.class);
    if (ndkOptionsDslElement == null) {
      ndkOptionsDslElement = new NdkOptionsDslElement(myDslElement);
      myDslElement.setNewElement(NDK_BLOCK_NAME, ndkOptionsDslElement);
    }
    return new NdkOptionsModelImpl(ndkOptionsDslElement);
  }

  @Override
  public void removeNdk() {
    myDslElement.removeProperty(NDK_BLOCK_NAME);
  }

  @Override
  @Nullable
  public List<GradleNotNullValue<String>> resConfigs() {
    return myDslElement.getListProperty(RES_CONFIGS, String.class);
  }

  @Override
  public void addResConfig(@NotNull String resConfig) {
    myDslElement.addToNewLiteralList(RES_CONFIGS, resConfig);
  }

  @Override
  public void removeResConfig(@NotNull String resConfig) {
    myDslElement.removeFromExpressionList(RES_CONFIGS, resConfig);
  }

  @Override
  public void removeAllResConfigs() {
    myDslElement.removeProperty(RES_CONFIGS);
  }

  @Override
  public void replaceResConfig(@NotNull String oldResConfig, @NotNull String newResConfig) {
    myDslElement.replaceInExpressionList(RES_CONFIGS, oldResConfig, newResConfig);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel targetSdkVersion() {
    return getModelForProperty(TARGET_SDK_VERSION);
  }

  @Override
  public void removeTargetSdkVersion() {
    myDslElement.removeProperty(TARGET_SDK_VERSION);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel testApplicationId() {
    return getModelForProperty(TEST_APPLICATION_ID);
  }

  @Override
  public void removeTestApplicationId() {
    myDslElement.removeProperty(TEST_APPLICATION_ID);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel testFunctionalTest() {
    return getModelForProperty(TEST_FUNCTIONAL_TEST);
  }

  @Override
  public void removeTestFunctionalTest() {
    myDslElement.removeProperty(TEST_FUNCTIONAL_TEST);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel testHandleProfiling() {
    return getModelForProperty(TEST_HANDLE_PROFILING);
  }

  @Override
  public void removeTestHandleProfiling() {
    myDslElement.removeProperty(TEST_HANDLE_PROFILING);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel testInstrumentationRunner() {
    return getModelForProperty(TEST_INSTRUMENTATION_RUNNER);
  }

  @Override
  public void removeTestInstrumentationRunner() {
    myDslElement.removeProperty(TEST_INSTRUMENTATION_RUNNER);
  }

  @Override
  @Nullable
  public Map<String, GradleNotNullValue<String>> testInstrumentationRunnerArguments() {
    return myDslElement.getMapProperty(TEST_INSTRUMENTATION_RUNNER_ARGUMENTS, String.class);
  }

  @Override
  public void setTestInstrumentationRunnerArgument(@NotNull String name, @NotNull String value) {
    myDslElement.setInNewLiteralMap(TEST_INSTRUMENTATION_RUNNER_ARGUMENTS, name, value);
  }

  @Override
  public void removeTestInstrumentationRunnerArgument(@NotNull String name) {
    myDslElement.removeFromExpressionMap(TEST_INSTRUMENTATION_RUNNER_ARGUMENTS, name);
  }

  @Override
  public void removeAllTestInstrumentationRunnerArguments() {
    myDslElement.removeProperty(TEST_INSTRUMENTATION_RUNNER_ARGUMENTS);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel versionCode() {
    return getModelForProperty(VERSION_CODE);
  }

  @Override
  public void removeVersionCode() {
    myDslElement.removeProperty(VERSION_CODE);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel versionName() {
    return getModelForProperty(VERSION_NAME);
  }

  @Override
  public void removeVersionName() {
    myDslElement.removeProperty(VERSION_NAME);
  }
}
