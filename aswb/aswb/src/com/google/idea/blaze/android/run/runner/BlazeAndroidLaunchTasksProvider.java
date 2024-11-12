/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
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

import static com.android.tools.idea.profilers.AndroidProfilerLaunchTaskContributor.isProfilerLaunch;

import com.android.ddmlib.IDevice;
import com.android.tools.deployer.ApkVerifierTracker;
import com.android.tools.idea.execution.common.DeployOptions;
import com.android.tools.idea.execution.common.debug.AndroidDebugger;
import com.android.tools.idea.execution.common.debug.AndroidDebuggerState;
import com.android.tools.idea.profilers.AndroidProfilerLaunchTaskContributor;
import com.android.tools.idea.run.ApkProvisionException;
import com.android.tools.idea.run.ApplicationIdProvider;
import com.android.tools.idea.run.LaunchOptions;
import com.android.tools.idea.run.blaze.BlazeLaunchTask;
import com.android.tools.idea.run.blaze.BlazeLaunchTasksProvider;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.idea.blaze.android.run.binary.UserIdHelper;
import com.google.idea.blaze.android.run.deployinfo.BlazeAndroidDeployInfo;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebugSession;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/** Normal launch tasks provider. #api4.1 */
public class BlazeAndroidLaunchTasksProvider implements BlazeLaunchTasksProvider {
  public static final String NATIVE_DEBUGGING_ENABLED = "NATIVE_DEBUGGING_ENABLED";
  private static final Logger LOG = Logger.getInstance(BlazeAndroidLaunchTasksProvider.class);

  private final Project project;
  private final BlazeAndroidRunContext runContext;
  private final ApplicationIdProvider applicationIdProvider;
  private final LaunchOptions launchOptions;

  public BlazeAndroidLaunchTasksProvider(
      Project project,
      BlazeAndroidRunContext runContext,
      ApplicationIdProvider applicationIdProvider,
      LaunchOptions launchOptions) {
    this.project = project;
    this.runContext = runContext;
    this.applicationIdProvider = applicationIdProvider;
    this.launchOptions = launchOptions;
  }

  @NotNull
  @Override
  public List<BlazeLaunchTask> getTasks(@NotNull IDevice device, boolean isDebug)
      throws ExecutionException {
    final List<BlazeLaunchTask> launchTasks = Lists.newArrayList();

    String packageName;
    try {
      packageName = applicationIdProvider.getPackageName();
    } catch (ApkProvisionException e) {
      throw new ExecutionException("Unable to determine application id: " + e);
    }

    Integer userId = runContext.getUserId(device);

    // NOTE: Task for opening the profiler tool-window should come before deployment
    // to ensure the tool-window opens correctly. This is required because starting
    // the profiler session requires the tool-window to be open.
    if (isProfilerLaunch(runContext.getExecutor())) {
      launchTasks.add(new BlazeAndroidOpenProfilerWindowTask(project));
    }

    if (launchOptions.isDeploy()) {
      String userIdFlags = UserIdHelper.getFlagsFromUserId(userId);
      String skipVerification =
          ApkVerifierTracker.getSkipVerificationInstallationFlag(device, packageName);
      String pmInstallOption;
      if (skipVerification != null) {
        pmInstallOption = userIdFlags + " " + skipVerification;
      } else {
        pmInstallOption = userIdFlags;
      }
      DeployOptions deployOptions =
          new DeployOptions(Collections.emptyList(), pmInstallOption, false, false, false);
      ImmutableList<BlazeLaunchTask> deployTasks = runContext.getDeployTasks(device, deployOptions);
      launchTasks.addAll(deployTasks);
    }

    try {
      if (isDebug) {
        launchTasks.add(
            new CheckApkDebuggableTask(project, runContext.getBuildStep().getDeployInfo()));
      }

      ImmutableList.Builder<String> amStartOptions = ImmutableList.builder();
      amStartOptions.add(runContext.getAmStartOptions());
      if (isProfilerLaunch(runContext.getExecutor())) {
        amStartOptions.add(
            AndroidProfilerLaunchTaskContributor.getAmStartOptions(
                project,
                packageName,
                runContext.getProfileState(),
                device,
                runContext.getExecutor()));
      }
      BlazeLaunchTask appLaunchTask =
          runContext.getApplicationLaunchTask(
              isDebug, userId, String.join(" ", amStartOptions.build()));
      if (appLaunchTask != null) {
        launchTasks.add(appLaunchTask);
        // TODO(arvindanekal): the live edit api changed and we cannot get the apk here to create
        // live
        // edit; the live edit team or Arvind need to fix this
      }
    } catch (ApkProvisionException e) {
      throw new ExecutionException("Unable to determine application id: " + e);
    }

    return ImmutableList.copyOf(launchTasks);
  }

  @NotNull
  @Override
  public XDebugSession startDebugSession(
      @NotNull ExecutionEnvironment environment,
      @NotNull IDevice device,
      @NotNull ConsoleView console,
      @NotNull ProgressIndicator indicator,
      @NotNull String packageName)
      throws ExecutionException {
    // Do not get debugger state directly from the debugger itself.
    // See BlazeAndroidDebuggerService#getDebuggerState for an explanation.
    boolean isNativeDebuggingEnabled = isNativeDebuggingEnabled(launchOptions);
    BlazeAndroidDebuggerService debuggerService = BlazeAndroidDebuggerService.getInstance(project);
    AndroidDebugger debugger = debuggerService.getDebugger(isNativeDebuggingEnabled);
    if (debugger == null) {
      throw new ExecutionException("Can't find AndroidDebugger for launch");
    }
    AndroidDebuggerState debuggerState = debuggerService.getDebuggerState(debugger);
    if (debuggerState == null) {
      throw new ExecutionException("Can't find AndroidDebuggerState for launch");
    }
    if (isNativeDebuggingEnabled) {
      BlazeAndroidDeployInfo deployInfo = null;
      try {
        deployInfo = runContext.getBuildStep().getDeployInfo();
      } catch (ApkProvisionException e) {
        LOG.error(e);
      }
      debuggerService.configureNativeDebugger(debuggerState, deployInfo);
    }

    return runContext.startDebuggerSession(
        debugger, debuggerState, environment, device, console, indicator, packageName);
  }

  private boolean isNativeDebuggingEnabled(LaunchOptions launchOptions) {
    Object flag = launchOptions.getExtraOption(NATIVE_DEBUGGING_ENABLED);
    return flag instanceof Boolean && (Boolean) flag;
  }
}
