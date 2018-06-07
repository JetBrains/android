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

import com.android.tools.idea.gradle.dsl.api.ExternalNativeBuildModel;
import com.android.tools.idea.gradle.dsl.api.android.externalNativeBuild.AdbOptionsModel;
import com.android.tools.idea.gradle.dsl.api.values.GradleNotNullValue;
import com.android.tools.idea.gradle.dsl.api.values.GradleNullableValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface AndroidModel {
  @NotNull
  AaptOptionsModel aaptOptions();

  @NotNull
  AdbOptionsModel adbOptions();

  @NotNull
  GradleNullableValue<String> buildToolsVersion();

  @NotNull
  AndroidModel setBuildToolsVersion(int buildToolsVersion);

  @NotNull
  AndroidModel setBuildToolsVersion(@NotNull String buildToolsVersion);

  @NotNull
  AndroidModel removeBuildToolsVersion();

  @NotNull
  List<BuildTypeModel> buildTypes();

  @NotNull
  AndroidModel addBuildType(@NotNull String buildType);

  @NotNull
  AndroidModel removeBuildType(@NotNull String buildType);

  @NotNull
  CompileOptionsModel compileOptions();

  @NotNull
  GradleNullableValue<String> compileSdkVersion();

  @NotNull
  AndroidModel setCompileSdkVersion(int compileSdkVersion);

  @NotNull
  AndroidModel setCompileSdkVersion(@NotNull String compileSdkVersion);

  @NotNull
  AndroidModel removeCompileSdkVersion();

  @NotNull
  DataBindingModel dataBinding();

  @NotNull
  ProductFlavorModel defaultConfig();

  @NotNull
  GradleNullableValue<String> defaultPublishConfig();

  @NotNull
  AndroidModel setDefaultPublishConfig(@NotNull String defaultPublishConfig);

  @NotNull
  AndroidModel removeDefaultPublishConfig();

  @NotNull
  DexOptionsModel dexOptions();

  @NotNull
  ExternalNativeBuildModel externalNativeBuild();

  @Nullable
  List<GradleNotNullValue<String>> flavorDimensions();

  @NotNull
  AndroidModel addFlavorDimension(@NotNull String flavorDimension);

  @NotNull
  AndroidModel removeFlavorDimension(@NotNull String flavorDimension);

  @NotNull
  AndroidModel removeAllFlavorDimensions();

  @NotNull
  AndroidModel replaceFlavorDimension(@NotNull String oldFlavorDimension, @NotNull String newFlavorDimension);

  @NotNull
  GradleNullableValue<Boolean> generatePureSplits();

  @NotNull
  AndroidModel setGeneratePureSplits(boolean generatePureSplits);

  @NotNull
  AndroidModel removeGeneratePureSplits();

  @NotNull
  LintOptionsModel lintOptions();

  @NotNull
  PackagingOptionsModel packagingOptions();

  @NotNull
  List<ProductFlavorModel> productFlavors();

  @NotNull
  AndroidModel addProductFlavor(@NotNull String flavor);

  @NotNull
  AndroidModel removeProductFlavor(@NotNull String flavor);

  @NotNull
  List<SigningConfigModel> signingConfigs();

  @NotNull
  AndroidModel addSigningConfig(@NotNull String config);

  @NotNull
  AndroidModel removeSigningConfig(@NotNull String configName);

  @NotNull
  List<SourceSetModel> sourceSets();

  @NotNull
  AndroidModel addSourceSet(@NotNull String sourceSet);

  @NotNull
  AndroidModel removeSourceSet(@NotNull String sourceSet);

  @NotNull
  SplitsModel splits();

  @NotNull
  TestOptionsModel testOptions();

  @NotNull
  GradleNullableValue<Boolean> publishNonDefault();

  @NotNull
  AndroidModel setPublishNonDefault(boolean publishNonDefault);

  @NotNull
  AndroidModel removePublishNonDefault();

  @NotNull
  GradleNullableValue<String> resourcePrefix();

  @NotNull
  AndroidModel setResourcePrefix(@NotNull String resourcePrefix);

  @NotNull
  AndroidModel removeResourcePrefix();
}
