/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.run.deployment.legacyselector;

import com.intellij.openapi.project.Project;
import java.nio.file.Path;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class BootWithSnapshotTarget extends Target {
  private final @NotNull Path mySnapshotKey;

  BootWithSnapshotTarget(@NotNull Key deviceKey, @NotNull Path snapshotKey) {
    super(deviceKey);
    mySnapshotKey = snapshotKey;
  }

  @NotNull Path getSnapshotKey() {
    return mySnapshotKey;
  }

  @Override
  boolean matches(@NotNull Device device) {
    if (!device.getKey().equals(getDeviceKey())) {
      return false;
    }

    return device.getSnapshots().stream()
      .map(Snapshot::getDirectory)
      .anyMatch(snapshotKey -> snapshotKey.equals(mySnapshotKey));
  }

  @Override
  @NotNull String getText(@NotNull Device device) {
    Optional<String> text = device.getSnapshots().stream()
      .filter(snapshot -> snapshot.getDirectory().equals(mySnapshotKey))
      .map(Snapshot::toString)
      .findFirst();

    return text.orElseThrow(AssertionError::new);
  }

  @Override
  void boot(@NotNull VirtualDevice device, @NotNull Project project) {
    device.bootWithSnapshot(project, mySnapshotKey.getFileName());
  }

  @Override
  public int hashCode() {
    return 31 * getDeviceKey().hashCode() + mySnapshotKey.hashCode();
  }

  @Override
  public boolean equals(@Nullable Object object) {
    if (!(object instanceof BootWithSnapshotTarget)) {
      return false;
    }

    BootWithSnapshotTarget target = (BootWithSnapshotTarget)object;
    return getDeviceKey().equals(target.getDeviceKey()) && mySnapshotKey.equals(target.mySnapshotKey);
  }
}
