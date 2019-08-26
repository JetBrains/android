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
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.run.LaunchCompatibilityChecker;
import com.android.tools.idea.run.LaunchCompatibilityCheckerImpl;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Streams;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AsyncDevicesGetter {
  @NotNull
  private final Project myProject;

  @NotNull
  private final BooleanSupplier mySelectDeviceSnapshotComboBoxSnapshotsEnabled;

  @NotNull
  private final Worker<Collection<VirtualDevice>> myVirtualDevicesWorker;

  @NotNull
  private final Worker<Collection<ConnectedDevice>> myConnectedDevicesWorker;

  @NotNull
  private final KeyToConnectionTimeMap myMap;

  @NotNull
  private final DeviceNamePropertiesFetcher myDevicePropertiesFetcher;

  @Nullable
  private LaunchCompatibilityChecker myChecker;

  @SuppressWarnings("unused")
  private AsyncDevicesGetter(@NotNull Project project) {
    this(project, () -> StudioFlags.SELECT_DEVICE_SNAPSHOT_COMBO_BOX_SNAPSHOTS_ENABLED.get(), new KeyToConnectionTimeMap());
  }

  @VisibleForTesting
  AsyncDevicesGetter(@NotNull Project project,
                     @NotNull BooleanSupplier selectDeviceSnapshotComboBoxSnapshotsEnabled,
                     @NotNull KeyToConnectionTimeMap map) {
    myProject = project;
    mySelectDeviceSnapshotComboBoxSnapshotsEnabled = selectDeviceSnapshotComboBoxSnapshotsEnabled;

    myVirtualDevicesWorker = new Worker<>(Collections.emptyList());
    myConnectedDevicesWorker = new Worker<>(Collections.emptyList());

    myMap = map;
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

    if (!mySelectDeviceSnapshotComboBoxSnapshotsEnabled.getAsBoolean()) {
      Callable<Collection<VirtualDevice>> virtualDevicesTask = new VirtualDevicesTask(false, myChecker);
      Callable<Collection<ConnectedDevice>> connectedDevicesTask = new ConnectedDevicesTask(myProject, myChecker);

      return getImpl(myVirtualDevicesWorker.get(virtualDevicesTask), myConnectedDevicesWorker.get(connectedDevicesTask));
    }

    // TODO Reconcile the virtual devices with the connected ones. Retain the connected device keys in the map.
    return new ArrayList<>(myVirtualDevicesWorker.get(new VirtualDevicesTask(true, myChecker)));
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
    @SuppressWarnings("UnstableApiUsage")
    Stream<Device> deviceStream = Streams.concat(
      connectedVirtualDeviceStream(connectedDevices, virtualDevices),
      physicalDeviceStream(connectedDevices),
      disconnectedVirtualDeviceStream(virtualDevices, connectedDevices));

    List<Device> devices = deviceStream.collect(Collectors.toList());

    Collection<String> keys = devices.stream()
      .filter(Device::isConnected)
      .map(Device::getKey)
      .collect(Collectors.toList());

    myMap.retainAll(keys);
    return devices;
  }

  @NotNull
  private Stream<VirtualDevice> connectedVirtualDeviceStream(@NotNull Collection<ConnectedDevice> connectedDevices,
                                                             @NotNull Collection<VirtualDevice> virtualDevices) {
    Map<String, VirtualDevice> keyToVirtualDeviceMap = virtualDevices.stream().collect(Collectors.toMap(Device::getKey, device -> device));

    return connectedDevices.stream()
      .filter(device -> keyToVirtualDeviceMap.containsKey(device.getVirtualDeviceKey()))
      .map(device -> VirtualDevice.newConnectedDevice(keyToVirtualDeviceMap.get(device.getVirtualDeviceKey()), device, myMap));
  }

  @NotNull
  private Stream<PhysicalDevice> physicalDeviceStream(@NotNull Collection<ConnectedDevice> connectedDevices) {
    return connectedDevices.stream()
      .filter(device -> device.getVirtualDeviceKey() == null)
      .map(device -> PhysicalDevice.newDevice(device, myDevicePropertiesFetcher, myMap));
  }

  @NotNull
  private static Stream<VirtualDevice> disconnectedVirtualDeviceStream(@NotNull Collection<VirtualDevice> virtualDevices,
                                                                       @NotNull Collection<ConnectedDevice> connectedDevices) {
    Collection<String> connectedVirtualDeviceKeys = connectedDevices.stream()
      .map(ConnectedDevice::getVirtualDeviceKey)
      .collect(Collectors.toSet());

    return virtualDevices.stream().filter(device -> !connectedVirtualDeviceKeys.contains(device.getKey()));
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

  @VisibleForTesting
  final Object getChecker() {
    return myChecker;
  }
}
