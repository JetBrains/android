/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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

import static java.util.stream.Collectors.joining;

import com.android.annotations.Nullable;
import com.android.tools.idea.run.ApkProvisionException;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.build.lib.rules.android.deployinfo.AndroidDeployInfoOuterClass.AndroidDeployInfo;
import com.google.idea.blaze.android.run.NativeSymbolFinder;
import com.google.idea.blaze.android.run.RemoteApkDownloader;
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
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.scope.output.StatusOutput;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.aspects.BlazeBuildOutputs;
import com.google.idea.blaze.base.sync.aspects.BuildResult;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.util.SaveUtil;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

/** Builds the APK using normal blaze build. */
public class FullApkBuildStep implements ApkBuildStep {
  @VisibleForTesting public static final String DEPLOY_INFO_SUFFIX = ".deployinfo.pb";

  /** Controls the post-build remote APK fetching step. */
  @VisibleForTesting
  public static final BoolExperiment FETCH_REMOTE_APKS =
      new BoolExperiment("blaze.apk.buildstep.fetch.remote.apks", true);

  private static final Logger log = Logger.getInstance(FullApkBuildStep.class);

  private final Project project;
  private final Label label;
  private final ImmutableList<String> buildFlags;
  private final BlazeApkDeployInfoProtoHelper deployInfoHelper;
  private final boolean nativeDebuggingEnabled;
  private BlazeAndroidDeployInfo deployInfo = null;
  @Nullable private final List<NativeSymbolFinder> defaultNativeSymbolFinderList;

  @VisibleForTesting
  public FullApkBuildStep(
      Project project,
      Label label,
      ImmutableList<String> buildFlags,
      boolean nativeDebuggingEnabled,
      BlazeApkDeployInfoProtoHelper deployInfoHelper,
      List<NativeSymbolFinder> defaultNativeSymbolFinderList) {
    this.project = project;
    this.label = label;
    this.buildFlags = buildFlags;
    this.deployInfoHelper = deployInfoHelper;
    this.nativeDebuggingEnabled = nativeDebuggingEnabled;
    this.defaultNativeSymbolFinderList = defaultNativeSymbolFinderList;
  }

  public FullApkBuildStep(
      Project project,
      Label label,
      ImmutableList<String> buildFlags,
      boolean nativeDebuggingEnabled) {
    this(
        project,
        label,
        buildFlags,
        nativeDebuggingEnabled,
        new BlazeApkDeployInfoProtoHelper(),
        null);
  }

  private static boolean apksRequireDownload(BlazeAndroidDeployInfo deployInfo) {
    for (File apk : deployInfo.getApksToDeploy()) {
      for (RemoteApkDownloader downloader : RemoteApkDownloader.EP_NAME.getExtensionList()) {
        if (downloader.canDownload(apk)) {
          return true;
        }
      }
    }
    return false;
  }

  private static File downloadApkIfRemote(File apk, BlazeContext context) {
    for (RemoteApkDownloader downloader : RemoteApkDownloader.EP_NAME.getExtensionList()) {
      if (downloader.canDownload(apk)) {
        try {
          context.output(new StatusOutput("Downloading " + apk.getPath()));
          File tempFile = Files.createTempFile("localcopy", apk.getName()).toFile();
          tempFile.deleteOnExit();
          downloader.download(apk, tempFile);
          return tempFile;
        } catch (IOException ex) {
          // fallback to using original, don't want to block the whole app deployment process.
          log.warn("Couldn't create local copy of file " + apk.getPath(), ex);
        }
      }
    }
    return apk;
  }

  private static File downloadLibIfRemote(File lib, BlazeContext context) {
    for (RemoteApkDownloader downloader : RemoteApkDownloader.EP_NAME.getExtensionList()) {
      if (downloader.canDownload(lib)) {
        try {
          // File name must be preserved for LLDB lookup, but multiple files will have the same name
          // if multiple android_platforms are built, so place each file in its own temp dir.
          context.output(new StatusOutput("Downloading " + lib.getPath()));
          File tempDir = Files.createTempDirectory("localcopy").toFile();
          tempDir.deleteOnExit();
          File tempFile = new File(tempDir, lib.getName());
          tempFile.deleteOnExit();
          downloader.download(lib, tempFile);
          return tempFile;
        } catch (IOException ex) {
          // fallback to using original, don't want to block the whole app deployment process.
          log.warn("Couldn't create local copy of file " + lib.getPath(), ex);
        }
      }
    }
    return lib;
  }

  private List<NativeSymbolFinder> getNativeSymbolFinderList() {
    if (defaultNativeSymbolFinderList != null) {
      return defaultNativeSymbolFinderList;
    }
    return NativeSymbolFinder.EP_NAME.getExtensionList();
  }

  @Override
  public void build(BlazeContext context, BlazeAndroidDeviceSelector.DeviceSession deviceSession) {
    BlazeProjectData projectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();

    if (projectData == null) {
      IssueOutput.error("Missing project data. Please sync and try again.").submit(context);
      return;
    }

    BuildInvoker invoker =
        Blaze.getBuildSystemProvider(project).getBuildSystem().getBuildInvoker(project, context);
    BlazeCommand.Builder command = BlazeCommand.builder(invoker, BlazeCommandName.BUILD);
    WorkspaceRoot workspaceRoot = WorkspaceRoot.fromProject(project);

    try (BuildResultHelper buildResultHelper = invoker.createBuildResultHelper()) {
      List<NativeSymbolFinder> nativeSymbolFinderList = getNativeSymbolFinderList();
      command.addTargets(label).addBlazeFlags("--output_groups=+android_deploy_info");

      if (!nativeSymbolFinderList.isEmpty()) {
        command.addBlazeFlags(
            nativeSymbolFinderList.stream()
                .map(NativeSymbolFinder::getAdditionalBuildFlags)
                .collect(joining(" ")));
      }

      command.addBlazeFlags(buildFlags).addBlazeFlags(buildResultHelper.getBuildFlags());

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

      context.output(new StatusOutput("Reading deployment information..."));
      String executionRoot = ExecRootUtil.getExecutionRoot(invoker, context);
      if (executionRoot == null) {
        IssueOutput.error("Could not locate execroot!").submit(context);
        return;
      }

      AndroidDeployInfo deployInfoProto =
          deployInfoHelper.readDeployInfoProtoForTarget(
              label, buildResultHelper, fileName -> fileName.endsWith(DEPLOY_INFO_SUFFIX));
      ImmutableList<File> libs =
          nativeSymbolFinderList.stream()
              .flatMap(
                  finder ->
                      finder.getNativeSymbolsForBuild(context, label, buildResultHelper).stream())
              .collect(ImmutableList.toImmutableList());
      deployInfo =
          deployInfoHelper.extractDeployInfoAndInvalidateManifests(
              project, new File(executionRoot), deployInfoProto, libs);
    } catch (GetArtifactsException e) {
      IssueOutput.error("Could not read BEP output: " + e.getMessage()).submit(context);
    } catch (GetDeployInfoException e) {
      IssueOutput.error("Could not read apk deploy info from build: " + e.getMessage())
          .submit(context);
    }

    if (FETCH_REMOTE_APKS.getValue() && deployInfo != null && apksRequireDownload(deployInfo)) {
      context.output(new StatusOutput("Fetching remotely built APKs... "));
      ImmutableList<File> localApks =
          deployInfo.getApksToDeploy().stream()
              .map(apk -> FullApkBuildStep.downloadApkIfRemote(apk, context))
              .collect(ImmutableList.toImmutableList());
      ImmutableList<File> localLibs =
          this.nativeDebuggingEnabled
              ? deployInfo.getSymbolFiles().stream()
                  .map(lib -> FullApkBuildStep.downloadLibIfRemote(lib, context))
                  .collect(ImmutableList.toImmutableList())
              : ImmutableList.of();
      deployInfo =
          new BlazeAndroidDeployInfo(
              deployInfo.getMergedManifest(),
              deployInfo.getTestTargetMergedManifest(),
              localApks,
              localLibs);
      context.output(new StatusOutput("Done fetching APKs."));
    }
  }

  @Override
  public BlazeAndroidDeployInfo getDeployInfo() throws ApkProvisionException {
    if (deployInfo != null) {
      return deployInfo;
    }
    throw new ApkProvisionException("Failed to read APK deploy info");
  }
}
