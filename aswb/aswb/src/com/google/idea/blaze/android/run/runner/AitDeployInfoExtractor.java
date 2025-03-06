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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.android.manifest.ManifestParser.ParsedManifest;
import com.google.idea.blaze.base.run.RuntimeArtifactCache;
import com.google.idea.blaze.android.run.deployinfo.BlazeAndroidDeployInfo;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.run.RuntimeArtifactKind;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.sync.aspects.BlazeBuildOutputs;
import com.google.idea.blaze.common.artifact.OutputArtifact;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import javax.annotation.Nullable;

/** Extracts {@link BlazeAndroidDeployInfo} for {@code android_instrumentation_test} builds. */
public final class AitDeployInfoExtractor implements DeployInfoExtractor {
  private final Project project;
  private final InstrumentationInfo instrumentationInfo;

  public AitDeployInfoExtractor(Project project, InstrumentationInfo instrumentationInfo) {
    this.project = project;
    this.instrumentationInfo = instrumentationInfo;
  }

  @Override
  public BlazeAndroidDeployInfo extract(
      BlazeBuildOutputs buildOutputs,
      String deployInfoOutputGroups,
      String apkOutputGroup,
      BlazeContext context)
      throws IOException {
    DeployData testData =
        deployDataForTarget(
            instrumentationInfo.testApp,
            buildOutputs,
            deployInfoOutputGroups,
            apkOutputGroup,
            context);
    DeployData targetData = null;
    if (instrumentationInfo.targetApp != null) {
      targetData =
          deployDataForTarget(
              instrumentationInfo.targetApp,
              buildOutputs,
              deployInfoOutputGroups,
              apkOutputGroup,
              context);
    }
    return merge(testData, targetData, context);
  }

  private DeployData deployDataForTarget(
      Label label,
      BlazeBuildOutputs buildOutputs,
      String deployInfoOutputGroups,
      String apkOutputGroup,
      BlazeContext context)
      throws IOException {
    ImmutableList<OutputArtifact> infoArtifacts =
        buildOutputs.getOutputGroupTargetArtifacts(deployInfoOutputGroups, label.toString());
    ImmutableList<OutputArtifact> apkArtifacts =
        buildOutputs.getOutputGroupTargetArtifacts(apkOutputGroup, label.toString());
    return DeployDataExtractor.extract(
        infoArtifacts.asList(), apkArtifacts.asList(), "deployinfo.pb", context);
  }

  private BlazeAndroidDeployInfo merge(
      DeployData testData, @Nullable DeployData targetData, BlazeContext context) {
    ParsedManifest targetManifest = targetData == null ? null : targetData.mergedManifest();

    ImmutableList.Builder<File> apks = new ImmutableList.Builder<File>();
    apks.addAll(cacheLocally(instrumentationInfo.testApp, testData.apks(), context));
    if (targetData != null) {
      apks.addAll(cacheLocally(instrumentationInfo.targetApp, targetData.apks(), context));
    }
    return new BlazeAndroidDeployInfo(testData.mergedManifest(), targetManifest, apks.build());
  }

  private ImmutableList<File> cacheLocally(
      Label targetLabel, ImmutableList<OutputArtifact> artifacts, BlazeContext context) {
    RuntimeArtifactCache runtimeArtifactCache = RuntimeArtifactCache.getInstance(project);
    return runtimeArtifactCache
        .fetchArtifacts(
          com.google.idea.blaze.common.Label.of(targetLabel.toString()), artifacts, context, RuntimeArtifactKind.APK)
        .stream()
        .map(Path::toFile)
        .collect(toImmutableList());
  }
}
