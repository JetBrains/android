/*
 * Copyright (C) 2023 The Android Open Source Project
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

import java.nio.file.Path;
import org.jetbrains.annotations.NotNull;

final class Keys {
  static final Key PIXEL_2_API_Q = new VirtualDevicePath(virtualDevicePathOf("Pixel_2_API_Q"));
  static final Key PIXEL_3_API_Q = new VirtualDevicePath(virtualDevicePathOf("Pixel_3_API_Q"));
  static final Key PIXEL_3_API_29 = new VirtualDevicePath(virtualDevicePathOf("Pixel_3_API_29"));

  @NotNull
  static final Path PIXEL_3_API_29_SNAPSHOT_1 = snapshotPathOf("Pixel_3_API_29", "snap_2018-08-07_16-27-58");

  static final Key PIXEL_3_API_30 = new VirtualDevicePath(virtualDevicePathOf("Pixel_3_API_30"));

  @NotNull
  static final Path PIXEL_3_API_30_SNAPSHOT_1 = snapshotPathOf("Pixel_3_API_30", "snap_2020-12-17_12-26-30");

  static final Key PIXEL_4_API_29 = new VirtualDevicePath(virtualDevicePathOf("Pixel_4_API_29"));
  static final Key PIXEL_4_API_30 = new VirtualDevicePath(virtualDevicePathOf("Pixel_4_API_30"));

  @NotNull
  static final Path PIXEL_4_API_30_SNAPSHOT_1 = snapshotPathOf("Pixel_4_API_30", "snap_2020-12-07_16-36-58");

  @NotNull
  static final Path PIXEL_4_API_30_SNAPSHOT_2 = snapshotPathOf("Pixel_4_API_30", "snap_2020-12-17_12-26-30");

  @NotNull
  private static Path virtualDevicePathOf(@NotNull String deviceName) {
    return Path.of(System.getProperty("user.home"), ".android", "avd", deviceName + ".avd");
  }

  @NotNull
  private static Path snapshotPathOf(@NotNull String deviceName, @NotNull String snapshotName) {
    return virtualDevicePathOf(deviceName).resolve(Path.of("snapshots", snapshotName));
  }

  private Keys() {
  }
}
