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

import com.android.ddmlib.IDevice;
import com.android.tools.idea.run.tasks.LaunchTasksProvider;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class AndroidRunState implements RunProfileState {
  @NotNull private final ExecutionEnvironment myEnv;
  @NotNull private final String myLaunchConfigName;
  @NotNull private final Module myModule;
  @NotNull private final ApkProvider myApkProvider;
  @NotNull private final ConsoleProvider myConsoleProvider;
  @NotNull private final Collection<ListenableFuture<IDevice>> myDeviceFutures;
  @NotNull private final LaunchTasksProvider myLaunchTasksProvider;

  public AndroidRunState(@NotNull ExecutionEnvironment env,
                         @NotNull String launchConfigName,
                         @NotNull Module module,
                         @NotNull ApkProvider apkProvider,
                         @NotNull ConsoleProvider consoleProvider,
                         @NotNull Collection<ListenableFuture<IDevice>> deviceFutures,
                         @NotNull LaunchTasksProvider launchTasksProvider) {
    myEnv = env;
    myLaunchConfigName = launchConfigName;
    myModule = module;
    myApkProvider = apkProvider;
    myConsoleProvider = consoleProvider;
    myDeviceFutures = deviceFutures;
    myLaunchTasksProvider = launchTasksProvider;
  }

  @Nullable
  @Override
  public ExecutionResult execute(Executor executor, @NotNull ProgramRunner runner) throws ExecutionException {
    AndroidProcessHandler processHandler;
    ConsoleView console;

    String applicationId;
    try {
      applicationId = myApkProvider.getPackageName();
    }
    catch (ApkProvisionException e) {
      throw new ExecutionException("Unable to obtain application id");
    }

    if (myLaunchTasksProvider.createsNewProcess()) {
      processHandler = new AndroidProcessHandler(applicationId);
      console = attachConsole(processHandler, executor);
    } else {
      // TODO: reuse existing process handler? (when hotswap is plugged in e.g.)
      processHandler = null;
      console = null;
      throw new IllegalStateException("Not implemented yet!");
    }

    LaunchInfo launchInfo = new LaunchInfo(executor, runner, myEnv, myConsoleProvider);

    LaunchTaskRunner task = new LaunchTaskRunner(myModule.getProject(),
                                                 myLaunchConfigName,
                                                 launchInfo,
                                                 processHandler,
                                                 myDeviceFutures,
                                                 myLaunchTasksProvider);
    ProgressManager.getInstance().run(task);

    return console == null ? null : new DefaultExecutionResult(console, processHandler);
  }

  @NotNull
  public ConsoleView attachConsole(@NotNull ProcessHandler processHandler, @NotNull Executor executor) throws ExecutionException {
    return myConsoleProvider.createAndAttach(myModule.getProject(), processHandler, executor);
  }
}
