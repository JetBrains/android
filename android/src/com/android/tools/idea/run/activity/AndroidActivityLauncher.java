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
package com.android.tools.idea.run.activity;

import com.android.annotations.VisibleForTesting;
import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.ClientData;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.TimeoutException;
import com.android.tools.idea.run.AndroidApplicationLauncher;
import com.android.tools.idea.run.AndroidRunningState;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

import static com.intellij.execution.process.ProcessOutputTypes.STDOUT;

public class AndroidActivityLauncher extends AndroidApplicationLauncher {
  private static final Logger LOG = Logger.getInstance(AndroidActivityLauncher.class);

  @NotNull private final ActivityLocator myActivityLocator;
  @NotNull private final String myActivityExtraFlags;

  public AndroidActivityLauncher(@NotNull ActivityLocator locator, @NotNull String activityExtraFlags) {
    myActivityLocator = locator;
    myActivityExtraFlags = activityExtraFlags;
  }

  @Override
  public boolean isReadyForDebugging(@NotNull ClientData data, @Nullable ProcessHandler processHandler) {
    ClientData.DebuggerStatus status = data.getDebuggerConnectionStatus();
    switch (status) {
      case ERROR:
        if (processHandler != null) {
          processHandler.notifyTextAvailable("Debug port is busy\n", STDOUT);
        }
        LOG.info("Debug port is busy");
        return false;
      case ATTACHED:
        if (processHandler != null) {
          processHandler.notifyTextAvailable("Debugger already attached\n", STDOUT);
        }
        LOG.info("Debugger already attached");
        return false;
      case WAITING:
        return true;
      case DEFAULT:
      default:
        String msg = "Client not ready yet.";
        if (processHandler != null) {
          processHandler.notifyTextAvailable(msg + "\n", STDOUT);
        }
        LOG.info(msg);
        return false;
    }
  }

  @Override
  public LaunchResult launch(@NotNull AndroidRunningState state, @NotNull IDevice device)
    throws IOException, AdbCommandRejectedException, TimeoutException {
    ProcessHandler processHandler = state.getProcessHandler();
    String activityName;
    try {
      activityName = myActivityLocator.getQualifiedActivityName(device);
    }
    catch (ActivityLocator.ActivityLocatorException e) {
      processHandler.notifyTextAvailable("Could not identify launch activity: " + e.getMessage(), STDOUT);
      return LaunchResult.NOTHING_TO_DO;
    }

    final String activityPath = getLauncherActivityPath(state.getPackageName(), activityName);
    if (state.isStopped()) return LaunchResult.STOP;
    processHandler.notifyTextAvailable("Launching application: " + activityPath + ".\n", STDOUT);

    String command = getStartActivityCommand(activityPath, getDebugFlags(state), myActivityExtraFlags);
    return executeCommand(command, state, device);
  }

  @VisibleForTesting
  @NotNull
  static String getStartActivityCommand(@NotNull String activityPath, @NotNull String debugFlags, @NotNull String extraFlags) {
    return "am start " +
           debugFlags +
           " -n \"" + activityPath + "\" " +
           "-a android.intent.action.MAIN " +
           "-c android.intent.category.LAUNCHER" +
           (extraFlags.isEmpty() ? "" : " " + extraFlags);
  }

  @VisibleForTesting
  @NotNull
  static String getLauncherActivityPath(@NotNull String packageName, @NotNull String activityName) {
    return packageName + "/" + activityName.replace("$", "\\$");
  }
}
