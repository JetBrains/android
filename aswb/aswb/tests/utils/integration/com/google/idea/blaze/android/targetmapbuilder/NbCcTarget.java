/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.targetmapbuilder;

import static com.google.idea.blaze.android.targetmapbuilder.NbTargetMapUtils.makeSourceArtifact;

import com.google.idea.blaze.base.ideinfo.CIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.model.primitives.ExecutionRootPath;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.cpp.CppBlazeRules;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Builder for a blaze C/C++ target's IDE info. Defines common attributes across C/C++ targets. This
 * builder accumulates attributes to a {@link TargetIdeInfo.Builder} which can be used to build
 * {@link TargetMap}.
 *
 * <p>Targets built with {@link NbCcTarget} always have a {@link CIdeInfo} attached, even if it's
 * empty.
 */
public class NbCcTarget extends NbBaseTargetBuilder {
  private final NbTarget target;
  private final CIdeInfo.Builder cIdeInfoBuilder;
  private final WorkspacePath blazePackage;

  public static NbCcTarget cc_library(String label) {
    return cc_library(label, BlazeInfoData.DEFAULT);
  }

  public static NbCcTarget cc_library(String label, BlazeInfoData blazeInfoData) {
    return new NbCcTarget(blazeInfoData, label, CppBlazeRules.RuleTypes.CC_LIBRARY.getKind());
  }

  NbCcTarget(BlazeInfoData blazeInfoData, String label, Kind kind) {
    super(blazeInfoData);
    target = new NbTarget(blazeInfoData, label, kind);
    cIdeInfoBuilder = new CIdeInfo.Builder();
    this.blazePackage = NbTargetMapUtils.blazePackageForLabel(label);
  }

  @Override
  public TargetIdeInfo.Builder getIdeInfoBuilder() {
    return target.getIdeInfoBuilder().setCInfo(cIdeInfoBuilder);
  }

  /**
   * Add files pointed by given labels as sources. Note: {@link NbCcTarget#src(java.lang.String...)}
   * also adds a source entry to the target's {@link CIdeInfo}, unlike most other src() functions.
   */
  public NbCcTarget src(String... sourceLabels) {
    target.src(sourceLabels);

    // CC targets also add their sources to C ide info.
    for (String sourceLabel : sourceLabels) {
      String sourcePath = NbTargetMapUtils.workspacePathForLabel(blazePackage, sourceLabel);
      cIdeInfoBuilder.addSource(makeSourceArtifact(sourcePath));
    }
    return this;
  }

  public NbCcTarget dep(String... targetLabels) {
    target.dep(targetLabels);
    return this;
  }

  public NbCcTarget transitive_quote_include_dirs(String... directories) {
    cIdeInfoBuilder.addTransitiveQuoteIncludeDirectories(
        Arrays.stream(directories).map(ExecutionRootPath::new).collect(Collectors.toList()));
    return this;
  }

  public NbCcTarget transitive_system_include_dirs(String... directories) {
    cIdeInfoBuilder.addTransitiveSystemIncludeDirectories(
        Arrays.stream(directories).map(ExecutionRootPath::new).collect(Collectors.toList()));
    return this;
  }
}
