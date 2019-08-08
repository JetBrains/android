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
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ThreeState;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class VirtualDevicesTask implements Callable<Collection<VirtualDevice>> {
  @Nullable
  private final LaunchCompatibilityChecker myChecker;

  VirtualDevicesTask(@Nullable LaunchCompatibilityChecker checker) {
    myChecker = checker;
  }

  @NotNull
  @Override
  public Collection<VirtualDevice> call() {
    return AvdManagerConnection.getDefaultAvdManagerConnection().getAvds(false).stream()
      .map(this::newDisconnectedDevice)
      .collect(Collectors.toList());
  }

  @NotNull
  private VirtualDevice newDisconnectedDevice(@NotNull AvdInfo avdInfo) {
    AndroidDevice device = new LaunchableAndroidDevice(avdInfo);

    VirtualDevice.Builder builder = new VirtualDevice.Builder()
      .setName(AvdManagerConnection.getAvdDisplayName(avdInfo))
      .setKey(avdInfo.getName())
      .setAndroidDevice(device)
      .setSnapshots(getSnapshots(avdInfo));

    if (myChecker == null) {
      return builder.build();
    }

    LaunchCompatibility compatibility = myChecker.validate(device);

    return builder
      .setValid(!compatibility.isCompatible().equals(ThreeState.NO))
      .setValidityReason(compatibility.getReason())
      .build();
  }

  @NotNull
  private static ImmutableCollection<Snapshot> getSnapshots(@NotNull AvdInfo avdInfo) {
    Path snapshots = Paths.get(avdInfo.getDataFolderPath(), "snapshots");

    if (!Files.isDirectory(snapshots)) {
      return ImmutableList.of();
    }

    try (Stream<Path> stream = Files.list(snapshots)) {
      @SuppressWarnings("UnstableApiUsage")
      Collector<Snapshot, ?, ImmutableList<Snapshot>> collector = ImmutableList.toImmutableList();

      return stream
        .filter(Files::isDirectory)
        .map(VirtualDevicesTask::getSnapshot)
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
  static Snapshot getSnapshot(@NotNull Path snapshotDirectory) {
    Path snapshotProtocolBuffer = snapshotDirectory.resolve("snapshot.pb");
    String snapshotDirectoryName = snapshotDirectory.getFileName().toString();

    if (!Files.exists(snapshotProtocolBuffer)) {
      return new Snapshot(snapshotDirectoryName);
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
  static Snapshot getSnapshot(@NotNull SnapshotOuterClass.Snapshot snapshot, @NotNull String fallbackName) {
    if (snapshot.getImagesCount() == 0) {
      return null;
    }

    String name = snapshot.getLogicalName();

    if (name.isEmpty()) {
      return new Snapshot(fallbackName);
    }

    return new Snapshot(name, fallbackName);
  }
}
