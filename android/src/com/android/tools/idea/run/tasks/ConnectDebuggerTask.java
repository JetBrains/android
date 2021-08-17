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

import com.android.tools.idea.run.ApkProvisionException;
import com.android.tools.idea.run.ApplicationIdProvider;
import com.google.common.annotations.VisibleForTesting;
import com.android.ddmlib.Client;
import com.android.ddmlib.ClientData;
import com.android.ddmlib.IDevice;
import com.android.tools.idea.run.ConsolePrinter;
import com.android.tools.idea.run.DeploymentApplicationService;
import com.android.tools.idea.run.LaunchInfo;
import com.android.tools.idea.run.ProcessHandlerConsolePrinter;
import com.android.tools.idea.run.editor.AndroidDebugger;
import com.android.tools.idea.run.util.LaunchStatus;
import com.android.tools.idea.run.util.ProcessHandlerLaunchStatus;
import com.google.common.base.Joiner;
import com.google.common.util.concurrent.Uninterruptibles;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.ui.UIUtil;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class ConnectDebuggerTask implements DebugConnectorTask {
  private static final int POLL_TIMEOUT = 15;
  private static final TimeUnit POLL_TIMEUNIT = TimeUnit.SECONDS;

  // The first entry in the list contains the main package name, and an optional second entry contains test package name.
  @NotNull protected final List<String> myApplicationIds;
  @NotNull protected final AndroidDebugger myDebugger;
  @NotNull protected final Project myProject;
  protected final boolean myAttachToRunningProcess;

  protected ConnectDebuggerTask(@NotNull ApplicationIdProvider applicationIdProvider,
                                @NotNull AndroidDebugger debugger,
                                @NotNull Project project,
                                boolean attachToRunningProcess) {
    myDebugger = debugger;
    myProject = project;
    myAttachToRunningProcess = attachToRunningProcess;

    Logger logger = Logger.getInstance(ConnectDebuggerTask.class);
    myApplicationIds = new LinkedList<>();

    try {
      String packageName = applicationIdProvider.getPackageName();
      myApplicationIds.add(packageName);
    }
    catch (ApkProvisionException e) {
      logger.error(e);
    }

    try {
      String testPackageName = applicationIdProvider.getTestPackageName();
      if (testPackageName != null) {
        myApplicationIds.add(testPackageName);
      }
    }
    catch (ApkProvisionException e) {
      // not as severe as failing to obtain package id for main application
      logger.warn("Unable to obtain test package name, will not connect debugger if tests don't instantiate main application");
    }
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
        List<Client> clients = DeploymentApplicationService.getInstance().findClient(device, name);
        if (clients.isEmpty()) {
          printer.stdout("Waiting for application to come online: " + Joiner.on(" | ").join(myApplicationIds));
        }
        else {
          printer.stdout("Connecting to " + name);
          // Even though multiple processes may be related to a particular application ID, we'll only connect to the first one
          // in the list since the debugger is set up to only connect to at most one process.
          // TODO b/122613825: improve support for connecting to multiple processes with the same application ID.
          // This requires this task to wait for potentially multiple Clients before returning.
          if (clients.size() > 1) {
            Logger.getInstance(ConnectDebuggerTask.class).info("Multiple clients with same application ID: " + name);
          }
          Client client = clients.get(0);
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
