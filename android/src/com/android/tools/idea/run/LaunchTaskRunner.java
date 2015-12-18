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

import com.android.ddmlib.IDevice;
import com.android.tools.idea.run.tasks.DebugConnectorTask;
import com.android.tools.idea.run.tasks.LaunchTask;
import com.android.tools.idea.run.tasks.LaunchTasksProvider;
import com.android.tools.idea.run.util.LaunchStatus;
import com.android.tools.idea.run.util.LaunchUtils;
import com.android.tools.idea.run.util.ProcessHandlerLaunchStatus;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class LaunchTaskRunner extends Task.Backgroundable {
  @NotNull private final String myConfigName;
  @NotNull private final LaunchInfo myLaunchInfo;
  @NotNull private final AndroidProcessHandler myProcessHandler;
  @NotNull private final Collection<ListenableFuture<IDevice>> myDeviceFutures;
  @NotNull private final LaunchTasksProvider myLaunchTasksProvider;

  @Nullable private String myError;

  public LaunchTaskRunner(@NotNull Project project,
                          @NotNull String configName,
                          @NotNull LaunchInfo launchInfo,
                          @NotNull AndroidProcessHandler processHandler,
                          @NotNull Collection<ListenableFuture<IDevice>> deviceFutures,
                          @NotNull LaunchTasksProvider launchTasksProvider) {
    super(project, "Launching " + configName);

    myConfigName = configName;
    myLaunchInfo = launchInfo;
    myProcessHandler = processHandler;
    myDeviceFutures = deviceFutures;
    myLaunchTasksProvider = launchTasksProvider;
  }

  @Override
  public void run(@NotNull ProgressIndicator indicator) {
    indicator.setText(getTitle());
    indicator.setIndeterminate(false);

    LaunchStatus launchStatus = new ProcessHandlerLaunchStatus(myProcessHandler);
    ConsolePrinter consolePrinter = new ProcessHandlerConsolePrinter(myProcessHandler);

    DebugConnectorTask debugSessionTask = myLaunchTasksProvider.getConnectDebuggerTask(launchStatus);

    if (debugSessionTask != null && myDeviceFutures.size() != 1) {
      launchStatus.terminateLaunch("Cannot launch a debug session on more than 1 device.");
    }

    if (debugSessionTask != null) {
      // we need to copy over console output from the first console to the debug console once it is established
      AndroidProcessText.attach(myProcessHandler);
    }

    for (ListenableFuture<IDevice> deviceFuture : myDeviceFutures) {
      indicator.setText2("Waiting for target device to come online");
      IDevice device = waitForDevice(deviceFuture, indicator, launchStatus);
      if (device == null) {
        return;
      }

      indicator.setText("Launching on " + device.getName());

      List<LaunchTask> launchTasks = myLaunchTasksProvider.getTasks(device, launchStatus);
      int totalDuration = myDeviceFutures.size() * getTotalDuration(launchTasks, debugSessionTask);
      int elapsed = 0;

      for (LaunchTask task : launchTasks) {
        // perform each task
        indicator.setText2(task.getDescription());
        if (!task.perform(device, launchStatus, consolePrinter)) {
          myError = "Error " + task.getDescription();
          launchStatus.terminateLaunch("Error during launch");
          return;
        }

        // update progress
        elapsed += task.getDuration();
        indicator.setFraction((double)elapsed/totalDuration);

        // check for cancellation via progress bar
        if (indicator.isCanceled()) {
          launchStatus.terminateLaunch("User cancelled launch");
          return;
        }

        // check for cancellation via stop button
        if (launchStatus.isLaunchTerminated()) {
          return;
        }
      }

      if (debugSessionTask != null) {
        debugSessionTask
          .perform(myLaunchInfo, device, (ProcessHandlerLaunchStatus)launchStatus, (ProcessHandlerConsolePrinter)consolePrinter);
      }
      else {
        // we only need this if we are not debugging..
        myProcessHandler.addTargetDevice(device);
      }
    }
  }

  @Override
  public void onSuccess() {
    NotificationType type;
    String message;
    if (myError != null) {
      type = NotificationType.ERROR;
      message = myError;
    }
    else {
      type = NotificationType.INFORMATION;
      message = "Launched";
    }

    LaunchUtils.showNotification(myProject, myLaunchInfo.executor, myConfigName, message, type);
  }

  @Nullable
  private static IDevice waitForDevice(@NotNull ListenableFuture<IDevice> deviceFuture,
                                       @NotNull ProgressIndicator indicator,
                                       @NotNull LaunchStatus launchStatus) {
    while (true) {
      try {
        return deviceFuture.get(1, TimeUnit.SECONDS);
      }
      catch (TimeoutException ignored) {
      }
      catch (InterruptedException e) {
        launchStatus.terminateLaunch("Interrupted while waiting for device");
        return null;
      }
      catch (ExecutionException e) {
        launchStatus.terminateLaunch("Error while waiting for device: " + e.getCause().getMessage());
        return null;
      }

      if (indicator.isCanceled()) {
        launchStatus.terminateLaunch("User cancelled launch");
        return null;
      }

      if (launchStatus.isLaunchTerminated()) {
        return null;
      }
    }
  }

  private static int getTotalDuration(@NotNull List<LaunchTask> launchTasks, @Nullable DebugConnectorTask debugSessionTask) {
    int total = 0;

    for (LaunchTask task : launchTasks) {
      total += task.getDuration();
    }

    if (debugSessionTask != null) {
      total += debugSessionTask.getDuration();
    }

    return total;
  }
}
