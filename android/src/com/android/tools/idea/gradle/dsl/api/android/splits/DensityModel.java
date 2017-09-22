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
package com.android.tools.idea.gradle.dsl.api.android.splits;

import com.android.tools.idea.gradle.dsl.api.values.GradleNotNullValue;
import com.android.tools.idea.gradle.dsl.api.values.GradleNullableValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface DensityModel {
  @NotNull
  GradleNullableValue<Boolean> auto();

  @NotNull
  DensityModel setAuto(boolean auto);

  @NotNull
  DensityModel removeAuto();

  @Nullable
  List<GradleNotNullValue<String>> compatibleScreens();

  @NotNull
  DensityModel addCompatibleScreen(@NotNull String compatibleScreen);

  @NotNull
  DensityModel removeCompatibleScreen(@NotNull String compatibleScreen);

  @NotNull
  DensityModel removeAllCompatibleScreens();

  @NotNull
  DensityModel replaceCompatibleScreen(@NotNull String oldCompatibleScreen, @NotNull String newCompatibleScreen);

  @NotNull
  GradleNullableValue<Boolean> enable();

  @NotNull
  DensityModel setEnable(boolean enable);

  @NotNull
  DensityModel removeEnable();

  @Nullable
  List<GradleNotNullValue<String>> exclude();

  @NotNull
  DensityModel addExclude(@NotNull String exclude);

  @NotNull
  DensityModel removeExclude(@NotNull String exclude);

  @NotNull
  DensityModel removeAllExclude();

  @NotNull
  DensityModel replaceExclude(@NotNull String oldExclude, @NotNull String newExclude);

  @Nullable
  List<GradleNotNullValue<String>> include();

  @NotNull
  DensityModel addInclude(@NotNull String include);

  @NotNull
  DensityModel removeInclude(@NotNull String include);

  @NotNull
  DensityModel removeAllInclude();

  @NotNull
  DensityModel replaceInclude(@NotNull String oldInclude, @NotNull String newInclude);

  @NotNull
  DensityModel addReset();

  @NotNull
  DensityModel removeReset();
}
