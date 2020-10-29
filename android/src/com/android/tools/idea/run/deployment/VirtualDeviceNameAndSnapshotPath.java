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
package com.android.tools.idea.run.deployment;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class VirtualDeviceNameAndSnapshotPath extends Key {
  static final String PREFIX = "VirtualDeviceNameAndSnapshotPath@";

  private final @NotNull String myVirtualDeviceName;
  private final @NotNull String mySnapshotPath;

  VirtualDeviceNameAndSnapshotPath(@NotNull String virtualDeviceName, @NotNull String snapshotPath) {
    myVirtualDeviceName = virtualDeviceName;
    mySnapshotPath = snapshotPath;
  }

  static @NotNull VirtualDeviceNameAndSnapshotPath parse(@NotNull String string) {
    int index = string.indexOf(':');
    return new VirtualDeviceNameAndSnapshotPath(string.substring(PREFIX.length(), index), string.substring(index + 1));
  }

  @Override
  @NotNull NonprefixedKey asNonprefixedKey() {
    return new NonprefixedKey(myVirtualDeviceName + ':' + mySnapshotPath);
  }

  @Override
  @NotNull String getDeviceKey() {
    return myVirtualDeviceName;
  }

  @Override
  public int hashCode() {
    return 31 * myVirtualDeviceName.hashCode() + mySnapshotPath.hashCode();
  }

  @Override
  public boolean equals(@Nullable Object object) {
    if (!(object instanceof VirtualDeviceNameAndSnapshotPath)) {
      return false;
    }

    VirtualDeviceNameAndSnapshotPath deviceNameAndSnapshotPath = (VirtualDeviceNameAndSnapshotPath)object;

    return myVirtualDeviceName.equals(deviceNameAndSnapshotPath.myVirtualDeviceName) &&
           mySnapshotPath.equals(deviceNameAndSnapshotPath.mySnapshotPath);
  }

  @Override
  public @NotNull String toString() {
    return PREFIX + myVirtualDeviceName + ':' + mySnapshotPath;
  }
}
