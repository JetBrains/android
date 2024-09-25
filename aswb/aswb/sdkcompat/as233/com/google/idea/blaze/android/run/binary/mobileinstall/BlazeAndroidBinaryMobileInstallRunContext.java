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
import com.android.tools.idea.execution.common.debug.AndroidDebugger;
import com.android.tools.idea.execution.common.debug.AndroidDebuggerState;
import com.android.tools.idea.execution.common.debug.DebugSessionStarter;
import com.android.tools.idea.projectsystem.ApplicationProjectContext;
import com.android.tools.idea.run.ApkProvisionException;
import com.android.tools.idea.run.activity.DefaultStartActivityFlagsProvider;
import com.android.tools.idea.run.activity.StartActivityFlagsProvider;
import com.android.tools.idea.run.blaze.BlazeLaunchTask;
import com.google.idea.blaze.android.run.BazelApplicationProjectContext;
import com.google.idea.blaze.android.run.binary.BlazeAndroidBinaryApplicationLaunchTaskProvider;
import com.google.idea.blaze.android.run.binary.BlazeAndroidBinaryRunConfigurationState;
import com.google.idea.blaze.android.run.binary.UserIdHelper;
import com.google.idea.blaze.android.run.deployinfo.BlazeAndroidDeployInfo;
import com.google.idea.blaze.android.run.runner.ApkBuildStep;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunConfiguration;
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
import org.jetbrains.android.facet.AndroidFacet;

/** Run Context for mobile install launches, #api4.0 compat. */
public class BlazeAndroidBinaryMobileInstallRunContext
    extends BlazeAndroidBinaryMobileInstallRunContextBase {
  public BlazeAndroidBinaryMobileInstallRunContext(
      Project project,
      AndroidFacet facet,
      RunConfiguration runConfiguration,
      ExecutionEnvironment env,
      BlazeAndroidBinaryRunConfigurationState configState,
      ApkBuildStep buildStep,
      String launchId) {
    super(project, facet, runConfiguration, env, configState, buildStep, launchId);
  }

  @Override
  public ApplicationProjectContext getApplicationProjectContext() {
    return new BazelApplicationProjectContext(project, getApplicationIdProvider());
  }

  @SuppressWarnings("unchecked") // upstream API
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
  public XDebugSession startDebuggerSession(
      AndroidDebugger androidDebugger,
      AndroidDebuggerState androidDebuggerState,
      ExecutionEnvironment env,
      IDevice device,
      ConsoleView consoleView,
      ProgressIndicator indicator,
      String packageName) {
    try {
      return BuildersKt.runBlocking(
          EmptyCoroutineContext.INSTANCE,
          (scope, continuation) ->
              DebugSessionStarter.INSTANCE.attachDebuggerToStartedProcess(
                  device,
                  new BazelApplicationProjectContext(project, packageName),
                  env,
                  androidDebugger,
                  androidDebuggerState,
                  /*destroyRunningProcess*/ d -> {
                    d.forceStop(packageName);
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

  @Override
  public Executor getExecutor() {
    return env.getExecutor();
  }
}
