/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.devicemanager.physicaltab;

import com.android.annotations.concurrency.UiThread;
import com.android.annotations.concurrency.WorkerThread;
import com.android.ddmlib.AndroidDebugBridge.IDeviceChangeListener;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.internal.DeviceMonitor;
import com.android.sdklib.deviceprovisioner.DeviceProvisioner;
import com.android.tools.idea.devicemanager.DeviceManagerAndroidDebugBridge;
import com.android.tools.idea.devicemanager.DeviceManagerFutureCallback;
import com.android.tools.idea.deviceprovisioner.DeviceProvisionerService;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.EdtExecutorService;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;

/**
 * Notifies the physical device table when physical devices are connected, disconnected, and when they become online. The
 * {@link DeviceMonitor#start device list monitor} thread passes IDevices to the IDeviceChangeListener methods of this class. Those methods
 * convert them to PhysicalDevices and notify the table model on the event dispatch thread.
 */
final class PhysicalDeviceChangeListener implements Disposable, IDeviceChangeListener {
  private static final @NotNull Pattern RE_LOCALHOST_SN = Pattern.compile("localhost:(\\d+)");
  private final @NotNull DeviceManagerAndroidDebugBridge myBridge;
  private final @NotNull ListeningExecutorService myEdtExecutorService;
  private final @NotNull Supplier<BuilderService> myBuilderServiceGetInstance;
  private final @NotNull FutureCallback<PhysicalDevice> myCallback;
  private final @NotNull Project myProject;

  // Keep track of devices that are firebase devices. Firebase devices connecting via
  // ADB are identified as physical devices when they are not.
  // Add the device serial number to this set when the device connects and remove it
  // when it disconnects.
  // Do not take any actions for events with these devices.
  // TODO(b/260153322): Remove once device manager moves to device provisioner framework
  private final @NotNull Set<String> myFirebaseDeviceTracker;

  @UiThread
  PhysicalDeviceChangeListener(@NotNull PhysicalDeviceTableModel model, @NotNull Project project) {
    this(new DeviceManagerAndroidDebugBridge(), BuilderService::getInstance, newAddOrSet(model), project);
  }

  @UiThread
  @VisibleForTesting
  PhysicalDeviceChangeListener(@NotNull DeviceManagerAndroidDebugBridge bridge,
                               @NotNull Supplier<BuilderService> builderServiceGetInstance,
                               @NotNull FutureCallback<PhysicalDevice> callback,
                               @NotNull Project project) {
    myBridge = bridge;
    myEdtExecutorService = MoreExecutors.listeningDecorator(EdtExecutorService.getInstance());
    myBuilderServiceGetInstance = builderServiceGetInstance;
    myCallback = callback;
    myProject = project;
    myFirebaseDeviceTracker = new HashSet<>();

    bridge.addDeviceChangeListener(this);
  }

  @UiThread
  @VisibleForTesting
  static @NotNull FutureCallback<PhysicalDevice> newAddOrSet(@NotNull PhysicalDeviceTableModel model) {
    return new DeviceManagerFutureCallback<>(PhysicalDeviceChangeListener.class, model::addOrSet);
  }

  @UiThread
  @Override
  public void dispose() {
    myBridge.removeDeviceChangeListener(this);
  }

  // TODO Throttle the notifications from the device list monitor thread

  /**
   * Called by the device list monitor thread
   */
  @WorkerThread
  @Override
  public void deviceConnected(@NotNull IDevice device) {
    if (device.isEmulator()) {
      return;
    }

    if (isFirebaseDevice(device)) {
      myFirebaseDeviceTracker.add(device.getSerialNumber());
      return;
    }

    Logger.getInstance(PhysicalDeviceChangeListener.class).info(device + " connected");
    buildPhysicalDevice(device);
  }

  /**
   * Called by the device list monitor thread
   */
  @WorkerThread
  @Override
  public void deviceDisconnected(@NotNull IDevice device) {
    if (device.isEmulator()) {
      return;
    }

    if (myFirebaseDeviceTracker.remove(device.getSerialNumber())) {
      return;
    }

    Logger.getInstance(PhysicalDeviceChangeListener.class).info(device + " disconnected");
    buildPhysicalDevice(device);
  }

  /**
   * Called by the device list monitor and the device client monitor threads
   */
  @WorkerThread
  @Override
  public void deviceChanged(@NotNull IDevice device, int mask) {
    if (device.isEmulator()) {
      return;
    }

    if ((mask & IDevice.CHANGE_STATE) == 0) {
      return;
    }

    if (myFirebaseDeviceTracker.contains(device.getSerialNumber())) {
      return;
    }

    Logger.getInstance(PhysicalDeviceChangeListener.class).info(device + " state changed to " + device.getState());
    buildPhysicalDevice(device);
  }

  /**
   * Called by the device list monitor thread
   */
  @WorkerThread
  private void buildPhysicalDevice(@NotNull IDevice device) {
    // noinspection UnstableApiUsage
    FluentFuture.from(myEdtExecutorService.submit(myBuilderServiceGetInstance::get))
      .transformAsync(builderService -> Objects.requireNonNull(builderService).build(device), myEdtExecutorService)
      .addCallback(myCallback, myEdtExecutorService);
  }


  /**
   * Checks if a device that has connected is a firebase device.<br>
   * This is a temporary fix. DO <b>*NOT*</b> COPY THIS SOLUTION.
   * @param device the device that is to be checked
   * @return true if device is a firebase device, false otherwise.
   */
  // TODO(b/260153322): Remove once device manager moves to device provisioner framework
  private boolean isFirebaseDevice(@NotNull IDevice device) {
    DeviceProvisioner deviceProvisioner = myProject.getService(DeviceProvisionerService.class).getDeviceProvisioner();
    // When the user clicks on the run icon for a firebase device, we create a device handle for that device
    // and add it to the device provisioner framework. This happens before the device connects via adb. This guarantees
    // that the provisioner will know of the device.
    // We don't use findConnectedDeviceHandle() because the device that has connected can be a normal physical device
    // resulting in an unnecessary wait for the user.
    // This is a temporary fix. Do *NOT* copy this solution.
    return deviceProvisioner.getDevices().getValue().stream()
      .anyMatch(deviceHandle -> {
        if (RE_LOCALHOST_SN.matcher(device.getSerialNumber()).matches()) {
          String port = device.getSerialNumber().split(":")[1];
          return port.equals(deviceHandle.getState().getProperties().getDisambiguator());
        }
        return false;
      });
  }
}
