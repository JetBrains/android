/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.run.tasks;

import com.android.ddmlib.Client;
import com.android.ddmlib.IDevice;
import com.android.tools.idea.run.*;
import com.android.tools.idea.run.editor.AndroidDebugger;
import com.android.tools.idea.run.util.ProcessHandlerLaunchStatus;
import com.google.idea.blaze.android.run.test.ReattachingDebugConnectorTaskBase;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Combines the {@link ReattachingDebugConnectorTaskBase} implementation of {@code perform} with {@link ConnectJavaDebuggerTask}
 * implementation of {@link #launchDebugger(LaunchInfo, Client, ProcessHandlerLaunchStatus, ProcessHandlerConsolePrinter)}.
 *
 * <p>Used for tests that use Android Test Orchstrator.
 */
public class AndroidTestOrchestratorJavaDebuggerTask extends ReattachingDebugConnectorTaskBase {

  /**
   * {@link ProcessHandler} for monitoring the orchestrator process. This allows us to notice (and control) when the outer orchestration has
   * finished.
   */
  private AndroidProcessHandler orchestratorHandler;

  public AndroidTestOrchestratorJavaDebuggerTask(@NotNull Set<String> applicationIds,
                                                 @NotNull AndroidDebugger debugger,
                                                 @NotNull Project project,
                                                 boolean monitorRemoteProcess) {
    super(applicationIds, debugger, project, monitorRemoteProcess);
  }

  @Nullable
  @Override
  public ProcessHandler perform(@NotNull LaunchInfo launchInfo,
                      @NotNull IDevice device,
                      @NotNull ProcessHandlerLaunchStatus status,
                      @NotNull ProcessHandlerConsolePrinter printer) {
    orchestratorHandler = new AndroidProcessHandler("android.support.test.orchestrator", true);
    orchestratorHandler.addTargetDevice(device);
    orchestratorHandler.startNotify();

    return super.perform(launchInfo, device, status, printer);
  }

  @Override
  protected void registerLaunchTaskCompleteCallback(@NotNull Runnable runnable) {
    orchestratorHandler.addProcessListener(new ProcessAdapter() {
      @Override
      public void processTerminated(ProcessEvent event) {
        runnable.run();
      }
    });

    if (orchestratorHandler.isProcessTerminated()) {
      runnable.run();
    }
  }

  @NotNull
  @Override
  public ProcessHandler createDebugProcessHandler(@NotNull ProcessHandlerLaunchStatus launchStatus) {
    AndroidRemoteDebugProcessHandler newHandler = new AndroidRemoteDebugProcessHandler(myProject, myMonitorRemoteProcess);
    newHandler.addProcessListener(new ProcessAdapter() {
      @Override
      public void processWillTerminate(ProcessEvent event, boolean willBeDestroyed) {
        if (willBeDestroyed) {
          // willBeDestroyed == true means it's either the user cancelling or test listener decided we're done. If it's the former, we need
          // to kill the orchestrator to stop more instrumentations from being created. If it's the latter, it should be dead anyway.
          orchestratorHandler.destroyProcess();
        }
      }
    });
    AndroidProcessText.attach(newHandler);
    return newHandler;
  }

  @Override
  @NotNull
  public RunContentDescriptor getDescriptor(@NotNull LaunchInfo currentLaunchInfo,
                                            @NotNull ProcessHandlerLaunchStatus launchStatus) {
    AndroidSessionInfo oldSession = getOldProcessHandler(currentLaunchInfo, launchStatus).getUserData(AndroidSessionInfo.KEY);
    assert oldSession != null;
    return oldSession.getDescriptor();
  }

  @Override
  @NotNull
  public ProcessHandler getOldProcessHandler(@NotNull LaunchInfo currentLaunchInfo,
                                             @NotNull ProcessHandlerLaunchStatus launchStatus) {
    return launchStatus.getProcessHandler();
  }
}
