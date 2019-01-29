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
import com.android.emulator.SnapshotOuterClass.Snapshot;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.tools.idea.avdmanager.AvdManagerConnection;
import com.android.tools.idea.run.AndroidDevice;
import com.android.tools.idea.run.ConnectedAndroidDevice;
import com.android.tools.idea.run.DeviceFutures;
import com.android.tools.idea.run.LaunchCompatibilityChecker;
import com.android.tools.idea.run.LaunchableAndroidDevice;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import icons.AndroidIcons;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Objects;
import java.util.stream.Stream;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class VirtualDevice extends Device {
  static final String DEFAULT_SNAPSHOT = "default_boot";
  static final ImmutableCollection<String> DEFAULT_SNAPSHOT_COLLECTION = ImmutableList.of(DEFAULT_SNAPSHOT);

  private static final Icon ourConnectedIcon = ExecutionUtil.getLiveIndicator(AndroidIcons.Ddms.EmulatorDevice);

  private final boolean myConnected;

  /**
   * Snapshot directory names displayed to the developer.
   */
  @NotNull
  private final ImmutableCollection<String> mySnapshots;

  @NotNull
  static Builder newDisconnectedDeviceBuilder(@NotNull AvdInfo avdInfo) {
    return new Builder()
      .setName(AvdManagerConnection.getAvdDisplayName(avdInfo))
      .setKey(avdInfo.getName())
      .setAndroidDevice(new LaunchableAndroidDevice(avdInfo))
      .setSnapshots(getSnapshots(avdInfo));
  }

  @NotNull
  private static ImmutableCollection<String> getSnapshots(@NotNull AvdInfo avdInfo) {
    Path snapshots = Paths.get(avdInfo.getDataFolderPath(), "snapshots");

    if (!Files.isDirectory(snapshots)) {
      return ImmutableList.of();
    }

    try (Stream<Path> stream = Files.list(snapshots)) {
      return stream
        .filter(Files::isDirectory)
        .map(VirtualDevice::getName)
        .filter(Objects::nonNull)
        .sorted()
        .collect(ImmutableList.toImmutableList());
    }
    catch (IOException exception) {
      Logger.getInstance(VirtualDevice.class).warn(snapshots.toString(), exception);
      return ImmutableList.of();
    }
  }

  @Nullable
  @VisibleForTesting
  static String getName(@NotNull Path snapshotDirectory) {
    Path snapshotProtocolBuffer = snapshotDirectory.resolve("snapshot.pb");
    String snapshotDirectoryName = snapshotDirectory.getFileName().toString();

    if (!Files.exists(snapshotProtocolBuffer)) {
      return snapshotDirectoryName;
    }

    try (InputStream in = Files.newInputStream(snapshotProtocolBuffer)) {
      return getName(Snapshot.parseFrom(in), snapshotDirectoryName);
    }
    catch (IOException exception) {
      Logger.getInstance(VirtualDevice.class).warn(snapshotDirectory.toString(), exception);
      return null;
    }
  }

  @Nullable
  @VisibleForTesting
  static String getName(@NotNull Snapshot snapshot, @NotNull String fallbackName) {
    if (snapshot.getImagesCount() == 0) {
      return null;
    }

    String name = snapshot.getLogicalName();

    if (name.isEmpty()) {
      return fallbackName;
    }

    return name;
  }

  @NotNull
  static Builder newConnectedDeviceBuilder(@NotNull VirtualDevice virtualDevice, @NotNull IDevice ddmlibDevice) {
    AvdInfo avdInfo = ((LaunchableAndroidDevice)virtualDevice.getAndroidDevice()).getAvdInfo();

    return new Builder()
      .setName(virtualDevice.getName())
      .setKey(virtualDevice.getKey())
      .setAndroidDevice(new ConnectedAndroidDevice(ddmlibDevice, Collections.singletonList(avdInfo)))
      .setConnected(true)
      .setSnapshots(virtualDevice.mySnapshots);
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
    VirtualDevice build(@Nullable LaunchCompatibilityChecker checker, @NotNull KeyToConnectionTimeMap map) {
      return new VirtualDevice(this, checker, map);
    }
  }

  private VirtualDevice(@NotNull Builder builder, @Nullable LaunchCompatibilityChecker checker, @NotNull KeyToConnectionTimeMap map) {
    super(builder, checker, map);

    myConnected = builder.myConnected;
    mySnapshots = builder.mySnapshots;
  }

  @NotNull
  @Override
  Icon getIcon() {
    return myConnected ? ourConnectedIcon : AndroidIcons.Ddms.EmulatorDevice;
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
  AndroidVersion getAndroidVersion() {
    Object androidDevice = getAndroidDevice();

    if (androidDevice instanceof LaunchableAndroidDevice) {
      return ((LaunchableAndroidDevice)androidDevice).getAvdInfo().getAndroidVersion();
    }

    IDevice ddmlibDevice = getDdmlibDevice();
    assert ddmlibDevice != null;

    return ddmlibDevice.getVersion();
  }

  @NotNull
  @Override
  DeviceFutures newDeviceFutures(@NotNull Project project, @Nullable String snapshot) {
    AndroidDevice device = getAndroidDevice();

    if (!myConnected) {
      device.launch(project, snapshot);
    }

    return new DeviceFutures(Collections.singletonList(device));
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
