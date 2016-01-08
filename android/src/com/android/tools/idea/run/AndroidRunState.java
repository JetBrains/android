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
import com.android.tools.idea.fd.InstantRunBuildInfo;
import com.android.tools.idea.fd.InstantRunManager;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.run.tasks.LaunchTasksProvider;
import com.android.tools.idea.run.tasks.LaunchTasksProviderFactory;
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
  @NotNull private final LaunchTasksProviderFactory myLaunchTasksProviderFactory;
  @Nullable private final ProcessHandler myPreviousSessionProcessHandler;

  public AndroidRunState(@NotNull ExecutionEnvironment env,
                         @NotNull String launchConfigName,
                         @NotNull Module module,
                         @NotNull ApkProvider apkProvider,
                         @NotNull ConsoleProvider consoleProvider,
                         @NotNull Collection<ListenableFuture<IDevice>> deviceFutures,
                         @NotNull LaunchTasksProviderFactory launchTasksProviderFactory,
                         @Nullable ProcessHandler processHandler) {
    myEnv = env;
    myLaunchConfigName = launchConfigName;
    myModule = module;
    myApkProvider = apkProvider;
    myConsoleProvider = consoleProvider;
    myDeviceFutures = deviceFutures;
    myLaunchTasksProviderFactory = launchTasksProviderFactory;
    myPreviousSessionProcessHandler = processHandler;
  }

  @Nullable
  @Override
  public ExecutionResult execute(Executor executor, @NotNull ProgramRunner runner) throws ExecutionException {
    ProcessHandler processHandler;
    ConsoleView console;

    String applicationId;
    try {
      applicationId = myApkProvider.getPackageName();
    }
    catch (ApkProvisionException e) {
      throw new ExecutionException("Unable to obtain application id");
    }

    AndroidGradleModel model = AndroidGradleModel.get(myModule);
    if (model != null && InstantRunManager.isInstantRunSupported(model)) {
      InstantRunBuildInfo info = InstantRunBuildInfo.get(model);
      if (info != null) {
        int format = info.getFormat();
        if (format == -1 || format > 1) {
          throw new ExecutionException("This version of Android Studio is incompatible with the Gradle Plugin used. " +
                                       "Try disabling Instant Run.");
        }
      }
    }

    LaunchTasksProvider launchTasksProvider = myLaunchTasksProviderFactory.get();

    if (launchTasksProvider.createsNewProcess()) {
      // In the case of cold swap, there is an existing process that is connected, but we are going to launch a new one.
      // Detach the previous process handler so that we don't end up with 2 run tabs for the same launch (the existing one
      // and the new one).
      if (myPreviousSessionProcessHandler != null) {
        myPreviousSessionProcessHandler.detachProcess();
      }

      processHandler = new AndroidProcessHandler(applicationId);
      console = attachConsole(processHandler, executor);
    } else {
      processHandler = myPreviousSessionProcessHandler;
      console = null;
    }

    LaunchInfo launchInfo = new LaunchInfo(executor, runner, myEnv, myConsoleProvider);

    LaunchTaskRunner task = new LaunchTaskRunner(myModule.getProject(),
                                                 myLaunchConfigName,
                                                 launchInfo,
                                                 processHandler,
                                                 myDeviceFutures,
                                                 launchTasksProvider);
    ProgressManager.getInstance().run(task);

    return console == null ? null : new DefaultExecutionResult(console, processHandler);
  }

  @NotNull
  public ConsoleView attachConsole(@NotNull ProcessHandler processHandler, @NotNull Executor executor) throws ExecutionException {
    return myConsoleProvider.createAndAttach(myModule.getProject(), processHandler, executor);
  }
}
