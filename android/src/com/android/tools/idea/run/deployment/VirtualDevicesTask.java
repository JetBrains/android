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

import com.android.emulator.snapshot.SnapshotOuterClass;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.tools.idea.run.AndroidDevice;
import com.android.tools.idea.run.LaunchCompatibility;
import com.android.tools.idea.run.LaunchCompatibilityChecker;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ThreeState;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class VirtualDevicesTask implements AsyncSupplier<Collection<VirtualDevice>> {
  private final @NotNull ExecutorService myExecutorService;
  private final @NotNull Supplier<@NotNull Collection<@NotNull AvdInfo>> myGetAvds;

  @NotNull
  private final FileSystem myFileSystem;

  private final @NotNull Function<@NotNull AvdInfo, @NotNull AndroidDevice> myNewLaunchableAndroidDevice;

  @Nullable
  private final LaunchCompatibilityChecker myChecker;

  static final class Builder {
    private @Nullable ExecutorService myExecutorService;
    private @Nullable Supplier<@NotNull Collection<@NotNull AvdInfo>> myGetAvds;
    private @Nullable FileSystem myFileSystem;
    private @Nullable Function<@NotNull AvdInfo, @NotNull AndroidDevice> myNewLaunchableAndroidDevice;
    private @Nullable LaunchCompatibilityChecker myChecker;

    @NotNull Builder setExecutorService(@NotNull ExecutorService executorService) {
      myExecutorService = executorService;
      return this;
    }

    @NotNull Builder setGetAvds(@NotNull Supplier<@NotNull Collection<@NotNull AvdInfo>> getAvds) {
      myGetAvds = getAvds;
      return this;
    }

    @NotNull Builder setFileSystem(@NotNull FileSystem fileSystem) {
      myFileSystem = fileSystem;
      return this;
    }

    @NotNull Builder setNewLaunchableAndroidDevice(@NotNull Function<@NotNull AvdInfo, @NotNull AndroidDevice> newLaunchableAndroidDevice) {
      myNewLaunchableAndroidDevice = newLaunchableAndroidDevice;
      return this;
    }

    @NotNull Builder setChecker(@Nullable LaunchCompatibilityChecker checker) {
      myChecker = checker;
      return this;
    }

    @NotNull VirtualDevicesTask build() {
      return new VirtualDevicesTask(this);
    }
  }

  private VirtualDevicesTask(@NotNull Builder builder) {
    assert builder.myExecutorService != null;
    myExecutorService = builder.myExecutorService;

    assert builder.myGetAvds != null;
    myGetAvds = builder.myGetAvds;

    assert builder.myFileSystem != null;
    myFileSystem = builder.myFileSystem;

    assert builder.myNewLaunchableAndroidDevice != null;
    myNewLaunchableAndroidDevice = builder.myNewLaunchableAndroidDevice;

    myChecker = builder.myChecker;
  }

  @NotNull
  @Override
  public ListenableFuture<Collection<VirtualDevice>> get() {
    return MoreExecutors.listeningDecorator(myExecutorService).submit(this::getVirtualDevices);
  }

  @NotNull
  private Collection<VirtualDevice> getVirtualDevices() {
    return myGetAvds.get().stream()
      .map(avd -> newDisconnectedDevice(avd, getSnapshots(avd)))
      .collect(Collectors.toList());
  }

  @NotNull
  private Collection<Snapshot> getSnapshots(@NotNull AvdInfo device) {
    Path snapshots = myFileSystem.getPath(device.getDataFolderPath(), "snapshots");

    if (!Files.isDirectory(snapshots)) {
      return ImmutableList.of();
    }

    try (Stream<Path> stream = Files.list(snapshots)) {
      Object defaultBoot = myFileSystem.getPath("default_boot");

      return stream
        .filter(Files::isDirectory)
        .filter(directory -> !directory.getFileName().equals(defaultBoot))
        .map(this::getSnapshot)
        .filter(Objects::nonNull)
        .sorted()
        .collect(ImmutableList.toImmutableList());
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

    if (!Files.exists(snapshotProtocolBuffer)) {
      return new Snapshot(snapshotDirectory);
    }

    try (InputStream in = Files.newInputStream(snapshotProtocolBuffer)) {
      return getSnapshot(SnapshotOuterClass.Snapshot.parseFrom(in), snapshotDirectory);
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
      return new Snapshot(snapshotDirectory);
    }

    return new Snapshot(snapshotDirectory, name);
  }

  private @NotNull VirtualDevice newDisconnectedDevice(@NotNull AvdInfo avd, @NotNull Collection<@NotNull Snapshot> snapshots) {
    AndroidDevice device = myNewLaunchableAndroidDevice.apply(avd);

    VirtualDevice.Builder builder = new VirtualDevice.Builder()
      .setName(avd.getDisplayName())
      .setKey(new VirtualDevicePath(avd.getDataFolderPath()))
      .setAndroidDevice(device)
      .setNameKey(new VirtualDeviceName(avd.getName()))
      .addAllSnapshots(snapshots);

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
