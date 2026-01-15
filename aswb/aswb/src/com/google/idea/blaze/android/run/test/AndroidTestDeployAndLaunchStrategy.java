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
package com.google.idea.blaze.android.run.test;

import static com.google.idea.blaze.android.run.binary.NormalBuildDeployAndLaunchStrategy.getApkInfoToInstall;
import static com.google.idea.blaze.android.run.runner.BlazeAndroidConfigurationExecutor.NATIVE_DEBUGGING_ENABLED;

import com.android.ddmlib.ClientData;
import com.android.ddmlib.IDevice;
import com.android.tools.idea.execution.common.DeployOptions;
import com.android.tools.idea.execution.common.debug.AndroidDebugger;
import com.android.tools.idea.execution.common.debug.AndroidDebuggerState;
import com.android.tools.idea.execution.common.debug.DebugSessionStarter;
import com.android.tools.idea.run.ApkProvisionException;
import com.android.tools.idea.run.LaunchOptions;
import com.android.tools.idea.run.tasks.DeployTasksHelper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.idea.blaze.android.run.BazelAndroidRunContext;
import com.google.idea.blaze.android.run.BazelApkProvider;
import com.google.idea.blaze.android.run.BazelApplicationIdProvider;
import com.google.idea.blaze.android.run.BazelApplicationProjectContext;
import com.google.idea.blaze.android.run.runner.ApkBuildStep;
import com.google.idea.blaze.android.run.runner.BlazeAndroidDeployAndLaunchStrategy;
import com.google.idea.blaze.android.run.runner.BlazeAndroidDeviceSelector;
import com.google.idea.blaze.android.run.runner.BlazeLaunchTask;
import com.google.idea.blaze.android.run.test.BlazeAndroidTestLaunchMethodsProvider.AndroidTestLaunchMethod;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.testlogs.BlazeTestResultFetcher;
import com.google.idea.blaze.common.Label;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.NopProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebugSession;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import kotlin.Unit;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlinx.coroutines.BuildersKt;
import org.jetbrains.annotations.NotNull;

/** Launch Strategy for android_test targets. */
public class AndroidTestDeployAndLaunchStrategy implements BlazeAndroidDeployAndLaunchStrategy {
  private final Project project;
  private final BlazeAndroidTestRunConfigurationState configState;
  private final Label label;
  private final ImmutableList<String> blazeFlags;
  private final BlazeTestResultFetcher testResultsHolder;

  private final List<Runnable> launchTaskCompleteListeners = Lists.newArrayList();

  public AndroidTestDeployAndLaunchStrategy(
      Project project,
      BlazeAndroidTestRunConfigurationState configState,
      Label label,
      ImmutableList<String> blazeFlags,
      BlazeTestResultFetcher testResultsHolder) {
    this.project = project;
    this.configState = configState;
    this.label = label;
    this.blazeFlags = blazeFlags;
    this.testResultsHolder = testResultsHolder;
  }

  @Override
  public BlazeAndroidDeviceSelector getDeviceSelector() {
    return new BlazeAndroidDeviceSelector.NormalDeviceSelector();
  }

  @Override
  public void augmentLaunchOptions(LaunchOptions.Builder options) {
    options.setDeploy(!configState.getLaunchMethod().equals(AndroidTestLaunchMethod.BLAZE_TEST));
    if (configState.getCommonState().isNativeDebuggingEnabled()) {
      options.addExtraOptions(Map.of(NATIVE_DEBUGGING_ENABLED, true));
    }
  }

  @Override
  public String getAmStartOptions() {
    return "";
  }

  @Nullable
  @Override
  public Integer getUserId(IDevice device) {
    return null;
  }

  @Override
  public ImmutableList<BlazeLaunchTask> getDeployTasks(
    BazelAndroidRunContext runContext, IDevice device, DeployOptions deployOptions)
      throws ExecutionException {
    if (configState.getLaunchMethod() != AndroidTestLaunchMethod.NON_BLAZE) {
      return ImmutableList.of();
    }
    return ImmutableList.of(
        DeployTasksHelper.createDeployTask(
            project,
            getApkInfoToInstall(device, deployOptions, runContext.getApkProvider()),
            deployOptions));
  }

  @Override
  public BazelAndroidRunContext createBlazeAndroidRunContext(
      ExecutionEnvironment env, ApkBuildStep buildStep, BlazeCommandRunConfiguration configuration) throws ApkProvisionException {
    var deployInfo = buildStep.getDeployInfo();

    var testPackageName = deployInfo.getMainAppPackageName();
    var packageName = deployInfo.getAppUnderTestPackageName() != null ? deployInfo.getAppUnderTestPackageName() : testPackageName;
    var applicationIds = new BazelApplicationIdProvider(packageName, testPackageName);
    var applicationId = applicationIds.getPackageName();
    var apkProvider = new BazelApkProvider(deployInfo.getApkInfos());
    var applicationProjectContext = new BazelApplicationProjectContext(project, applicationId);

    var consoleProvider =
        switch (configState.getLaunchMethod()) {
          case MOBILE_INSTALL, NON_BLAZE -> new AitIdeTestConsoleProvider(configuration, configState);
          case BLAZE_TEST -> new AitBlazeTestConsoleProvider(
              project, configuration, testResultsHolder);
        };

    return new BazelAndroidRunContext(
        consoleProvider,
        buildStep,
        applicationIds,
        apkProvider,
        applicationProjectContext,
        env.getExecutor(),
        null
    );
  }

  @Override
  @Nullable
  public BlazeLaunchTask getApplicationLaunchTask(
    BazelAndroidRunContext runContext,
    boolean isDebug,
    @Nullable Integer userId,
    @NotNull String contributorsAmStartOptions)
      throws ExecutionException {
    switch (configState.getLaunchMethod()) {
      case BLAZE_TEST:
        var testFilter =
            new BlazeAndroidTestFilter(
                configState.getTestingType(),
                configState.getClassName(),
                configState.getMethodName(),
                configState.getPackageName());
        return new BlazeAndroidTestLaunchTask(
            project, label, blazeFlags, testFilter, this, isDebug, testResultsHolder);
      case NON_BLAZE:
      case MOBILE_INSTALL:
        var deployInfo = runContext.getBuildStep().getDeployInfo();
        return StockAndroidTestLaunchTask.getStockTestLaunchTask(
            configState, runContext.getApplicationIdProvider(), isDebug, deployInfo, project);
    }
    throw new AssertionError();
  }

  @Override
  @SuppressWarnings({"unchecked", "rawtypes"}) // Raw type from upstream.
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
          (scope, continuation) -> {
            switch (configState.getLaunchMethod()) {
              case BLAZE_TEST:

                /**
                 * Wires up listeners to automatically reconnect the debugger for each test method.
                 * When you `blaze test` an android_test in debug mode, it kills the
                 * instrumentation process between each test method, disconnecting the debugger. We
                 * listen for the start of a new method waiting for a debugger, and reconnect. TODO:
                 * Support stopping Blaze from the UI. This is hard because we have no way to
                 * distinguish process handler termination/debug session ending initiated by the
                 * user.
                 */
                final ProcessHandler masterProcessHandler = new NopProcessHandler();
                addLaunchTaskCompleteListener(
                    () -> {
                      masterProcessHandler.notifyTextAvailable(
                          "Test run completed.\n", ProcessOutputTypes.STDOUT);
                      masterProcessHandler.detachProcess();
                    });
                return DebugSessionStarter.INSTANCE.attachReattachingDebuggerToStartedProcess(
                    device,
                    runContext.getApplicationProjectContext(),
                    masterProcessHandler,
                    env,
                    androidDebugger,
                    androidDebuggerState,
                    indicator,
                    consoleView,
                    Long.MAX_VALUE,
                    continuation);
              case NON_BLAZE:
              case MOBILE_INSTALL:
                return DebugSessionStarter.INSTANCE.attachDebuggerToStartedProcess(
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
                    Long.MAX_VALUE,
                    ClientData.DebuggerStatus.WAITING,
                    continuation);
            }
            throw new RuntimeException("Unknown lunch mode");
          });
    } catch (InterruptedException e) {
      throw new ProcessCanceledException();
    }
  }

  public void onLaunchTaskComplete() {
    for (Runnable runnable : launchTaskCompleteListeners) {
      runnable.run();
    }
  }

  public void addLaunchTaskCompleteListener(Runnable runnable) {
    launchTaskCompleteListeners.add(runnable);
  }
}