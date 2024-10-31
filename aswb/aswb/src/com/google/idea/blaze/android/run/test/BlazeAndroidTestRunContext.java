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

import static com.google.idea.blaze.android.run.binary.BlazeAndroidBinaryNormalBuildRunContextBase.getApkInfoToInstall;

import com.android.ddmlib.ClientData;
import com.android.ddmlib.IDevice;
import com.android.tools.idea.execution.common.DeployOptions;
import com.android.tools.idea.execution.common.debug.AndroidDebugger;
import com.android.tools.idea.execution.common.debug.AndroidDebuggerState;
import com.android.tools.idea.execution.common.debug.DebugSessionStarter;
import com.android.tools.idea.projectsystem.ApplicationProjectContext;
import com.android.tools.idea.run.ApkProvider;
import com.android.tools.idea.run.ApkProvisionException;
import com.android.tools.idea.run.ApplicationIdProvider;
import com.android.tools.idea.run.ConsoleProvider;
import com.android.tools.idea.run.LaunchOptions;
import com.android.tools.idea.run.blaze.BlazeLaunchTask;
import com.android.tools.idea.run.blaze.BlazeLaunchTasksProvider;
import com.android.tools.idea.run.editor.ProfilerState;
import com.android.tools.idea.run.tasks.DeployTasksHelper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.idea.blaze.android.run.BazelApplicationProjectContext;
import com.google.idea.blaze.android.run.deployinfo.BlazeAndroidDeployInfo;
import com.google.idea.blaze.android.run.deployinfo.BlazeApkProviderService;
import com.google.idea.blaze.android.run.runner.ApkBuildStep;
import com.google.idea.blaze.android.run.runner.BlazeAndroidDeviceSelector;
import com.google.idea.blaze.android.run.runner.BlazeAndroidLaunchTasksProvider;
import com.google.idea.blaze.android.run.runner.BlazeAndroidRunContext;
import com.google.idea.blaze.android.run.test.BlazeAndroidTestLaunchMethodsProvider.AndroidTestLaunchMethod;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.smrunner.BlazeTestUiSession;
import com.google.idea.blaze.base.run.testlogs.BlazeTestResultHolder;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
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
import javax.annotation.Nullable;
import kotlin.Unit;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlinx.coroutines.BuildersKt;
import org.jetbrains.android.facet.AndroidFacet;

/** Run context for android_test. */
public class BlazeAndroidTestRunContext implements BlazeAndroidRunContext {
  protected final Project project;
  protected final AndroidFacet facet;
  protected final BlazeCommandRunConfiguration runConfiguration;
  protected final ExecutionEnvironment env;
  protected final BlazeAndroidTestRunConfigurationState configState;
  protected final Label label;
  protected final ImmutableList<String> blazeFlags;
  protected final List<Runnable> launchTaskCompleteListeners = Lists.newArrayList();
  protected final ConsoleProvider consoleProvider;
  protected final ApkBuildStep buildStep;
  protected final ApplicationIdProvider applicationIdProvider;
  protected final ApkProvider apkProvider;
  private final BlazeTestResultHolder testResultsHolder = new BlazeTestResultHolder();

  public BlazeAndroidTestRunContext(
      Project project,
      AndroidFacet facet,
      BlazeCommandRunConfiguration runConfiguration,
      ExecutionEnvironment env,
      BlazeAndroidTestRunConfigurationState configState,
      Label label,
      ImmutableList<String> blazeFlags,
      ApkBuildStep buildStep) {
    this.project = project;
    this.facet = facet;
    this.runConfiguration = runConfiguration;
    this.env = env;
    this.label = label;
    this.configState = configState;
    this.buildStep = buildStep;
    this.blazeFlags = blazeFlags;
    switch (configState.getLaunchMethod()) {
      case MOBILE_INSTALL:
      case NON_BLAZE:
        consoleProvider = new AitIdeTestConsoleProvider(runConfiguration, configState);
        break;
      case BLAZE_TEST:
        BlazeTestUiSession session =
            BlazeTestUiSession.create(ImmutableList.of(), testResultsHolder);
        this.consoleProvider = new AitBlazeTestConsoleProvider(project, runConfiguration, session);
        break;
      default:
        throw new IllegalStateException(
            "Unsupported launch method " + configState.getLaunchMethod());
    }
    applicationIdProvider = new BlazeAndroidTestApplicationIdProvider(buildStep);
    apkProvider = BlazeApkProviderService.getInstance().getApkProvider(project, buildStep);
  }

  @Override
  public BlazeAndroidDeviceSelector getDeviceSelector() {
    return new BlazeAndroidDeviceSelector.NormalDeviceSelector();
  }

  @Override
  public void augmentLaunchOptions(LaunchOptions.Builder options) {
    options.setDeploy(!configState.getLaunchMethod().equals(AndroidTestLaunchMethod.BLAZE_TEST));
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
  public ApplicationProjectContext getApplicationProjectContext() {
    return new BazelApplicationProjectContext(project, getApplicationIdProvider());
  }

  @Nullable
  @Override
  public ApkBuildStep getBuildStep() {
    return buildStep;
  }

  @Override
  public ProfilerState getProfileState() {
    return null;
  }

  @Override
  public BlazeLaunchTasksProvider getLaunchTasksProvider(LaunchOptions launchOptions)
      throws ExecutionException {
    return new BlazeAndroidLaunchTasksProvider(project, this, applicationIdProvider, launchOptions);
  }

  @Override
  public ImmutableList<BlazeLaunchTask> getDeployTasks(IDevice device, DeployOptions deployOptions)
      throws ExecutionException {
    if (configState.getLaunchMethod() != AndroidTestLaunchMethod.NON_BLAZE) {
      return ImmutableList.of();
    }
    return ImmutableList.of(
      DeployTasksHelper.createDeployTask(
            project, getApkInfoToInstall(device, deployOptions, apkProvider), deployOptions));
  }

  @Override
  @Nullable
  public BlazeLaunchTask getApplicationLaunchTask(
      boolean isDebug, @Nullable Integer userId, String contributorsAmStartOptions)
      throws ExecutionException {
    switch (configState.getLaunchMethod()) {
      case BLAZE_TEST:
        BlazeAndroidTestFilter testFilter =
            new BlazeAndroidTestFilter(
                configState.getTestingType(),
                configState.getClassName(),
                configState.getMethodName(),
                configState.getPackageName());
        return new BlazeAndroidTestLaunchTask(
            project, label, blazeFlags, testFilter, this, isDebug, testResultsHolder);
      case NON_BLAZE:
      case MOBILE_INSTALL:
        BlazeAndroidDeployInfo deployInfo;
        try {
          deployInfo = buildStep.getDeployInfo();
        } catch (ApkProvisionException e) {
          throw new ExecutionException(e);
        }
        return StockAndroidTestLaunchTask.getStockTestLaunchTask(
            configState, applicationIdProvider, isDebug, deployInfo, project);
    }
    throw new AssertionError();
  }

  @Override
  @SuppressWarnings({"unchecked", "rawtypes"}) // Raw type from upstream.
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
          (scope, continuation) -> {
            switch (configState.getLaunchMethod()) {
              case BLAZE_TEST:

                /**
                 * Wires up listeners to automatically reconnect the debugger for each test method.
                 * When you `blaze test` an android_test in debug mode, it kills the instrumentation
                 * process between each test method, disconnecting the debugger. We listen for the
                 * start of a new method waiting for a debugger, and reconnect. TODO: Support
                 * stopping Blaze from the UI. This is hard because we have no way to distinguish
                 * process handler termination/debug session ending initiated by the user.
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
                    new BazelApplicationProjectContext(project, packageName),
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

  void onLaunchTaskComplete() {
    for (Runnable runnable : launchTaskCompleteListeners) {
      runnable.run();
    }
  }

  void addLaunchTaskCompleteListener(Runnable runnable) {
    launchTaskCompleteListeners.add(runnable);
  }

  @Override
  public Executor getExecutor() {
    return env.getExecutor();
  }

  @Nullable
  @Override
  public Integer getUserId(IDevice device) {
    return null;
  }

  @Override
  public String getAmStartOptions() {
    return "";
  }
}
