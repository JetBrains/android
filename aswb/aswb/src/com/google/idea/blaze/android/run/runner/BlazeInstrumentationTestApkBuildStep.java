/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.build.lib.rules.android.deployinfo.AndroidDeployInfoOuterClass.AndroidDeployInfo;
import com.google.idea.blaze.android.run.deployinfo.BlazeAndroidDeployInfo;
import com.google.idea.blaze.android.run.deployinfo.BlazeApkDeployInfoProtoHelper;
import com.google.idea.blaze.android.run.deployinfo.BlazeApkDeployInfoProtoHelper.GetDeployInfoException;
import com.google.idea.blaze.base.bazel.BuildSystem.BuildInvoker;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.buildresult.BuildResult;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper.GetArtifactsException;
import com.google.idea.blaze.base.command.buildresult.BuildResultParser;
import com.google.idea.blaze.base.command.buildresult.bepparser.BuildEventStreamProvider;
import com.google.idea.blaze.base.filecache.FileCaches;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.scope.output.StatusOutput;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.aspects.BlazeBuildOutputs;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.util.SaveUtil;
import com.google.idea.blaze.common.Interners;
import com.google.idea.blaze.exception.BuildException;
import com.intellij.openapi.project.Project;
import java.io.File;

/** Builds the APKs required for an android instrumentation test. */
public class BlazeInstrumentationTestApkBuildStep implements ApkBuildStep {
  private static final String ANDROID_DEPLOY_INFO_OUTPUT_GROUP_NAME = "android_deploy_info";

  /** Subject to change with changes to android build rules. */
  private static final String DEPLOY_INFO_FILE_SUFFIX = ".deployinfo.pb";

  private final Project project;
  private final InstrumentationInfo instrumentationInfo;
  private final ImmutableList<String> buildFlags;
  private final BlazeApkDeployInfoProtoHelper deployInfoHelper;
  private BlazeAndroidDeployInfo deployInfo = null;

  /**
   * Note: Target kind of {@param instrumentationTestlabel} must be "android_instrumentation_test".
   */
  public BlazeInstrumentationTestApkBuildStep(
      Project project, InstrumentationInfo instrumentationInfo, ImmutableList<String> buildFlags) {
    this(project, instrumentationInfo, buildFlags, new BlazeApkDeployInfoProtoHelper());
  }

  @VisibleForTesting
  public BlazeInstrumentationTestApkBuildStep(
      Project project,
      InstrumentationInfo instrumentationInfo,
      ImmutableList<String> buildFlags,
      BlazeApkDeployInfoProtoHelper deployInfoHelper) {
    this.project = project;
    this.instrumentationInfo = instrumentationInfo;
    this.buildFlags = buildFlags;
    this.deployInfoHelper = deployInfoHelper;
  }

  @Override
  public void build(BlazeContext context, BlazeAndroidDeviceSelector.DeviceSession deviceSession) {
    BlazeProjectData projectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (projectData == null) {
      IssueOutput.error("Invalid project data. Please sync the project.").submit(context);
      return;
    }

    BuildInvoker invoker =
        Blaze.getBuildSystemProvider(project)
            .getBuildSystem()
            .getBuildInvoker(project, context, ImmutableSet.of(BuildInvoker.Capability.IS_LOCAL));
    BlazeCommand.Builder command = BlazeCommand.builder(invoker, BlazeCommandName.BUILD);
    // TODO(mathewi) we implicitly rely here on the fact that the getBuildInvoker() call above
    //   will always return a local invoker (deployInfoHelper below required that the artifacts
    //   are on the local filesystem).
    if (instrumentationInfo.isSelfInstrumentingTest()) {
      command.addTargets(instrumentationInfo.testApp);
    } else {
      command.addTargets(instrumentationInfo.targetApp, instrumentationInfo.testApp);
    }
    command.addBlazeFlags("--output_groups=+android_deploy_info");
    command.addBlazeFlags(buildFlags);

    SaveUtil.saveAllFiles();
    try (BuildEventStreamProvider streamProvider = invoker.invoke(command, context)) {
      BlazeBuildOutputs outputs = BlazeBuildOutputs.fromParsedBepOutput(BuildResultParser.getBuildOutput(streamProvider, Interners.STRING));
      int exitCode = outputs.buildResult().exitCode;
      if (exitCode != 0) {
        IssueOutput.error("Blaze build failed. See Blaze Console for details.").submit(context);
        return;
      }

      ListenableFuture<Void> unusedFuture =
        FileCaches.refresh(
          project, context, BlazeBuildOutputs.noOutputs(BuildResult.fromExitCode(exitCode)));

      context.output(new StatusOutput("Reading deployment information..."));
      String executionRoot = ExecRootUtil.getExecutionRoot(invoker, context);
      if (executionRoot == null) {
        context.setHasError();
        IssueOutput.error("Could not locate execroot!").submit(context);
        return;
      }

      AndroidDeployInfo instrumentorDeployInfoProto =
          deployInfoHelper.readDeployInfoProtoForTarget(
              instrumentationInfo.testApp,
              ANDROID_DEPLOY_INFO_OUTPUT_GROUP_NAME,
              outputs,
              fileName -> fileName.endsWith(DEPLOY_INFO_FILE_SUFFIX));
      if (instrumentationInfo.isSelfInstrumentingTest()) {
        deployInfo =
            deployInfoHelper.extractDeployInfoAndInvalidateManifests(
                project, new File(executionRoot), instrumentorDeployInfoProto);
      } else {
        AndroidDeployInfo targetDeployInfoProto =
            deployInfoHelper.readDeployInfoProtoForTarget(
                instrumentationInfo.targetApp,
                ANDROID_DEPLOY_INFO_OUTPUT_GROUP_NAME,
                outputs,
                fileName -> fileName.endsWith(DEPLOY_INFO_FILE_SUFFIX));
        deployInfo =
            deployInfoHelper.extractInstrumentationTestDeployInfoAndInvalidateManifests(
                project,
                new File(executionRoot),
                instrumentorDeployInfoProto,
                targetDeployInfoProto);
      }
    } catch (GetArtifactsException e) {
      // TODO b/374906681 - The following errors are internal errors and showing them to the users is not very useful.
      //  Handle/log them more elegantly.
      IssueOutput.error("Could not read BEP output: " + e.getMessage()).submit(context);
    } catch (GetDeployInfoException e) {
      IssueOutput.error("Could not read apk deploy info from build: " + e.getMessage())
          .submit(context);
    } catch (BuildException e) {
      IssueOutput.error("Could not invoke blaze build: " + e.getMessage()).submit(context);
    }
  }

  @Override
  public BlazeAndroidDeployInfo getDeployInfo() throws ApkProvisionException {
    if (deployInfo != null) {
      return deployInfo;
    }
    throw new ApkProvisionException(
        "Failed to read APK deploy info.  Either build step hasn't been executed or there was an"
            + " error obtaining deploy info after build.");
  }
}
