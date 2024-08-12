/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.cpp;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.ideinfo.CIdeInfo;
import com.google.idea.blaze.base.ideinfo.CToolchainIdeInfo;
import com.google.idea.blaze.base.model.primitives.ExecutionRootPath;
import java.util.Objects;

/** Data for clustering {@link BlazeResolveConfiguration} by "equivalence". */
final class BlazeResolveConfigurationData {

  final BlazeCompilerSettings compilerSettings;
  private final CToolchainIdeInfo toolchainIdeInfo;

  // Everything from CIdeInfo except for sources, headers, etc.
  // That is parts that influence the flags, but not the actual input files.
  final ImmutableList<String> localCopts;
  // From the cpp compilation context provider.
  // These should all be for the entire transitive closure.
  final ImmutableList<ExecutionRootPath> transitiveIncludeDirectories;
  final ImmutableList<ExecutionRootPath> transitiveQuoteIncludeDirectories;
  private final ImmutableList<String> transitiveDefines;
  final ImmutableList<ExecutionRootPath> transitiveSystemIncludeDirectories;

  static BlazeResolveConfigurationData create(
      CIdeInfo cIdeInfo,
      CToolchainIdeInfo toolchainIdeInfo,
      BlazeCompilerSettings compilerSettings) {
    return new BlazeResolveConfigurationData(compilerSettings, cIdeInfo, toolchainIdeInfo);
  }

  private BlazeResolveConfigurationData(
      BlazeCompilerSettings compilerSettings,
      CIdeInfo cIdeInfo,
      CToolchainIdeInfo toolchainIdeInfo) {
    this.toolchainIdeInfo = toolchainIdeInfo;
    this.compilerSettings = compilerSettings;

    this.transitiveIncludeDirectories = cIdeInfo.getTransitiveIncludeDirectories();
    this.transitiveSystemIncludeDirectories = cIdeInfo.getTransitiveSystemIncludeDirectories();
    this.transitiveQuoteIncludeDirectories = cIdeInfo.getTransitiveQuoteIncludeDirectories();
    this.transitiveDefines = cIdeInfo.getTransitiveDefines();
    this.localCopts = cIdeInfo.getLocalCopts();
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof BlazeResolveConfigurationData)) {
      return false;
    }
    BlazeResolveConfigurationData otherData = (BlazeResolveConfigurationData) other;
    return this.transitiveIncludeDirectories.equals(otherData.transitiveIncludeDirectories)
        && this.transitiveSystemIncludeDirectories.equals(
            otherData.transitiveSystemIncludeDirectories)
        && this.transitiveQuoteIncludeDirectories.equals(
            otherData.transitiveQuoteIncludeDirectories)
        && this.localCopts.equals(otherData.localCopts)
        && this.transitiveDefines.equals(otherData.transitiveDefines)
        && this.toolchainIdeInfo.equals(otherData.toolchainIdeInfo)
        && this.compilerSettings
            .getCompilerVersion()
            .equals(otherData.compilerSettings.getCompilerVersion());
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        transitiveIncludeDirectories,
        transitiveSystemIncludeDirectories,
        transitiveQuoteIncludeDirectories,
        localCopts,
        transitiveDefines,
        toolchainIdeInfo,
        compilerSettings.getCompilerVersion());
  }

  CToolchainIdeInfo getCToolchainIdeInfo() {
    return toolchainIdeInfo;
  }
}
