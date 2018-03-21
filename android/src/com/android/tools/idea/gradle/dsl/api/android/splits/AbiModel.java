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

public interface AbiModel {
  @NotNull
  GradleNullableValue<Boolean> enable();

  @NotNull
  AbiModel setEnable(boolean enable);

  @NotNull
  AbiModel removeEnable();

  @Nullable
  List<GradleNotNullValue<String>> exclude();

  @NotNull
  AbiModel addExclude(@NotNull String exclude);

  @NotNull
  AbiModel removeExclude(@NotNull String exclude);

  @NotNull
  AbiModel removeAllExclude();

  @NotNull
  AbiModel replaceExclude(@NotNull String oldExclude, @NotNull String newExclude);

  @Nullable
  List<GradleNotNullValue<String>> include();

  @NotNull
  AbiModel addInclude(@NotNull String include);

  @NotNull
  AbiModel removeInclude(@NotNull String include);

  @NotNull
  AbiModel removeAllInclude();

  @NotNull
  AbiModel replaceInclude(@NotNull String oldInclude, @NotNull String newInclude);

  @NotNull
  AbiModel addReset();

  @NotNull
  AbiModel removeReset();

  @NotNull
  GradleNullableValue<Boolean> universalApk();

  @NotNull
  AbiModel setUniversalApk(boolean universalApk);

  @NotNull
  AbiModel removeUniversalApk();
}
