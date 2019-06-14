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

import com.android.tools.idea.run.tasks.LaunchTasksProvider;
import com.android.tools.idea.run.ui.ApplyChangesAction;
import com.android.tools.idea.run.ui.CodeSwapAction;
import com.android.tools.idea.stats.RunStats;
import com.google.common.base.MoreObjects;
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
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunContentManager;
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
  @Nullable private final ProcessHandler myPreviousSessionProcessHandler;

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

    AndroidSessionInfo existingSessionInfo = AndroidSessionInfo.findOldSession(
      env.getProject(), null, ((AndroidRunConfigurationBase)env.getRunProfile()).getUniqueID(), env.getExecutionTarget());
    myPreviousSessionProcessHandler = existingSessionInfo != null ? existingSessionInfo.getProcessHandler() : null;
  }

  @Nullable
  @Override
  public ExecutionResult execute(Executor executor, @NotNull ProgramRunner runner) throws ExecutionException {
    ProcessHandler processHandler;
    ExecutionConsole console;
    RunStats stats = RunStats.from(myEnv);

    String applicationId;
    try {
      applicationId = myApplicationIdProvider.getPackageName();
    }
    catch (ApkProvisionException e) {
      throw new ExecutionException("Unable to obtain application id", e);
    }

    stats.setPackage(applicationId);

    boolean isSwap = MoreObjects.firstNonNull(myEnv.getCopyableUserData(CodeSwapAction.KEY), Boolean.FALSE) ||
                     MoreObjects.firstNonNull(myEnv.getCopyableUserData(ApplyChangesAction.KEY), Boolean.FALSE);
    RunContentManager manager = RunContentManager.getInstance(myEnv.getProject());
    RunContentDescriptor previousDescriptor = manager.findContentDescriptor(executor, myPreviousSessionProcessHandler);
    if (!isSwap) {
      if (myPreviousSessionProcessHandler != null) {
        // In the case of cold swap, there is an existing process that is connected, but we are going to launch a new one.
        // Destroy the previous content and detach the previous process handler so that we don't end up with 2 run tabs
        // for the same launch (the existing one and the new one).
        if (previousDescriptor != null) {
          if (!manager.removeRunContent(executor, previousDescriptor)) {
            // In case there's an existing handler, it could pop up a dialog prompting the user to confirm. If the user
            // cancels, removeRunContent will return false. In such case, stop the run.
            return null;
          }
        }
        myPreviousSessionProcessHandler.detachProcess();
      }

      processHandler = new AndroidProcessHandler.Builder(myEnv.getProject())
        .setApplicationId(applicationId)
        .monitorRemoteProcesses(myLaunchTasksProvider.monitorRemoteProcess())
        .build();
      console = attachConsole(processHandler, executor);
      // Stash the console. When we swap, we need the console, as that has the method to print a hyperlink.
      // (If we only need normal text output, we can call ProcessHandler#notifyTextAvailable instead.)
    }
    else {
      assert myPreviousSessionProcessHandler != null : "No process handler from previous session, yet current tasks don't create one";
      processHandler = myPreviousSessionProcessHandler;
      // Try to find the old console from the previous process handler, since we're swapping into that same tool window tab.
      console = previousDescriptor == null ? null : previousDescriptor.getExecutionConsole();
      console = console == null ? attachConsole(processHandler, executor) : console;
    }

    BiConsumer<String, HyperlinkInfo> hyperlinkConsumer =
      console instanceof ConsoleView ? ((ConsoleView)console)::printHyperlink : (s, h) -> {};

    LaunchInfo launchInfo = new LaunchInfo(executor, runner, myEnv, myConsoleProvider);
    LaunchTaskRunner task = new LaunchTaskRunner(myModule.getProject(),
                                                 myLaunchConfigName,
                                                 myEnv.getExecutionTarget().getDisplayName(),
                                                 launchInfo,
                                                 processHandler,
                                                 myDeviceFutures,
                                                 myLaunchTasksProvider,
                                                 stats,
                                                 hyperlinkConsumer);
    ProgressManager.getInstance().run(task);
    return new DefaultExecutionResult(console, processHandler);
  }

  @NotNull
  public ConsoleView attachConsole(@NotNull ProcessHandler processHandler, @NotNull Executor executor) throws ExecutionException {
    return myConsoleProvider.createAndAttach(myModule.getProject(), processHandler, executor);
  }
}
