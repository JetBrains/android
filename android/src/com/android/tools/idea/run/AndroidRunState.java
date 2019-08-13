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

import com.android.ddmlib.Client;
import com.android.ddmlib.IDevice;
import com.android.tools.idea.run.tasks.LaunchTasksProvider;
import com.android.tools.idea.run.util.SwapInfo;
import com.android.tools.idea.stats.RunStats;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.impl.DebuggerSession;
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
import com.intellij.execution.ui.RunContentManagerImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.ui.content.Content;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
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
    RunStats stats = RunStats.from(myEnv);

    String applicationId;
    try {
      applicationId = myApplicationIdProvider.getPackageName();
    }
    catch (ApkProvisionException e) {
      throw new ExecutionException("Unable to obtain application id", e);
    }

    stats.setPackage(applicationId);

    AndroidSessionInfo existingSessionInfo = AndroidSessionInfo.findOldSession(
      myEnv.getProject(), null, (AndroidRunConfigurationBase)myEnv.getRunProfile(), myEnv.getExecutionTarget());
    ProcessHandler previousSessionProcessHandler;
    SwapInfo swapInfo = myEnv.getUserData(SwapInfo.SWAP_INFO_KEY);
    if (existingSessionInfo == null) { // We might have a RemoteDebugProcess.
      // The following cases are possible:
      // 1) We're swapping to an existing regular Android Run/Debug tab, then use what was provided.
      if (swapInfo != null) {
        previousSessionProcessHandler = swapInfo.getHandler();
      }
      // 2) We can't find an existing session and we're not swapping, then look for an existing remote debugger (or null if not present).
      else {
        Collection<DebuggerSession> debuggerSessions = DebuggerManagerEx.getInstanceEx(myEnv.getProject()).getSessions();
        List<IDevice> liveDevices = myDeviceFutures.getIfReady();
        if (!debuggerSessions.isEmpty() && liveDevices != null) {
          // Create a map of debugger ports to their corresponding Clients.
          Map<String, Client> portClientMap = liveDevices.stream()
            .flatMap(device -> Arrays.stream(device.getClients()))
            .collect(Collectors.toMap(client -> Integer.toString(client.getDebuggerListenPort()), client -> client));

          // Find a Client that uses the same port as the debugging session.
          previousSessionProcessHandler = debuggerSessions.stream()
            .filter(session -> portClientMap.containsKey(session.getProcess().getConnection().getAddress().trim()))
            .map(session -> session.getProcess().getProcessHandler())
            .findAny()
            .orElse(null);
        }
        else {
          // 3) We're swapping directly to a ddmlib-only-aware process; just set previousSessionProcessHandler to null, creating a new tab.
          previousSessionProcessHandler = null;
        }
      }
    }
    else {
      previousSessionProcessHandler = existingSessionInfo.getProcessHandler();
    }

    RunContentManager manager = RunContentManager.getInstance(myEnv.getProject());
    RunContentDescriptor previousDescriptor = manager.getAllDescriptors().stream()
      .filter(descriptor -> descriptor.getProcessHandler() == previousSessionProcessHandler)
      .findFirst()
      .orElse(null);

    ProcessHandler processHandler;
    ExecutionConsole console;
    if (swapInfo != null && previousSessionProcessHandler != null && previousDescriptor != null) {
      // When we're swapping into a connected process, use the existing descriptor/ProcessHandler instead of creating a new one.
      processHandler = previousSessionProcessHandler;
      // Try to find the old console from the previous process handler, since we're swapping into that same tool window tab.
      console = previousDescriptor.getExecutionConsole();
      if (console == null) {
        console = attachConsole(processHandler, executor);
      }
    }
    else {
      if (previousSessionProcessHandler != null) {
        // In the case of cold swap, there is an existing process that is connected, but we are going to launch a new one.
        // Destroy the previous content and detach the previous process handler so that we don't end up with 2 run tabs
        // for the same launch (the existing one and the new one).
        if (previousDescriptor != null) {
          Content previousContent = previousDescriptor.getAttachedContent();
          if (previousContent == null) {
            Logger.getInstance(AndroidRunState.class).warn("Descriptor without content.");
          }
          else {
            Executor previousExecutor = RunContentManagerImpl.getExecutorByContent(previousContent);
            if (previousExecutor == null) {
              Logger.getInstance(AndroidRunState.class).warn("No executor found for content");
            }
            else if (!manager.removeRunContent(previousExecutor, previousDescriptor)) {
              // In case there's an existing handler, it could pop up a dialog prompting the user to confirm. If the user
              // cancels, removeRunContent will return false. In such case, stop the run.
              return null;
            }
          }
        }
        previousSessionProcessHandler.detachProcess();
      }

      processHandler = new AndroidProcessHandler(myEnv.getProject(), applicationId);
      console = attachConsole(processHandler, executor);
    }

    BiConsumer<String, HyperlinkInfo> hyperlinkConsumer =
      console instanceof ConsoleView ? ((ConsoleView)console)::printHyperlink : (s, h) -> {};

    LaunchInfo launchInfo = new LaunchInfo(executor, runner, myEnv, myConsoleProvider);
    LaunchTaskRunner task = new LaunchTaskRunner(myModule.getProject(),
                                                 myLaunchConfigName,
                                                 applicationId,
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
