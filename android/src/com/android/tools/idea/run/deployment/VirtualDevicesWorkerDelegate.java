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

import com.android.emulator.SnapshotProtoException;
import com.android.emulator.SnapshotProtoParser;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.tools.idea.avdmanager.AvdManagerConnection;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

final class VirtualDevicesWorkerDelegate extends SwingWorker<Map<VirtualDevice, AvdInfo>, Void> {
  @NotNull
  @Override
  protected Map<VirtualDevice, AvdInfo> doInBackground() {
    return AvdManagerConnection.getDefaultAvdManagerConnection().getAvds(true).stream()
                               .collect(Collectors.toMap(VirtualDevicesWorkerDelegate::newVirtualDevice, Function.identity()));
  }

  @NotNull
  private static VirtualDevice newVirtualDevice(@NotNull AvdInfo device) {
    return new VirtualDevice(false, AvdManagerConnection.getAvdDisplayName(device), getSnapshots(device));
  }

  @NotNull
  private static ImmutableCollection<String> getSnapshots(@NotNull AvdInfo device) {
    Path snapshots = Paths.get(device.getDataFolderPath(), "snapshots");

    if (!Files.isDirectory(snapshots)) {
      return ImmutableList.of();
    }

    try {
      return Files.list(snapshots)
                  .filter(Files::isDirectory)
                  .map(VirtualDevicesWorkerDelegate::getName)
                  .filter(Objects::nonNull)
                  .collect(ImmutableList.toImmutableList());
    }
    catch (IOException exception) {
      Logger.getInstance(VirtualDevicesWorkerDelegate.class).warn(exception);
      return ImmutableList.of();
    }
  }

  @Nullable
  private static String getName(@NotNull Path snapshot) {
    try {
      return new SnapshotProtoParser(snapshot.resolve("snapshot.pb").toFile(), snapshot.getFileName().toString()).getLogicalName();
    }
    catch (SnapshotProtoException exception) {
      Logger.getInstance(VirtualDevicesWorkerDelegate.class).warn(exception);
      return null;
    }
  }
}
