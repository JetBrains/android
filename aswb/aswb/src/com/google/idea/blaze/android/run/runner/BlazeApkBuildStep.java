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

import static com.google.idea.blaze.android.run.LaunchMetrics.logBuildTime;

import com.android.tools.idea.run.ApkProvisionException;
import com.google.auto.value.AutoBuilder;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.android.run.binary.mobileinstall.StudioDeployerExperiment;
import com.google.idea.blaze.android.run.deployinfo.BlazeAndroidDeployInfo;
import com.google.idea.blaze.android.run.runner.BlazeAndroidDeviceSelector.DeviceSession;
import com.google.idea.blaze.base.bazel.BazelExitCodeException;
import com.google.idea.blaze.base.bazel.BuildSystem.BuildInvoker;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.buildresult.BuildResultParser;
import com.google.idea.blaze.base.command.buildresult.bepparser.BuildEventStreamProvider;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.scope.output.StatusOutput;
import com.google.idea.blaze.base.sync.aspects.BlazeBuildOutputs;
import com.google.idea.blaze.base.util.SaveUtil;
import com.google.idea.blaze.common.Interners;
import com.google.idea.blaze.exception.BuildException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import java.io.IOException;

/** Blaze specific build flow for android_binary builds. */
public class BlazeApkBuildStep implements ApkBuildStep {
  private static final Logger logger = Logger.getInstance(BlazeApkBuildStep.class);

  private final Project project;
  private final ImmutableList<Label> targets;
  private final ImmutableList<String> blazeFlags;
  private final ImmutableList<String> exeFlags;
  private final String launchId;
  private final boolean useMobileInstall;
  private final BuildInvoker buildInvoker;
  private final DeployInfoExtractor deployInfoExtractor;

  private BlazeAndroidDeployInfo blazeAndroidDeployInfo;

  BlazeApkBuildStep(
      Project project,
      ImmutableList<Label> targets,
      ImmutableList<String> blazeFlags,
      ImmutableList<String> exeFlags,
      boolean useMobileInstall,
      String launchId,
      BuildInvoker buildInvoker,
      DeployInfoExtractor deployInfoExtractor) {
    this.project = project;
    this.targets = targets;
    this.blazeFlags = blazeFlags;
    this.exeFlags = exeFlags;
    this.useMobileInstall = useMobileInstall;
    this.launchId = launchId;
    this.buildInvoker = buildInvoker;
    this.deployInfoExtractor = deployInfoExtractor;
  }

  /**
   * Builds the android_binary, and save the deployment information such that it can be retrieved by
   * {@link #getDeployInfo()}.
   */
  @Override
  public void build(BlazeContext context, DeviceSession deviceSession) {
    SaveUtil.saveAllFiles();

    context.output(new StatusOutput("Building Application."));
    BlazeBuildOutputs buildOutputs;
    Stopwatch stopwatch = Stopwatch.createStarted();
    String deployOutputGroup;
    String apkOutputGroup;
    BlazeCommandName commandName =
        useMobileInstall ? BlazeCommandName.MOBILE_INSTALL : BlazeCommandName.BUILD;
    BlazeCommand.Builder command =
        BlazeCommand.builder(buildInvoker, commandName)
            .addTargets(targets)
            .addBlazeFlags(blazeFlags)
            .addExeFlags(exeFlags);
    if (useMobileInstall) {
      // deploy_info.pb and .apk files are in mobile_install_INTERNAL_ output group.
      deployOutputGroup = "mobile_install_INTERNAL_";
      apkOutputGroup = "mobile_install_INTERNAL_";
      command.addExeFlags("--nolaunch_app", "--nodeploy");
    } else {
      // deploy_info.pb is in android_deploy_info output group and .apk files are in the default.
      deployOutputGroup = "android_deploy_info";
      apkOutputGroup = "default";
      command.addBlazeFlags("--output_groups=+android_deploy_info");
    }
    try (BuildEventStreamProvider streamProvider = buildInvoker.invoke(command, context)) {
      buildOutputs =
          BlazeBuildOutputs.fromParsedBepOutput(
              BuildResultParser.getBuildOutput(streamProvider, Interners.STRING));
      logBuildTime(
          launchId,
          StudioDeployerExperiment.isEnabled(),
          stopwatch.elapsed(),
          buildOutputs.buildResult().exitCode,
          ImmutableMap.of());
      BazelExitCodeException.throwIfFailed(command, buildOutputs.buildResult());
      logger.info("Finished build, id: " + buildOutputs.idForLogging());
      context.output(new StatusOutput("Build complete."));
    } catch (BuildException e) {
      context.handleException("Failed to build APK", e);
      return;
    }

    try {
      blazeAndroidDeployInfo =
          deployInfoExtractor.extract(buildOutputs, deployOutputGroup, apkOutputGroup, context);
    } catch (IOException e) {
      logger.warn("Unexpected error while retrieving deploy info", e);
      String message = "Error retrieving deployment info from build results: " + e.getMessage();
      IssueOutput.error(message).submit(context);
      return;
    }

    context.output(new StatusOutput("Deployment information parsed from build artifacts."));
  }

  @Override
  public BlazeAndroidDeployInfo getDeployInfo() throws ApkProvisionException {
    return blazeAndroidDeployInfo;
  }

  public static Builder blazeApkBuildStepBuilder() {
    return new AutoBuilder_BlazeApkBuildStep_Builder();
  }

  /** Builder for {@link BlazeApkBuildStep}. */
  @AutoBuilder(ofClass = BlazeApkBuildStep.class)
  public interface Builder {
    Builder setProject(Project p);

    Builder setTargets(ImmutableList<Label> targets);

    Builder setBlazeFlags(ImmutableList<String> flags);

    Builder setExeFlags(ImmutableList<String> flags);

    Builder setUseMobileInstall(boolean v);

    Builder setLaunchId(String id);

    Builder setBuildInvoker(BuildInvoker invoker);

    Builder setDeployInfoExtractor(DeployInfoExtractor extractor);

    BlazeApkBuildStep build();
  }
}
