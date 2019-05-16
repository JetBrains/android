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

import com.android.tools.idea.ddms.DeviceNamePropertiesFetcher;
import com.android.tools.idea.run.LaunchCompatibilityChecker;
import com.android.tools.idea.run.LaunchCompatibilityCheckerImpl;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AsyncDevicesGetter {
  @NotNull
  private final Project myProject;

  @NotNull
  private final KeyToConnectionTimeMap myMap;

  @NotNull
  private final Worker<Collection<VirtualDevice>> myVirtualDevicesWorker;

  @NotNull
  private final Worker<Collection<ConnectedDevice>> myConnectedDevicesWorker;

  @NotNull
  private final DeviceNamePropertiesFetcher myDevicePropertiesFetcher;

  @Nullable
  private LaunchCompatibilityChecker myChecker;

  @SuppressWarnings("unused")
  private AsyncDevicesGetter(@NotNull Project project) {
    this(project, new KeyToConnectionTimeMap());
  }

  @VisibleForTesting
  AsyncDevicesGetter(@NotNull Project project, @NotNull KeyToConnectionTimeMap map) {
    myProject = project;
    myMap = map;

    myVirtualDevicesWorker = new Worker<>(() -> new VirtualDevicesWorkerDelegate(myChecker));
    myConnectedDevicesWorker = new Worker<>(() -> new ConnectedDevicesWorkerDelegate(project, myChecker));
    myDevicePropertiesFetcher = new DeviceNamePropertiesFetcher(new DefaultCallback<>(), project);
  }

  /**
   * @return a list of devices including the virtual devices ready to be launched, virtual devices that have been launched, and the
   * connected physical devices
   */
  @NotNull
  List<Device> get() {
    if (Disposer.isDisposed(myDevicePropertiesFetcher)) {
      return Collections.emptyList();
    }

    initChecker(RunManager.getInstance(myProject).getSelectedConfiguration(), AndroidFacet::getInstance);
    return getImpl(myVirtualDevicesWorker.get(), myConnectedDevicesWorker.get());
  }

  /**
   * The meat of {@link #get()} separated out with injected device collections for testing
   *
   * <p>Virtual devices that have not been launched will have a single entry in virtualDevices. Virtual devices that have been launched will
   * have an entry in each list; this method will combine them into a single device and return it. Connected physical devices will have a
   * single entry in connectedDevices.
   *
   * @param virtualDevices   the virtual devices that have and have not been launched
   * @param connectedDevices the virtual devices that have been launched and the connected physical devices
   */
  @NotNull
  @VisibleForTesting
  List<Device> getImpl(@NotNull Collection<VirtualDevice> virtualDevices, @NotNull Collection<ConnectedDevice> connectedDevices) {
    List<Device> devices = new ArrayList<>(virtualDevices.size() + connectedDevices.size());

    virtualDevices.stream()
      .map(device -> newVirtualDeviceIfItsConnected(device, connectedDevices))
      .forEach(devices::add);

    connectedDevices.stream()
      .map(device -> PhysicalDevice.newDevice(device, myDevicePropertiesFetcher, myMap))
      .forEach(devices::add);

    Collection<String> keys = devices.stream()
      .filter(Device::isConnected)
      .map(Device::getKey)
      .collect(Collectors.toList());

    myMap.retainAll(keys);
    return devices;
  }

  @VisibleForTesting
  final void initChecker(@Nullable RunnerAndConfigurationSettings configurationAndSettings,
                         @NotNull Function<Module, AndroidFacet> facetGetter) {
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

    AndroidFacet facet = facetGetter.apply(module);

    if (facet == null) {
      myChecker = null;
      return;
    }

    Object platform = facet.getConfiguration().getAndroidPlatform();

    if (platform == null) {
      myChecker = null;
      return;
    }

    myChecker = LaunchCompatibilityCheckerImpl.create(facet, null, null);
  }

  // TODO(b/122476635) Simplify this method
  @NotNull
  @VisibleForTesting
  final Device newVirtualDeviceIfItsConnected(@NotNull VirtualDevice virtualDevice, @NotNull Iterable<ConnectedDevice> connectedDevices) {
    Object key = virtualDevice.getKey();

    for (Iterator<ConnectedDevice> i = connectedDevices.iterator(); i.hasNext(); ) {
      ConnectedDevice connectedDevice = i.next();

      if (Objects.equals(connectedDevice.getVirtualDeviceKey(), key)) {
        i.remove();
        return VirtualDevice.newConnectedDevice(virtualDevice, connectedDevice, myMap);
      }
    }

    assert !virtualDevice.isConnected();
    return virtualDevice;
  }

  @VisibleForTesting
  final Object getChecker() {
    return myChecker;
  }
}
