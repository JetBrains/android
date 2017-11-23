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
package com.android.tools.idea.gradle.dsl.api.android.sourceSets;

import com.android.tools.idea.gradle.dsl.api.values.GradleNotNullValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface SourceDirectoryModel {
  @NotNull
  String name();

  @Nullable
  List<GradleNotNullValue<String>> excludes();

  @NotNull
  SourceDirectoryModel addExclude(@NotNull String exclude);

  @NotNull
  SourceDirectoryModel removeExclude(@NotNull String exclude);

  @NotNull
  SourceDirectoryModel removeAllExcludes();

  @NotNull
  SourceDirectoryModel replaceExclude(@NotNull String oldExclude, @NotNull String newExclude);

  @Nullable
  List<GradleNotNullValue<String>> includes();

  @NotNull
  SourceDirectoryModel addInclude(@NotNull String include);

  @NotNull
  SourceDirectoryModel removeInclude(@NotNull String include);

  @NotNull
  SourceDirectoryModel removeAllIncludes();

  @NotNull
  SourceDirectoryModel replaceInclude(@NotNull String oldInclude, @NotNull String newInclude);

  @Nullable
  List<GradleNotNullValue<String>> srcDirs();

  @NotNull
  SourceDirectoryModel addSrcDir(@NotNull String srcDir);

  @NotNull
  SourceDirectoryModel removeSrcDir(@NotNull String srcDir);

  @NotNull
  SourceDirectoryModel removeAllSrcDirs();

  @NotNull
  SourceDirectoryModel replaceSrcDir(@NotNull String oldSrcDir, @NotNull String newSrcDir);
}
