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
package com.google.idea.blaze.android.run.binary.mobileinstall;

import static com.google.idea.blaze.android.run.LaunchMetrics.logBuildTime;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.android.tools.idea.run.ApkProvisionException;
import com.android.tools.idea.run.DeviceFutures;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.google.devtools.build.lib.rules.android.deployinfo.AndroidDeployInfoOuterClass.AndroidDeployInfo;
import com.google.idea.blaze.android.run.binary.mobileinstall.AdbTunnelConfigurator.AdbTunnelConfiguratorProvider;
import com.google.idea.blaze.android.run.deployinfo.BlazeAndroidDeployInfo;
import com.google.idea.blaze.android.run.deployinfo.BlazeApkDeployInfoProtoHelper;
import com.google.idea.blaze.android.run.deployinfo.BlazeApkDeployInfoProtoHelper.GetDeployInfoException;
import com.google.idea.blaze.android.run.runner.ApkBuildStep;
import com.google.idea.blaze.android.run.runner.BlazeAndroidDeviceSelector;
import com.google.idea.blaze.android.run.runner.ExecRootUtil;
import com.google.idea.blaze.base.bazel.BuildSystem.BuildInvoker;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.command.buildresult.BuildResult;
import com.google.idea.blaze.base.command.buildresult.BuildResultParser;
import com.google.idea.blaze.base.command.buildresult.bepparser.BuildEventStreamProvider;
import com.google.idea.blaze.base.filecache.FileCaches;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.scope.output.StatusOutput;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.google.idea.blaze.base.sync.aspects.BlazeBuildOutputs;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.util.SaveUtil;
import com.google.idea.blaze.common.Interners;
import com.google.idea.blaze.exception.BuildException;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.execution.ExecutionException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CancellationException;
import javax.annotation.Nullable;
import org.jetbrains.annotations.TestOnly;

/** Builds and installs the APK using mobile-install. */
public class MobileInstallBuildStep implements ApkBuildStep {

  private static final Logger log = Logger.getInstance(MobileInstallBuildStep.class);
  private final Project project;
  private final Label label;
  private final ImmutableList<String> blazeFlags;
  private final ImmutableList<String> exeFlags;
  private final BlazeApkDeployInfoProtoHelper deployInfoHelper;
  private final String launchId;
  private BlazeAndroidDeployInfo deployInfo = null;

  /**
   * Returns the correct DeployInfo file suffix for mobile-install classic (bazel). This should be
   * removed once mobile-install v2 is open sourced, at which point the internal and external
   * versions will both use _mi.deployinfo.pb
   */
  public static String getDeployInfoSuffix(BuildSystemName buildSystemName) {
    return buildSystemName == BuildSystemName.Bazel
        ? "_incremental.deployinfo.pb"
        : "_mi.deployinfo.pb";
  }

  private MobileInstallBuildStep(
      Project project,
      Label label,
      ImmutableList<String> blazeFlags,
      ImmutableList<String> exeFlags,
      BlazeApkDeployInfoProtoHelper deployInfoHelper,
      String launchId) {
    this.project = project;
    this.label = label;
    this.blazeFlags = blazeFlags;
    this.exeFlags = exeFlags;
    this.deployInfoHelper = deployInfoHelper;
    this.launchId = launchId;
  }

  public MobileInstallBuildStep(
      Project project,
      Label label,
      ImmutableList<String> blazeFlags,
      ImmutableList<String> exeFlags,
      String launchId) {
    this(project, label, blazeFlags, exeFlags, new BlazeApkDeployInfoProtoHelper(), launchId);
  }

  @TestOnly
  public MobileInstallBuildStep(
      Project project,
      Label label,
      ImmutableList<String> blazeFlags,
      ImmutableList<String> exeFlags,
      BlazeApkDeployInfoProtoHelper deployInfoHelper) {
    this(project, label, blazeFlags, exeFlags, deployInfoHelper, "");
  }

  @Override
  public void build(BlazeContext context, BlazeAndroidDeviceSelector.DeviceSession deviceSession) {
    BlazeProjectData projectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();

    if (projectData == null) {
      IssueOutput.error("Missing project data. Please sync and try again.").submit(context);
      return;
    }

    DeviceFutures deviceFutures = deviceSession.deviceFutures;
    if (deviceFutures == null) {
      IssueOutput.error("Error fetching devices!").submit(context);
      return;
    }

    context.output(new StatusOutput("Waiting for target device..."));
    IDevice device = resolveDevice(context, deviceFutures);
    if (device == null) {
      return;
    }

    BuildInvoker invoker =
        Blaze.getBuildSystemProvider(project)
            .getBuildSystem()
            .getBuildInvoker(project, context, BlazeCommandName.MOBILE_INSTALL);
    BlazeCommand.Builder command = BlazeCommand.builder(invoker, BlazeCommandName.MOBILE_INSTALL);

    BuildSystemName buildSystemName = Blaze.getBuildSystemName(project);
    String deployInfoSuffix = getDeployInfoSuffix(buildSystemName);
    try (AdbTunnelConfigurator tunnelConfig = getTunnelConfigurator(context)) {
      tunnelConfig.setupConnection(context);

      command.addTargets(label);
      command.addBlazeFlags(blazeFlags);
      command.addExeFlags(exeFlags);

      if (buildSystemName == BuildSystemName.Blaze) {
        // MI launches apps by default. Defer app launch to BlazeAndroidLaunchTasksProvider.
        command.addExeFlags("--nolaunch_app");
      }
      command.addExeFlags("--nodeploy");

      SaveUtil.saveAllFiles();
      context.output(new StatusOutput("Invoking mobile-install..."));
      Stopwatch s = Stopwatch.createStarted();
      try (BuildEventStreamProvider streamProvider = invoker.invoke(command, context)) {
        Duration buildDuration = s.elapsed();
        BlazeBuildOutputs outputs = BlazeBuildOutputs.fromParsedBepOutput(BuildResultParser.getBuildOutput(streamProvider, Interners.STRING));
        int exitCode = outputs.buildResult().exitCode;
        logBuildTime(launchId, buildDuration, exitCode, ImmutableMap.of());
        if (exitCode != 0) {
          IssueOutput.error("Blaze build failed. See Blaze Console for details.").submit(context);
          return;
        }

        ListenableFuture<Void> unusedFuture =
          FileCaches.refresh(project, context, BlazeBuildOutputs.noOutputs(BuildResult.fromExitCode(exitCode)));

        context.output(new StatusOutput("Reading deployment information..."));
        String executionRoot = ExecRootUtil.getExecutionRoot(invoker, context);
        if (executionRoot == null) {
          IssueOutput.error("Could not locate execroot!").submit(context);
          return;
        }

        AndroidDeployInfo deployInfoProto =
            deployInfoHelper.readDeployInfoProtoForTarget(
                label,
                "mobile_install_INTERNAL_",
                outputs,
                fileName -> fileName.endsWith(deployInfoSuffix));
        deployInfo =
            deployInfoHelper.extractDeployInfoAndInvalidateManifests(
                project, new File(executionRoot), deployInfoProto);

        context.output(new StatusOutput("mobile-install build completed, deploying split apks..."));
      } catch (BuildException e) {
        IssueOutput.error("Could not invoke mobile-install: " + e.getMessage()).submit(context);
      }
    } catch (GetDeployInfoException e) {
      IssueOutput.error("Could not read apk deploy info from build: " + e.getMessage())
          .submit(context);
    }
  }

  @Override
  public BlazeAndroidDeployInfo getDeployInfo() throws ApkProvisionException {
    if (deployInfo != null) {
      return deployInfo;
    }
    throw new ApkProvisionException("Failed to read APK deploy info");
  }

  private static AdbTunnelConfigurator getTunnelConfigurator(BlazeContext context) {
    List<AdbTunnelConfiguratorProvider> extensions =
        AdbTunnelConfiguratorProvider.EP_NAME.getExtensionList();
    if (extensions.size() > 1) {
      // This is fine in tests.
      log.warn("More than one provider registered; Using the first one!\n" + extensions);
    }
    // Fail quietly when there's no configurable registered.
    if (!extensions.isEmpty()) {
      AdbTunnelConfigurator configurator = extensions.get(0).createConfigurator(context);
      if (configurator != null) {
        return configurator;
      }
    }
    return new AdbTunnelConfigurator() {
      @Override
      public void setupConnection(BlazeContext context) {}

      @Override
      public void tearDownConnection() {}

      @Override
      public int getAdbServerPort() {
        throw new IllegalStateException("Stub configurator is inactive.");
      }

      @Override
      public boolean isActive() {
        return false;
      }
    };
  }

  @Nullable
  private static IDevice resolveDevice(BlazeContext context, DeviceFutures deviceFutures) {
    if (deviceFutures.get().size() != 1) {
      IssueOutput.error("Only one device can be used with mobile-install.").submit(context);
      return null;
    }
    try {
      return Futures.getChecked(
          Iterables.getOnlyElement(deviceFutures.get()), ExecutionException.class);
    } catch (ExecutionException | UncheckedExecutionException e) {
      IssueOutput.error("Could not get device: " + e.getMessage()).submit(context);
      return null;
    } catch (CancellationException e) {
      // The user cancelled the device launch.
      context.setCancelled();
      return null;
    }
  }
}
