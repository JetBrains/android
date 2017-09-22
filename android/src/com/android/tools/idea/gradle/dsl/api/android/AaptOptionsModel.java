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

import com.android.tools.idea.gradle.dsl.api.values.GradleNotNullValue;
import com.android.tools.idea.gradle.dsl.api.values.GradleNullableValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface AaptOptionsModel {
  @Nullable
  List<GradleNotNullValue<String>> additionalParameters();

  @NotNull
  AaptOptionsModel addAdditionalParameter(@NotNull String additionalParameter);

  @NotNull
  AaptOptionsModel removeAdditionalParameter(@NotNull String additionalParameter);

  @NotNull
  AaptOptionsModel removeAllAdditionalParameters();

  @NotNull
  AaptOptionsModel replaceAdditionalParameter(@NotNull String oldAdditionalParameter, @NotNull String newAdditionalParameter);

  @NotNull
  GradleNullableValue<String> ignoreAssets();

  @NotNull
  AaptOptionsModel setIgnoreAssets(@NotNull String ignoreAssets);

  @NotNull
  AaptOptionsModel removeIgnoreAssets();

  @NotNull
  GradleNullableValue<Boolean> failOnMissingConfigEntry();

  @NotNull
  AaptOptionsModel setFailOnMissingConfigEntry(boolean failOnMissingConfigEntry);

  @NotNull
  AaptOptionsModel removeFailOnMissingConfigEntry();

  @NotNull
  GradleNullableValue<Integer> cruncherProcesses();

  @NotNull
  AaptOptionsModel setCruncherProcesses(int cruncherProcesses);

  @NotNull
  AaptOptionsModel removeCruncherProcesses();

  @NotNull
  GradleNullableValue<Boolean> cruncherEnabled();

  @NotNull
  AaptOptionsModel setCruncherEnabled(boolean cruncherEnabled);

  @NotNull
  AaptOptionsModel removeCruncherEnabled();

  @Nullable
  List<GradleNotNullValue<String>> noCompress();

  @NotNull
  AaptOptionsModel addNoCompress(@NotNull String noCompress);

  @NotNull
  AaptOptionsModel removeNoCompress(@NotNull String noCompress);

  @NotNull
  AaptOptionsModel removeAllNoCompress();

  @NotNull
  AaptOptionsModel replaceNoCompress(@NotNull String oldNoCompress, @NotNull String newNoCompress);
}
