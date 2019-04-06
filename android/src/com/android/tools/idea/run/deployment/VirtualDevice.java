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

import com.android.ddmlib.IDevice;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.run.AndroidDevice;
import com.android.tools.idea.run.DeploymentApplicationService;
import com.android.tools.idea.run.DeviceFutures;
import com.android.tools.idea.run.LaunchableAndroidDevice;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.openapi.project.Project;
import icons.StudioIcons;
import java.util.Objects;
import java.util.concurrent.Future;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class VirtualDevice extends Device {
  static final String DEFAULT_SNAPSHOT = "default_boot";
  static final ImmutableCollection<String> DEFAULT_SNAPSHOT_COLLECTION = ImmutableList.of(DEFAULT_SNAPSHOT);

  private static final Icon ourConnectedIcon = ExecutionUtil.getLiveIndicator(StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_PHONE);

  private final boolean myConnected;

  /**
   * Snapshot directory names displayed to the developer.
   */
  @NotNull
  private final ImmutableCollection<String> mySnapshots;

  @NotNull
  static VirtualDevice newConnectedDevice(@NotNull VirtualDevice virtualDevice,
                                          @NotNull ConnectedDevice connectedDevice,
                                          @NotNull KeyToConnectionTimeMap map) {
    String key = virtualDevice.getKey();

    return new Builder()
      .setName(virtualDevice.getName())
      .setValid(connectedDevice.isValid())
      .setKey(key)
      .setConnectionTime(map.get(key))
      .setAndroidDevice(connectedDevice.getAndroidDevice())
      .setConnected(true)
      .setSnapshots(virtualDevice.mySnapshots)
      .build();
  }

  static final class Builder extends Device.Builder<Builder> {
    private boolean myConnected;

    @NotNull
    private ImmutableCollection<String> mySnapshots;

    Builder() {
      mySnapshots = ImmutableList.of();
    }

    @NotNull
    Builder setConnected(boolean connected) {
      myConnected = connected;
      return this;
    }

    @NotNull
    Builder setSnapshots(@NotNull ImmutableCollection<String> snapshots) {
      mySnapshots = snapshots;
      return this;
    }

    @NotNull
    @Override
    Builder self() {
      return this;
    }

    @NotNull
    @Override
    VirtualDevice build() {
      return new VirtualDevice(this);
    }
  }

  private VirtualDevice(@NotNull Builder builder) {
    super(builder);

    myConnected = builder.myConnected;
    mySnapshots = builder.mySnapshots;
  }

  @NotNull
  @Override
  Icon getIcon() {
    return myConnected ? ourConnectedIcon : StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_PHONE;
  }

  @Override
  boolean isConnected() {
    return myConnected;
  }

  @NotNull
  @Override
  ImmutableCollection<String> getSnapshots() {
    return mySnapshots;
  }

  @NotNull
  @Override
  Future<AndroidVersion> getAndroidVersion() {
    Object androidDevice = getAndroidDevice();

    if (androidDevice instanceof LaunchableAndroidDevice) {
      // noinspection UnstableApiUsage
      return Futures.immediateFuture(((LaunchableAndroidDevice)androidDevice).getAvdInfo().getAndroidVersion());
    }

    IDevice ddmlibDevice = getDdmlibDevice();
    assert ddmlibDevice != null;

    return DeploymentApplicationService.getInstance().getVersion(ddmlibDevice);
  }

  @Override
  void addTo(@NotNull DeviceFutures futures, @NotNull Project project, @Nullable String snapshot) {
    AndroidDevice device = getAndroidDevice();

    if (!myConnected) {
      device.launch(project, snapshot);
    }

    futures.getDevices().add(device);
  }

  @Override
  public boolean equals(@Nullable Object object) {
    if (!(object instanceof VirtualDevice)) {
      return false;
    }

    VirtualDevice device = (VirtualDevice)object;

    return getName().equals(device.getName()) &&
           isValid() == device.isValid() &&
           getKey().equals(device.getKey()) &&
           Objects.equals(getConnectionTime(), device.getConnectionTime()) &&
           getAndroidDevice().equals(device.getAndroidDevice()) &&
           myConnected == device.myConnected &&
           mySnapshots.equals(device.mySnapshots);
  }

  @Override
  public int hashCode() {
    return Objects.hash(getName(), isValid(), getKey(), getConnectionTime(), getAndroidDevice(), myConnected, mySnapshots);
  }
}
