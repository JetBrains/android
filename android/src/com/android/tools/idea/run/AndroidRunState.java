/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.run;


import com.android.tools.idea.execution.common.processhandler.AndroidProcessHandler;
import com.android.tools.idea.run.applychanges.ApplyChangesUtilsKt;
import com.android.tools.idea.run.applychanges.ExistingSession;
import com.android.tools.idea.run.editor.DeployTarget;
import com.android.tools.idea.run.tasks.LaunchTasksProvider;
import com.android.tools.idea.stats.RunStats;
import com.android.tools.idea.testartifacts.instrumented.AndroidTestRunConfiguration;
import com.android.tools.idea.testartifacts.instrumented.AndroidTestRunConfigurationType;
import com.android.tools.idea.testartifacts.instrumented.orchestrator.OrchestratorUtilsKt;
import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import java.util.function.BiConsumer;
import kotlin.Unit;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
public class AndroidRunState implements RunProfileState {
  @NotNull private final ExecutionEnvironment myEnv;
  @NotNull private final String myLaunchConfigName;
  @NotNull private final Module myModule;
  @NotNull private final ApplicationIdProvider myApplicationIdProvider;
  @NotNull private final ConsoleProvider myConsoleProvider;
  private final DeployTarget myDeviceFutures;
  @NotNull private final LaunchTasksProvider myLaunchTasksProvider;

  public AndroidRunState(@NotNull ExecutionEnvironment env,
                         @NotNull String launchConfigName,
                         @NotNull Module module,
                         @NotNull ApplicationIdProvider applicationIdProvider,
                         @NotNull ConsoleProvider consoleProvider,
                         @NotNull DeployTarget deviceFutures,
                         @NotNull LaunchTasksProvider launchTasksProvider) {
    myEnv = env;
    myLaunchConfigName = launchConfigName;
    myModule = module;
    myApplicationIdProvider = applicationIdProvider;
    myConsoleProvider = consoleProvider;
    myDeviceFutures = deviceFutures;
    myLaunchTasksProvider = launchTasksProvider;
  }

  @Nullable
  @Override
  public ExecutionResult execute(Executor executor, @NotNull ProgramRunner runner) throws ExecutionException {
    ExistingSession prevHandler = ApplyChangesUtilsKt.findExistingSessionAndMaybeDetachForColdSwap(myEnv);
    ProcessHandler processHandler = prevHandler.getProcessHandler();
    ExecutionConsole console = prevHandler.getExecutionConsole();

    if (processHandler == null) {
      String appId = getMasterAndroidProcessId(myEnv.getRunProfile());
      processHandler = new AndroidProcessHandler(
        myEnv.getProject(),
        appId,
        (device) -> {
          device.forceStop(appId);
          return Unit.INSTANCE;
        },
        shouldAutoTerminate(myEnv.getRunnerAndConfigurationSettings()));
    }
    Project project = myModule.getProject();
    if (console == null) {
      console = myConsoleProvider.createAndAttach(project, processHandler, executor);
    }

    BiConsumer<String, HyperlinkInfo> hyperlinkConsumer =
      console instanceof ConsoleView ? ((ConsoleView)console)::printHyperlink : (s, h) -> {
      };

    LaunchTaskRunner taskRunner = new LaunchTaskRunner(project,
                                                       getApplicationId(),
                                                       myEnv,
                                                       processHandler,
                                                       myDeviceFutures,
                                                       myLaunchTasksProvider,
                                                       createRunStats(),
                                                       hyperlinkConsumer);
    ProgressManager.getInstance().run(new Task.Backgroundable(project, "Launching " + myLaunchConfigName) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        taskRunner.run(indicator);
      }
    });
    return new DefaultExecutionResult(console, processHandler);
  }

  private static boolean shouldAutoTerminate(@Nullable RunnerAndConfigurationSettings runnerAndConfigurationSettings) {
    // AndroidProcessHandler should not be closed even if the target application process is killed. During an
    // instrumentation tests, the target application may be killed in between test cases by test runner. Only test
    // runner knows when all test run completes.
    return !isAndroidInstrumentationTest(runnerAndConfigurationSettings);
  }

  private static boolean isAndroidInstrumentationTest(@Nullable RunnerAndConfigurationSettings runnerAndConfigurationSettings) {
    if (runnerAndConfigurationSettings == null) {
      return false;
    }
    return AndroidTestRunConfigurationType.getInstance().equals(runnerAndConfigurationSettings.getType());
  }

  private RunStats createRunStats() throws ExecutionException {
    RunStats stats = RunStats.from(myEnv);
    stats.setPackage(getApplicationId());
    return stats;
  }

  private String getApplicationId() throws ExecutionException {
    try {
      return myApplicationIdProvider.getPackageName();
    }
    catch (ApkProvisionException e) {
      throw new ExecutionException("Unable to obtain application id", e);
    }
  }

  /**
   * Returns a target Android process ID to be monitored by {@link AndroidProcessHandler}.
   *
   * If this run is a standard Android application or instrumentation test without test orchestration, the target Android process ID
   * is simply the application name. Otherwise we should monitor the test orchestration process because the orchestrator starts and
   * kills the target application process per test case which confuses AndroidProcessHandler (b/150320657).
   */
  private String getMasterAndroidProcessId(@NotNull RunProfile runProfile) throws ExecutionException {
    if (!(runProfile instanceof AndroidTestRunConfiguration)) {
      return getApplicationId();
    }
    AndroidTestRunConfiguration testRunConfiguration = (AndroidTestRunConfiguration) runProfile;
    return OrchestratorUtilsKt.MAP_EXECUTION_TYPE_TO_MASTER_ANDROID_PROCESS_NAME.getOrDefault(
      testRunConfiguration.getTestExecutionOption(AndroidFacet.getInstance(myModule)),
      getApplicationId());
  }
}
