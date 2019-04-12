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
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.run.tasks.DebugConnectorTask;
import com.android.tools.idea.run.tasks.LaunchResult;
import com.android.tools.idea.run.tasks.LaunchTask;
import com.android.tools.idea.run.tasks.LaunchTasksProvider;
import com.android.tools.idea.run.ui.ApplyChangesAction;
import com.android.tools.idea.run.ui.CodeSwapAction;
import com.android.tools.idea.run.util.LaunchStatus;
import com.android.tools.idea.run.util.LaunchUtils;
import com.android.tools.idea.run.util.ProcessHandlerLaunchStatus;
import com.android.tools.idea.stats.RunStats;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.wireless.android.sdk.stats.LaunchTaskDetail;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.RunContentManager;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import java.util.function.BiConsumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class LaunchTaskRunner extends Task.Backgroundable {
  @NotNull private final String myConfigName;
  @Nullable private final String myExecutionTargetName; // Change to NotNull once everything is moved over to DeviceAndSnapshot
  @NotNull private final LaunchInfo myLaunchInfo;
  @NotNull private final ProcessHandler myProcessHandler;
  @NotNull private final DeviceFutures myDeviceFutures;
  @NotNull private final LaunchTasksProvider myLaunchTasksProvider;
  @NotNull private final RunStats myStats;
  @NotNull private final BiConsumer<String, HyperlinkInfo> myConsoleConsumer;

  @Nullable private String myError;
  @Nullable private NotificationListener myErrorNotificationListener;

  public LaunchTaskRunner(@NotNull Project project,
                          @NotNull String configName,
                          @Nullable String executionTargetName,
                          @NotNull LaunchInfo launchInfo,
                          @NotNull ProcessHandler processHandler,
                          @NotNull DeviceFutures deviceFutures,
                          @NotNull LaunchTasksProvider launchTasksProvider,
                          @NotNull RunStats stats,
                          @NotNull BiConsumer<String, HyperlinkInfo> consoleConsumer) {
    super(project, "Launching " + configName);

    myConfigName = configName;
    myExecutionTargetName = executionTargetName;
    myLaunchInfo = launchInfo;
    myProcessHandler = processHandler;
    myDeviceFutures = deviceFutures;
    myLaunchTasksProvider = launchTasksProvider;
    myStats = stats;
    myConsoleConsumer = consoleConsumer;
  }

  @Override
  public void run(@NotNull ProgressIndicator indicator) {
    indicator.setText(getTitle());
    indicator.setIndeterminate(false);
    myStats.beginLaunchTasks();

    LaunchStatus launchStatus = new ProcessHandlerLaunchStatus(myProcessHandler);
    ConsolePrinter consolePrinter = new ProcessHandlerConsolePrinter(myProcessHandler);
    List<ListenableFuture<IDevice>> listenableDeviceFutures = myDeviceFutures.get();
    AndroidVersion androidVersion = myDeviceFutures.getDevices().size() == 1
                                    ? myDeviceFutures.getDevices().get(0).getVersion()
                                    : null;
    DebugConnectorTask debugSessionTask = isSwap() ? null : myLaunchTasksProvider.getConnectDebuggerTask(launchStatus, androidVersion);

    if (debugSessionTask != null && listenableDeviceFutures.size() != 1) {
      launchStatus.terminateLaunch("Cannot launch a debug session on more than 1 device.", true);
    }

    if (debugSessionTask != null) {
      // we need to copy over console output from the first console to the debug console once it is established
      AndroidProcessText.attach(myProcessHandler);
    }

    StringBuilder launchString = new StringBuilder("\n");
    DateFormat dateFormat = new SimpleDateFormat("MM/dd HH:mm:ss");
    launchString.append(dateFormat.format(new Date())).append(": ");
    launchString.append(getLaunchVerb()).append(" ");
    launchString.append("'").append(myConfigName).append("'");
    if (!StringUtil.isEmpty(myExecutionTargetName)) {
      launchString.append(" on ");
      launchString.append(myExecutionTargetName);
    }
    launchString.append(".");
    consolePrinter.stdout(launchString.toString());

    for (ListenableFuture<IDevice> deviceFuture : listenableDeviceFutures) {
      indicator.setText("Waiting for target device to come online");
      myStats.beginWaitForDevice();
      IDevice device = waitForDevice(deviceFuture, indicator, launchStatus);
      myStats.endWaitForDevice(device);
      if (device == null) {
        break;
      }

      List<LaunchTask> launchTasks = null;
      try {
        launchTasks = myLaunchTasksProvider.getTasks(device, launchStatus, consolePrinter);
      }
      catch (com.intellij.execution.ExecutionException e) {
        launchStatus.terminateLaunch(e.getMessage(), !isSwap());
        break;
      }
      catch (IllegalStateException e) {
        launchStatus.terminateLaunch(e.getMessage(), !isSwap());
        Logger.getInstance(LaunchTaskRunner.class).error(e);
        break;
      }

      int totalDuration = listenableDeviceFutures.size() * getTotalDuration(launchTasks, debugSessionTask);
      int elapsed = 0;

      boolean success = true;
      for (LaunchTask task : launchTasks) {
        // perform each task
        LaunchTaskDetail.Builder details = myStats.beginLaunchTask(task);
        indicator.setText(task.getDescription());
        LaunchResult result = task.run(myLaunchInfo.executor, device, launchStatus, consolePrinter);
        success = result.getSuccess();
        myStats.endLaunchTask(task, details, success);
        if (!success) {
          myErrorNotificationListener = result.getNotificationListener();
          myError = result.getError();
          launchStatus.terminateLaunch(result.getConsoleError(), !isSwap());

          // append a footer hyperlink, if one was provided
          if (result.getConsoleHyperlinkInfo() != null) {
            myConsoleConsumer.accept(result.getConsoleHyperlinkText() + "\n",
                                     result.getConsoleHyperlinkInfo());
          }

          // show the tool window when we have an error
          RunContentManager.getInstance(myProject).toFrontRunContent(myLaunchInfo.executor, myProcessHandler);

          myStats.setErrorId(result.getErrorId());
          break;
        }

        // update progress
        elapsed += task.getDuration();
        indicator.setFraction((double)elapsed / totalDuration);

        // check for cancellation via progress bar
        if (indicator.isCanceled()) {
          launchStatus.terminateLaunch("User cancelled launch", !isSwap());
          success = false;
          break;
        }

        // check for cancellation via stop button
        if (launchStatus.isLaunchTerminated()) {
          success = false;
          break;
        }
      }
      if (!success) {
        break;
      }

      if (debugSessionTask != null) {
        debugSessionTask
          .perform(myLaunchInfo, device, (ProcessHandlerLaunchStatus)launchStatus, (ProcessHandlerConsolePrinter)consolePrinter);
      }
      else { // we only need to inform the process handler if certain scenarios
        if (myProcessHandler instanceof AndroidProcessHandler) { // we aren't debugging (in which case its a DebugProcessHandler)
          AndroidProcessHandler procHandler = (AndroidProcessHandler) myProcessHandler;
          procHandler.addTargetDevice(device);
        }
      }
    }
    myStats.endLaunchTasks();
  }

  @Override
  public void onSuccess() {
    if (myError == null) {
      myStats.success();
    } else {
      myStats.fail();
      LaunchUtils.showNotification(
        myProject, myLaunchInfo.executor, myConfigName, myError, NotificationType.ERROR, myErrorNotificationListener);
    }
  }

  @Nullable
  private IDevice waitForDevice(@NotNull ListenableFuture<IDevice> deviceFuture,
                                       @NotNull ProgressIndicator indicator,
                                       @NotNull LaunchStatus launchStatus) {
    while (true) {
      try {
        return deviceFuture.get(1, TimeUnit.SECONDS);
      }
      catch (TimeoutException ignored) {
      }
      catch (InterruptedException e) {
        launchStatus.terminateLaunch("Interrupted while waiting for device", true);
        return null;
      }
      catch (ExecutionException e) {
        launchStatus.terminateLaunch("Error while waiting for device: " + e.getCause().getMessage(), true);
        return null;
      }

      if (indicator.isCanceled()) {
        launchStatus.terminateLaunch("User cancelled launch", !isSwap());
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

  private boolean isSwap() {
    return Boolean.TRUE.equals(myLaunchInfo.env.getCopyableUserData(ApplyChangesAction.KEY)) ||
           Boolean.TRUE.equals(myLaunchInfo.env.getCopyableUserData(CodeSwapAction.KEY));
  }

  @NotNull
  private String getLaunchVerb() {
    if (Boolean.TRUE.equals(myLaunchInfo.env.getCopyableUserData(ApplyChangesAction.KEY))) {
      return "Applying changes to";
    }
    else if (Boolean.TRUE.equals(myLaunchInfo.env.getCopyableUserData(CodeSwapAction.KEY))) {
      return "Applying code changes to";
    }
    else {
      return "Launching";
    }
  }
}
