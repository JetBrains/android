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
import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.build.lib.rules.android.deployinfo.AndroidDeployInfoOuterClass.AndroidDeployInfo;
import com.google.idea.blaze.android.run.deployinfo.BlazeAndroidDeployInfo;
import com.google.idea.blaze.android.run.deployinfo.BlazeApkDeployInfoProtoHelper;
import com.google.idea.blaze.android.run.deployinfo.BlazeApkDeployInfoProtoHelper.GetDeployInfoException;
import com.google.idea.blaze.base.async.process.ExternalTask;
import com.google.idea.blaze.base.async.process.LineProcessingOutputStream;
import com.google.idea.blaze.base.bazel.BuildSystem.BuildInvoker;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper.GetArtifactsException;
import com.google.idea.blaze.base.console.BlazeConsoleLineProcessorProvider;
import com.google.idea.blaze.base.filecache.FileCaches;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.scope.output.StatusOutput;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.aspects.BlazeBuildOutputs;
import com.google.idea.blaze.base.sync.aspects.BuildResult;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.util.SaveUtil;
import com.intellij.openapi.project.Project;
import java.io.File;

/** Builds the APKs required for an android instrumentation test. */
public class BlazeInstrumentationTestApkBuildStep implements ApkBuildStep {

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
        Blaze.getBuildSystemProvider(project).getBuildSystem().getBuildInvoker(project, context);
    BlazeCommand.Builder command = BlazeCommand.builder(invoker, BlazeCommandName.BUILD);
    WorkspaceRoot workspaceRoot = WorkspaceRoot.fromProject(project);

    // TODO(mathewi) we implicitly rely here on the fact that the getBuildInvoker() call above
    //   will always return a local invoker (deployInfoHelper below required that the artifacts
    //   are on the local filesystem).
    try (BuildResultHelper buildResultHelper = invoker.createBuildResultHelper()) {
      if (instrumentationInfo.isSelfInstrumentingTest()) {
        command.addTargets(instrumentationInfo.testApp);
      } else {
        command.addTargets(instrumentationInfo.targetApp, instrumentationInfo.testApp);
      }
      command
          .addBlazeFlags("--output_groups=+android_deploy_info")
          .addBlazeFlags(buildFlags)
          .addBlazeFlags(buildResultHelper.getBuildFlags());

      SaveUtil.saveAllFiles();
      int retVal =
          ExternalTask.builder(workspaceRoot)
              .addBlazeCommand(command.build())
              .context(context)
              .stderr(
                  LineProcessingOutputStream.of(
                      BlazeConsoleLineProcessorProvider.getAllStderrLineProcessors(context)))
              .build()
              .run();
      ListenableFuture<Void> unusedFuture =
          FileCaches.refresh(
              project, context, BlazeBuildOutputs.noOutputs(BuildResult.fromExitCode(retVal)));

      if (retVal != 0) {
        IssueOutput.error("Blaze build failed. See Blaze Console for details.").submit(context);
        return;
      }
      try {
        context.output(new StatusOutput("Reading deployment information..."));
        String executionRoot = ExecRootUtil.getExecutionRoot(invoker, context);
        if (executionRoot == null) {
          IssueOutput.error("Could not locate execroot!").submit(context);
          return;
        }

        AndroidDeployInfo instrumentorDeployInfoProto =
            deployInfoHelper.readDeployInfoProtoForTarget(
                instrumentationInfo.testApp,
                buildResultHelper,
                fileName -> fileName.endsWith(DEPLOY_INFO_FILE_SUFFIX));
        if (instrumentationInfo.isSelfInstrumentingTest()) {
          deployInfo =
              deployInfoHelper.extractDeployInfoAndInvalidateManifests(
                  project, new File(executionRoot), instrumentorDeployInfoProto);
        } else {
          AndroidDeployInfo targetDeployInfoProto =
              deployInfoHelper.readDeployInfoProtoForTarget(
                  instrumentationInfo.targetApp,
                  buildResultHelper,
                  fileName -> fileName.endsWith(DEPLOY_INFO_FILE_SUFFIX));
          deployInfo =
              deployInfoHelper.extractInstrumentationTestDeployInfoAndInvalidateManifests(
                  project,
                  new File(executionRoot),
                  instrumentorDeployInfoProto,
                  targetDeployInfoProto);
        }
      } catch (GetArtifactsException e) {
        IssueOutput.error("Could not read BEP output: " + e.getMessage()).submit(context);
      } catch (GetDeployInfoException e) {
        IssueOutput.error("Could not read apk deploy info from build: " + e.getMessage())
            .submit(context);
      }
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
