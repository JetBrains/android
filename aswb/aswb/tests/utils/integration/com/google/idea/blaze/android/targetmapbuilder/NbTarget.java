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

import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.JavaToolchainIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;

/**
 * Builder for a generic blaze target's IDE info. Defines common attributes across all generic blaze
 * targets. This builder accumulates attributes to a {@link TargetIdeInfo.Builder} which can be used
 * to build {@link TargetMap}.
 *
 * <p>During initialization, NbTarget infers the BUILD file the target belongs to from its label.
 */
public class NbTarget extends NbBaseTargetBuilder {
  private final TargetIdeInfo.Builder targetIdeInfoBuilder;
  private final WorkspacePath blazePackage;

  public NbTarget(BlazeInfoData blazeInfoData, String label, Kind kind) {
    super(blazeInfoData);
    this.targetIdeInfoBuilder = new TargetIdeInfo.Builder();
    this.blazePackage = NbTargetMapUtils.blazePackageForLabel(label);

    targetIdeInfoBuilder.setKind(kind).setLabel(label).setBuildFile(inferBuildFileLocation(label));
  }

  public NbTarget src(String... sourceLabels) {
    for (String sourceLabel : sourceLabels) {
      String sourcePath = NbTargetMapUtils.workspacePathForLabel(blazePackage, sourceLabel);
      targetIdeInfoBuilder.addSource(makeSourceArtifact(sourcePath));
    }
    return this;
  }

  public NbTarget dep(String... targetLabels) {
    for (String targetLabel : targetLabels) {
      targetIdeInfoBuilder.addDependency(targetLabel);
    }
    return this;
  }

  /** Sets both source and target versions. */
  public NbTarget java_toolchain_version(String version) {
    targetIdeInfoBuilder.setJavaToolchainIdeInfo(
        JavaToolchainIdeInfo.builder().setSourceVersion(version).setTargetVersion(version));
    return this;
  }

  @Override
  public TargetIdeInfo.Builder getIdeInfoBuilder() {
    return targetIdeInfoBuilder;
  }

  private ArtifactLocation inferBuildFileLocation(String label) {
    return makeSourceArtifact(NbTargetMapUtils.blazePackageForLabel(label) + "/BUILD");
  }
}
