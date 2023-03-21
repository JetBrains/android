/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.execution.common.debug.utils;

import static com.android.tools.idea.execution.common.debug.utils.UtilsKt.showError;

import com.android.annotations.concurrency.Slow;
import com.android.ddmlib.Client;
import com.android.tools.idea.execution.common.debug.AndroidDebugger;
import com.android.tools.idea.execution.common.debug.AndroidDebuggerState;
import com.android.tools.idea.execution.common.debug.RunConfigurationWithDebugger;
import com.android.tools.idea.execution.common.processhandler.AndroidProcessHandler;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidConnectDebugger {
  @Slow
  public static void closeOldSessionAndRun(@NotNull Project project,
                                           @NotNull AndroidDebugger androidDebugger,
                                           @NotNull Client client,
                                           @Nullable RunConfigurationWithDebugger configuration) {
    terminateRunSessions(project, client);
    AndroidDebuggerState state;
    if (configuration != null) {
      state = configuration.getAndroidDebuggerContext().getAndroidDebuggerState();
    }
    else {
      state = androidDebugger.createState();
    }
    try {
      androidDebugger.attachToClient(project, client, state);
    }
    catch (ExecutionException e) {
      showError(project, e, "Attach debug to process");
    }
  }

  // Disconnect any active run sessions to the same client
  private static void terminateRunSessions(@NotNull Project project, @NotNull Client selectedClient) {
    int pid = selectedClient.getClientData().getPid();

    // find if there are any active run sessions to the same client, and terminate them if so
    for (ProcessHandler handler : ExecutionManager.getInstance(project).getRunningProcesses()) {
      if (handler instanceof AndroidProcessHandler) {
        Client client = ((AndroidProcessHandler)handler).getClient(selectedClient.getDevice());
        if (client != null && client.getClientData().getPid() == pid) {
          handler.notifyTextAvailable("Disconnecting run session: a new debug session will be established.\n", ProcessOutputTypes.STDOUT);
          handler.detachProcess();
          break;
        }
      }
    }
  }
}
