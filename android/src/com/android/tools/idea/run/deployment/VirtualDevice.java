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

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.run.AndroidDevice;
import com.android.tools.idea.run.LaunchCompatibility;
import com.android.tools.idea.run.LaunchableAndroidDevice;
import com.google.common.annotations.VisibleForTesting;
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

  private VirtualDevice(@NotNull Builder builder) {
    super(builder);

    myNameKey = builder.myNameKey;
    mySnapshots = new ArrayList<>(builder.mySnapshots);
    mySelectDeviceSnapshotComboBoxSnapshotsEnabled = builder.mySelectDeviceSnapshotComboBoxSnapshotsEnabled;
  }

  @NotNull
  static VirtualDevice newConnectedDevice(@NotNull ConnectedDevice connectedDevice,
                                          @NotNull KeyToConnectionTimeMap map,
                                          @Nullable VirtualDevice virtualDevice) {
    VirtualDeviceName nameKey;
    Device device;

    if (virtualDevice == null) {
      nameKey = null;
      device = connectedDevice;
    }
    else {
      nameKey = virtualDevice.myNameKey;
      device = virtualDevice;
    }

    Key key = device.getKey();

    return new Builder()
      .setKey(key)
      .setNameKey(nameKey)
      .setType(device.getType())
      .setLaunchCompatibility(connectedDevice.getLaunchCompatibility())
      .setConnectionTime(map.get(key))
      .setName(device.getName())
      .addAllSnapshots(device.getSnapshots())
      .setAndroidDevice(connectedDevice.getAndroidDevice())
      .build();
  }

  static final class Builder extends Device.Builder {
    private @Nullable VirtualDeviceName myNameKey;
    private final @NotNull Collection<Snapshot> mySnapshots = new ArrayList<>();
    private boolean mySelectDeviceSnapshotComboBoxSnapshotsEnabled = StudioFlags.SELECT_DEVICE_SNAPSHOT_COMBO_BOX_SNAPSHOTS_ENABLED.get();

    @NotNull
    Builder setKey(@NotNull Key key) {
      myKey = key;
      return this;
    }

    @NotNull
    Builder setNameKey(@Nullable VirtualDeviceName nameKey) {
      myNameKey = nameKey;
      return this;
    }

    @NotNull
    Builder setType(@NotNull Type type) {
      myType = type;
      return this;
    }

    @NotNull
    Builder setLaunchCompatibility(@NotNull LaunchCompatibility launchCompatibility) {
      myLaunchCompatibility = launchCompatibility;
      return this;
    }

    @NotNull
    @VisibleForTesting
    Builder setConnectionTime(@NotNull Instant connectionTime) {
      myConnectionTime = connectionTime;
      return this;
    }

    @NotNull
    Builder setName(@NotNull String name) {
      myName = name;
      return this;
    }

    @NotNull
    @VisibleForTesting
    Builder addSnapshot(@NotNull Snapshot snapshot) {
      mySnapshots.add(snapshot);
      return this;
    }

    @NotNull
    Builder addAllSnapshots(@NotNull Collection<Snapshot> snapshots) {
      mySnapshots.addAll(snapshots);
      return this;
    }

    @NotNull
    Builder setAndroidDevice(@NotNull AndroidDevice androidDevice) {
      myAndroidDevice = androidDevice;
      return this;
    }

    @NotNull
    @VisibleForTesting
    Builder setSelectDeviceSnapshotComboBoxSnapshotsEnabled(boolean selectDeviceSnapshotComboBoxSnapshotsEnabled) {
      mySelectDeviceSnapshotComboBoxSnapshotsEnabled = selectDeviceSnapshotComboBoxSnapshotsEnabled;
      return this;
    }

    @NotNull
    @Override
    VirtualDevice build() {
      return new VirtualDevice(this);
    }
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
  Optional<Object> getNameKey() {
    return Optional.ofNullable(myNameKey);
  }

  @NotNull
  @Override
  Icon getIcon() {
    var icon = switch (getType()) {
      case PHONE -> ourPhoneIcon;
      case WEAR -> ourWearIcon;
      case TV -> ourTvIcon;
    };

    if (isConnected()) {
      icon = ExecutionUtil.getLiveIndicator(icon);
    }

    return switch (getLaunchCompatibility().getState()) {
      case OK -> icon;
      case WARNING -> new LayeredIcon(icon, AllIcons.General.WarningDecorator);
      case ERROR -> new LayeredIcon(icon, StudioIcons.Common.ERROR_DECORATOR);
    };
  }

  @Override
  boolean isConnected() {
    return getConnectionTime() != null;
  }

  @NotNull
  @Override
  Collection<Snapshot> getSnapshots() {
    return mySnapshots;
  }

  @NotNull
  @Override
  Target getDefaultTarget() {
    if (!mySelectDeviceSnapshotComboBoxSnapshotsEnabled) {
      return new QuickBootTarget(getKey());
    }

    if (isConnected()) {
      return new RunningDeviceTarget(getKey());
    }

    return new QuickBootTarget(getKey());
  }

  @NotNull
  @Override
  Collection<Target> getTargets() {
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

  @Override
  public int hashCode() {
    return Objects.hash(getKey(),
                        myNameKey,
                        getType(),
                        getLaunchCompatibility(),
                        getConnectionTime(),
                        getName(),
                        mySnapshots,
                        getAndroidDevice(),
                        mySelectDeviceSnapshotComboBoxSnapshotsEnabled);
  }

  @Override
  public boolean equals(@Nullable Object object) {
    if (!(object instanceof VirtualDevice device)) {
      return false;
    }

    return getKey().equals(device.getKey()) &&
           Objects.equals(myNameKey, device.myNameKey) &&
           getType().equals(device.getType()) &&
           getLaunchCompatibility().equals(device.getLaunchCompatibility()) &&
           Objects.equals(getConnectionTime(), device.getConnectionTime()) &&
           getName().equals(device.getName()) &&
           mySnapshots.equals(device.mySnapshots) &&
           getAndroidDevice().equals(device.getAndroidDevice()) &&
           mySelectDeviceSnapshotComboBoxSnapshotsEnabled == device.mySelectDeviceSnapshotComboBoxSnapshotsEnabled;
  }
}
