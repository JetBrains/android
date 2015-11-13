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
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SmartList;
import com.intellij.xdebugger.DefaultDebugProcessHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * {@link AndroidMultiProcessHandler} is a {@link com.intellij.execution.process.ProcessHandler} that corresponds to a single Android app
 * potentially running on multiple connected devices after a launch of the app from Studio.
 *
 * It encodes the following behavior:
 *  - A stop action kills the processes
 *  - The process is assumed to have terminated if it has died in all the connected devices
 */
public class AndroidMultiProcessHandler extends DefaultDebugProcessHandler implements AndroidDebugBridge.IDeviceChangeListener,
                                                                                 AndroidDebugBridge.IClientChangeListener {
  private static final Logger LOG = Logger.getInstance(AndroidMultiProcessHandler.class);

  // If the client is not present on the monitored devices after this time, then it is assumed to have died.
  // We are keeping it so long because sometimes (for cold-swap) it seems to take a while..
  private static final long TIMEOUT_MS = 10000;

  @NotNull private final String myApplicationId;
  @NotNull private final List<String> myDevices;
  @NotNull private final Set<Client> myClients;

  private long myDeviceAdded;
  private boolean myNoKill;

  public AndroidMultiProcessHandler(@NotNull String applicationId) {
    myApplicationId = applicationId;
    myDevices = new SmartList<String>();
    myClients = Sets.newHashSet();

    AndroidDebugBridge.addClientChangeListener(this);
    AndroidDebugBridge.addDeviceChangeListener(this);
  }

  public void addTargetDevice(@NotNull IDevice device) {
    myDevices.add(device.getSerialNumber());

    Client client = device.getClient(myApplicationId);
    if (client != null) {
      myClients.add(client);
      notifyTextAvailable("Connected to process " + client.getClientData().getPid() + " on device " + device.getName() + "\n",
                          ProcessOutputTypes.STDOUT);
    } else {
      notifyTextAvailable("Client not ready yet..", ProcessOutputTypes.STDOUT);
    }

    LOG.info("Adding device " + device.getName() + " to monitor for launched app: " + myApplicationId);
    myDeviceAdded = System.currentTimeMillis();
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
    killProcesses();
    cleanup();
  }

  private void killProcesses() {
    if (myNoKill) {
      return;
    }

    for (IDevice device : AndroidDebugBridge.getBridge().getDevices()) {
      if (myDevices.contains(device.getSerialNumber())) {
        Client client = device.getClient(myApplicationId);
        if (client != null) {
          client.kill();
        }
      }
    }
  }

  @Override
  protected void destroyProcessImpl() {
    super.destroyProcessImpl();
    killProcesses();
    cleanup();
  }

  public void setNoKill() {
    myNoKill = true;
  }

  private void cleanup() {
    myDevices.clear();
    myClients.clear();

    AndroidDebugBridge.removeClientChangeListener(this);
    AndroidDebugBridge.removeDeviceChangeListener(this);
  }

  @Override
  public void deviceConnected(IDevice device) {
  }

  @Override
  public void deviceDisconnected(IDevice device) {
    if (device == null) {
      return;
    }

    if (!myDevices.contains(device.getSerialNumber())) {
      return;
    }

    print("Device " + device.getName() + "disconnected, monitoring stopped.");
    stopMonitoring(device);
  }

  private void stopMonitoring(IDevice device) {
    myDevices.remove(device.getSerialNumber());
    if (myDevices.isEmpty()) {
      detachProcess();
    }
  }

  @Override
  public void deviceChanged(IDevice device, int changeMask) {
    if ((changeMask & IDevice.CHANGE_CLIENT_LIST) != IDevice.CHANGE_CLIENT_LIST) {
      return;
    }

    if (!myDevices.contains(device.getSerialNumber())) {
      return;
    }

    Client client = device.getClient(myApplicationId);
    if (client == null && (System.currentTimeMillis() - myDeviceAdded) > TIMEOUT_MS) {
      print("Timed out waiting for process to appear on " + device.getName());
      stopMonitoring(device);
    } else if (client != null) {
      print("Connected to process " + client.getClientData().getPid() + " on device " + device.getName());
      myClients.add(client);
    }
  }

  @Override
  public void clientChanged(Client client, int changeMask) {
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
  public Collection<IDevice> getDevices() {
    List<IDevice> devices = Lists.newArrayListWithExpectedSize(myClients.size());
    for (Client client : myClients) {
      devices.add(client.getDevice());
    }

    return devices;
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

  private void print(@NotNull String s) {
    notifyTextAvailable(s + "\n", ProcessOutputTypes.STDOUT);
  }
}
