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
package com.google.idea.blaze.android.run.binary;

import static com.google.idea.blaze.android.run.runner.BlazeAndroidLaunchTasksProvider.NATIVE_DEBUGGING_ENABLED;

import com.android.ddmlib.ClientData;
import com.android.ddmlib.IDevice;
import com.android.tools.idea.execution.common.DeployOptions;
import com.android.tools.idea.execution.common.debug.AndroidDebugger;
import com.android.tools.idea.execution.common.debug.AndroidDebuggerState;
import com.android.tools.idea.execution.common.debug.DebugSessionStarter;
import com.android.tools.idea.projectsystem.ApplicationProjectContext;
import com.android.tools.idea.run.ApkFileUnit;
import com.android.tools.idea.run.ApkInfo;
import com.android.tools.idea.run.ApkProvider;
import com.android.tools.idea.run.ApkProvisionException;
import com.android.tools.idea.run.ApplicationIdProvider;
import com.android.tools.idea.run.ConsoleProvider;
import com.android.tools.idea.run.LaunchOptions;
import com.android.tools.idea.run.activity.DefaultStartActivityFlagsProvider;
import com.android.tools.idea.run.activity.StartActivityFlagsProvider;
import com.android.tools.idea.run.editor.ProfilerState;
import com.android.tools.idea.util.DynamicAppUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.android.run.deployinfo.BlazeAndroidDeployInfo;
import com.google.idea.blaze.android.run.runner.ApkBuildStep;
import com.google.idea.blaze.android.run.runner.BlazeAndroidDeviceSelector;
import com.google.idea.blaze.android.run.runner.BlazeAndroidRunContext;
import com.google.idea.blaze.android.run.runner.BlazeLaunchTask;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebugSession;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import kotlin.Unit;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlinx.coroutines.BuildersKt;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

/** Run context for android_binary. */
public class BlazeAndroidBinaryNormalBuildRunContext implements BlazeAndroidRunContext {
  private final Project project;
  private final ExecutionEnvironment env;
  private final BlazeAndroidBinaryRunConfigurationState configState;
  private final ConsoleProvider consoleProvider;
  private final ApkBuildStep buildStep;
  private final ApkProvider apkProvider;
  private final ApplicationIdProvider applicationIdProvider;
  private final String launchId;
  private final ApplicationProjectContext applicationProjectContext;

  BlazeAndroidBinaryNormalBuildRunContext(
    Project project,
    ExecutionEnvironment env,
    BlazeAndroidBinaryRunConfigurationState configState,
    ApkBuildStep buildStep,
    String launchId,
    BlazeAndroidBinaryApplicationIdProvider applicationIdProvider,
    ApkProvider apkProvider,
    ApplicationProjectContext applicationProjectContext) {
    this.project = project;
    this.env = env;
    this.configState = configState;
    this.consoleProvider = new BlazeAndroidBinaryConsoleProvider(project);
    this.buildStep = buildStep;
    this.launchId = launchId;
    this.applicationIdProvider = applicationIdProvider;
    this.apkProvider = apkProvider;
    this.applicationProjectContext = applicationProjectContext;
  }

  @Override
  public BlazeAndroidDeviceSelector getDeviceSelector() {
    return new BlazeAndroidDeviceSelector.NormalDeviceSelector();
  }

  @Override
  public void augmentLaunchOptions(LaunchOptions.Builder options) {
    options
        .setDeploy(true)
        .setOpenLogcatAutomatically(configState.showLogcatAutomatically())
        .addExtraOptions(
            ImmutableMap.of(
                NATIVE_DEBUGGING_ENABLED,
                configState.getCommonState().isNativeDebuggingEnabled()))
        .setClearAppStorage(configState.getClearAppStorage());
  }

  @Override
  public ConsoleProvider getConsoleProvider() {
    return consoleProvider;
  }

  @Override
  public ApplicationIdProvider getApplicationIdProvider() {
    return applicationIdProvider;
  }

  @Override
  public ApkProvider getApkProvider() {
    return apkProvider;
  }

  @Override
  public ApkBuildStep getBuildStep() {
    return buildStep;
  }

  @Nullable
  @Override
  public Integer getUserId(IDevice device) throws ExecutionException {
    return UserIdHelper.getUserIdFromConfigurationState(project, device, configState);
  }

  @Override
  public String getAmStartOptions() {
    return configState.getAmStartOptions();
  }

  @Override
  public BlazeLaunchTask getApplicationLaunchTask(
      boolean isDebug, @Nullable Integer userId, String contributorsAmStartOptions)
      throws ExecutionException {
    String extraFlags = UserIdHelper.getFlagsFromUserId(userId);
    if (!contributorsAmStartOptions.isEmpty()) {
      extraFlags += (extraFlags.isEmpty() ? "" : " ") + contributorsAmStartOptions;
    }
    if (isDebug) {
      extraFlags += (extraFlags.isEmpty() ? "" : " ") + "-D";
    }

    final StartActivityFlagsProvider startActivityFlagsProvider =
        new DefaultStartActivityFlagsProvider(project, isDebug, extraFlags);

    BlazeAndroidDeployInfo deployInfo;
    try {
      deployInfo = buildStep.getDeployInfo();
    } catch (ApkProvisionException e) {
      throw new ExecutionException(e);
    }

    return BlazeAndroidBinaryApplicationLaunchTaskProvider.getApplicationLaunchTask(
        applicationIdProvider,
        deployInfo.getMergedManifest(),
        configState,
        startActivityFlagsProvider);
  }

  @Nullable
  @Override
  public ImmutableList<BlazeLaunchTask> getDeployTasks(IDevice device, DeployOptions deployOptions)
      throws ExecutionException {
    return ImmutableList.of(
        new DeploymentTimingReporterTask(
            launchId,
            project,
            getApkInfoToInstall(device, deployOptions, apkProvider),
            deployOptions));
  }

  /** Returns a list of APKs excluding any APKs for features that are disabled. */
  public static List<ApkInfo> getApkInfoToInstall(
      IDevice device, DeployOptions deployOptions, ApkProvider apkProvider)
      throws ExecutionException {
    Collection<ApkInfo> apks;
    try {
      apks = apkProvider.getApks(device);
    } catch (ApkProvisionException e) {
      throw new ExecutionException(e);
    }
    List<String> disabledFeatures = deployOptions.getDisabledDynamicFeatures();
    return apks.stream()
        .map(apk -> getApkInfoToInstall(apk, disabledFeatures))
        .collect(Collectors.toList());
  }

  @NotNull
  private static ApkInfo getApkInfoToInstall(ApkInfo apkInfo, List<String> disabledFeatures) {
    if (apkInfo.getFiles().size() > 1) {
      List<ApkFileUnit> filteredApks =
          apkInfo.getFiles().stream()
              .filter(feature -> DynamicAppUtils.isFeatureEnabled(disabledFeatures, feature))
              .collect(Collectors.toList());
      return new ApkInfo(filteredApks, apkInfo.getApplicationId());
    } else {
      return apkInfo;
    }
  }

  @Nullable
  @Override
  public XDebugSession startDebuggerSession(
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
                  getApplicationProjectContext(),
                  env,
                  androidDebugger,
                  androidDebuggerState,
                  /*destroyRunningProcess*/ d -> {
                    d.forceStop(getApplicationProjectContext().getApplicationId());
                    return Unit.INSTANCE;
                  },
                  indicator,
                  consoleView,
                  15L,
                  ClientData.DebuggerStatus.WAITING,
                  continuation));
    } catch (InterruptedException e) {
      throw new ProcessCanceledException();
    }
  }

  @Override
  public Executor getExecutor() {
    return env.getExecutor();
  }

  @Override
  public ProfilerState getProfileState() {
    return configState.getProfilerState();
  }

  public ApplicationProjectContext getApplicationProjectContext() {
    return applicationProjectContext;
  }
}
