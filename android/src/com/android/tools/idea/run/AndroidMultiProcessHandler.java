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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.SmartList;
import com.intellij.xdebugger.DefaultDebugProcessHandler;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * {@link AndroidMultiProcessHandler} is a {@link com.intellij.execution.process.ProcessHandler} that corresponds to a single Android app
 * potentially running on multiple connected devices after a launch of the app from Studio.
 *
 * It encodes the following behavior:
 *  - A stop action doesn't kill the processes, but does disconnect this process handler from the target devices.
 *  - The process is assumed to have terminated if it has died in all the connected devices
 *  -
 */
public class AndroidMultiProcessHandler extends DefaultDebugProcessHandler implements AndroidDebugBridge.IDeviceChangeListener,
                                                                                 AndroidDebugBridge.IClientChangeListener {
  private static final Logger LOG = Logger.getInstance(AndroidMultiProcessHandler.class);

  @NotNull private final String myApplicationId;
  @NotNull private final List<String> myDevices;
  @NotNull private final List<Client> myClients;

  private long myDeviceAdded;

  public AndroidMultiProcessHandler(@NotNull String applicationId) {
    myApplicationId = applicationId;
    myDevices = new SmartList<String>();
    myClients = new SmartList<Client>();

    AndroidDebugBridge.addClientChangeListener(this);
    AndroidDebugBridge.addDeviceChangeListener(this);
  }

  public void addTargetDevice(@NotNull IDevice device) {
    myDevices.add(device.getSerialNumber());

    Client client = device.getClient(myApplicationId);
    if (client != null) {
      myClients.add(client);
    }

    LOG.info("Adding device " + device.getName() + " to monitor for launched app: " + myApplicationId);
    myDeviceAdded = System.currentTimeMillis();
  }

  @Override
  public boolean detachIsDefault() {
    return true;
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
    cleanup();
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
    if (client == null && (System.currentTimeMillis() - myDeviceAdded) > 5000) {
      stopMonitoring(device);
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

    String name = client.getClientData().getClientDescription();
    if (name != null && myApplicationId.equals(name) && !client.isValid()) {
      stopMonitoring(client.getDevice());
    }
  }
}
