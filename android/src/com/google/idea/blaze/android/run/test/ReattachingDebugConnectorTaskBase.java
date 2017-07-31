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
package com.google.idea.blaze.android.run.test;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.Client;
import com.android.ddmlib.ClientData;
import com.android.ddmlib.IDevice;
import com.android.tools.idea.run.LaunchInfo;
import com.android.tools.idea.run.ProcessHandlerConsolePrinter;
import com.android.tools.idea.run.editor.AndroidDebugger;
import com.android.tools.idea.run.tasks.ConnectDebuggerTask;
import com.android.tools.idea.run.tasks.ConnectJavaDebuggerTask;
import com.android.tools.idea.run.util.ProcessHandlerLaunchStatus;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Base class for {@link ConnectDebuggerTask} implementations that need to keep reattaching the debugger.
 *
 * <p>Wires up adb listeners to automatically reconnect the debugger for each test. This is necessary when
 * using instrumentation runners that kill the instrumentation process between each test, disconnecting
 * the debugger. We listen for the start of a new test, waiting for a debugger, and reconnect.
 */
public abstract class ReattachingDebugConnectorTaskBase extends ConnectJavaDebuggerTask {

  /**
   * Changes to {@link Client} instances that mean a new debugger should be connected.
   *
   * The target application can either:
   *   1. Match our target name, and become available for debugging.
   *   2. Be available for debugging, and suddenly have its name changed to match.
   */
  private static final int CHANGE_MASK = Client.CHANGE_DEBUGGER_STATUS | Client.CHANGE_NAME;

  public ReattachingDebugConnectorTaskBase(@NotNull Set<String> applicationIds,
                                           @NotNull AndroidDebugger debugger,
                                           @NotNull Project project,
                                           boolean monitorRemoteProcess) {
    super(applicationIds, debugger, project, monitorRemoteProcess, false);
  }

  @Nullable
  @Override
  public ProcessHandler perform(
      @NotNull LaunchInfo launchInfo,
      @NotNull IDevice device,
      @NotNull ProcessHandlerLaunchStatus status,
      @NotNull ProcessHandlerConsolePrinter printer) {
    // TODO(b/4135490): This duplicates some internal code, we need to refactor it to be shared.

    AndroidDebugBridge.IClientChangeListener reattachingListener = (client, changeMask) -> {
      ClientData data = client.getClientData();
      String clientDescription = data.getClientDescription();
      if (myApplicationIds.contains(clientDescription)) {
        if ((changeMask & CHANGE_MASK) != 0 && data.getDebuggerConnectionStatus().equals(ClientData.DebuggerStatus.WAITING)) {
          ApplicationManager.getApplication().invokeLater(() -> launchDebugger(launchInfo, client, status, printer));
        }
      }
    };

    AndroidDebugBridge.addClientChangeListener(reattachingListener);
    registerLaunchTaskCompleteCallback(() -> AndroidDebugBridge.removeClientChangeListener(reattachingListener));

    return null;
  }

  /**
   * Registers the given code to be executed after the tests have completed. Used to remove the adb listener.
   */
  protected abstract void registerLaunchTaskCompleteCallback(@NotNull Runnable runnable);

}
