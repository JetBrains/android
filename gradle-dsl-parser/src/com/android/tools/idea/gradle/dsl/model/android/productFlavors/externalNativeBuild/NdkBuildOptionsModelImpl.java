/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.model.android.productFlavors.externalNativeBuild;

import com.android.tools.idea.gradle.dsl.api.android.productFlavors.externalNativeBuild.NdkBuildOptionsModel;
import com.android.tools.idea.gradle.dsl.parser.android.productFlavors.externalNativeBuild.NdkBuildOptionsDslElement;
import org.jetbrains.annotations.NotNull;

public class NdkBuildOptionsModelImpl extends AbstractBuildOptionsModelImpl implements NdkBuildOptionsModel {
  public NdkBuildOptionsModelImpl(@NotNull NdkBuildOptionsDslElement dslElement) {
    super(dslElement);
  }

  @Override
  @NotNull
  public NdkBuildOptionsModel addAbiFilter(@NotNull String abiFilter) {
    return (NdkBuildOptionsModel)super.addAbiFilter(abiFilter);
  }

  @Override
  @NotNull
  public NdkBuildOptionsModel removeAbiFilter(@NotNull String abiFilter) {
    return (NdkBuildOptionsModel)super.removeAbiFilter(abiFilter);
  }

  @Override
  @NotNull
  public NdkBuildOptionsModel removeAllAbiFilters() {
    return (NdkBuildOptionsModel)super.removeAllAbiFilters();
  }

  @Override
  @NotNull
  public NdkBuildOptionsModel replaceAbiFilter(@NotNull String oldAbiFilter, @NotNull String newAbiFilter) {
    return (NdkBuildOptionsModel)super.replaceAbiFilter(oldAbiFilter, newAbiFilter);
  }

  @Override
  @NotNull
  public NdkBuildOptionsModel addArgument(@NotNull String argument) {
    return (NdkBuildOptionsModel)super.addArgument(argument);
  }

  @Override
  @NotNull
  public NdkBuildOptionsModel removeArgument(@NotNull String argument) {
    return (NdkBuildOptionsModel)super.removeArgument(argument);
  }

  @Override
  @NotNull
  public NdkBuildOptionsModel removeAllArguments() {
    return (NdkBuildOptionsModel)super.removeAllArguments();
  }

  @Override
  @NotNull
  public NdkBuildOptionsModel replaceArgument(@NotNull String oldArgument, @NotNull String newArgument) {
    return (NdkBuildOptionsModel)super.replaceArgument(oldArgument, newArgument);
  }

  @Override
  @NotNull
  public NdkBuildOptionsModel addCFlag(@NotNull String cFlag) {
    return (NdkBuildOptionsModel)super.addCFlag(cFlag);
  }

  @Override
  @NotNull
  public NdkBuildOptionsModel removeCFlag(@NotNull String cFlag) {
    return (NdkBuildOptionsModel)super.removeCFlag(cFlag);
  }

  @Override
  @NotNull
  public NdkBuildOptionsModel removeAllCFlags() {
    return (NdkBuildOptionsModel)super.removeAllCFlags();
  }

  @Override
  @NotNull
  public NdkBuildOptionsModel replaceCFlag(@NotNull String oldCFlag, @NotNull String newCFlag) {
    return (NdkBuildOptionsModel)super.replaceCFlag(oldCFlag, newCFlag);
  }

  @Override
  @NotNull
  public NdkBuildOptionsModel addCppFlag(@NotNull String cppFlag) {
    return (NdkBuildOptionsModel)super.addCppFlag(cppFlag);
  }

  @Override
  @NotNull
  public NdkBuildOptionsModel removeCppFlag(@NotNull String cppFlag) {
    return (NdkBuildOptionsModel)super.removeCppFlag(cppFlag);
  }

  @Override
  @NotNull
  public NdkBuildOptionsModel removeAllCppFlags() {
    return (NdkBuildOptionsModel)super.removeAllCppFlags();
  }

  @Override
  @NotNull
  public NdkBuildOptionsModel replaceCppFlag(@NotNull String oldCppFlag, @NotNull String newCppFlag) {
    return (NdkBuildOptionsModel)super.replaceCppFlag(oldCppFlag, newCppFlag);
  }

  @Override
  @NotNull
  public NdkBuildOptionsModel addTarget(@NotNull String target) {
    return (NdkBuildOptionsModel)super.addTarget(target);
  }

  @Override
  @NotNull
  public NdkBuildOptionsModel removeTarget(@NotNull String target) {
    return (NdkBuildOptionsModel)super.removeTarget(target);
  }

  @Override
  @NotNull
  public NdkBuildOptionsModel removeAllTargets() {
    return (NdkBuildOptionsModel)super.removeAllTargets();
  }

  @Override
  @NotNull
  public NdkBuildOptionsModel replaceTarget(@NotNull String oldTarget, @NotNull String newTarget) {
    return (NdkBuildOptionsModel)super.replaceTarget(oldTarget, newTarget);
  }
}
