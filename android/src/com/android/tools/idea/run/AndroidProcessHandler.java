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

import com.android.ddmlib.*;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.run.deployable.SwappableProcessHandler;
import com.android.tools.idea.run.deployment.AndroidExecutionTarget;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.execution.DefaultExecutionTarget;
import com.intellij.execution.ExecutionTarget;
import com.intellij.execution.ExecutionTargetManager;
import com.intellij.execution.Executor;
import com.intellij.execution.KillableProcess;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.OutputStream;
import java.util.*;

/**
 * AndroidProcessHandler is a {@link ProcessHandler} that corresponds to a single Android app potentially running on multiple connected
 * devices after it's launched from Studio.
 *
 * <p>It provides an option to connect and monitor the processes running on the devices. If the processes are being monitored, then
 * destroyProcess kills the processes (typically by a stop button in the UI). If all the processes die, then this handler terminates as
 * well.
 */
public class AndroidProcessHandler extends ProcessHandler
  implements KillableProcess, SwappableProcessHandler, AndroidDebugBridge.IDeviceChangeListener {
  private static final Logger LOG = Logger.getInstance(AndroidProcessHandler.class);

  @NotNull private final DeploymentApplicationService myDeploymentApplicationService;
  // identifier for the running application, same as packageId unless android:process attribute is set
  @NotNull private final String myApplicationId;

  @NotNull private final Map<IDevice, ProcessInfo> myDeviceProcessMap;
  @NotNull private final AndroidLogcatOutputCapture myAndroidLogcatOutputCapture;
  @NotNull private final Project myProject;

  private class ProcessInfo implements Disposable {
    // If the client is not present on the monitored devices after this many retries (approx seconds), then it is assumed to have died.
    // We are keeping it this many times/long because sometimes it takes a while for the Clients to come online.
    private static final long MAX_RETRY_COUNT = 10;

    @NotNull
    private final IDevice myDevice;

    // Since ADB operates in its own thread and Client validity states can change at any given point in time relative to the
    // ProcessInfo's internal thread, we need to synchronize the Client add/remove ordering.

    // We use a map to avoid the issue of Clients getting recreated due to bad handshakes (recreated Clients will have the same PID).
    @NotNull
    private final Map<Integer, Client> myPidClientMap;

    // Future for the client finding process, in case we want to cancel early.
    @NotNull
    private final Future<Void> myClientFuture;

    // A latch for the Client searching busy loop to wait on.
    // It can be #countDown to force an iteration of search.
    // It is replaced every time the busy loop encounters it with count of 0.
    @NotNull
    private volatile CountDownLatch myAwaitLatch;

    private ProcessInfo(@NotNull IDevice device) {
      myDevice = device;
      myAwaitLatch = new CountDownLatch(1);

      // Check if Clients are running already.
      myPidClientMap = new ConcurrentHashMap<>(checkForClient());
      boolean foundClientsInitially = !myPidClientMap.isEmpty();
      if (foundClientsInitially) {
        notifyClientsFound(myPidClientMap.keySet());
      }
      else {
        print("Waiting for process to come online...\n");
      }

      // We will continue to wait for more PIDs to come online (since apps can have multiple processes).
      // We'll set up a busy loop to look for Clients (we need to periodically poll because the lookup itself is asynchronous).
      myClientFuture = ApplicationManager.getApplication().executeOnPooledThread(() -> {
        int retryCount = 0;
        // This flag is to check the case where we've found Clients before the search period ended,
        // but then all the Clients disappeared when the search period ends.
        boolean foundClients = foundClientsInitially;
        while (retryCount <= MAX_RETRY_COUNT) {
          try {
            // Refresh the list every second.
            myAwaitLatch.await(1, TimeUnit.SECONDS);
            if (myAwaitLatch.getCount() == 0) {
              myAwaitLatch = new CountDownLatch(1);
            }
            else {
              // Only increment retry count when the loop isn't sped up by outside activity.
              retryCount++;
            }

            Map<Integer, Client> resolvedClients = checkForClient();
            // Clients may be stale already, so we do a best-effort stale Client removal. If we miss it, we'll just end up printing
            // connected status message twice: once for the stale Client, and once for the recreated Client that will come online soon.
            resolvedClients.values().removeIf(client -> !client.isValid());

            Map<Integer, Client> validClients = new HashMap<>(myPidClientMap);
            validClients.values().removeIf(client -> !client.isValid());

            // Get the set of Clients that're not in mPidClientMap or are stale -- the stale ones we want to replace.
            resolvedClients.keySet().removeAll(validClients.keySet());
            if (!resolvedClients.isEmpty()) {
              notifyClientsFound(resolvedClients.keySet());
              myPidClientMap.putAll(resolvedClients);
              myPidClientMap.values().removeIf(client -> !client.isValid());
              foundClients = true;
            }
          }
          catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
            return null;
          }
        }

        if (myPidClientMap.isEmpty() && !foundClients) {
          // Perhaps the app crashed or the user killed the app through the phone.
          print("Timed out waiting for process to appear on %s.\n", myDevice.getName());
        }
        return null;
      });
      Disposer.register(myProject, this);
    }

    @NotNull
    private Map<Integer, Client> checkForClient() {
      List<Client> clients = myDeploymentApplicationService.findClient(myDevice, myApplicationId);
      return clients.isEmpty()
             ? Collections.emptyMap()
             : clients.stream().collect(Collectors.toMap(c -> c.getClientData().getPid(), c -> c));
    }

    public void onClientListChanged() {
      // When CHANGE_CLIENT_LIST is signaled, it is a good time to hasten the {@link Client} search in the busy loop.
      myAwaitLatch.countDown();

      // Also, some Clients may become invalid (either terminated or recreated).
      Map<Integer, Client> validClients =
        Arrays.stream(myDevice.getClients()).collect(Collectors.toMap(client -> client.getClientData().getPid(), client -> client));

      // Sometimes, the application crashes before all retries are exhausted.
      // So if we already knew of the app, and it is not there anymore, then assume it got killed.
      // Note that since we work with PIDs and not Clients directly, stale Clients due to recreation don't get removed here.
      boolean removed = myPidClientMap.keySet().removeIf(pid -> {
        if (!validClients.containsKey(pid)) {
          print("Process %d terminated.\n", pid);
          return true;
        }
        return false;
      });

      if (removed && myPidClientMap.isEmpty()) {
        // Since we recognized the Client before it got terminated, it means it's debuggable.
        AndroidProcessHandler.this.disassociate(myDevice);
        return;
      }

      // Sometimes we get a bad handshake for the debugger port long after the correct process has been resolved.
      // In those cases, we would find a Client with the same PID as the stale Client. If so, restart the ProcessInfo.
      // Leave existing logcat capture intact, since PIDs are the same.
      // Note that #replaceAll effectively atomically replaces the Clients whose PIDs already exists in the map.
      myPidClientMap.replaceAll((pid, client) -> validClients.get(pid));
    }

    /**
     * Starts the logcat capture and prints a status message to stdout.
     */
    private void notifyClientsFound(@NotNull Set<Integer> pids) {
      for (int pid : pids) {
        myAndroidLogcatOutputCapture.startCapture(myDevice, pid, AndroidProcessHandler.this::notifyTextAvailable);
        print("Connected to process %d on device '%s'.\n", pid, myDevice.getName());
      }
    }

    /**
     * Use IntelliJ's disposal to clean up outstanding threads.
     */
    @Override
    public void dispose() {
      myClientFuture.cancel(true);
      myAwaitLatch.countDown();
      try {
        myClientFuture.get();
        myPidClientMap.clear();
      }
      catch (Exception ignored) {
      }
    }
  }

  private AndroidProcessHandler(@NotNull Project project,
                                @NotNull DeploymentApplicationService deploymentApplicationService,
                                @NotNull String applicationId) {
    myProject = project;
    myDeploymentApplicationService = deploymentApplicationService;

    myApplicationId = applicationId;
    myDeviceProcessMap = new ConcurrentHashMap<>();
    myAndroidLogcatOutputCapture = new AndroidLogcatOutputCapture(applicationId);

    putCopyableUserData(SwappableProcessHandler.EXTENSION_KEY, this);
  }

  /**
   * Requests that this object should start listening for AndroidDebugBridge
   * client and device changes. This method should be called before
   * {@link #addTargetDevice} to prevent a race condition where a device or
   * client update occurs between {@code addTargetDevice}'s end and this
   * method's start.
   */
  private void addListenersToAdb() {
    AndroidDebugBridge.addDeviceChangeListener(this);
  }

  /**
   * Adds a device to listen to from ADB. This should ideally be called after
   * {@link #addListenersToAdb} to avoid a race condition where a device or client
   * update occurs between this method's return and the entry to
   * {@link #addListenersToAdb}.
   */
  public void addTargetDevice(@NotNull final IDevice device) {
    myDeviceProcessMap.computeIfAbsent(device, unused -> new ProcessInfo(device));

    AndroidVersion apiLevel = getUserData(AndroidSessionInfo.ANDROID_DEVICE_API_LEVEL);
    AndroidVersion deviceVersion = device.getVersion();
    if (apiLevel == null || apiLevel.compareTo(deviceVersion) > 0) {
      putUserData(AndroidSessionInfo.ANDROID_DEVICE_API_LEVEL, deviceVersion);
    }

    LOG.info("Adding device " + device.getName() + " to monitor for launched app: " + myApplicationId);
  }

  @Override
  public boolean detachIsDefault() {
    return false;
  }

  @Override
  public boolean isSilentlyDestroyOnClose() {
    return true;
  }

  @Override
  public OutputStream getProcessInput() {
    return null;
  }

  @Override
  protected void detachProcessImpl() {
    notifyProcessDetached();
    cleanup();
  }

  @Override
  protected void destroyProcessImpl() {
    notifyProcessTerminated(0);
    killApps(myDeviceProcessMap.keySet());
  }

  private void killApps(@NotNull Collection<IDevice> devices) {
    // Kill processes in another thread, as we run the "am force-stop" shell command and it might take a while. Show a progress indicator in
    // the meanwhile. This way we prevent the UI from freezing if the kill command runs slowly. Make sure to clean up afterwards.
    // TODO(b/122820269): Figure out a nice way to prevent the pooled thread from leaking and testing it's executed properly.
    ProgressManager.getInstance().run(new Task.Backgroundable(myProject, "Stopping Application...") {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        AndroidDebugBridge bridge = AndroidDebugBridge.getBridge();
        if (bridge == null) {
          return;
        }

        for (IDevice device : devices) {
          killApp(device);
        }
      }

      @Override
      public void onSuccess() {
        cleanup();
      }
    });
  }

  private void killApp(@NotNull IDevice device) {
    if (device.getState() != IDevice.DeviceState.ONLINE) {
      return;
    }

    // Workaround https://code.google.com/p/android/issues/detail?id=199342
    // Sometimes, just calling client.kill() could end up with the app dying and then coming back up
    // Very likely, this is because of how cold swap restarts the process (maybe it is using some persistent pending intents?)
    // However, calling am force-stop seems to solve that issue, so we do that first..
    try {
      device.executeShellCommand("am force-stop " + myApplicationId, new NullOutputReceiver());
    }
    catch (Exception ignored) {
    }

    // IDevice#executeShellCommand might've taken long enough that ADB already triggered CHANGE_CLIENT_LIST event due to force-stop.
    ProcessInfo processInfo = myDeviceProcessMap.get(device);
    if (processInfo != null) {
      processInfo.myPidClientMap.values().removeIf(client -> {
        if (client.isValid()) {
          client.kill();
        }
        return true;
      });
    }

    for (Client client : device.getClients()) {
      if (myApplicationId.equals(client.getClientData().getPackageName())) {
        client.kill();
      }
    }
  }

  /**
   * Stops monitoring and removes the device from myDeviceProcessMap. Stop will not stop the process on the given device afterwards.
   */
  private void disassociate(@NotNull IDevice device) {
    myAndroidLogcatOutputCapture.stopCapture(device);
    ProcessInfo info = myDeviceProcessMap.get(device);
    if (info != null) {
      Disposer.dispose(info);
    }
    myDeviceProcessMap.remove(device);
  }

  private void cleanup() {
    AndroidDebugBridge.removeDeviceChangeListener(this);
    myAndroidLogcatOutputCapture.stopAll();
    boolean removedAny = myDeviceProcessMap.values().removeIf(processInfo -> {
      Disposer.dispose(processInfo);
      return true;
    });
    if (removedAny) { // In case cleanup is called more than once.
      print("Terminated all processes.");
    }
  }

  @Nullable
  public Client getClient(@NotNull IDevice device) {
    // TODO: should we be able to pick the Client to debug?
    ProcessInfo info = myDeviceProcessMap.get(device);
    if (info == null) {
      return null;
    }
    return info.myPidClientMap.values().stream().findAny().orElse(null);
  }

  private void print(@NotNull String format, @NotNull Object... args) {
    notifyTextAvailable(String.format(format, args), ProcessOutputTypes.STDOUT);
  }

  /**
   * We provide a custom implementation to tie the device combo box selector to the global Stop button.
   * Note the global Stop button prefers the result of this method over content descriptor internal state,
   * but the tool window Stop button prefers the content descriptor internal state over this method.
   */
  @Override
  public boolean canKillProcess() {
    if (!StudioFlags.SELECT_DEVICE_SNAPSHOT_COMBO_BOX_VISIBLE.get()) {
      return !isProcessTerminated() && !isProcessTerminating();
    }

    AndroidDebugBridge bridge = AndroidDebugBridge.getBridge();
    if (bridge == null) {
      return false;
    }

    ExecutionTarget activeTarget = ExecutionTargetManager.getInstance(myProject).getActiveTarget();
    if (activeTarget == DefaultExecutionTarget.INSTANCE || !(activeTarget instanceof AndroidExecutionTarget)) {
      return false;
    }

    IDevice targetIDevice = ((AndroidExecutionTarget)activeTarget).getIDevice();
    return targetIDevice != null && myDeviceProcessMap.containsKey(targetIDevice);
  }

  @Override
  public void killProcess() {
    AndroidDebugBridge bridge = AndroidDebugBridge.getBridge();
    if (bridge == null) {
      return;
    }

    destroyProcess(); // We always kill everything in this descriptor, since we aren't going to muck with IJ code.
  }

  @Nullable
  @Override
  public Executor getExecutor() {
    AndroidSessionInfo sessionInfo = getUserData(AndroidSessionInfo.KEY);
    if (sessionInfo == null) {
      return null;
    }

    return sessionInfo.getExecutor();
  }

  @Override
  public boolean isExecutedWith(@NotNull RunConfiguration runConfiguration, @NotNull ExecutionTarget executionTarget) {
    AndroidSessionInfo sessionInfo = getUserData(AndroidSessionInfo.KEY);
    if (sessionInfo == null) {
      return false;
    }

    return sessionInfo.getExecutionTarget().getId().equals(executionTarget.getId()) &&
           sessionInfo.getRunConfigurationId() == runConfiguration.getUniqueID();
  }

  @Override
  public void deviceConnected(@NotNull IDevice device) {
  }

  @Override
  public void deviceDisconnected(@NotNull IDevice device) {
    if (myDeviceProcessMap.containsKey(device)) {
      print("Device %s disconnected, monitoring stopped.\n", device.getName());
      disassociate(device);

      // We only remove the device from myDeviceProcessMap when the device disconnects.
      // This allows cases where the app is not debuggable, and allowing the StopAction enabled.
      myDeviceProcessMap.remove(device);
    }
  }

  @Override
  public void deviceChanged(@NotNull IDevice device, int changeMask) {
    if ((changeMask & IDevice.CHANGE_CLIENT_LIST) != IDevice.CHANGE_CLIENT_LIST) {
      return;
    }

    ProcessInfo processInfo = myDeviceProcessMap.get(device);
    if (processInfo == null) {
      return;
    }

    processInfo.onClientListChanged();
  }

  public static class Builder {
    @NotNull private final Project myProject;
    private String applicationId;

    /**
     * By default, we want to add listeners to ADB
     */
    private boolean shouldAddListeners = true;
    private DeploymentApplicationService myDeploymentApplicationService;

    public Builder(@NotNull Project project) {
      myProject = project;
      myDeploymentApplicationService = DeploymentApplicationService.getInstance();
    }

    @NotNull
    public Builder setApplicationId(@NotNull String appId) {
      applicationId = appId;
      return this;
    }

    @NotNull
    public Builder monitorRemoteProcesses(boolean shouldMonitorRemoteProcesses) {
      shouldAddListeners = shouldMonitorRemoteProcesses;
      return this;
    }

    @NotNull
    @VisibleForTesting
    public Builder setDeploymentApplicationService(@NotNull DeploymentApplicationService deploymentApplicationService) {
      myDeploymentApplicationService = deploymentApplicationService;
      return this;
    }

    /**
     * @throws IllegalStateException if setApplicationId was not called
     */
    @NotNull
    public AndroidProcessHandler build() {
      if (applicationId == null) {
        throw new IllegalStateException("applicationId not set");
      }

      AndroidProcessHandler handler = new AndroidProcessHandler(myProject, myDeploymentApplicationService, applicationId);
      if (shouldAddListeners) {
        handler.addListenersToAdb();
      }
      return handler;
    }
  }
}
