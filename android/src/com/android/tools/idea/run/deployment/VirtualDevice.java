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
import com.android.emulator.SnapshotProtoException;
import com.android.emulator.SnapshotProtoParser;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.tools.idea.avdmanager.AvdManagerConnection;
import com.android.tools.idea.run.AndroidDevice;
import com.android.tools.idea.run.ConnectedAndroidDevice;
import com.android.tools.idea.run.DeviceFutures;
import com.android.tools.idea.run.LaunchableAndroidDevice;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import icons.AndroidIcons;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Objects;
import java.util.stream.Collector;
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
  private ImmutableCollection<String> mySnapshots;

  private final AvdInfo myAvdInfo;

  VirtualDevice(@NotNull AvdInfo avdInfo) {
    super(AvdManagerConnection.getAvdDisplayName(avdInfo), null);

    myConnected = false;
    myAvdInfo = avdInfo;

    initSnapshots();
  }

  private void initSnapshots() {
    Path snapshots = Paths.get(myAvdInfo.getDataFolderPath(), "snapshots");

    if (!Files.isDirectory(snapshots)) {
      mySnapshots = ImmutableList.of();
      return;
    }

    try {
      @SuppressWarnings("UnstableApiUsage")
      Collector<String, ?, ImmutableList<String>> collector = ImmutableList.toImmutableList();

      mySnapshots = Files.list(snapshots)
        .filter(Files::isDirectory)
        .map(VirtualDevice::getName)
        .filter(Objects::nonNull)
        .sorted()
        .collect(collector);
    }
    catch (IOException exception) {
      Logger.getInstance(VirtualDevice.class).warn(snapshots.toString(), exception);
      mySnapshots = ImmutableList.of();
    }
  }

  @Nullable
  private static String getName(@NotNull Path snapshot) {
    try {
      return new SnapshotProtoParser(snapshot.resolve("snapshot.pb").toFile(), snapshot.getFileName().toString()).getLogicalName();
    }
    catch (SnapshotProtoException exception) {
      Logger.getInstance(VirtualDevice.class).warn(snapshot.toString(), exception);
      return null;
    }
  }

  VirtualDevice(@NotNull VirtualDevice virtualDevice, @NotNull IDevice ddmlibDevice) {
    super(virtualDevice.getName(), ddmlibDevice);

    myConnected = true;
    mySnapshots = virtualDevice.mySnapshots;
    myAvdInfo = virtualDevice.myAvdInfo;
  }

  @VisibleForTesting
  VirtualDevice(boolean connected, @NotNull String name) {
    this(connected, name, ImmutableList.of());
  }

  @VisibleForTesting
  VirtualDevice(boolean connected, @NotNull String name, @NotNull ImmutableCollection<String> snapshots) {
    super(name, null);

    myConnected = connected;
    mySnapshots = snapshots;
    myAvdInfo = null;
  }

  boolean isConnected() {
    return myConnected;
  }

  @NotNull
  @Override
  Icon getIcon() {
    return myConnected ? ourConnectedIcon : AndroidIcons.Ddms.EmulatorDevice;
  }

  @NotNull
  @Override
  ImmutableCollection<String> getSnapshots() {
    return mySnapshots;
  }

  @Nullable
  AvdInfo getAvdInfo() {
    return myAvdInfo;
  }

  @NotNull
  @Override
  DeviceFutures newDeviceFutures(@NotNull Project project, @Nullable String snapshot) {
    AndroidDevice androidDevice;

    if (myConnected) {
      IDevice ddmlibDevice = getDdmlibDevice();
      assert ddmlibDevice != null;

      androidDevice = new ConnectedAndroidDevice(ddmlibDevice, Collections.singletonList(myAvdInfo));
    }
    else {
      androidDevice = new LaunchableAndroidDevice(myAvdInfo);
      androidDevice.launch(project, snapshot);
    }

    return new DeviceFutures(Collections.singletonList(androidDevice));
  }

  @Override
  public boolean equals(@Nullable Object object) {
    if (!(object instanceof VirtualDevice)) {
      return false;
    }

    VirtualDevice device = (VirtualDevice)object;

    return myConnected == device.myConnected &&
           getName().equals(device.getName()) &&
           mySnapshots.equals(device.mySnapshots) &&
           Objects.equals(myAvdInfo, device.myAvdInfo) &&
           Objects.equals(getDdmlibDevice(), device.getDdmlibDevice());
  }

  @Override
  public int hashCode() {
    int hashCode = Boolean.hashCode(myConnected);

    hashCode = 31 * hashCode + getName().hashCode();
    hashCode = 31 * hashCode + mySnapshots.hashCode();
    hashCode = 31 * hashCode + Objects.hashCode(myAvdInfo);
    hashCode = 31 * hashCode + Objects.hashCode(getDdmlibDevice());

    return hashCode;
  }
}
