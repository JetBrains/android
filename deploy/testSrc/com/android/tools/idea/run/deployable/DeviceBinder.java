/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.run.deployable;

import static com.android.ddmlib.IDevice.CHANGE_STATE;
import static com.google.common.truth.Truth.assertThat;

import com.android.annotations.NonNull;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.AndroidDebugBridge.IDeviceChangeListener;
import com.android.ddmlib.Client;
import com.android.ddmlib.IDevice;
import com.android.fakeadbserver.ClientState;
import com.android.fakeadbserver.DeviceState;
import com.android.fakeadbserver.DeviceState.DeviceStatus;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import org.jetbrains.annotations.NotNull;

public class DeviceBinder {
  @NotNull private final DeviceState myState;
  @NotNull private final IDevice myIDevice;

  public DeviceBinder(@NotNull DeviceState deviceState) throws InterruptedException {
    myState = deviceState;
    waitUntilAdbHasDevice(myState.getDeviceId());
    myIDevice = findDevice(myState);
  }

  @NotNull
  public DeviceState getState() {
    return myState;
  }

  @NotNull
  public IDevice getIDevice() {
    return myIDevice;
  }

  public void setStatus(@NotNull DeviceStatus status) throws InterruptedException {
    myState.setDeviceStatus(status);
    waitUntilDeviceIsInState(convertToDeviceState(status));
  }

  @NotNull
  public ClientState startClient(int pid, int uid, @NonNull String packageName, boolean isWaiting)
    throws InterruptedException {
    return startClient(pid, uid, packageName, packageName, isWaiting);
  }

  @NotNull
  public ClientState startClient(int pid, int uid, @NonNull String processName, @NonNull String packageName, boolean isWaiting)
    throws InterruptedException {
    ClientState clientState = myState.startClient(pid, uid, processName, packageName, isWaiting);
    waitUntilIDeviceHasPid(pid);
    return clientState;
  }

  public void stopClient(@NotNull ClientState clientState) throws InterruptedException {
    myState.stopClient(clientState.getPid());
    waitUntilIDeviceDoesNotHavePid(clientState.getPid());
  }

  @NotNull
  private static IDevice.DeviceState convertToDeviceState(@NotNull DeviceStatus deviceStatus) {
    // DeviceStatus is mirror of DeiceState.
    return IDevice.DeviceState.valueOf(deviceStatus.name());
  }

  @NotNull
  private static IDevice findDevice(@NotNull DeviceState deviceState) {
    IDevice iDevice = Arrays.stream(AndroidDebugBridge.getBridge().getDevices())
      .filter(d -> deviceState.getDeviceId().equals(d.getSerialNumber()))
      .findFirst().orElse(null);
    assertThat(iDevice).isNotNull();
    return iDevice;
  }

  @NotNull
  public Client findClient(@NotNull ClientState clientState) {
    Client client =
      Arrays.stream(myIDevice.getClients()).filter(c -> c.getClientData().getPid() == clientState.getPid()).findFirst().orElse(null);
    assertThat(client).isNotNull();
    return client;
  }

  private static void waitUntilAdbHasDevice(@NotNull String deviceId) throws InterruptedException {
    CountDownLatch countDownLatch = new CountDownLatch(1);
    IDeviceChangeListener deviceChangeListener = new IDeviceChangeListener() {
      @Override
      public void deviceConnected(@NonNull IDevice device) {
        deviceChanged(device, CHANGE_STATE);
      }

      @Override
      public void deviceDisconnected(@NonNull IDevice device) {
        deviceChanged(device, CHANGE_STATE);
      }

      @Override
      public void deviceChanged(@NonNull IDevice device, int changeMask) {
        if (deviceId.equals(device.getSerialNumber())) {
          AndroidDebugBridge.removeDeviceChangeListener(this);
          countDownLatch.countDown();
        }
      }
    };

    AndroidDebugBridge.addDeviceChangeListener(deviceChangeListener);
    while (!AndroidDebugBridge.getBridge().hasInitialDeviceList()) {
      //noinspection BusyWait
      Thread.sleep(50); // We'll have to busy wait due to API limitations.
    }
    for (IDevice device : AndroidDebugBridge.getBridge().getDevices()) {
      deviceChangeListener.deviceConnected(device);
    }
    countDownLatch.await();
  }

  private void waitUntilDeviceIsInState(@NotNull IDevice.DeviceState state) throws InterruptedException {
    CountDownLatch deviceIsInStateLatch = new CountDownLatch(1);
    IDeviceChangeListener listener = new IDeviceChangeListener() {
      @Override
      public void deviceConnected(@NonNull IDevice device) {
        deviceChanged(device, CHANGE_STATE);
      }

      @Override
      public void deviceDisconnected(@NonNull IDevice device) {
        deviceChanged(device, CHANGE_STATE);
      }

      @Override
      public void deviceChanged(@NonNull IDevice device, int changeMask) {
        if (device == myIDevice && device.getState() == state) {
          AndroidDebugBridge.removeDeviceChangeListener(this);
          deviceIsInStateLatch.countDown();
        }
      }
    };

    // Add the listener before we test the device's state, since the callback is asynchronous.
    AndroidDebugBridge.addDeviceChangeListener(listener);
    listener.deviceConnected(myIDevice);
    deviceIsInStateLatch.await();
  }

  private void waitUntilIDeviceHasPid(int pid) throws InterruptedException {
    CountDownLatch deviceHasPidLatch = new CountDownLatch(1);
    IDeviceChangeListener listener = new IDeviceChangeListener() {
      @Override
      public void deviceConnected(@NonNull IDevice device) {
        deviceChanged(device, IDevice.CHANGE_CLIENT_LIST);
      }

      @Override
      public void deviceDisconnected(@NonNull IDevice device) {
        deviceChanged(device, IDevice.CHANGE_CLIENT_LIST);
      }

      @Override
      public void deviceChanged(@NonNull IDevice device, int changeMask) {
        if (device == myIDevice &&
            Arrays.stream(device.getClients()).mapToInt(c -> c.getClientData().getPid()).anyMatch(value -> value == pid)) {
          AndroidDebugBridge.removeDeviceChangeListener(this);
          deviceHasPidLatch.countDown();
        }
      }
    };

    AndroidDebugBridge.addDeviceChangeListener(listener);
    listener.deviceConnected(myIDevice);
    deviceHasPidLatch.await();
  }

  private void waitUntilIDeviceDoesNotHavePid(int pid) throws InterruptedException {
    CountDownLatch deviceDoesNotHavePidLatch = new CountDownLatch(1);
    IDeviceChangeListener listener = new IDeviceChangeListener() {
      @Override
      public void deviceConnected(@NonNull IDevice device) {
        deviceChanged(device, IDevice.CHANGE_CLIENT_LIST);
      }

      @Override
      public void deviceDisconnected(@NonNull IDevice device) {
        deviceChanged(device, IDevice.CHANGE_CLIENT_LIST);
      }

      @Override
      public void deviceChanged(@NonNull IDevice device, int changeMask) {
        if (device == myIDevice &&
            Arrays.stream(device.getClients()).mapToInt(c -> c.getClientData().getPid()).noneMatch(value -> value == pid)) {
          AndroidDebugBridge.removeDeviceChangeListener(this);
          deviceDoesNotHavePidLatch.countDown();
        }
      }
    };

    AndroidDebugBridge.addDeviceChangeListener(listener);
    listener.deviceConnected(myIDevice);
    deviceDoesNotHavePidLatch.await();
  }
}
