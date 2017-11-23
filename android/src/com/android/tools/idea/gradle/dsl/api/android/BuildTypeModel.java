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

import com.android.tools.idea.gradle.dsl.api.FlavorTypeModel;
import com.android.tools.idea.gradle.dsl.api.values.GradleNotNullValue;
import com.android.tools.idea.gradle.dsl.api.values.GradleNullableValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface BuildTypeModel extends FlavorTypeModel {
  @NotNull
  GradleNullableValue<String> applicationIdSuffix();

  @NotNull
  BuildTypeModel setApplicationIdSuffix(@NotNull String applicationIdSuffix);

  @NotNull
  BuildTypeModel removeApplicationIdSuffix();

  @Nullable
  List<GradleNotNullValue<BuildConfigField>> buildConfigFields();

  @NotNull
  BuildTypeModel addBuildConfigField(@NotNull BuildConfigField buildConfigField);

  @NotNull
  BuildTypeModel removeBuildConfigField(@NotNull BuildConfigField buildConfigField);

  @NotNull
  BuildTypeModel removeAllBuildConfigFields();

  @NotNull
  BuildTypeModel replaceBuildConfigField(@NotNull BuildConfigField oldBuildConfigField, @NotNull BuildConfigField newBuildConfigField);

  @NotNull
  GradleNullableValue<Boolean> debuggable();

  @NotNull
  BuildTypeModel setDebuggable(@NotNull Boolean debuggable);

  @NotNull
  BuildTypeModel removeDebuggable();

  @NotNull
  GradleNullableValue<Boolean> embedMicroApp();

  @NotNull
  BuildTypeModel setEmbedMicroApp(@NotNull Boolean embedMicroApp);

  @NotNull
  BuildTypeModel removeEmbedMicroApp();

  @NotNull
  GradleNullableValue<Boolean> jniDebuggable();

  @NotNull
  BuildTypeModel setJniDebuggable(@NotNull Boolean jniDebuggable);

  @NotNull
  BuildTypeModel removeJniDebuggable();

  @NotNull
  GradleNullableValue<Boolean> minifyEnabled();

  @NotNull
  BuildTypeModel setMinifyEnabled(@NotNull Boolean minifyEnabled);

  @NotNull
  BuildTypeModel removeMinifyEnabled();

  @NotNull
  GradleNullableValue<Boolean> pseudoLocalesEnabled();

  @NotNull
  BuildTypeModel setPseudoLocalesEnabled(@NotNull Boolean pseudoLocalesEnabled);

  @NotNull
  BuildTypeModel removePseudoLocalesEnabled();

  @NotNull
  GradleNullableValue<Boolean> renderscriptDebuggable();

  @NotNull
  BuildTypeModel setRenderscriptDebuggable(@NotNull Boolean renderscriptDebuggable);

  @NotNull
  BuildTypeModel removeRenderscriptDebuggable();

  @NotNull
  GradleNullableValue<Integer> renderscriptOptimLevel();

  @NotNull
  BuildTypeModel setRenderscriptOptimLevel(@NotNull Integer renderscriptOptimLevel);

  @NotNull
  BuildTypeModel removeRenderscriptOptimLevel();

  @NotNull
  GradleNullableValue<Boolean> shrinkResources();

  @NotNull
  BuildTypeModel setShrinkResources(@NotNull Boolean shrinkResources);

  @NotNull
  BuildTypeModel removeShrinkResources();

  @NotNull
  GradleNullableValue<Boolean> testCoverageEnabled();

  @NotNull
  BuildTypeModel setTestCoverageEnabled(@NotNull Boolean testCoverageEnabled);

  @NotNull
  BuildTypeModel removeTestCoverageEnabled();

  @NotNull
  GradleNullableValue<String> versionNameSuffix();

  @NotNull
  BuildTypeModel setVersionNameSuffix(@NotNull String versionNameSuffix);

  @NotNull
  BuildTypeModel removeVersionNameSuffix();

  @NotNull
  GradleNullableValue<Boolean> zipAlignEnabled();

  @NotNull
  BuildTypeModel setZipAlignEnabled(@NotNull Boolean zipAlignEnabled);

  @NotNull
  BuildTypeModel removeZipAlignEnabled();

  @Override
  @NotNull
  BuildTypeModel addConsumerProguardFile(@NotNull String consumerProguardFile);

  @Override
  @NotNull
  BuildTypeModel removeConsumerProguardFile(@NotNull String consumerProguardFile);

  @Override
  @NotNull
  BuildTypeModel removeAllConsumerProguardFiles();

  @Override
  @NotNull
  BuildTypeModel replaceConsumerProguardFile(@NotNull String oldConsumerProguardFile, @NotNull String newConsumerProguardFile);

  @Override
  @NotNull
  BuildTypeModel setManifestPlaceholder(@NotNull String name, @NotNull String value);

  @Override
  @NotNull
  BuildTypeModel setManifestPlaceholder(@NotNull String name, int value);

  @Override
  @NotNull
  BuildTypeModel setManifestPlaceholder(@NotNull String name, boolean value);

  @Override
  @NotNull
  BuildTypeModel removeManifestPlaceholder(@NotNull String name);

  @Override
  @NotNull
  BuildTypeModel removeAllManifestPlaceholders();

  @Override
  @NotNull
  BuildTypeModel setMultiDexEnabled(boolean multiDexEnabled);

  @Override
  @NotNull
  BuildTypeModel removeMultiDexEnabled();

  @Override
  @NotNull
  BuildTypeModel addProguardFile(@NotNull String proguardFile);

  @Override
  @NotNull
  BuildTypeModel removeProguardFile(@NotNull String proguardFile);

  @Override
  @NotNull
  BuildTypeModel removeAllProguardFiles();

  @Override
  @NotNull
  BuildTypeModel replaceProguardFile(@NotNull String oldProguardFile, @NotNull String newProguardFile);

  @Override
  @NotNull
  BuildTypeModel addResValue(@NotNull ResValue resValue);

  @Override
  @NotNull
  BuildTypeModel removeResValue(@NotNull ResValue resValue);

  @Override
  @NotNull
  BuildTypeModel removeAllResValues();

  @Override
  @NotNull
  BuildTypeModel replaceResValue(@NotNull ResValue oldResValue, @NotNull ResValue newResValue);

  @Override
  @NotNull
  BuildTypeModel setUseJack(boolean useJack);

  @Override
  @NotNull
  BuildTypeModel removeUseJack();

  /**
   * Represents a {@code buildConfigField} statement defined in the build type block of the Gradle file.
   */
  interface BuildConfigField extends TypeNameValueElement {

  }
}
