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
import com.android.tools.idea.gradle.dsl.api.android.productFlavors.ExternalNativeBuildOptionsModel;
import com.android.tools.idea.gradle.dsl.api.android.productFlavors.NdkOptionsModel;
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.dsl.api.values.GradleNotNullValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public interface ProductFlavorModel extends FlavorTypeModel {
  @NotNull
  ResolvedPropertyModel applicationId();

  @NotNull
  ResolvedPropertyModel dimension();

  @NotNull
  ExternalNativeBuildOptionsModel externalNativeBuild();

  void removeExternalNativeBuild();

  @NotNull
  ResolvedPropertyModel maxSdkVersion();

  @NotNull
  ResolvedPropertyModel minSdkVersion();

  @NotNull
  NdkOptionsModel ndk();

  void removeNdk();

  @Nullable
  List<GradleNotNullValue<String>> resConfigs();

  void addResConfig(@NotNull String resConfig);

  void removeResConfig(@NotNull String resConfig);

  void removeAllResConfigs();

  void replaceResConfig(@NotNull String oldResConfig, @NotNull String newResConfig);

  @NotNull
  ResolvedPropertyModel targetSdkVersion();

  @NotNull
  ResolvedPropertyModel testApplicationId();

  @NotNull
  ResolvedPropertyModel testFunctionalTest();

  @NotNull
  ResolvedPropertyModel testHandleProfiling();

  @NotNull
  ResolvedPropertyModel testInstrumentationRunner();

  @Nullable
  Map<String, GradleNotNullValue<String>> testInstrumentationRunnerArguments();

  void setTestInstrumentationRunnerArgument(@NotNull String name, @NotNull String value);

  void removeTestInstrumentationRunnerArgument(@NotNull String name);

  void removeAllTestInstrumentationRunnerArguments();

  @NotNull
  ResolvedPropertyModel versionCode();

  @NotNull
  ResolvedPropertyModel versionName();
}
