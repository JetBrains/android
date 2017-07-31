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
package com.android.tools.idea.run.tasks;

import com.android.annotations.VisibleForTesting;
import com.android.ddmlib.Client;
import com.android.ddmlib.ClientData;
import com.android.ddmlib.IDevice;
import com.android.tools.idea.run.ConsolePrinter;
import com.android.tools.idea.run.LaunchInfo;
import com.android.tools.idea.run.ProcessHandlerConsolePrinter;
import com.android.tools.idea.run.editor.AndroidDebugger;
import com.android.tools.idea.run.util.LaunchStatus;
import com.android.tools.idea.run.util.ProcessHandlerLaunchStatus;
import com.google.common.base.Joiner;
import com.google.common.util.concurrent.Uninterruptibles;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.project.Project;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public abstract class ConnectDebuggerTask implements DebugConnectorTask {
  private static final int POLL_TIMEOUT = 15;
  private static final TimeUnit POLL_TIMEUNIT = TimeUnit.SECONDS;

  @NotNull protected final Set<String> myApplicationIds;
  @NotNull protected final AndroidDebugger myDebugger;
  @NotNull protected final Project myProject;
  protected final boolean myMonitorRemoteProcess;
  protected final boolean myAttachToRunningProcess;

  protected ConnectDebuggerTask(@NotNull Set<String> applicationIds,
                                @NotNull AndroidDebugger debugger,
                                @NotNull Project project,
                                boolean monitorRemoteProcess) {
    this(applicationIds, debugger, project, monitorRemoteProcess, false);
  }

  protected ConnectDebuggerTask(@NotNull Set<String> applicationIds,
                                @NotNull AndroidDebugger debugger,
                                @NotNull Project project,
                                boolean monitorRemoteProcess,
                                boolean attachToRunningProcess) {
    myApplicationIds = applicationIds;
    myDebugger = debugger;
    myProject = project;
    myMonitorRemoteProcess = monitorRemoteProcess;
    myAttachToRunningProcess = attachToRunningProcess;
  }

  @NotNull
  @Override
  public String getDescription() {
    return "Connecting Debugger";
  }

  @Override
  public int getDuration() {
    return LaunchTaskDurations.CONNECT_DEBUGGER;
  }

  @Override
  public ProcessHandler perform(@NotNull final LaunchInfo launchInfo,
                                @NotNull IDevice device,
                                @NotNull final ProcessHandlerLaunchStatus state,
                                @NotNull final ProcessHandlerConsolePrinter printer) {
    final Client client = waitForClient(device, state, printer);
    if (client == null) {
      return null;
    }

    return UIUtil.invokeAndWaitIfNeeded(() -> launchDebugger(launchInfo, client, state, printer));
  }

  @Nullable
  protected Client waitForClient(@NotNull IDevice device, @NotNull LaunchStatus state, @NotNull ConsolePrinter printer) {
    for (int i = 0; i < POLL_TIMEOUT; i++) {
      if (state.isLaunchTerminated()) {
        return null;
      }

      if (!device.isOnline()) {
        printer.stderr("Device is offline");
        return null;
      }

      for (String name : myApplicationIds) {
        Client client = device.getClient(name);
        if (client == null) {
          printer.stdout("Waiting for application to come online: " + Joiner.on(" | ").join(myApplicationIds));
        }
        else {
          printer.stdout("Connecting to " + name);
          ClientData.DebuggerStatus status = client.getClientData().getDebuggerConnectionStatus();
          switch (status) {
            case ERROR:
              String message = String
                .format(Locale.US, "Debug port (%1$d) is busy, make sure there is no other active debug connection to the same application",
                        client.getDebuggerListenPort());
              printer.stderr(message);
              return null;
            case ATTACHED:
              printer.stderr("A debugger is already attached");
              return null;
            case WAITING:
              if (isReadyForDebugging(client, printer)) {
                return client;
              }
              break;
            default:
              if (myAttachToRunningProcess && isReadyForDebugging(client, printer)) {
                return client;
              }
              printer.stderr("Waiting for application to start debug server");
              break;
          }
        }
      }
      sleep(1, POLL_TIMEUNIT);
    }
    printer.stderr("Could not connect to remote process. Aborting debug session.");
    return null;
  }

  @VisibleForTesting // Allow unit tests to avoid actually sleeping.
  protected void sleep(long sleepFor, @NotNull TimeUnit unit) {
    Uninterruptibles.sleepUninterruptibly(sleepFor, unit);
  }

  public boolean isReadyForDebugging(@NotNull Client client, @NotNull ConsolePrinter printer) {
    return true;
  }

  @Nullable
  public abstract ProcessHandler launchDebugger(@NotNull LaunchInfo currentLaunchInfo,
                                                @NotNull Client client,
                                                @NotNull ProcessHandlerLaunchStatus state,
                                                @NotNull ProcessHandlerConsolePrinter printer);
}
