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

import com.google.common.annotations.VisibleForTesting;
import com.android.ddmlib.IDevice;
import com.android.tools.idea.run.ConsolePrinter;
import com.android.tools.idea.run.activity.AndroidActivityLauncher;
import com.android.tools.idea.run.activity.StartActivityFlagsProvider;
import com.android.tools.idea.run.util.LaunchStatus;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.TimeUnit;

public abstract class ActivityLaunchTask extends AppLaunchTask {
  @VisibleForTesting
  static final String ACTIVITY_DOES_NOT_EXIST = "ACTIVITY_DOES_NOT_EXIST";
  @VisibleForTesting
  static final String UNABLE_TO_DETERMINE_LAUNCH_ACTIVITY = "UNABLE_TO_DETERMINE_LAUNCH_ACTIVITY";
  @VisibleForTesting
  static final String UNKNOWN_ACTIVITY_LAUNCH_TASK_ERROR = "UNKNOWN_ACTIVITY_LAUNCH_TASK_ERROR";

  @NotNull private final String myApplicationId;
  @NotNull private final StartActivityFlagsProvider myStartActivityFlagsProvider;

  public ActivityLaunchTask(@NotNull String applicationId,
                            @NotNull StartActivityFlagsProvider startActivityFlagsProvider) {
    myApplicationId = applicationId;
    myStartActivityFlagsProvider = startActivityFlagsProvider;
  }

  @NotNull
  @Override
  public String getDescription() {
    return "Launching activity";
  }

  @Override
  public int getDuration() {
    return LaunchTaskDurations.LAUNCH_ACTIVITY;
  }

  @Override
  public LaunchResult run(@NotNull LaunchContext launchContext) {
    LaunchStatus launchStatus = launchContext.getLaunchStatus();
    ConsolePrinter printer = launchContext.getConsolePrinter();
    IDevice device = launchContext.getDevice();

    String command = getStartActivityCommand(device, launchStatus, printer);
    if (command == null) {
      return LaunchResult.error(UNABLE_TO_DETERMINE_LAUNCH_ACTIVITY, getDescription());
    }
    ErrorAwarePrinterWrapper printerWrapper = new ErrorAwarePrinterWrapper(printer);
    // The timeout is quite large to accomodate ARM emulators.
    boolean successful = executeShellCommand(command, device, launchStatus, printerWrapper, 15, TimeUnit.SECONDS);
    if (printerWrapper.sawActivityDoesNotExistError()) {
      return LaunchResult.error(ACTIVITY_DOES_NOT_EXIST, getDescription());
    }
    return successful ? LaunchResult.success() : LaunchResult.error(UNKNOWN_ACTIVITY_LAUNCH_TASK_ERROR, getDescription());
  }

  /**
   * Executes the given command, collecting the entire shell output into a single String
   * before passing it to the given {@link ConsolePrinter}.
   */
  protected boolean executeShellCommand(@NotNull String command,
                                        @NotNull IDevice device,
                                        @NotNull LaunchStatus launchStatus,
                                        @NotNull ConsolePrinter printer,
                                        long timeout,
                                        @NotNull TimeUnit timeoutUnit) {
    return ShellCommandLauncher.execute(command, device, launchStatus, printer, timeout, timeoutUnit);
  }

  @VisibleForTesting
  @Nullable
  public String getStartActivityCommand(@NotNull IDevice device, @NotNull LaunchStatus launchStatus, @NotNull ConsolePrinter printer) {
    String activityName = getQualifiedActivityName(device, printer);
    if (activityName == null) {
      return null;
    }
    String activityPath = AndroidActivityLauncher.getLauncherActivityPath(myApplicationId, activityName);
    return AndroidActivityLauncher.getStartActivityCommand(activityPath, myStartActivityFlagsProvider.getFlags(device));
  }

  @Nullable
  protected abstract String getQualifiedActivityName(@NotNull IDevice device, @NotNull ConsolePrinter printer);

  /**
   * Wraps a delegate {@link ConsolePrinter}, parsing stderr output for the error we see when trying
   * to launch an activity that's missing from the installed APK.
   */
  private static class ErrorAwarePrinterWrapper implements ConsolePrinter {
    private static final String ACTIVITY_DOES_NOT_EXIST_REGEX = "Activity class \\{[^}]*} does not exist";
    private static final Pattern activityDoesNotExistPattern = Pattern.compile(ACTIVITY_DOES_NOT_EXIST_REGEX);

    private final ConsolePrinter delegate;
    private boolean sawActivityDoesNotExist;

    ErrorAwarePrinterWrapper(ConsolePrinter delegate) {
      this.delegate = delegate;
    }

    boolean sawActivityDoesNotExistError() {
      return sawActivityDoesNotExist;
    }

    @Override
    public void stdout(@NotNull String message) {
      delegate.stdout(message);
    }

    @Override
    public void stderr(@NotNull String message) {
      delegate.stderr(message);
      // Note that this is safe because ActivityLaunchTask#executeShellCommand is guaranteed to collect
      // complete shell output before passing it to the ConsolePrinter. Otherwise, we could receive bits
      // of shell output across multiple calls that we'd need to buffer ourselves.
      if (activityDoesNotExistPattern.matcher(message).find()) {
        sawActivityDoesNotExist = true;
      }
    }
  }
}
