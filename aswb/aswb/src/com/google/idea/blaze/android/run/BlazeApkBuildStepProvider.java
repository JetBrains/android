/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.google.idea.blaze.android.run;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.android.run.runner.AitDeployInfoExtractor;
import com.google.idea.blaze.android.run.runner.ApkBuildStep;
import com.google.idea.blaze.android.run.runner.BinaryDeployInfoExtractor;
import com.google.idea.blaze.android.run.runner.BlazeApkBuildStep;
import com.google.idea.blaze.android.run.runner.InstrumentationInfo;
import com.google.idea.blaze.android.run.runner.InstrumentationInfo.InstrumentationParserException;
import com.google.idea.blaze.base.bazel.BuildSystem.BuildInvoker;
import com.google.idea.blaze.base.bazel.BuildSystem.BuildInvoker.Capability;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.intellij.execution.ExecutionException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;

import java.util.Objects;
import java.util.stream.Stream;

/**
 * Provides APK build steps for Blaze projects.
 *
 * <p>See {@link BazelApkBuildStepProvider} for the Bazel equivalent. The main reason for splitting
 * the build steps by build system is that we are introducing a lot more Blaze specific features
 * into the build pipeline: in particular, we support remote builds via mdproxy initially, and then
 * via rabbit, both of which are specific to Google3.
 */
public class BlazeApkBuildStepProvider implements ApkBuildStepProvider {
  private static final Logger logger = Logger.getInstance(BlazeApkBuildStepProvider.class);

  @Override
  public ApkBuildStep getBinaryBuildStep(
      Project project,
      boolean useMobileInstall,
      boolean nativeDebuggingEnabled,
      Label label,
      ImmutableList<String> blazeFlags,
      ImmutableList<String> exeFlags,
      String launchId) {
      BuildInvoker buildInvoker =
          Blaze.getBuildSystemProvider(project)
              .getBuildSystem()
              .getBuildInvoker(project, ImmutableSet.of(Capability.RUN_REMOTE_QUERIES))
              .orElseThrow();

       return BlazeApkBuildStep.blazeApkBuildStepBuilder()
                .setProject(project)
                .setTargets(ImmutableList.of(label))
                .setBlazeFlags(blazeFlags)
                .setExeFlags(exeFlags)
                .setUseMobileInstall(useMobileInstall)
                .setNativeDebuggingEnabled(nativeDebuggingEnabled)
                .setLaunchId(launchId)
                .setBuildInvoker(buildInvoker)
                .setDeployInfoExtractor(
                        new BinaryDeployInfoExtractor(
                                project,
                                com.google.idea.blaze.common.Label.of(label.toString()),
                                useMobileInstall,
                                nativeDebuggingEnabled))
                .build();
  }

  @Override
  public ApkBuildStep getAitBuildStep(
      Project project,
      boolean useMobileInstall,
      boolean nativeDebuggingEnabled,
      Label label,
      ImmutableList<String> blazeFlags,
      ImmutableList<String> exeFlags,
      String launchId)
      throws ExecutionException {
    BlazeProjectData data =
        Preconditions.checkNotNull(
            BlazeProjectDataManager.getInstance(project).getBlazeProjectData());
    InstrumentationInfo info;
    try {
      info = InstrumentationInfo.getInstrumentationInfo(label, data);
    } catch (InstrumentationParserException e) {
      logger.warn("Could not get instrumentation info: " + e.getMessage());
      throw new ExecutionException(e.getMessage(), e);
    }

    ImmutableList<Label> targets =
        Stream.of(info.targetApp, info.testApp)
            .filter(Objects::nonNull)
            .collect(ImmutableList.toImmutableList());
    BuildInvoker buildInvoker =
        Blaze.getBuildSystemProvider(project).getBuildSystem().getBuildInvoker(project);
    return BlazeApkBuildStep.blazeApkBuildStepBuilder()
        .setProject(project)
        .setTargets(targets)
        .setBlazeFlags(blazeFlags)
        .setExeFlags(exeFlags)
        .setUseMobileInstall(useMobileInstall)
        .setLaunchId(launchId)
        .setBuildInvoker(buildInvoker)
        .setDeployInfoExtractor(new AitDeployInfoExtractor(project, info))
        .build();
  }

  @Override
  public ImmutableSet<BuildSystemName> getSupportedBuildSystems() {
    return ImmutableSet.of(BuildSystemName.Blaze);
  }
}
