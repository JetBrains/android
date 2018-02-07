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
import com.android.ddmlib.NullOutputReceiver;
import com.android.ddmlib.logcat.LogCatMessage;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.logcat.AndroidLogcatFormatter;
import com.android.tools.idea.logcat.AndroidLogcatService;
import com.android.tools.idea.logcat.output.LogcatOutputConfigurableProvider;
import com.android.tools.idea.logcat.output.LogcatOutputSettings;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.concurrent.GuardedBy;
import java.io.OutputStream;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

/**
 * AndroidProcessHandler is a {@link ProcessHandler} that corresponds to a single Android app potentially running on multiple connected
 * devices after it's launched from Studio.
 *
 * <p>It provides an option to connect and monitor the processes running on the devices. If the processes are being monitored, then
 * destroyProcess kills the processes (typically by a stop button in the UI). If all the processes die, then this handler terminates as
 * well.
 */
public class AndroidProcessHandler extends ProcessHandler implements AndroidDebugBridge.IDeviceChangeListener,
                                                                     AndroidDebugBridge.IClientChangeListener {
  private static final Logger LOG = Logger.getInstance(AndroidProcessHandler.class);

  // If the client is not present on the monitored devices after this time, then it is assumed to have died.
  // We are keeping it so long because sometimes (for cold-swap) it seems to take a while..
  private static final long TIMEOUT_MS = 10000;

  // identifier for the running application, same as packageId unless android:process attribute is set
  @NotNull private final String myApplicationId;
  private final boolean myMonitoringRemoteProcess;

  @NotNull private final Set<String> myDevices;
  @NotNull private final Set<Client> myClients;
  @NotNull private final LogcatOutputCapture myLogcatOutputCapture;

  private long myDeviceAdded;
  private boolean myNoKill;

  public AndroidProcessHandler(@NotNull String applicationId) {
    this(applicationId, true);
  }

  public AndroidProcessHandler(@NotNull String applicationId, boolean monitorRemoteProcess) {
    myApplicationId = applicationId;
    myDevices = Sets.newConcurrentHashSet();
    myClients = Sets.newHashSet();
    myLogcatOutputCapture = new LogcatOutputCapture(applicationId);

    myMonitoringRemoteProcess = monitorRemoteProcess;
    if (myMonitoringRemoteProcess) {
      AndroidDebugBridge.addClientChangeListener(this);
      AndroidDebugBridge.addDeviceChangeListener(this);
    }
  }

  public void addTargetDevice(@NotNull final IDevice device) {
    myDevices.add(device.getSerialNumber());

    setMinDeviceApiLevel(device.getVersion());

    Client client = device.getClient(myApplicationId);
    if (client != null) {
      addClient(client);
    }
    else {
      notifyTextAvailable("Client not ready yet..", ProcessOutputTypes.STDOUT);
    }

    LOG.info("Adding device " + device.getName() + " to monitor for launched app: " + myApplicationId);
    myDeviceAdded = System.currentTimeMillis();
  }

  private void addClient(@NotNull final Client client) {
    if (!myClients.add(client)) {
      return;
    }
    IDevice device = client.getDevice();
    notifyTextAvailable("Connected to process " + client.getClientData().getPid() + " on device " + device.getName() + "\n",
                        ProcessOutputTypes.STDOUT);

    myLogcatOutputCapture.startCapture(device, client, this::notifyTextAvailable);
  }

  private void setMinDeviceApiLevel(@NotNull AndroidVersion deviceVersion) {
    AndroidVersion apiLevel = getUserData(AndroidSessionInfo.ANDROID_DEVICE_API_LEVEL);
    if (apiLevel == null || apiLevel.compareTo(deviceVersion) > 0) {
      putUserData(AndroidSessionInfo.ANDROID_DEVICE_API_LEVEL, deviceVersion);
    }
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
    killProcesses();
    cleanup();
  }

  private void killProcesses() {
    if (myNoKill) {
      return;
    }

    AndroidDebugBridge bridge = AndroidDebugBridge.getBridge();
    if (bridge == null) {
      return;
    }

    for (IDevice device : bridge.getDevices()) {
      if (myDevices.contains(device.getSerialNumber())) {
        // Workaround https://code.google.com/p/android/issues/detail?id=199342
        // Sometimes, just calling client.kill() could end up with the app dying and then coming back up
        // Very likely, this is because of how cold swap restarts the process (maybe it is using some persistent pending intents?)
        // However, calling am force-stop seems to solve that issue, so we do that first..
        try {
          device.executeShellCommand("am force-stop " + myApplicationId, new NullOutputReceiver());
        }
        catch (Exception ignored) {
        }

        Client client = device.getClient(myApplicationId);
        if (client != null) {
          client.kill();
        }
      }
    }
  }

  public void setNoKill() {
    myNoKill = true;
  }

  private void cleanup() {
    myDevices.clear();
    myClients.clear();
    myLogcatOutputCapture.stopAll();

    if (myMonitoringRemoteProcess) {
      AndroidDebugBridge.removeClientChangeListener(this);
      AndroidDebugBridge.removeDeviceChangeListener(this);
    }
  }

  @Override
  public void deviceConnected(@NotNull IDevice device) {
  }

  @Override
  public void deviceDisconnected(@NotNull IDevice device) {
    if (!myDevices.contains(device.getSerialNumber())) {
      return;
    }

    print("Device " + device.getName() + "disconnected, monitoring stopped.");
    stopMonitoring(device);
  }

  private void stopMonitoring(@NotNull IDevice device) {
    myLogcatOutputCapture.stopCapture(device);

    myDevices.remove(device.getSerialNumber());

    if (myDevices.isEmpty()) {
      detachProcess();
    }
  }

  @Override
  public void deviceChanged(@NotNull IDevice device, int changeMask) {
    if ((changeMask & IDevice.CHANGE_CLIENT_LIST) != IDevice.CHANGE_CLIENT_LIST) {
      return;
    }

    if (!myDevices.contains(device.getSerialNumber())) {
      return;
    }

    Client client = device.getClient(myApplicationId);
    if (client != null) {
      addClient(client);
      return;
    }

    // sometimes, the application crashes before TIMEOUT_MS. So if we already knew of the app, and it is not there anymore, then assume
    // it got killed
    if (!myClients.isEmpty()) {
      for (Client c : myClients) {
        if (device.equals(c.getDevice())) {
          stopMonitoring(device);
          print("Application terminated.");
          return;
        }
      }
    }

    if ((System.currentTimeMillis() - myDeviceAdded) > TIMEOUT_MS) {
      print("Timed out waiting for process to appear on " + device.getName());
      stopMonitoring(device);
    }
    else {
      print("Waiting for process to come online");
    }
  }

  @Override
  public void clientChanged(@NotNull Client client, int changeMask) {
    if ((changeMask & Client.CHANGE_NAME) != Client.CHANGE_NAME) {
      return;
    }

    if (!myDevices.contains(client.getDevice().getSerialNumber())) {
      return;
    }

    if (isMatchingClient(client)) {
      addClient(client);
    }

    if (isMatchingClient(client) && !client.isValid()) {
      print("Process " + client.getClientData().getPid() + " is not valid anymore!");
      stopMonitoring(client.getDevice());
    }
  }

  /**
   * Matches the client against the applicationId given in the constructor. Normally the client's description matches the applicationId.
   * However if android:process attribute is applied to the default activity then:
   *
   * if it's a local process (starts with :) the description is: $packageId:$process => we match against client's packageName
   * if it's a global process, the description is just: $process => TODO(b/71645350)
   */
  private boolean isMatchingClient(@NotNull Client client) {
    return StringUtil.equals(myApplicationId, client.getClientData().getClientDescription()) ||
           StringUtil.equals(myApplicationId, client.getClientData().getPackageName());
  }

  @NotNull
  public List<IDevice> getDevices() {
    Set<IDevice> devices = Sets.newHashSet();
    for (Client client : myClients) {
      devices.add(client.getDevice());
    }

    return Lists.newArrayList(devices);
  }

  @Nullable
  public Client getClient(@NotNull IDevice device) {
    String serial = device.getSerialNumber();

    for (Client client : myClients) {
      if (StringUtil.equals(client.getDevice().getSerialNumber(), serial)) {
        return client;
      }
    }

    return null;
  }

  @NotNull
  public Set<Client> getClients() {
    return myClients;
  }

  private void print(@NotNull String s) {
    notifyTextAvailable(s + "\n", ProcessOutputTypes.STDOUT);
  }

  public void reset() {
    myDevices.clear();
    myClients.clear();
    myLogcatOutputCapture.stopAll();
  }

  /**
   * Capture logcat messages of all known client processes and dispatch them so that
   * they are shown in the Run Console window.
   */
  static class LogcatOutputCapture {
    @NotNull private final String myApplicationId;
    /**
     * Keeps track of the registered listener associated to each device running the application.
     *
     * <p>Note: We need to serialize access to this field because calls to {@link #cleanup} and
     * {@link #stopMonitoring(IDevice)} come from different threads (EDT and Monitor Thread respectively).
     */
    @GuardedBy("myLock")
    @NotNull private final Map<IDevice, AndroidLogcatService.LogcatListener> myLogListeners = new HashMap<>();
    @NotNull private final Object myLock = new Object();

    public LogcatOutputCapture(@NotNull String applicationId) {
      myApplicationId = applicationId;
    }

    public void startCapture(@NotNull final IDevice device, @NotNull final Client client, @NotNull BiConsumer<String, Key> consumer) {
      if (!StudioFlags.RUNDEBUG_LOGCAT_CONSOLE_OUTPUT_ENABLED.get()) {
        return;
      }
      if (!LogcatOutputSettings.getInstance().isRunOutputEnabled()) {
        return;
      }

      LOG.info(String.format("startCapture(\"%s\")", device.getName()));
      AndroidLogcatService.LogcatListener logListener = new ApplicationLogListener(myApplicationId, client.getClientData().getPid()) {
        private final String SIMPLE_FORMAT = AndroidLogcatFormatter.createCustomFormat(false, false, false, true);
        private final AtomicBoolean myIsFirstMessage = new AtomicBoolean(true);

        @Override
        protected String formatLogLine(@NotNull LogCatMessage line) {
          String message = AndroidLogcatFormatter.formatMessage(SIMPLE_FORMAT, line.getHeader(), line.getMessage());
          synchronized (myLock) {
            if (myLogListeners.size() > 1) {
              return String.format("[%1$s]: %2$s", device.getName(), message);
            }
            else {
              return message;
            }
          }
        }

        @Override
        protected void notifyTextAvailable(@NotNull String message, @NotNull Key key) {
          if (myIsFirstMessage.compareAndSet(true, false)) {
            consumer.accept(LogcatOutputConfigurableProvider.BANNER_MESSAGE + "\n", ProcessOutputTypes.STDOUT);
          }
          consumer.accept(message, key);
        }
      };

      AndroidLogcatService.getInstance().addListener(device, logListener, true);

      // Remember the listener for later cleanup
      AndroidLogcatService.LogcatListener previousListener;
      synchronized (myLock) {
        previousListener = myLogListeners.put(device, logListener);
      }

      // Outside of lock to avoid deadlock with AndroidLogcatService internal lock
      if (previousListener != null) {
        // This should not happen (and we have never seen it happening), but removing the existing listener
        // ensures there are no memory leaks.
        LOG.warn(String.format("The device \"%s\" already has a registered logcat listener for application \"%s\". Removing it",
                               device.getName(), myApplicationId));
        AndroidLogcatService.getInstance().removeListener(device, previousListener);
      }
    }

    public void stopCapture(@NotNull IDevice device) {
      LOG.info(String.format("stopCapture(\"%s\")", device.getName()));

      AndroidLogcatService.LogcatListener previousListener;
      synchronized (myLock) {
        previousListener = myLogListeners.remove(device);
      }

      // Outside of lock to avoid deadlock with AndroidLogcatService internal lock
      if (previousListener != null) {
        AndroidLogcatService.getInstance().removeListener(device, previousListener);
      }
    }

    public void stopAll() {
      LOG.info("stopAll()");

      List<Map.Entry<IDevice, AndroidLogcatService.LogcatListener>> listeners;
      synchronized (myLock) {
        listeners = new ArrayList<>(myLogListeners.entrySet());
        myLogListeners.clear();
      }

      // Outside of lock to avoid deadlock with AndroidLogcatService internal lock
      for (Map.Entry<IDevice, AndroidLogcatService.LogcatListener> entry : listeners) {
        AndroidLogcatService.getInstance().removeListener(entry.getKey(), entry.getValue());
      }
    }
  }
}
