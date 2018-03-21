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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface PackagingOptionsModel {
  @Nullable
  List<GradleNotNullValue<String>> excludes();

  @NotNull
  PackagingOptionsModel addExclude(@NotNull String exclude);

  @NotNull
  PackagingOptionsModel removeExclude(@NotNull String exclude);

  @NotNull
  PackagingOptionsModel removeAllExclude();

  @NotNull
  PackagingOptionsModel replaceExclude(@NotNull String oldExclude, @NotNull String newExclude);

  @Nullable
  List<GradleNotNullValue<String>> merges();

  @NotNull
  PackagingOptionsModel addMerge(@NotNull String merge);

  @NotNull
  PackagingOptionsModel removeMerge(@NotNull String merge);

  @NotNull
  PackagingOptionsModel removeAllMerges();

  @NotNull
  PackagingOptionsModel replaceMerge(@NotNull String oldMerge, @NotNull String newMerge);

  @Nullable
  List<GradleNotNullValue<String>> pickFirsts();

  @NotNull
  PackagingOptionsModel addPickFirst(@NotNull String pickFirst);

  @NotNull
  PackagingOptionsModel removePickFirst(@NotNull String pickFirst);

  @NotNull
  PackagingOptionsModel removeAllPickFirsts();

  @NotNull
  PackagingOptionsModel replacePickFirst(@NotNull String oldPickFirst, @NotNull String newPickFirst);
}
