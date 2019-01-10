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
import com.android.tools.idea.ddms.DeviceNamePropertiesFetcher;
import com.android.tools.idea.ddms.DeviceNamePropertiesProvider;
import com.android.tools.idea.run.LaunchCompatibilityChecker;
import com.android.tools.idea.run.LaunchCompatibilityCheckerImpl;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class AsyncDevicesGetter {
  @NotNull
  private final Worker<Collection<VirtualDevice>> myVirtualDevicesWorker;

  @NotNull
  private final Worker<Collection<IDevice>> myConnectedDevicesWorker;

  @NotNull
  private final DeviceNamePropertiesProvider myDevicePropertiesProvider;

  @Nullable
  private LaunchCompatibilityChecker myChecker;

  @Nullable
  private ConnectionTimeService myService;

  AsyncDevicesGetter(@NotNull Disposable parent) {
    this(parent, null);
  }

  @VisibleForTesting
  AsyncDevicesGetter(@NotNull Disposable parent, @Nullable ConnectionTimeService service) {
    myVirtualDevicesWorker = new Worker<>();
    myConnectedDevicesWorker = new Worker<>();
    myDevicePropertiesProvider = new DeviceNamePropertiesFetcher(new DefaultCallback<>(), parent);

    myService = service;
  }

  @NotNull
  List<Device> get(@NotNull Project project) {
    initChecker(project);
    initService(project);

    assert myService != null;

    Collection<VirtualDevice> virtualDevices = myVirtualDevicesWorker.get(
      () -> new VirtualDevicesWorkerDelegate(myChecker, myService),
      Collections.emptyList());

    Collection<IDevice> connectedDevices = new ArrayList<>(myConnectedDevicesWorker.get(
      () -> new ConnectedDevicesWorkerDelegate(project),
      Collections.emptyList()));

    List<Device> devices = new ArrayList<>(virtualDevices.size() + connectedDevices.size());

    virtualDevices.stream()
      .map(device -> newVirtualDeviceIfItsConnected(device, connectedDevices))
      .forEach(devices::add);

    connectedDevices.stream()
      .map(device -> PhysicalDevice.newBuilder(myDevicePropertiesProvider.get(device), device).build(myChecker, myService))
      .forEach(devices::add);

    Collection<String> keys = devices.stream()
      .filter(Device::isConnected)
      .map(Device::getKey)
      .collect(Collectors.toList());

    myService.retainAll(keys);
    return devices;
  }

  private void initChecker(@NotNull Project project) {
    RunnerAndConfigurationSettings configurationAndSettings = RunManager.getInstance(project).getSelectedConfiguration();

    if (configurationAndSettings == null) {
      myChecker = null;
      return;
    }

    Object configuration = configurationAndSettings.getConfiguration();

    if (!(configuration instanceof ModuleBasedConfiguration)) {
      myChecker = null;
      return;
    }

    Module module = ((ModuleBasedConfiguration)configuration).getConfigurationModule().getModule();

    if (module == null) {
      myChecker = null;
      return;
    }

    AndroidFacet facet = AndroidFacet.getInstance(module);

    if (facet == null) {
      myChecker = null;
      return;
    }

    myChecker = LaunchCompatibilityCheckerImpl.create(facet, null, null);
  }

  private void initService(@NotNull Project project) {
    myService = ServiceManager.getService(project, ConnectionTimeService.class);
  }

  // TODO(b/122476635) Simplify this method
  @NotNull
  @VisibleForTesting
  final Device newVirtualDeviceIfItsConnected(@NotNull VirtualDevice virtualDevice, @NotNull Iterable<IDevice> connectedDevices) {
    Object key = virtualDevice.getKey();
    assert myService != null;

    for (Iterator<IDevice> i = connectedDevices.iterator(); i.hasNext(); ) {
      IDevice device = i.next();

      if (Objects.equals(device.getAvdName(), key)) {
        i.remove();
        return VirtualDevice.newConnectedDeviceBuilder(virtualDevice, device).build(myChecker, myService);
      }
    }

    assert !virtualDevice.isConnected();
    return virtualDevice;
  }
}
