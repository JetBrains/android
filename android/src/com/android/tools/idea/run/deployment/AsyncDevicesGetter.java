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

import com.android.tools.idea.avdmanager.AvdManagerConnection;
import com.android.tools.idea.run.LaunchCompatibilityChecker;
import com.android.tools.idea.run.LaunchCompatibilityCheckerImpl;
import com.android.tools.idea.run.LaunchableAndroidDevice;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Streams;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.serviceContainer.NonInjectable;
import com.intellij.util.concurrency.AppExecutorUtil;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A supplier of an optional list of VirtualDevices or PhysicalDevices. It is safe to call the get method from the event dispatch thread.
 *
 * <p>One worker collects the available virtual devices and the other one collects the running devices (which can be virtual or physical).
 * This class combines both results.
 *
 * <p>The "Async" part of the name comes from the workers doing their work off the event dispatch thread. The get method does not block; the
 * workers hold onto previous results and those are used if the background threads are busy. If a worker does not have a previous result the
 * get method returns Optional.empty.
 */
final class AsyncDevicesGetter implements Disposable {
  @NotNull
  private final Project myProject;

  @NotNull
  private final Worker<Collection<VirtualDevice>> myVirtualDevicesWorker;

  @NotNull
  private final Worker<Collection<ConnectedDevice>> myConnectedDevicesWorker;

  private final @NotNull AndroidDebugBridge myBridge;

  @NotNull
  private final KeyToConnectionTimeMap myMap;

  @Nullable
  private LaunchCompatibilityChecker myChecker;

  @SuppressWarnings("unused")
  private AsyncDevicesGetter(@NotNull Project project) {
    this(project, new KeyToConnectionTimeMap());
  }

  @NonInjectable
  @VisibleForTesting
  AsyncDevicesGetter(@NotNull Project project, @NotNull KeyToConnectionTimeMap map) {
    myProject = project;
    myVirtualDevicesWorker = new Worker<>();
    myConnectedDevicesWorker = new Worker<>();
    myBridge = new DdmlibAndroidDebugBridge(project);
    myMap = map;
  }

  @Override
  public void dispose() {
  }

  @NotNull
  static AsyncDevicesGetter getInstance(@NotNull Project project) {
    return project.getService(AsyncDevicesGetter.class);
  }

  /**
   * @return an optional list of devices including the virtual devices ready to be launched, virtual devices that have been launched, and
   * the connected physical devices
   */
  @NotNull
  Optional<List<Device>> get() {
    initChecker(RunManager.getInstance(myProject).getSelectedConfiguration(), AndroidFacet::getInstance);

    AsyncSupplier<Collection<VirtualDevice>> virtualDevicesTask = new VirtualDevicesTask.Builder()
      .setExecutorService(AppExecutorUtil.getAppExecutorService())
      .setGetAvds(() -> AvdManagerConnection.getDefaultAvdManagerConnection().getAvds(false))
      .setNewLaunchableAndroidDevice(LaunchableAndroidDevice::new)
      .setChecker(myChecker)
      .build();

    Optional<Collection<VirtualDevice>> virtualDevices = myVirtualDevicesWorker.perform(virtualDevicesTask);
    var connectedDevices = myConnectedDevicesWorker.perform(new ConnectedDevicesTask2(myBridge, myChecker));

    if (virtualDevices.isEmpty() || connectedDevices.isEmpty()) {
      return Optional.empty();
    }

    return Optional.of(getImpl(virtualDevices.get(), connectedDevices.get()));
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
    Stream<Device> deviceStream = Streams.concat(
      connectedVirtualDeviceStream(connectedDevices, virtualDevices),
      physicalDeviceStream(connectedDevices),
      disconnectedVirtualDeviceStream(virtualDevices, connectedDevices));

    List<Device> devices = deviceStream.collect(Collectors.toList());

    Collection<Key> keys = devices.stream()
      .filter(Device::isConnected)
      .map(Device::getKey)
      .collect(Collectors.toList());

    myMap.retainAll(keys);
    return devices;
  }

  @NotNull
  private Stream<VirtualDevice> connectedVirtualDeviceStream(@NotNull Collection<ConnectedDevice> connectedDevices,
                                                             @NotNull Collection<VirtualDevice> virtualDevices) {
    return connectedDevices.stream()
      .filter(ConnectedDevice::isVirtualDevice)
      .map(device -> VirtualDevice.newConnectedDevice(device, myMap, findFirst(virtualDevices, device.getKey()).orElse(null)));
  }

  @NotNull
  private static Optional<VirtualDevice> findFirst(@NotNull Collection<VirtualDevice> devices, @NotNull Key key) {
    return devices.stream()
      .filter(device -> device.getKey().equals(key) || device.getNameKey().orElseThrow(AssertionError::new).equals(key))
      .findFirst();
  }

  @NotNull
  private Stream<PhysicalDevice> physicalDeviceStream(@NotNull Collection<ConnectedDevice> connectedDevices) {
    return connectedDevices.stream()
      .filter(ConnectedDevice::isPhysicalDevice)
      .map(device -> PhysicalDevice.newDevice(device, myMap));
  }

  @NotNull
  private static Stream<VirtualDevice> disconnectedVirtualDeviceStream(@NotNull Collection<VirtualDevice> virtualDevices,
                                                                       @NotNull Collection<ConnectedDevice> connectedDevices) {
    Collection<Key> connectedVirtualDeviceKeys = connectedDevices.stream()
      .filter(ConnectedDevice::isVirtualDevice)
      .map(ConnectedDevice::getKey)
      .collect(Collectors.toSet());

    return virtualDevices.stream().filter(device -> !containsPathOrName(connectedVirtualDeviceKeys, device));
  }

  private static boolean containsPathOrName(@NotNull Collection<Key> keys, @NotNull VirtualDevice device) {
    return keys.contains(device.getKey()) || keys.contains(device.getNameKey().orElseThrow(AssertionError::new));
  }

  @VisibleForTesting
  void initChecker(@Nullable RunnerAndConfigurationSettings configurationAndSettings, @NotNull Function<Module, AndroidFacet> facetGetter) {
    if (configurationAndSettings == null) {
      myChecker = null;
      return;
    }

    Object configuration = configurationAndSettings.getConfiguration();

    if (!(configuration instanceof ModuleBasedConfiguration)) {
      myChecker = null;
      return;
    }

    Module module = ((ModuleBasedConfiguration<?, ?>)configuration).getConfigurationModule().getModule();

    if (module == null) {
      myChecker = null;
      return;
    }

    AndroidFacet facet = facetGetter.apply(module);

    if (facet == null || facet.isDisposed()) {
      myChecker = null;
      return;
    }

    if (DumbService.isDumb(myProject)) {
      myChecker = null;
      return;
    }

    myChecker = LaunchCompatibilityCheckerImpl.create(facet, null, null);
  }

  @VisibleForTesting
  Object getChecker() {
    return myChecker;
  }
}
