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

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.Client;
import com.android.ddmlib.ClientData;
import com.android.ddmlib.IDevice;
import com.android.tools.idea.run.AndroidProcessText;
import com.android.tools.idea.run.AndroidRemoteDebugProcessHandler;
import com.android.tools.idea.run.AndroidSessionInfo;
import com.android.tools.idea.run.LaunchInfo;
import com.android.tools.idea.run.ProcessHandlerConsolePrinter;
import com.android.tools.idea.run.editor.AndroidDebugger;
import com.android.tools.idea.run.util.ProcessHandlerLaunchStatus;
import com.google.common.base.Preconditions;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A {@link ConnectDebuggerTask} implementations that need to keep reattaching the debugger.
 *
 * <p>Wires up adb listeners to automatically reconnect the debugger for each test. This is necessary when
 * using instrumentation runners that kill the instrumentation process between each test, disconnecting
 * the debugger. We listen for the start of a new test, waiting for a debugger, and reconnect.
 */
public class ReattachingDebugConnectorTask extends ConnectJavaDebuggerTask {

  /**
   * Changes to {@link Client} instances that mean a new debugger should be connected.
   *
   * The target application can either:
   *   1. Match our target name, and become available for debugging.
   *   2. Be available for debugging, and suddenly have its name changed to match.
   */
  private static final int CHANGE_MASK = Client.CHANGE_DEBUGGER_STATUS | Client.CHANGE_NAME;

  @Nullable
  private AndroidDebugBridge.IClientChangeListener myReattachingListener;

  public ReattachingDebugConnectorTask(@NotNull Set<String> applicationIds,
                                       @NotNull AndroidDebugger debugger,
                                       @NotNull Project project) {
    super(applicationIds, debugger, project, false);
  }

  @Nullable
  @Override
  public ProcessHandler perform(
      @NotNull LaunchInfo launchInfo,
      @NotNull IDevice device,
      @NotNull ProcessHandlerLaunchStatus status,
      @NotNull ProcessHandlerConsolePrinter printer) {
    if (myReattachingListener != null) {
      AndroidDebugBridge.removeClientChangeListener(myReattachingListener);
    }
    myReattachingListener = (client, changeMask) -> {
      ClientData data = client.getClientData();
      String clientDescription = data.getClientDescription();
      if (myApplicationIds.contains(clientDescription)) {
        if ((changeMask & CHANGE_MASK) != 0 && data.getDebuggerConnectionStatus().equals(ClientData.DebuggerStatus.WAITING)) {
          ApplicationManager.getApplication().invokeLater(() -> launchDebugger(launchInfo, client, status, printer));
        }
      }
    };

    AndroidDebugBridge.addClientChangeListener(myReattachingListener);

    return null;
  }

  @NotNull
  @Override
  public ProcessHandler createDebugProcessHandler(@NotNull ProcessHandlerLaunchStatus launchStatus) {
    AndroidRemoteDebugProcessHandler newHandler = new AndroidRemoteDebugProcessHandler(myProject);
    newHandler.addProcessListener(new ProcessAdapter() {
      @Override
      public void processWillTerminate(ProcessEvent event, boolean willBeDestroyed) {
        if (willBeDestroyed && myReattachingListener != null) {
          // willBeDestroyed == true means it's either the user cancelling or test listener decided we're done. If it's the former, we need
          // to kill the orchestrator to stop more instrumentations from being created. If it's the latter, it should be dead anyway.
          AndroidDebugBridge.removeClientChangeListener(myReattachingListener);
          myReattachingListener = null;
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
    return Preconditions.checkNotNull(oldSession).getDescriptor();
  }

  @Override
  @NotNull
  public ProcessHandler getOldProcessHandler(@NotNull LaunchInfo currentLaunchInfo,
                                             @NotNull ProcessHandlerLaunchStatus launchStatus) {
    return launchStatus.getProcessHandler();
  }
}
