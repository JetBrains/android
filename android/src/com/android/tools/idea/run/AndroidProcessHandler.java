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
import com.android.ddmlib.logcat.LogCatHeader;
import com.android.ddmlib.logcat.LogCatMessage;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.logcat.AndroidLogcatFormatter;
import com.android.tools.idea.logcat.AndroidLogcatService;
import com.android.tools.idea.logcat.AndroidLogcatUtils;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SmartList;
import com.intellij.xdebugger.DefaultDebugProcessHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * {@link AndroidProcessHandler} is a {@link com.intellij.execution.process.ProcessHandler} that corresponds to a single Android app
 * potentially running on multiple connected devices after a launch of the app from Studio.
 *
 * It encodes the following behavior:
 *  - Provides an option to connect and monitor the processes running on the device(s).
 *  - If the processes are being monitored, then:
 *     - destroyProcess provides a way to kill the processes (typically, this is connected to the stop button in the UI).
 *     - if all the process dies, then the handler terminates as well
 */
public class AndroidProcessHandler extends DefaultDebugProcessHandler implements AndroidDebugBridge.IDeviceChangeListener,
                                                                                 AndroidDebugBridge.IClientChangeListener {
  private static final Logger LOG = Logger.getInstance(AndroidProcessHandler.class);

  // If the client is not present on the monitored devices after this time, then it is assumed to have died.
  // We are keeping it so long because sometimes (for cold-swap) it seems to take a while..
  private static final long TIMEOUT_MS = 10000;

  @NotNull private final String myApplicationId;
  private final boolean myMonitoringRemoteProcess;

  @NotNull private final List<String> myDevices;
  @NotNull private final Set<Client> myClients;
  @NotNull private final Map<IDevice, AndroidLogcatService.LogLineListener> myLogListeners;

  private long myDeviceAdded;
  private boolean myNoKill;

  public AndroidProcessHandler(@NotNull String applicationId) {
    this(applicationId, true);
  }

  public AndroidProcessHandler(@NotNull String applicationId, boolean monitorRemoteProcess) {
    myApplicationId = applicationId;
    myDevices = new SmartList<>();
    myClients = Sets.newHashSet();
    myLogListeners = new HashMap<>();

    myMonitoringRemoteProcess = monitorRemoteProcess;
    if (myMonitoringRemoteProcess) {
      AndroidDebugBridge.addClientChangeListener(this);
      AndroidDebugBridge.addDeviceChangeListener(this);
    }
  }

  public void addTargetDevice(@NotNull final IDevice device) {
    myDevices.add(device.getSerialNumber());

    AndroidLogcatService.LogLineListener logListener = new AndroidLogcatService.LogLineListener() {
      private final String SIMPLE_FORMAT = AndroidLogcatFormatter.createCustomFormat(false, false, false, true);
      @Nullable private LogCatHeader myActiveHeader;

      @NotNull
      private String formatLogLine(@NotNull LogCatMessage line) {
        assert myActiveHeader != null;
        String message = AndroidLogcatFormatter.formatMessage(SIMPLE_FORMAT, myActiveHeader, line.getMessage());
        return (myDevices.size() > 1 ? "[" + device.getName() + "] " : "") + message;
      }

      @Override
      public void receiveLogLine(@NotNull LogCatMessage line) {
        if (!line.getHeader().getAppName().equals(myApplicationId)) {
          myActiveHeader = null;
          return;
        }

        String message;
        if (!line.getHeader().equals(myActiveHeader)) {
          myActiveHeader = line.getHeader();
          message = formatLogLine(line);
        } else {
          message = Strings.repeat(" ", formatLogLine(line).indexOf(line.getMessage())) + line.getMessage();
        }

        Key key = AndroidLogcatUtils.getProcessOutputType(myActiveHeader.getLogLevel());
        notifyTextAvailable(message + "\n", key);
      }
    };
    AndroidLogcatService.getInstance().addListener(device, logListener);
    myLogListeners.put(device, logListener);

    setMinDeviceApiLevel(device.getVersion());

    Client client = device.getClient(myApplicationId);
    if (client != null) {
      boolean added = myClients.add(client);
      if (added) {
        notifyTextAvailable("Connected to process " + client.getClientData().getPid() + " on device " + device.getName() + "\n",
                            ProcessOutputTypes.STDOUT);
      }
    } else {
      notifyTextAvailable("Client not ready yet..", ProcessOutputTypes.STDOUT);
    }

    LOG.info("Adding device " + device.getName() + " to monitor for launched app: " + myApplicationId);
    myDeviceAdded = System.currentTimeMillis();
  }

  private void setMinDeviceApiLevel(@NotNull AndroidVersion deviceVersion) {
    AndroidVersion apiLevel = getUserData(AndroidProgramRunner.ANDROID_DEVICE_API_LEVEL);
    if (apiLevel == null || apiLevel.compareTo(deviceVersion) > 0) {
      putUserData(AndroidProgramRunner.ANDROID_DEVICE_API_LEVEL, deviceVersion);
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
  protected void detachProcessImpl() {
    super.detachProcessImpl();
    cleanup();
  }

  @Override
  protected void destroyProcessImpl() {
    super.destroyProcessImpl();
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

    for (IDevice device : myLogListeners.keySet()) {
      AndroidLogcatService.getInstance().removeListener(device, myLogListeners.get(device));
    }
    myLogListeners.clear();

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
    myDevices.remove(device.getSerialNumber());
    AndroidLogcatService.getInstance().removeListener(device, myLogListeners.get(device));
    myLogListeners.remove(device);

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
      print("Connected to process " + client.getClientData().getPid() + " on device " + device.getName());
      myClients.add(client);
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
    } else {
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

    if (StringUtil.equals(myApplicationId, client.getClientData().getClientDescription())) {
      print("Connected to process " + client.getClientData().getPid() + " on device " + client.getDevice().getName());
      myClients.add(client);
    }

    String name = client.getClientData().getClientDescription();
    if (name != null && myApplicationId.equals(name) && !client.isValid()) {
      print("Process " + client.getClientData().getPid() + " is not valid anymore!");
      stopMonitoring(client.getDevice());
    }
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

    for (IDevice device : myLogListeners.keySet()) {
      AndroidLogcatService.getInstance().removeListener(device, myLogListeners.get(device));
    }
    myLogListeners.clear();
  }
}
