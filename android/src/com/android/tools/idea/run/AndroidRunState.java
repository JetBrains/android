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

import com.android.tools.idea.run.applychanges.ApplyChangesUtilsKt;
import com.android.tools.idea.run.applychanges.ExistingSession;
import com.android.tools.idea.run.tasks.LaunchTasksProvider;
import com.android.tools.idea.stats.RunStats;
import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressManager;
import java.util.function.BiConsumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidRunState implements RunProfileState {
  @NotNull private final ExecutionEnvironment myEnv;
  @NotNull private final String myLaunchConfigName;
  @NotNull private final Module myModule;
  @NotNull private final ApplicationIdProvider myApplicationIdProvider;
  @NotNull private final ConsoleProvider myConsoleProvider;
  @NotNull private final DeviceFutures myDeviceFutures;
  @NotNull private final LaunchTasksProvider myLaunchTasksProvider;

  public AndroidRunState(@NotNull ExecutionEnvironment env,
                         @NotNull String launchConfigName,
                         @NotNull Module module,
                         @NotNull ApplicationIdProvider applicationIdProvider,
                         @NotNull ConsoleProvider consoleProvider,
                         @NotNull DeviceFutures deviceFutures,
                         @NotNull AndroidLaunchTasksProvider launchTasksProvider) {
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
    ExistingSession prevHandler = ApplyChangesUtilsKt.findExistingSessionAndMaybeDetachForColdSwap(myEnv, myDeviceFutures);
    ProcessHandler processHandler = prevHandler.getProcessHandler();
    ExecutionConsole console = prevHandler.getExecutionConsole();

    if (processHandler == null) {
      processHandler = new AndroidProcessHandler(myEnv.getProject(), getApplicationId());
    }
    if (console == null) {
      console = myConsoleProvider.createAndAttach(myModule.getProject(), processHandler, executor);
    }

    BiConsumer<String, HyperlinkInfo> hyperlinkConsumer =
      console instanceof ConsoleView ? ((ConsoleView)console)::printHyperlink : (s, h) -> {
      };

    LaunchInfo launchInfo = new LaunchInfo(executor, runner, myEnv, myConsoleProvider);
    LaunchTaskRunner task = new LaunchTaskRunner(myModule.getProject(),
                                                 myLaunchConfigName,
                                                 getApplicationId(),
                                                 myEnv.getExecutionTarget().getDisplayName(),
                                                 launchInfo,
                                                 processHandler,
                                                 myDeviceFutures,
                                                 myLaunchTasksProvider,
                                                 createRunStats(),
                                                 hyperlinkConsumer);
    ProgressManager.getInstance().run(task);
    return new DefaultExecutionResult(console, processHandler);
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
}
