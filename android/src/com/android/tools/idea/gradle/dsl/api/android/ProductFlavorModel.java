/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.api.android;

import com.android.annotations.NonNull;
import com.android.tools.idea.gradle.dsl.api.FlavorTypeModel;
import com.android.tools.idea.gradle.dsl.api.android.productFlavors.ExternalNativeBuildOptionsModel;
import com.android.tools.idea.gradle.dsl.api.android.productFlavors.NdkOptionsModel;
import com.android.tools.idea.gradle.dsl.api.values.GradleNotNullValue;
import com.android.tools.idea.gradle.dsl.api.values.GradleNullableValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public interface ProductFlavorModel extends FlavorTypeModel {
  @NotNull
  GradleNullableValue<String> applicationId();

  @NotNull
  ProductFlavorModel setApplicationId(@NotNull String applicationId);

  @NotNull
  ProductFlavorModel removeApplicationId();

  @NotNull
  GradleNullableValue<String> dimension();

  @NotNull
  ProductFlavorModel setDimension(@NotNull String dimension);

  @NotNull
  ProductFlavorModel removeDimension();

  @NotNull
  ExternalNativeBuildOptionsModel externalNativeBuild();

  @NonNull
  ProductFlavorModel removeExternalNativeBuild();

  @NotNull
  GradleNullableValue<Integer> maxSdkVersion();

  @NotNull
  ProductFlavorModel setMaxSdkVersion(int maxSdkVersion);

  @NotNull
  ProductFlavorModel removeMaxSdkVersion();

  @NotNull
  GradleNullableValue<String> minSdkVersion();

  @NotNull
  ProductFlavorModel setMinSdkVersion(int minSdkVersion);

  @NotNull
  ProductFlavorModel setMinSdkVersion(@NotNull String minSdkVersion);

  @NotNull
  ProductFlavorModel removeMinSdkVersion();

  @NotNull
  NdkOptionsModel ndk();

  @NonNull
  ProductFlavorModel removeNdk();

  @Nullable
  List<GradleNotNullValue<String>> resConfigs();

  @NotNull
  ProductFlavorModel addResConfig(@NotNull String resConfig);

  @NotNull
  ProductFlavorModel removeResConfig(@NotNull String resConfig);

  @NotNull
  ProductFlavorModel removeAllResConfigs();

  @NotNull
  ProductFlavorModel replaceResConfig(@NotNull String oldResConfig, @NotNull String newResConfig);

  @NotNull
  GradleNullableValue<String> targetSdkVersion();

  @NotNull
  ProductFlavorModel setTargetSdkVersion(int targetSdkVersion);

  @NotNull
  ProductFlavorModel setTargetSdkVersion(@NotNull String targetSdkVersion);

  @NotNull
  ProductFlavorModel removeTargetSdkVersion();

  @NotNull
  GradleNullableValue<String> testApplicationId();

  @NotNull
  ProductFlavorModel setTestApplicationId(@NotNull String testApplicationId);

  @NotNull
  ProductFlavorModel removeTestApplicationId();

  @NotNull
  GradleNullableValue<Boolean> testFunctionalTest();

  @NotNull
  ProductFlavorModel setTestFunctionalTest(boolean testFunctionalTest);

  @NotNull
  ProductFlavorModel removeTestFunctionalTest();

  @NotNull
  GradleNullableValue<Boolean> testHandleProfiling();

  @NotNull
  ProductFlavorModel setTestHandleProfiling(boolean testHandleProfiling);

  @NotNull
  ProductFlavorModel removeTestHandleProfiling();

  @NotNull
  GradleNullableValue<String> testInstrumentationRunner();

  @NotNull
  ProductFlavorModel setTestInstrumentationRunner(@NotNull String testInstrumentationRunner);

  @NotNull
  ProductFlavorModel removeTestInstrumentationRunner();

  @Nullable
  Map<String, GradleNotNullValue<String>> testInstrumentationRunnerArguments();

  @NotNull
  ProductFlavorModel setTestInstrumentationRunnerArgument(@NotNull String name, @NotNull String value);

  @NotNull
  ProductFlavorModel removeTestInstrumentationRunnerArgument(@NotNull String name);

  @NotNull
  ProductFlavorModel removeAllTestInstrumentationRunnerArguments();

  @NotNull
  GradleNullableValue<String> versionCode();

  @NotNull
  ProductFlavorModel setVersionCode(int versionCode);

  @NotNull
  ProductFlavorModel setVersionCode(@NotNull String versionCode);

  @NotNull
  ProductFlavorModel removeVersionCode();

  @NotNull
  GradleNullableValue<String> versionName();

  @NotNull
  ProductFlavorModel setVersionName(@NotNull String versionName);

  @NotNull
  ProductFlavorModel removeVersionName();

  @Override
  @NotNull
  ProductFlavorModel addConsumerProguardFile(@NotNull String consumerProguardFile);

  @Override
  @NotNull
  ProductFlavorModel removeConsumerProguardFile(@NotNull String consumerProguardFile);

  @Override
  @NotNull
  ProductFlavorModel removeAllConsumerProguardFiles();

  @Override
  @NotNull
  ProductFlavorModel replaceConsumerProguardFile(@NotNull String oldConsumerProguardFile, @NotNull String newConsumerProguardFile);

  @Override
  @NotNull
  ProductFlavorModel setManifestPlaceholder(@NotNull String name, @NotNull String value);

  @Override
  @NotNull
  ProductFlavorModel setManifestPlaceholder(@NotNull String name, int value);

  @Override
  @NotNull
  ProductFlavorModel setManifestPlaceholder(@NotNull String name, boolean value);

  @Override
  @NotNull
  ProductFlavorModel removeManifestPlaceholder(@NotNull String name);

  @Override
  @NotNull
  ProductFlavorModel removeAllManifestPlaceholders();

  @Override
  @NotNull
  ProductFlavorModel setMultiDexEnabled(boolean multiDexEnabled);

  @Override
  @NotNull
  ProductFlavorModel removeMultiDexEnabled();

  @Override
  @NotNull
  ProductFlavorModel addProguardFile(@NotNull String proguardFile);

  @Override
  @NotNull
  ProductFlavorModel removeProguardFile(@NotNull String proguardFile);

  @Override
  @NotNull
  ProductFlavorModel removeAllProguardFiles();

  @Override
  @NotNull
  ProductFlavorModel replaceProguardFile(@NotNull String oldProguardFile, @NotNull String newProguardFile);

  @Override
  @NotNull
  ProductFlavorModel addResValue(@NotNull ResValue resValue);

  @Override
  @NotNull
  ProductFlavorModel removeResValue(@NotNull ResValue resValue);

  @Override
  @NotNull
  ProductFlavorModel removeAllResValues();

  @Override
  @NotNull
  ProductFlavorModel replaceResValue(@NotNull ResValue oldResValue, @NotNull ResValue newResValue);

  @Override
  @NotNull
  ProductFlavorModel setUseJack(boolean useJack);

  @Override
  @NotNull
  ProductFlavorModel removeUseJack();
}
