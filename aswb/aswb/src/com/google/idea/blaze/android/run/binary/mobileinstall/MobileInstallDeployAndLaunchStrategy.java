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

import com.android.ddmlib.ClientData;
import com.android.ddmlib.IDevice;
import com.android.tools.idea.execution.common.DeployOptions;
import com.android.tools.idea.execution.common.debug.AndroidDebugger;
import com.android.tools.idea.execution.common.debug.AndroidDebuggerState;
import com.android.tools.idea.execution.common.debug.DebugSessionStarter;
import com.android.tools.idea.run.ApkProvisionException;
import com.android.tools.idea.run.LaunchOptions;
import com.android.tools.idea.run.activity.DefaultStartActivityFlagsProvider;
import com.android.tools.idea.run.activity.StartActivityFlagsProvider;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.android.run.BazelAndroidRunContext;
import com.google.idea.blaze.android.run.BazelApplicationProjectContext;
import com.google.idea.blaze.android.run.binary.BlazeAndroidBinaryApplicationLaunchTaskProvider;
import com.google.idea.blaze.android.run.binary.BlazeAndroidBinaryConsoleProvider;
import com.google.idea.blaze.android.run.binary.BlazeAndroidBinaryRunConfigurationState;
import com.google.idea.blaze.android.run.binary.DeploymentTimingReporterTask;
import com.google.idea.blaze.android.run.binary.UserIdHelper;
import com.google.idea.blaze.android.run.deployinfo.BlazeAndroidDeployInfo;
import com.google.idea.blaze.android.run.runner.BlazeAndroidDeployAndLaunchStrategy;
import com.google.idea.blaze.android.run.runner.BlazeAndroidDeviceSelector;
import com.google.idea.blaze.android.run.runner.BlazeLaunchTask;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebugSession;
import javax.annotation.Nullable;
import kotlin.Unit;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlinx.coroutines.BuildersKt;
import org.jetbrains.annotations.NotNull;

/** Launch Strategy for mobile install launches. */
public class MobileInstallDeployAndLaunchStrategy implements BlazeAndroidDeployAndLaunchStrategy {
  private final Project project;
  private final BlazeAndroidBinaryRunConfigurationState configState;
  private final String launchId;

  public MobileInstallDeployAndLaunchStrategy(
      Project project,
      BlazeAndroidBinaryRunConfigurationState configState,
      String launchId) {
    this.project = project;
    this.configState = configState;
    this.launchId = launchId;
  }

  @Override
  public BlazeAndroidDeviceSelector getDeviceSelector() {
    return new BlazeAndroidDeviceSelector.NormalDeviceSelector();
  }

  @Override
  public void augmentLaunchOptions(LaunchOptions.Builder options) {
    options
        .setDeploy(true)
        .setOpenLogcatAutomatically(configState.showLogcatAutomatically());
    // This is needed for compatibility with #api211
    options.addExtraOptions(
        ImmutableMap.of("android.profilers.state", configState.getProfilerState()));
  }

  @Override
  public String getAmStartOptions() {
    return configState.getAmStartOptions();
  }

  @Nullable
  @Override
  public Integer getUserId(IDevice device) throws ExecutionException {
    return UserIdHelper.getUserIdFromConfigurationState(project, device, configState);
  }

  @Override
  public BazelAndroidRunContext createBlazeAndroidRunContext(
      ExecutionEnvironment env,
      BlazeAndroidDeployInfo deployInfo,
      BlazeCommandRunConfiguration configuration) {
    var applicationIds = deployInfo.toAndroidBinaryApplicationIdProvider();
    var apkProvider = deployInfo.toApkProvider();
    var applicationId = applicationIds.getPackageName();
    var applicationProjectContext = new BazelApplicationProjectContext(project, applicationId);

    var consoleProvider = new BlazeAndroidBinaryConsoleProvider(project);

    return new BazelAndroidRunContext(
        consoleProvider,
        deployInfo,
        applicationIds,
        apkProvider,
        applicationProjectContext,
        env.getExecutor(),
        configState.getProfilerState());
  }

  @Override
  public ImmutableList<BlazeLaunchTask> getDeployTasks(
    BazelAndroidRunContext runContext, IDevice device, DeployOptions deployOptions) {
    return ImmutableList.of(
        new DeploymentTimingReporterTask(
            launchId, project, runContext.getApkProvider().getApks(device), deployOptions));
  }

  @Override
  @Nullable
  public BlazeLaunchTask getApplicationLaunchTask(
    BazelAndroidRunContext runContext,
    boolean isDebug,
    @Nullable Integer userId,
    @NotNull String contributorsAmStartOptions)
      throws ExecutionException {

    var extraFlags = UserIdHelper.getFlagsFromUserId(userId);
    if (!contributorsAmStartOptions.isEmpty()) {
      extraFlags += (extraFlags.isEmpty() ? "" : " ") + contributorsAmStartOptions;
    }
    if (isDebug) {
      extraFlags += (extraFlags.isEmpty() ? "" : " ") + "-D";
    }

    final StartActivityFlagsProvider startActivityFlagsProvider =
        new DefaultStartActivityFlagsProvider(project, isDebug, extraFlags);
    var deployInfo = runContext.getDeployInfo();

    return BlazeAndroidBinaryApplicationLaunchTaskProvider.getApplicationLaunchTask(
      runContext.getApplicationIdProvider(),
        deployInfo.getMainAppMergedManifest(),
        configState,
        startActivityFlagsProvider);
  }

  @Nullable
  @Override
  public XDebugSession startDebuggerSession(
    BazelAndroidRunContext runContext,
    AndroidDebugger androidDebugger,
    AndroidDebuggerState androidDebuggerState,
    ExecutionEnvironment env,
    IDevice device,
    ConsoleView consoleView,
    ProgressIndicator indicator) {
    try {
      return BuildersKt.runBlocking(
          EmptyCoroutineContext.INSTANCE,
          (scope, continuation) ->
              DebugSessionStarter.INSTANCE.attachDebuggerToStartedProcess(
                  device,
                  runContext.getApplicationProjectContext(),
                  env,
                  androidDebugger,
                  androidDebuggerState,
                  /*destroyRunningProcess*/ d -> {
                    d.forceStop(runContext.getApplicationProjectContext().getApplicationId());
                    return Unit.INSTANCE;
                  },
                  indicator,
                  consoleView,
                  15L,
                  ClientData.DebuggerStatus.WAITING,
                  continuation));
    } catch (InterruptedException e) {
      throw new ProcessCanceledException(e);
    }
  }
}
