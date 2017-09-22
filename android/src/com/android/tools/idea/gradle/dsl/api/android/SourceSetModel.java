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

import com.android.tools.idea.gradle.dsl.api.android.sourceSets.SourceDirectoryModel;
import com.android.tools.idea.gradle.dsl.api.android.sourceSets.SourceFileModel;
import com.android.tools.idea.gradle.dsl.api.values.GradleNullableValue;
import org.jetbrains.annotations.NotNull;

public interface SourceSetModel {
  @NotNull
  String name();

  @NotNull
  GradleNullableValue<String> root();

  @NotNull
  SourceSetModel setRoot(@NotNull String root);

  @NotNull
  SourceSetModel removeRoot();

  @NotNull
  SourceDirectoryModel aidl();

  @NotNull
  SourceSetModel removeAidl();

  @NotNull
  SourceDirectoryModel assets();

  @NotNull
  SourceSetModel removeAssets();

  @NotNull
  SourceDirectoryModel java();

  @NotNull
  SourceSetModel removeJava();

  @NotNull
  SourceDirectoryModel jni();

  @NotNull
  SourceSetModel removeJni();

  @NotNull
  SourceDirectoryModel jniLibs();

  @NotNull
  SourceSetModel removeJniLibs();

  @NotNull
  SourceFileModel manifest();

  @NotNull
  SourceSetModel removeManifest();

  @NotNull
  SourceDirectoryModel renderscript();

  @NotNull
  SourceSetModel removeRenderscript();

  @NotNull
  SourceDirectoryModel res();

  @NotNull
  SourceSetModel removeRes();

  @NotNull
  SourceDirectoryModel resources();

  @NotNull
  SourceSetModel removeResources();
}
