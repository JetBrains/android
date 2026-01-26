/*
 * Copyright 2025 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.run.runner;

import com.android.tools.idea.run.ApkProvisionException;
import com.google.idea.blaze.android.run.deployinfo.BlazeAndroidDeployInfo;
import com.google.idea.blaze.android.run.deployinfo.DeployData;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.sync.aspects.BlazeBuildOutputs;
import com.google.idea.blaze.common.Label;
import com.intellij.openapi.project.Project;

/**
 * Deploy Info extractor for {@code android_binary} targets.
 */
public final class BinaryDeployInfoExtractor implements DeployInfoExtractor {
  private final boolean useMobileInstall;
  private final Label targetLabel;
  private final boolean nativeDebuggingEnabled;
  private final String deployInfoOutputGroup;
  private final String apkOutputGroup;

  public BinaryDeployInfoExtractor(
      Label targetLabel,
      boolean useMobileInstall,
      boolean nativeDebuggingEnabled,
      String deployInfoOutputGroup,
      String apkOutputGroup) {
    this.targetLabel = targetLabel;
    this.useMobileInstall = useMobileInstall;
    this.nativeDebuggingEnabled = nativeDebuggingEnabled;
    this.deployInfoOutputGroup = deployInfoOutputGroup;
    this.apkOutputGroup = apkOutputGroup;
  }

  @Override
  public BlazeAndroidDeployInfo extract(
    Project project,
    BlazeBuildOutputs buildOutputs,
    BlazeContext context)
    throws ApkProvisionException {

    String suffix = useMobileInstall ? "_mi.deployinfo.pb" : ".deployinfo.pb";

    DeployData deployData =
      DeployDataExtractor.extract(
        targetLabel,
        buildOutputs.getOutputGroupArtifacts(deployInfoOutputGroup),
        buildOutputs.getOutputGroupArtifacts(apkOutputGroup),
        suffix,
        context,
        project);
    return BlazeAndroidDeployInfo.fetchDeployArtifacts(project, buildOutputs, deployData, null, nativeDebuggingEnabled, context);
  }
}
