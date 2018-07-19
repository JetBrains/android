/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.run.deployment;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.tools.idea.adb.AdbService;
import com.android.tools.idea.avdmanager.AvdManagerConnection;
import com.android.tools.idea.ddms.DeviceNamePropertiesFetcher;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.File;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Supplier;

class AsyncDevicesGetter {
  private final Worker<Collection<AvdInfo>> myVirtualDevicesWorker;
  private final Worker<Collection<IDevice>> myConnectedDevicesWorker;
  private final DeviceNamePropertiesFetcher myDevicePropertiesFetcher;

  AsyncDevicesGetter(@NotNull Disposable parent) {
    myVirtualDevicesWorker = new Worker<>();
    myConnectedDevicesWorker = new Worker<>();
    myDevicePropertiesFetcher = new DeviceNamePropertiesFetcher(new DefaultCallback<>(), parent);
  }

  @NotNull
  List<Device> get(@NotNull Project project) {
    Collection<AvdInfo> virtualDevices = myVirtualDevicesWorker.get(VirtualDevicesWorkerDelegate::new, Collections.emptyList());

    Supplier<SwingWorker<Collection<IDevice>, Void>> supplier = () -> new ConnectedDevicesWorkerDelegate(project);
    Collection<IDevice> connectedDevices = new ArrayList<>(myConnectedDevicesWorker.get(supplier, Collections.emptyList()));

    List<Device> devices = new ArrayList<>(virtualDevices.size() + connectedDevices.size());

    virtualDevices.stream()
                  .map(virtualDevice -> VirtualDevice.newVirtualDevice(virtualDevice, connectedDevices))
                  .forEach(devices::add);

    connectedDevices.stream()
                    .map(connectedDevice -> myDevicePropertiesFetcher.get(connectedDevice))
                    .map(PhysicalDevice::new)
                    .forEach(devices::add);

    return devices;
  }

  private static final class VirtualDevicesWorkerDelegate extends SwingWorker<Collection<AvdInfo>, Void> {
    @NotNull
    @Override
    protected Collection<AvdInfo> doInBackground() {
      return AvdManagerConnection.getDefaultAvdManagerConnection().getAvds(true);
    }
  }

  private static final class ConnectedDevicesWorkerDelegate extends SwingWorker<Collection<IDevice>, Void> {
    private final Project myProject;

    private ConnectedDevicesWorkerDelegate(@NotNull Project project) {
      myProject = project;
    }

    @NotNull
    @Override
    protected Collection<IDevice> doInBackground() {
      File adb = AndroidSdkUtils.getAdb(myProject);

      if (adb == null) {
        return Collections.emptyList();
      }

      Future<AndroidDebugBridge> future = AdbService.getInstance().getDebugBridge(adb);

      if (!future.isDone()) {
        return Collections.emptyList();
      }

      try {
        return Arrays.asList(future.get().getDevices());
      }
      catch (InterruptedException exception) {
        // This should never happen. The future is done and can no longer be interrupted.
        throw new AssertionError(exception);
      }
      catch (ExecutionException exception) {
        Logger.getInstance(ConnectedDevicesWorkerDelegate.class).warn(exception);
        return Collections.emptyList();
      }
    }
  }
}
