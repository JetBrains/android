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

import com.android.annotations.VisibleForTesting;
import com.android.ddmlib.IDevice;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.tools.idea.ddms.DeviceNamePropertiesFetcher;
import com.android.tools.idea.ddms.DeviceNamePropertiesProvider;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import javax.swing.SwingWorker;
import org.jetbrains.annotations.NotNull;

class AsyncDevicesGetter {
  private final Worker<Collection<VirtualDevice>> myVirtualDevicesWorker;
  private final Worker<Collection<IDevice>> myConnectedDevicesWorker;
  private final DeviceNamePropertiesProvider myDevicePropertiesProvider;

  AsyncDevicesGetter(@NotNull Disposable parent) {
    myVirtualDevicesWorker = new Worker<>();
    myConnectedDevicesWorker = new Worker<>();
    myDevicePropertiesProvider = new DeviceNamePropertiesFetcher(new DefaultCallback<>(), parent);
  }

  @NotNull
  List<Device> get(@NotNull Project project) {
    Collection<VirtualDevice> virtualDevices = myVirtualDevicesWorker.get(VirtualDevicesWorkerDelegate::new, Collections.emptyList());

    Supplier<SwingWorker<Collection<IDevice>, Void>> supplier = () -> new ConnectedDevicesWorkerDelegate(project);
    Collection<IDevice> connectedDevices = new ArrayList<>(myConnectedDevicesWorker.get(supplier, Collections.emptyList()));

    List<Device> devices = new ArrayList<>(virtualDevices.size() + connectedDevices.size());

    virtualDevices.stream()
                  .map(device -> newVirtualDeviceIfItsConnected(device, connectedDevices))
                  .forEach(devices::add);

    connectedDevices.stream()
                    .map(connectedDevice -> new PhysicalDevice(myDevicePropertiesProvider.get(connectedDevice), connectedDevice))
                    .forEach(devices::add);

    return devices;
  }

  @NotNull
  @VisibleForTesting
  static Device newVirtualDeviceIfItsConnected(@NotNull VirtualDevice virtualDevice, @NotNull Iterable<IDevice> connectedDevices) {
    AvdInfo info = virtualDevice.getAvdInfo();
    assert info != null;

    Object name = info.getName();

    for (Iterator<IDevice> i = connectedDevices.iterator(); i.hasNext(); ) {
      IDevice device = i.next();

      if (Objects.equals(device.getAvdName(), name)) {
        i.remove();
        return VirtualDevice.newConnectedVirtualDevice(virtualDevice, device);
      }
    }

    assert !virtualDevice.isConnected();
    return virtualDevice;
  }
}
