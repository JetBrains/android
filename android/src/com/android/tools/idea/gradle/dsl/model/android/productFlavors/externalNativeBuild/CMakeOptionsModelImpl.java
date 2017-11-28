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

import com.android.tools.idea.gradle.dsl.api.android.productFlavors.externalNativeBuild.CMakeOptionsModel;
import com.android.tools.idea.gradle.dsl.parser.android.productFlavors.externalNativeBuild.CMakeOptionsDslElement;
import org.jetbrains.annotations.NotNull;

public class CMakeOptionsModelImpl extends AbstractBuildOptionsModelImpl implements CMakeOptionsModel {
  public CMakeOptionsModelImpl(@NotNull CMakeOptionsDslElement dslElement) {
    super(dslElement);
  }

  @Override
  @NotNull
  public CMakeOptionsModel addAbiFilter(@NotNull String abiFilter) {
    return (CMakeOptionsModel)super.addAbiFilter(abiFilter);
  }

  @Override
  @NotNull
  public CMakeOptionsModel removeAbiFilter(@NotNull String abiFilter) {
    return (CMakeOptionsModel)super.removeAbiFilter(abiFilter);
  }

  @Override
  @NotNull
  public CMakeOptionsModel removeAllAbiFilters() {
    return (CMakeOptionsModel)super.removeAllAbiFilters();
  }

  @Override
  @NotNull
  public CMakeOptionsModel replaceAbiFilter(@NotNull String oldAbiFilter, @NotNull String newAbiFilter) {
    return (CMakeOptionsModel)super.replaceAbiFilter(oldAbiFilter, newAbiFilter);
  }

  @Override
  @NotNull
  public CMakeOptionsModel addArgument(@NotNull String argument) {
    return (CMakeOptionsModel)super.addArgument(argument);
  }

  @Override
  @NotNull
  public CMakeOptionsModel removeArgument(@NotNull String argument) {
    return (CMakeOptionsModel)super.removeArgument(argument);
  }

  @Override
  @NotNull
  public CMakeOptionsModel removeAllArguments() {
    return (CMakeOptionsModel)super.removeAllArguments();
  }

  @Override
  @NotNull
  public CMakeOptionsModel replaceArgument(@NotNull String oldArgument, @NotNull String newArgument) {
    return (CMakeOptionsModel)super.replaceArgument(oldArgument, newArgument);
  }

  @Override
  @NotNull
  public CMakeOptionsModel addCFlag(@NotNull String cFlag) {
    return (CMakeOptionsModel)super.addCFlag(cFlag);
  }

  @Override
  @NotNull
  public CMakeOptionsModel removeCFlag(@NotNull String cFlag) {
    return (CMakeOptionsModel)super.removeCFlag(cFlag);
  }

  @Override
  @NotNull
  public CMakeOptionsModel removeAllCFlags() {
    return (CMakeOptionsModel)super.removeAllCFlags();
  }

  @Override
  @NotNull
  public CMakeOptionsModel replaceCFlag(@NotNull String oldCFlag, @NotNull String newCFlag) {
    return (CMakeOptionsModel)super.replaceCFlag(oldCFlag, newCFlag);
  }

  @Override
  @NotNull
  public CMakeOptionsModel addCppFlag(@NotNull String cppFlag) {
    return (CMakeOptionsModel)super.addCppFlag(cppFlag);
  }

  @Override
  @NotNull
  public CMakeOptionsModel removeCppFlag(@NotNull String cppFlag) {
    return (CMakeOptionsModel)super.removeCppFlag(cppFlag);
  }

  @Override
  @NotNull
  public CMakeOptionsModel removeAllCppFlags() {
    return (CMakeOptionsModel)super.removeAllCppFlags();
  }

  @Override
  @NotNull
  public CMakeOptionsModel replaceCppFlag(@NotNull String oldCppFlag, @NotNull String newCppFlag) {
    return (CMakeOptionsModel)super.replaceCppFlag(oldCppFlag, newCppFlag);
  }

  @Override
  @NotNull
  public CMakeOptionsModel addTarget(@NotNull String target) {
    return (CMakeOptionsModel)super.addTarget(target);
  }

  @Override
  @NotNull
  public CMakeOptionsModel removeTarget(@NotNull String target) {
    return (CMakeOptionsModel)super.removeTarget(target);
  }

  @Override
  @NotNull
  public CMakeOptionsModel removeAllTargets() {
    return (CMakeOptionsModel)super.removeAllTargets();
  }

  @Override
  @NotNull
  public CMakeOptionsModel replaceTarget(@NotNull String oldTarget, @NotNull String newTarget) {
    return (CMakeOptionsModel)super.replaceTarget(oldTarget, newTarget);
  }
}
