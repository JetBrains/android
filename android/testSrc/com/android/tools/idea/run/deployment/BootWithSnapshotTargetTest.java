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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.idea.run.AndroidDevice;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class BootWithSnapshotTargetTest {
  private final @NotNull Key myDeviceKey;
  private final @NotNull Path mySnapshotKey;

  private final @NotNull Target myTarget;

  public BootWithSnapshotTargetTest() {
    FileSystem fileSystem = Jimfs.newFileSystem(Configuration.unix());

    myDeviceKey = new VirtualDevicePath("/home/user/.android/avd/Pixel_4_API_30.avd");
    mySnapshotKey = fileSystem.getPath("/home/user/.android/avd/Pixel_4_API_30.avd/snapshots/snap_2020-12-17_12-26-30");

    myTarget = new BootWithSnapshotTarget(myDeviceKey, mySnapshotKey);
  }

  @Test
  public void matchesDeviceDoesntMatchDeviceKey() {
    // Arrange
    Device device = new VirtualDevice.Builder()
      .setName("Pixel 3 API 30")
      .setKey(new VirtualDevicePath("/home/user/.android/avd/Pixel_3_API_30.avd"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    // Act
    boolean matches = myTarget.matches(device);

    // Assert
    assertFalse(matches);
  }

  @Test
  public void matches() {
    // Arrange
    Device device = new VirtualDevice.Builder()
      .setName("Pixel 4 API 30")
      .setKey(myDeviceKey)
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .addSnapshot(new Snapshot(mySnapshotKey))
      .build();

    // Act
    boolean matches = myTarget.matches(device);

    // Assert
    assertTrue(matches);
  }
}
