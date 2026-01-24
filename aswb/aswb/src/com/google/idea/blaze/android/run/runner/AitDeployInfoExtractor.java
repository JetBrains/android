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

import static com.google.idea.blaze.android.run.NativeSymbolFinder.fetchNativeSymbols;

import com.android.tools.idea.run.ApkProvisionException;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.android.run.deployinfo.BlazeAndroidDeployInfo;
import com.google.idea.blaze.android.run.deployinfo.DeployData;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.sync.aspects.BlazeBuildOutputs;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.common.artifact.OutputArtifact;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.List;
import javax.annotation.Nullable;

/** Extracts {@link BlazeAndroidDeployInfo} for {@code android_instrumentation_test} builds. */
public final class AitDeployInfoExtractor implements DeployInfoExtractor {
  private final Project project;
  private final InstrumentationInfo instrumentationInfo;
  private final boolean nativeDebuggingEnabled;
  private final String deployInfoOutputGroup;
  private final String apkOutputGroup;

  public AitDeployInfoExtractor(
      Project project,
      InstrumentationInfo instrumentationInfo,
      boolean nativeDebuggingEnabled,
      String deployInfoOutputGroup,
      String apkOutputGroup) {
    this.project = project;
    this.instrumentationInfo = instrumentationInfo;
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
    DeployData testData =
        deployDataForTarget(
            instrumentationInfo.testApp,
            buildOutputs,
            deployInfoOutputGroup,
            apkOutputGroup,
            context);
    DeployData targetData = null;
    if (instrumentationInfo.targetApp != null) {
      targetData =
          deployDataForTarget(
              instrumentationInfo.targetApp,
              buildOutputs,
              deployInfoOutputGroup,
              apkOutputGroup,
              context);
    }
    var nativeSymbols =
      nativeDebuggingEnabled ? fetchNativeSymbols(project, context, instrumentationInfo.testApp, buildOutputs) : ImmutableList.<File>of();
    return merge(testData, targetData, context, nativeSymbols);
  }

  private DeployData deployDataForTarget(
      Label label,
      BlazeBuildOutputs buildOutputs,
      String deployInfoOutputGroups,
      String apkOutputGroup,
      BlazeContext context) throws ApkProvisionException {
    ImmutableList<OutputArtifact> infoArtifacts =
        buildOutputs.getOutputGroupTargetArtifacts(deployInfoOutputGroups, label.toString());
    ImmutableList<OutputArtifact> apkArtifacts =
        buildOutputs.getOutputGroupTargetArtifacts(apkOutputGroup, label.toString());
    return DeployDataExtractor.extract(
      label, infoArtifacts.asList(), apkArtifacts.asList(), "deployinfo.pb", context, project);
  }

  private BlazeAndroidDeployInfo merge(
      DeployData testData,
      @Nullable DeployData targetData,
      BlazeContext context,
      List<? extends File> nativeSymbols) throws ApkProvisionException {
    var mainAppPackage = DeployData.fetchApks(testData, project, context);
    BlazeAndroidDeployInfo.ManifestWithApkInfo testTargetAppPackage = null;
    if (targetData != null) {
      testTargetAppPackage = DeployData.fetchApks(targetData, project, context);
    }
    return BlazeAndroidDeployInfo.createBlazeAndroidDeployInfo(mainAppPackage, testTargetAppPackage, ImmutableList.copyOf(nativeSymbols));
  }
}
