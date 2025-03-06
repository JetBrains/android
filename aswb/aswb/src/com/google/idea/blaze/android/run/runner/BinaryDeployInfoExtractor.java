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
import com.google.idea.blaze.android.run.NativeSymbolFinder;
import com.google.idea.blaze.android.run.deployinfo.BlazeAndroidDeployInfo;
import com.google.idea.blaze.base.run.RuntimeArtifactCache;
import com.google.idea.blaze.base.run.RuntimeArtifactKind;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.sync.aspects.BlazeBuildOutputs;
import com.google.idea.blaze.common.Label;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.nio.file.Path;

/**
 * Deploy Info extractor for {@code android_binary} targets.
 */
public final class BinaryDeployInfoExtractor implements DeployInfoExtractor {
  private final Project project;
  private final boolean useMobileInstall;
  private final Label targetLabel;
  private final boolean nativeDebuggingEnabled;

  public BinaryDeployInfoExtractor(Project project, Label targetLabel, boolean useMobileInstall, boolean nativeDebuggingEnabled) {
    this.project = project;
    this.targetLabel = targetLabel;
    this.useMobileInstall = useMobileInstall;
    this.nativeDebuggingEnabled = nativeDebuggingEnabled;
  }

  @Override
  public BlazeAndroidDeployInfo extract(
    BlazeBuildOutputs buildOutputs,
    String deployInfoOutputGroups,
    String apkOutputGroup,
    BlazeContext context)
    throws IOException {

    String suffix = useMobileInstall ? "_mi.deployinfo.pb" : ".deployinfo.pb";

    DeployData deployData =
      DeployDataExtractor.extract(
        buildOutputs.getOutputGroupArtifacts(deployInfoOutputGroups),
        buildOutputs.getOutputGroupArtifacts(apkOutputGroup),
        suffix,
        context);
    RuntimeArtifactCache runtimeArtifactCache = RuntimeArtifactCache.getInstance(project);
    ImmutableList<File> localApks =
      runtimeArtifactCache.fetchArtifacts(targetLabel, deployData.apks(), context, RuntimeArtifactKind.APK).stream()
        .map(Path::toFile)
        .collect(toImmutableList());
    ImmutableList<File> nativeSymbols = fetchNativeSymbolArtifacts(buildOutputs, context);
    return new BlazeAndroidDeployInfo(
      deployData.mergedManifest(), /* testTargetMergedManifest */ null, localApks, nativeSymbols);
  }

  private ImmutableList<File> fetchNativeSymbolArtifacts(BlazeBuildOutputs buildOutputs, BlazeContext context) {
    ImmutableList<File> nativeSymbols = ImmutableList.of();
    if (nativeDebuggingEnabled) {
      nativeSymbols =
        NativeSymbolFinder.getInstances().stream()
          .flatMap(
            finder ->
              finder.getNativeSymbolsForBuild(project,
                                              context,
                                              com.google.idea.blaze.base.model.primitives.Label.create(targetLabel.toString()),
                                              buildOutputs)
                .stream()).collect(toImmutableList());
    }
    return nativeSymbols;
  }
}
