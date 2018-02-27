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
import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo;
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.dsl.api.ext.SigningConfigPropertyModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface BuildTypeModel extends FlavorTypeModel {
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

  /**
   * You most likely want to set this property as a reference to a signing config,
   * to do this please use {@link ReferenceTo#ReferenceTo(SigningConfigModel)}.
   *
   * You can obtain a list of signing configs from {@link AndroidModel#signingConfigs()}
   */
  @NotNull
  SigningConfigPropertyModel signingConfig();

  @NotNull
  ResolvedPropertyModel testCoverageEnabled();

  @NotNull
  ResolvedPropertyModel zipAlignEnabled();
}
