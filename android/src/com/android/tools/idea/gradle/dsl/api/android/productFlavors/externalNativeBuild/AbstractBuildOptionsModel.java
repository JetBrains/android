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
package com.android.tools.idea.gradle.dsl.api.android.productFlavors.externalNativeBuild;

import com.android.tools.idea.gradle.dsl.api.values.GradleNotNullValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface AbstractBuildOptionsModel {
  @Nullable
  List<GradleNotNullValue<String>> abiFilters();

  @NotNull
  AbstractBuildOptionsModel addAbiFilter(@NotNull String abiFilter);

  @NotNull
  AbstractBuildOptionsModel removeAbiFilter(@NotNull String abiFilter);

  @NotNull
  AbstractBuildOptionsModel removeAllAbiFilters();

  @NotNull
  AbstractBuildOptionsModel replaceAbiFilter(@NotNull String oldAbiFilter, @NotNull String newAbiFilter);

  @Nullable
  List<GradleNotNullValue<String>> arguments();

  @NotNull
  AbstractBuildOptionsModel addArgument(@NotNull String argument);

  @NotNull
  AbstractBuildOptionsModel removeArgument(@NotNull String argument);

  @NotNull
  AbstractBuildOptionsModel removeAllArguments();

  @NotNull
  AbstractBuildOptionsModel replaceArgument(@NotNull String oldArgument, @NotNull String newArgument);

  @Nullable
  List<GradleNotNullValue<String>> cFlags();

  @NotNull
  AbstractBuildOptionsModel addCFlag(@NotNull String cFlag);

  @NotNull
  AbstractBuildOptionsModel removeCFlag(@NotNull String cFlag);

  @NotNull
  AbstractBuildOptionsModel removeAllCFlags();

  @NotNull
  AbstractBuildOptionsModel replaceCFlag(@NotNull String oldCFlag, @NotNull String newCFlag);

  @Nullable
  List<GradleNotNullValue<String>> cppFlags();

  @NotNull
  AbstractBuildOptionsModel addCppFlag(@NotNull String cppFlag);

  @NotNull
  AbstractBuildOptionsModel removeCppFlag(@NotNull String cppFlag);

  @NotNull
  AbstractBuildOptionsModel removeAllCppFlags();

  @NotNull
  AbstractBuildOptionsModel replaceCppFlag(@NotNull String oldCppFlag, @NotNull String newCppFlag);

  @Nullable
  List<GradleNotNullValue<String>> targets();

  @NotNull
  AbstractBuildOptionsModel addTarget(@NotNull String target);

  @NotNull
  AbstractBuildOptionsModel removeTarget(@NotNull String target);

  @NotNull
  AbstractBuildOptionsModel removeAllTargets();

  @NotNull
  AbstractBuildOptionsModel replaceTarget(@NotNull String oldTarget, @NotNull String newTarget);
}