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

import com.android.emulator.SnapshotOuterClass;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.tools.idea.avdmanager.AvdManagerConnection;
import com.android.tools.idea.run.AndroidDevice;
import com.android.tools.idea.run.LaunchCompatibility;
import com.android.tools.idea.run.LaunchCompatibilityChecker;
import com.android.tools.idea.run.LaunchableAndroidDevice;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ThreeState;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class VirtualDevicesTask implements Callable<Collection<VirtualDevice>> {
  private final boolean mySelectDeviceSnapshotComboBoxSnapshotsEnabled;

  @NotNull
  private final FileSystem myFileSystem;

  @Nullable
  private final LaunchCompatibilityChecker myChecker;

  VirtualDevicesTask(boolean selectDeviceSnapshotComboBoxSnapshotsEnabled,
                     @NotNull FileSystem fileSystem,
                     @Nullable LaunchCompatibilityChecker checker) {
    mySelectDeviceSnapshotComboBoxSnapshotsEnabled = selectDeviceSnapshotComboBoxSnapshotsEnabled;
    myFileSystem = fileSystem;
    myChecker = checker;
  }

  @NotNull
  @Override
  public Collection<VirtualDevice> call() {
    Stream<AvdInfo> avds = AvdManagerConnection.getDefaultAvdManagerConnection().getAvds(false).stream();

    if (!mySelectDeviceSnapshotComboBoxSnapshotsEnabled) {
      return avds
        .map(avd -> newDisconnectedDevice(avd, null))
        .collect(Collectors.toList());
    }

    return avds
      .flatMap(this::newDisconnectedDevices)
      .collect(Collectors.toList());
  }

  @NotNull
  private Stream<VirtualDevice> newDisconnectedDevices(@NotNull AvdInfo avd) {
    Collection<Snapshot> snapshots = getSnapshots(avd);

    if (snapshots.isEmpty()) {
      return Stream.of(newDisconnectedDevice(avd, null));
    }

    return snapshots.stream().map(snapshot -> newDisconnectedDevice(avd, snapshot));
  }

  @NotNull
  private Collection<Snapshot> getSnapshots(@NotNull AvdInfo device) {
    Path snapshots = myFileSystem.getPath(device.getDataFolderPath(), "snapshots");

    if (!Files.isDirectory(snapshots)) {
      return ImmutableList.of();
    }

    try (Stream<Path> stream = Files.list(snapshots)) {
      @SuppressWarnings("UnstableApiUsage")
      Collector<Snapshot, ?, ImmutableList<Snapshot>> collector = ImmutableList.toImmutableList();

      return stream
        .filter(Files::isDirectory)
        .map(this::getSnapshot)
        .filter(Objects::nonNull)
        .sorted()
        .collect(collector);
    }
    catch (IOException exception) {
      Logger.getInstance(VirtualDevicesTask.class).warn(snapshots.toString(), exception);
      return ImmutableList.of();
    }
  }

  @Nullable
  @VisibleForTesting
  Snapshot getSnapshot(@NotNull Path snapshotDirectory) {
    Path snapshotProtocolBuffer = snapshotDirectory.resolve("snapshot.pb");
    Path snapshotDirectoryName = snapshotDirectory.getFileName();

    if (!Files.exists(snapshotProtocolBuffer)) {
      return new Snapshot(snapshotDirectoryName, myFileSystem);
    }

    try (InputStream in = Files.newInputStream(snapshotProtocolBuffer)) {
      return getSnapshot(SnapshotOuterClass.Snapshot.parseFrom(in), snapshotDirectoryName);
    }
    catch (IOException exception) {
      Logger.getInstance(VirtualDevicesTask.class).warn(snapshotDirectory.toString(), exception);
      return null;
    }
  }

  @Nullable
  @VisibleForTesting
  Snapshot getSnapshot(@NotNull SnapshotOuterClass.Snapshot snapshot, @NotNull Path snapshotDirectory) {
    if (snapshot.getImagesCount() == 0) {
      return null;
    }

    String name = snapshot.getLogicalName();

    if (name.isEmpty()) {
      return new Snapshot(snapshotDirectory, myFileSystem);
    }

    return new Snapshot(snapshotDirectory, name);
  }

  @NotNull
  private VirtualDevice newDisconnectedDevice(@NotNull AvdInfo avd, @Nullable Snapshot snapshot) {
    AndroidDevice device = new LaunchableAndroidDevice(avd);

    VirtualDevice.Builder builder = new VirtualDevice.Builder()
      .setName(AvdManagerConnection.getAvdDisplayName(avd))
      .setKey(new Key(avd.getName(), snapshot))
      .setAndroidDevice(device)
      .setSnapshot(snapshot);

    if (myChecker == null) {
      return builder.build();
    }

    LaunchCompatibility compatibility = myChecker.validate(device);

    return builder
      .setValid(!compatibility.isCompatible().equals(ThreeState.NO))
      .setValidityReason(compatibility.getReason())
      .build();
  }
}
