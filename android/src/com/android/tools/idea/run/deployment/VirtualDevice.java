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

import static icons.StudioIcons.Common.ERROR_DECORATOR;

import com.android.ddmlib.IDevice;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.run.AndroidDevice;
import com.android.tools.idea.run.DeploymentApplicationService;
import com.android.tools.idea.run.LaunchCompatibility;
import com.android.tools.idea.run.LaunchableAndroidDevice;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.Futures;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.ui.LayeredIcon;
import icons.StudioIcons;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Future;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A virtual device. If it's in the Android Virtual Device Manager Device.myKey is a VirtualDevicePath and myNameKey is not null. If not,
 * Device.myKey may be a VirtualDevicePath, VirtualDeviceName, or SerialNumber depending on what the IDevice returns and myNameKey is null.
 */
final class VirtualDevice extends Device {
  private static final Icon ourPhoneIcon = StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_PHONE;
  private static final Icon ourWearIcon = StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_WEAR;
  private static final Icon ourTvIcon = StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_TV;

  /**
   * The virtual device names match with ConnectedDevices that don't support the avd path emulator console subcommand added to the emulator
   * in Version 30.0.18
   */
  private final @Nullable VirtualDeviceName myNameKey;

  private final @NotNull Collection<Snapshot> mySnapshots;
  private final boolean mySelectDeviceSnapshotComboBoxSnapshotsEnabled;

  @NotNull
  static VirtualDevice newConnectedDevice(@NotNull ConnectedDevice connectedDevice,
                                          @NotNull KeyToConnectionTimeMap map,
                                          @Nullable VirtualDevice virtualDevice) {
    Device device;
    VirtualDeviceName nameKey;

    if (virtualDevice == null) {
      device = connectedDevice;
      nameKey = null;
    }
    else {
      device = virtualDevice;
      nameKey = virtualDevice.myNameKey;
    }

    Key key = device.getKey();

    return new Builder()
      .setName(device.getName())
      .setLaunchCompatibility(connectedDevice.getLaunchCompatibility())
      .setKey(key)
      .setConnectionTime(map.get(key))
      .setAndroidDevice(connectedDevice.getAndroidDevice())
      .setNameKey(nameKey)
      .addAllSnapshots(device.getSnapshots())
      .setType(device.getType())
      .build();
  }

  static final class Builder extends Device.Builder {
    private @Nullable VirtualDeviceName myNameKey;
    private final @NotNull Collection<Snapshot> mySnapshots = new ArrayList<>();
    private boolean mySelectDeviceSnapshotComboBoxSnapshotsEnabled = StudioFlags.SELECT_DEVICE_SNAPSHOT_COMBO_BOX_SNAPSHOTS_ENABLED.get();

    @NotNull
    Builder setName(@NotNull String name) {
      myName = name;
      return this;
    }

    @NotNull
    Builder setLaunchCompatibility(LaunchCompatibility launchCompatibility) {
      myLaunchCompatibility = launchCompatibility;
      return this;
    }

    @NotNull
    Builder setKey(@NotNull Key key) {
      myKey = key;
      return this;
    }

    @NotNull
    @VisibleForTesting
    Builder setConnectionTime(@NotNull Instant connectionTime) {
      myConnectionTime = connectionTime;
      return this;
    }

    @NotNull
    Builder setAndroidDevice(@NotNull AndroidDevice androidDevice) {
      myAndroidDevice = androidDevice;
      return this;
    }

    @NotNull Builder setNameKey(@Nullable VirtualDeviceName nameKey) {
      myNameKey = nameKey;
      return this;
    }

    @NotNull Builder addSnapshot(@NotNull Snapshot snapshot) {
      mySnapshots.add(snapshot);
      return this;
    }

    @NotNull Builder addAllSnapshots(@NotNull Collection<Snapshot> snapshots) {
      mySnapshots.addAll(snapshots);
      return this;
    }

    @NotNull Builder setSelectDeviceSnapshotComboBoxSnapshotsEnabled(@SuppressWarnings("SameParameterValue") boolean selectDeviceSnapshotComboBoxSnapshotsEnabled) {
      mySelectDeviceSnapshotComboBoxSnapshotsEnabled = selectDeviceSnapshotComboBoxSnapshotsEnabled;
      return this;
    }

    @NotNull Builder setType(@NotNull Type type) {
      myType = type;
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

    myNameKey = builder.myNameKey;
    mySnapshots = new ArrayList<>(builder.mySnapshots);
    mySelectDeviceSnapshotComboBoxSnapshotsEnabled = builder.mySelectDeviceSnapshotComboBoxSnapshotsEnabled;
  }

  @NotNull Optional<VirtualDeviceName> getNameKey() {
    return Optional.ofNullable(myNameKey);
  }

  void coldBoot(@NotNull Project project) {
    ((LaunchableAndroidDevice)getAndroidDevice()).coldBoot(project);
  }

  void quickBoot(@NotNull Project project) {
    ((LaunchableAndroidDevice)getAndroidDevice()).quickBoot(project);
  }

  void bootWithSnapshot(@NotNull Project project, @NotNull Path snapshot) {
    ((LaunchableAndroidDevice)getAndroidDevice()).bootWithSnapshot(project, snapshot.toString());
  }

  @NotNull
  @Override
  Icon getIcon() {
    Icon deviceIcon;
    switch (getType()) {
      case TV:
        deviceIcon = ourTvIcon;
        break;
      case WEAR:
        deviceIcon = ourWearIcon;
        break;
      case PHONE:
        deviceIcon = ourPhoneIcon;
        break;
      default:
        throw new IllegalStateException("Unexpected device type: " + getType());
    }

    if (isConnected()) {
      deviceIcon = ExecutionUtil.getLiveIndicator(deviceIcon);
    }

    switch (getLaunchCompatibility().getState()) {
      case ERROR:
        return new LayeredIcon(deviceIcon, ERROR_DECORATOR);
      case WARNING:
        return new LayeredIcon(deviceIcon, AllIcons.General.WarningDecorator);
      case OK:
        return deviceIcon;
      default:
        throw new IllegalStateException("Unexpected device state: " + getLaunchCompatibility().getState());
    }
  }

  @Override
  boolean isConnected() {
    return getConnectionTime() != null;
  }

  @Override
  @NotNull Collection<Snapshot> getSnapshots() {
    return mySnapshots;
  }

  @Override
  @NotNull Target getDefaultTarget() {
    if (!mySelectDeviceSnapshotComboBoxSnapshotsEnabled) {
      return new QuickBootTarget(getKey());
    }

    if (isConnected()) {
      return new RunningDeviceTarget(getKey());
    }

    return new QuickBootTarget(getKey());
  }

  @Override
  @NotNull Collection<Target> getTargets() {
    if (!mySelectDeviceSnapshotComboBoxSnapshotsEnabled) {
      return Collections.singletonList(new QuickBootTarget(getKey()));
    }

    if (isConnected()) {
      return Collections.singletonList(new RunningDeviceTarget(getKey()));
    }

    if (mySnapshots.isEmpty()) {
      return Collections.singletonList(new QuickBootTarget(getKey()));
    }

    Collection<Target> targets = new ArrayList<>(2 + mySnapshots.size());
    Key deviceKey = getKey();

    targets.add(new ColdBootTarget(deviceKey));
    targets.add(new QuickBootTarget(deviceKey));

    mySnapshots.stream()
      .map(Snapshot::getDirectory)
      .map(snapshotKey -> new BootWithSnapshotTarget(deviceKey, snapshotKey))
      .forEach(targets::add);

    return targets;
  }

  @NotNull
  @Override
  Future<AndroidVersion> getAndroidVersion() {
    Object androidDevice = getAndroidDevice();

    if (androidDevice instanceof LaunchableAndroidDevice) {
      return Futures.immediateFuture(((LaunchableAndroidDevice)androidDevice).getAvdInfo().getAndroidVersion());
    }

    IDevice ddmlibDevice = getDdmlibDevice();
    assert ddmlibDevice != null;

    return DeploymentApplicationService.getInstance().getVersion(ddmlibDevice);
  }

  @Override
  public boolean equals(@Nullable Object object) {
    if (!(object instanceof VirtualDevice)) {
      return false;
    }

    VirtualDevice device = (VirtualDevice)object;

    return getName().equals(device.getName()) &&
           getType().equals(device.getType()) &&
           getLaunchCompatibility().equals(device.getLaunchCompatibility()) &&
           getKey().equals(device.getKey()) &&
           Objects.equals(getConnectionTime(), device.getConnectionTime()) &&
           getAndroidDevice().equals(device.getAndroidDevice()) &&
           Objects.equals(myNameKey, device.myNameKey) &&
           mySnapshots.equals(device.mySnapshots) &&
           mySelectDeviceSnapshotComboBoxSnapshotsEnabled == device.mySelectDeviceSnapshotComboBoxSnapshotsEnabled;
  }

  @Override
  public int hashCode() {
    return Objects.hash(getName(),
                        getType(),
                        getLaunchCompatibility(),
                        getKey(),
                        getConnectionTime(),
                        getAndroidDevice(),
                        myNameKey,
                        mySnapshots,
                        mySelectDeviceSnapshotComboBoxSnapshotsEnabled);
  }
}
