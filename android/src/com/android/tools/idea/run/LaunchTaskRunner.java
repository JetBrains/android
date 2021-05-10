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

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.Client;
import com.android.ddmlib.IDevice;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.run.tasks.DebugConnectorTask;
import com.android.tools.idea.run.tasks.LaunchResult;
import com.android.tools.idea.run.tasks.LaunchTask;
import com.android.tools.idea.run.tasks.LaunchTasksProvider;
import com.android.tools.idea.run.util.LaunchStatus;
import com.android.tools.idea.run.util.LaunchUtils;
import com.android.tools.idea.run.util.ProcessHandlerLaunchStatus;
import com.android.tools.idea.run.util.SwapInfo;
import com.android.tools.idea.stats.RunStats;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.wireless.android.sdk.stats.LaunchTaskDetail;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.RunContentManager;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindowId;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LaunchTaskRunner extends Task.Backgroundable {
  @NotNull private final String myConfigName;
  @NotNull private final String myApplicationId;
  @Nullable private final String myExecutionTargetName; // Change to NotNull once everything is moved over to DeviceAndSnapshot
  @NotNull private final LaunchInfo myLaunchInfo;
  @NotNull private final ProcessHandler myProcessHandler;
  @NotNull private final DeviceFutures myDeviceFutures;
  @NotNull private final LaunchTasksProvider myLaunchTasksProvider;
  @NotNull private final RunStats myStats;
  @NotNull private final BiConsumer<String, HyperlinkInfo> myConsoleConsumer;
  @NotNull private final List<Runnable> myOnFinished;

  @Nullable private String myError;
  @Nullable private NotificationListener myErrorNotificationListener;

  public LaunchTaskRunner(@NotNull Project project,
                          @NotNull String configName,
                          @NotNull String applicationId,
                          @Nullable String executionTargetName,
                          @NotNull LaunchInfo launchInfo,
                          @NotNull ProcessHandler processHandler,
                          @NotNull DeviceFutures deviceFutures,
                          @NotNull LaunchTasksProvider launchTasksProvider,
                          @NotNull RunStats stats,
                          @NotNull BiConsumer<String, HyperlinkInfo> consoleConsumer) {
    super(project, "Launching " + configName);

    myConfigName = configName;
    myApplicationId = applicationId;
    myExecutionTargetName = executionTargetName;
    myLaunchInfo = launchInfo;
    myProcessHandler = processHandler;
    myDeviceFutures = deviceFutures;
    myLaunchTasksProvider = launchTasksProvider;
    myStats = stats;
    myConsoleConsumer = consoleConsumer;
    myOnFinished = new ArrayList<>();
  }

  @Override
  public void run(@NotNull ProgressIndicator indicator) {
    final boolean destroyProcessOnCancellation = !isSwap();
    indicator.setText(getTitle());
    indicator.setIndeterminate(false);
    myStats.beginLaunchTasks();
    try {
      ProcessHandlerLaunchStatus launchStatus = new ProcessHandlerLaunchStatus(myProcessHandler);
      ProcessHandlerConsolePrinter consolePrinter = new ProcessHandlerConsolePrinter(myProcessHandler);
      List<ListenableFuture<IDevice>> listenableDeviceFutures = myDeviceFutures.get();
      AndroidVersion androidVersion = myDeviceFutures.getDevices().size() == 1
                                      ? myDeviceFutures.getDevices().get(0).getVersion()
                                      : null;
      DebugConnectorTask debugSessionTask = isSwap() ? null : myLaunchTasksProvider.getConnectDebuggerTask(launchStatus, androidVersion);

      if (debugSessionTask != null) {
        if (listenableDeviceFutures.size() != 1) {
          launchStatus.terminateLaunch("Cannot launch a debug session on more than 1 device.", true);
          return;
        }
        // Copy over console output from the original console to the debug console once it is established.
        AndroidProcessText.attach(myProcessHandler);
      }

      printLaunchTaskStartedMessage(consolePrinter);

      indicator.setText("Waiting for all target devices to come online");
      List<IDevice> devices = listenableDeviceFutures.stream()
        .map(deviceFuture -> waitForDevice(deviceFuture, indicator, launchStatus, destroyProcessOnCancellation))
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
      if (devices.size() != listenableDeviceFutures.size()) {
        // Halt execution if any of target devices are unavailable.
        return;
      }

      // Wait for the previous android process with the same application ID to be terminated before we start the new process.
      // This step is necessary only for the standard launch (non-swap, android process handler). Ignore this step for
      // hot-swapping or debug runs.
      if (!isSwap() && myProcessHandler instanceof AndroidProcessHandler) {
        for (IDevice device : devices) {
          ApplicationTerminationWaiter listener = new ApplicationTerminationWaiter(device, myApplicationId);
          try {
            // Ensure all Clients are killed prior to handing off to the AndroidProcessHandler.
            if (!listener.await(10, TimeUnit.SECONDS)) {
              launchStatus.terminateLaunch(String.format("%s is already running.", myApplicationId), true);
              return;
            }
          }
          catch (InterruptedException ignored) {
            launchStatus.terminateLaunch(String.format("%s is already running.", myApplicationId), true);
            return;
          }
          if (listener.getIsDeviceAlive()) {
            AndroidProcessHandler procHandler = (AndroidProcessHandler)myProcessHandler;
            procHandler.addTargetDevice(device);
          }
        }
      }

      // Perform launch tasks for each device.
      for (int deviceIndex = 0; deviceIndex < devices.size(); deviceIndex++) {
        IDevice device = devices.get(deviceIndex);
        List<LaunchTask> launchTasks = null;
        try {
          myLaunchTasksProvider.fillStats(myStats);
          launchTasks = myLaunchTasksProvider.getTasks(device, launchStatus, consolePrinter);
        }
        catch (com.intellij.execution.ExecutionException e) {
          launchStatus.terminateLaunch(e.getMessage(), !isSwap());
          return;
        }
        catch (IllegalStateException e) {
          launchStatus.terminateLaunch(e.getMessage(), !isSwap());
          Logger.getInstance(LaunchTaskRunner.class).error(e);
          return;
        }

        // This totalDuration and elapsed step count is used only for showing a progress bar.
        int totalDuration = getTotalDuration(launchTasks, debugSessionTask);
        int elapsed = 0;
        NotificationGroup notificationGroup = NotificationGroup.toolWindowGroup("LaunchTaskRunner", ToolWindowId.RUN);
        for (LaunchTask task : launchTasks) {
          if (!checkIfLaunchIsAliveAndTerminateIfCancelIsRequested(indicator, launchStatus, destroyProcessOnCancellation)) {
            return;
          }

          LaunchTaskDetail.Builder details = myStats.beginLaunchTask(task);
          indicator.setText(task.getDescription());
          LaunchResult result = task.run(myLaunchInfo.executor, device, launchStatus, consolePrinter);
          myOnFinished.addAll(result.onFinishedCallbacks());
          boolean success = result.getSuccess();
          myStats.endLaunchTask(task, details, success);
          if (!success) {
            myErrorNotificationListener = result.getNotificationListener();
            myError = result.getError();
            launchStatus.terminateLaunch(result.getConsoleError(), !isSwap());

            // Append a footer hyperlink, if one was provided.
            if (result.getConsoleHyperlinkInfo() != null) {
              myConsoleConsumer.accept(result.getConsoleHyperlinkText() + "\n",
                                       result.getConsoleHyperlinkInfo());
            }

            notificationGroup.createNotification("Error", result.getError(), NotificationType.ERROR).setImportant(true).notify(myProject);

            // Show the tool window when we have an error.
            RunContentManager.getInstance(myProject).toFrontRunContent(myLaunchInfo.executor, myProcessHandler);

            myStats.setErrorId(result.getErrorId());
            return;
          }

          // Notify listeners of the deployment.
          myProject.getMessageBus().syncPublisher(AppDeploymentListener.TOPIC).appDeployedToDevice(device, myProject);

          // Update progress.
          elapsed += task.getDuration();
          indicator.setFraction((double)(elapsed / totalDuration + deviceIndex) / devices.size());
        }

        notificationGroup.createNotification("Success", "Operation succeeded", NotificationType.INFORMATION).setImportant(false).notify(myProject);

        // A debug session task should be performed at last.
        if (debugSessionTask != null) {
          debugSessionTask.perform(myLaunchInfo, device, launchStatus, consolePrinter);
        }
      }
    }
    finally {
      myStats.endLaunchTasks();
    }
  }

  private void printLaunchTaskStartedMessage(ConsolePrinter consolePrinter) {
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
  }

  @Override
  public void onSuccess() {
    if (myError == null) {
      myStats.success();
    }
    else {
      myStats.fail();
      LaunchUtils.showNotification(
        myProject, myLaunchInfo.executor, myConfigName, myError, NotificationType.ERROR, myErrorNotificationListener);
    }
  }

  @Override
  public void onFinished() {
    super.onFinished();
    for (Runnable runnable : myOnFinished) {
      ApplicationManager.getApplication().invokeLater(runnable);
    }
  }

  @Nullable
  private IDevice waitForDevice(@NotNull ListenableFuture<IDevice> deviceFuture,
                                @NotNull ProgressIndicator indicator,
                                @NotNull LaunchStatus launchStatus,
                                boolean destroyProcess) {
    myStats.beginWaitForDevice();
    IDevice device = null;
    while (checkIfLaunchIsAliveAndTerminateIfCancelIsRequested(indicator, launchStatus, destroyProcess)) {
      try {
        device = deviceFuture.get(1, TimeUnit.SECONDS);
        break;
      }
      catch (TimeoutException ignored) {
        // Let's check the cancellation request then continue to wait for a device again.
      }
      catch (InterruptedException e) {
        launchStatus.terminateLaunch("Interrupted while waiting for device", destroyProcess);
        break;
      }
      catch (ExecutionException e) {
        launchStatus.terminateLaunch("Error while waiting for device: " + e.getCause().getMessage(), destroyProcess);
        break;
      }
    }
    myStats.endWaitForDevice(device);
    return device;
  }

  /**
   * Checks if the launch is still alive and good to continue. Upon cancellation request, it updates a given {@code launchStatus} to
   * be terminated state. The associated process will be forcefully destroyed if {@code destroyProcess} is true.
   *
   * @param indicator      an progress indicator to check the user cancellation request
   * @param launchStatus   a launch status to be checked and updated upon the cancellation request
   * @param destroyProcess true to destroy the associated process upon cancellation, false to detach the process instead
   * @return true if the launch is still good to go, false otherwise.
   */
  private static boolean checkIfLaunchIsAliveAndTerminateIfCancelIsRequested(
    @NotNull ProgressIndicator indicator, @NotNull LaunchStatus launchStatus, boolean destroyProcess) {
    // Check for cancellation via stop button or unexpected failures in launch tasks.
    if (launchStatus.isLaunchTerminated()) {
      return false;
    }

    // Check for cancellation via progress bar.
    if (indicator.isCanceled()) {
      launchStatus.terminateLaunch("User cancelled launch", destroyProcess);
      return false;
    }

    return true;
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
    return myLaunchInfo.env.getUserData(SwapInfo.SWAP_INFO_KEY) != null;
  }

  @NotNull
  private String getLaunchVerb() {
    SwapInfo swapInfo = myLaunchInfo.env.getUserData(SwapInfo.SWAP_INFO_KEY);
    if (swapInfo != null) {
      if (swapInfo.getType() == SwapInfo.SwapType.APPLY_CHANGES) {
        return "Applying changes to";
      }
      else if (swapInfo.getType() == SwapInfo.SwapType.APPLY_CODE_CHANGES) {
        return "Applying code changes to";
      }
    }
    return "Launching";
  }

  /**
   * A waiter to ensure that all existing Clients matching the application ID are fully terminated before proceeding with handoff to
   * AndroidProcessHandler.
   * <p>
   * When the remote debugger is attached or when the app is run from the device directly, Running/Debugging an unchanged app may be fast
   * enough that the delta install "am force-stop" command's effect does not get reflected in ddmlib before the same IDevice is added to
   * AndroidProcessHandler. This is caused by the fact that a no-change Run does not wait until the stale Client is actually terminated
   * before proceeding to attempt to connect the AndroidProcessHandler to the desired device. In such circumstances, the handler will
   * connect to the stale Client, and almost immediately have the same Client's killed state get reflected by ddmlib, and removed from the
   * process handler.
   */
  private static class ApplicationTerminationWaiter implements AndroidDebugBridge.IDeviceChangeListener {
    @NotNull private final IDevice myIDevice;
    @NotNull private final List<Client> myClientsToWaitFor;
    @NotNull private final CountDownLatch myProcessKilledLatch = new CountDownLatch(1);
    private volatile boolean myIsDeviceAlive = true;

    private ApplicationTerminationWaiter(@NotNull IDevice iDevice, @NotNull String applicationId) {
      myIDevice = iDevice;
      myClientsToWaitFor = Collections.synchronizedList(DeploymentApplicationService.getInstance().findClient(myIDevice, applicationId));
      if (!myIDevice.isOnline() || myClientsToWaitFor.isEmpty()) {
        myProcessKilledLatch.countDown();
      }
      else {
        AndroidDebugBridge.addDeviceChangeListener(this);
        iDevice.forceStop(applicationId);
        myClientsToWaitFor.forEach(Client::kill);
        checkDone();
      }
    }

    public boolean await(long timeout, @NotNull TimeUnit unit) throws InterruptedException {
      return myProcessKilledLatch.await(timeout, unit);
    }

    public boolean getIsDeviceAlive() {
      return myIsDeviceAlive;
    }

    @Override
    public void deviceConnected(@NotNull IDevice device) {}

    @Override
    public void deviceDisconnected(@NotNull IDevice device) {
      myIsDeviceAlive = false;
      myProcessKilledLatch.countDown();
      AndroidDebugBridge.removeDeviceChangeListener(this);
    }

    @Override
    public void deviceChanged(@NotNull IDevice changedDevice, int changeMask) {
      if (changedDevice != myIDevice || (changeMask & IDevice.CHANGE_CLIENT_LIST) == 0) {
        checkDone();
        return;
      }

      myClientsToWaitFor.retainAll(Arrays.asList(changedDevice.getClients()));
      checkDone();
    }

    private void checkDone() {
      if (myClientsToWaitFor.isEmpty()) {
        myProcessKilledLatch.countDown();
        AndroidDebugBridge.removeDeviceChangeListener(this);
      }
    }
  }
}
