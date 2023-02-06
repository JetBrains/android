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

import com.android.ddmlib.CollectingOutputReceiver;
import com.android.ddmlib.IDevice;
import com.android.tools.idea.execution.common.AndroidExecutionException;
import com.android.tools.idea.run.activity.AndroidActivityLauncher;
import com.android.tools.idea.run.activity.StartActivityFlagsProvider;
import com.android.tools.idea.run.configuration.execution.ExecutionUtils;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ui.ConsoleView;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class ActivityLaunchTask extends AppLaunchTask {
  @VisibleForTesting
  static final String ACTIVITY_DOES_NOT_EXIST = "ACTIVITY_DOES_NOT_EXIST";
  @VisibleForTesting
  static final String UNABLE_TO_DETERMINE_LAUNCH_ACTIVITY = "UNABLE_TO_DETERMINE_LAUNCH_ACTIVITY";

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

  private static final String ACTIVITY_DOES_NOT_EXIST_REGEX = "Activity class \\{[^}]*} does not exist";
  private static final Pattern activityDoesNotExistPattern = Pattern.compile(ACTIVITY_DOES_NOT_EXIST_REGEX);

  @VisibleForTesting
  public String getStartActivityCommand(@NotNull IDevice device, @NotNull ConsoleView consoleView) throws ExecutionException {
    String activityName = getQualifiedActivityName(device, consoleView);
    if (activityName == null) {
      throw new AndroidExecutionException(UNABLE_TO_DETERMINE_LAUNCH_ACTIVITY, "Unable to determine activity name");
    }
    String activityPath = AndroidActivityLauncher.getLauncherActivityPath(myApplicationId, activityName);
    return AndroidActivityLauncher.getStartActivityCommand(activityPath, myStartActivityFlagsProvider.getFlags(device));
  }

  @Nullable
  protected abstract String getQualifiedActivityName(@NotNull IDevice device, @NotNull ConsoleView consoleView);

  @Override
  public void run(@NotNull LaunchContext launchContext) throws ExecutionException {
    ConsoleView console = launchContext.getConsoleView();
    IDevice device = launchContext.getDevice();

    String command = getStartActivityCommand(device, console);
    CollectingOutputReceiver collectingOutputReceiver = new CollectingOutputReceiver();

    executeShellCommand(launchContext, console, device, command, collectingOutputReceiver);
    final Matcher matcher = activityDoesNotExistPattern.matcher(collectingOutputReceiver.getOutput());
    if (matcher.find()) {
      throw new AndroidExecutionException(ACTIVITY_DOES_NOT_EXIST, matcher.group());
    }
  }

  @VisibleForTesting
  protected void executeShellCommand(@NotNull LaunchContext launchContext,
                                     ConsoleView console,
                                     IDevice device,
                                     String command,
                                     CollectingOutputReceiver collectingOutputReceiver) throws ExecutionException {
    // The timeout is quite large to accommodate ARM emulators.
    ExecutionUtils.executeShellCommand(device, command, console, collectingOutputReceiver, 15, TimeUnit.SECONDS,
                                       launchContext.getProgressIndicator());
  }
}
