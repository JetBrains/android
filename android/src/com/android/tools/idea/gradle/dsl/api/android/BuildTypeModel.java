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
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.dsl.api.values.GradleNotNullValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface BuildTypeModel extends FlavorTypeModel {
  @NotNull
  ResolvedPropertyModel applicationIdSuffix();

  @Nullable
  List<BuildConfigField> buildConfigFields();

  BuildConfigField addBuildConfigField(@NotNull String type, @NotNull String name, @NotNull String value);

  void removeBuildConfigField(@NotNull String type, @NotNull String name, @NotNull String value);

  BuildConfigField replaceBuildConfigField(@NotNull String oldType,
                                           @NotNull String oldName,
                                           @NotNull String oldValue,
                                           @NotNull String type,
                                           @NotNull String name,
                                           @NotNull String value);

  void removeAllBuildConfigFields();

  @NotNull
  ResolvedPropertyModel debuggable();

  @NotNull
  ResolvedPropertyModel embedMicroApp();

  @NotNull
  ResolvedPropertyModel jniDebuggable();

  @NotNull
  ResolvedPropertyModel minifyEnabled();

  @NotNull
  ResolvedPropertyModel pseudoLocalesEnabled();

  @NotNull
  ResolvedPropertyModel renderscriptDebuggable();

  @NotNull
  ResolvedPropertyModel renderscriptOptimLevel();

  @NotNull
  ResolvedPropertyModel shrinkResources();

  @NotNull
  ResolvedPropertyModel testCoverageEnabled();

  @NotNull
  ResolvedPropertyModel versionNameSuffix();

  @NotNull
  ResolvedPropertyModel zipAlignEnabled();

  /**
   * Represents a {@code buildConfigField} statement defined in the build type block of the Gradle file.
   */
  interface BuildConfigField extends TypeNameValueElement {

  }
}
