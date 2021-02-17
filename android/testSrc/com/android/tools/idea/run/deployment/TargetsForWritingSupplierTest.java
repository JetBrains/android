/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class TargetsForWritingSupplierTest {
  private final @NotNull FileSystem myFileSystem = Jimfs.newFileSystem(Configuration.unix());

  @Test
  public void targetsForWritingSupplierNewDeviceIsntRunningDevicesAreSame() {
    // Arrange
    Path oldSnapshotKey = myFileSystem.getPath("/home/user/.android/avd/Pixel_4_API_30.avd/snapshots/snap_2020-12-17_12-26-30");
    Target oldTarget = new BootWithSnapshotTarget(new VirtualDevicePath("/home/user/.android/avd/Pixel_4_API_30.avd"), oldSnapshotKey);

    Path newSnapshotKey = myFileSystem.getPath("/home/user/.android/avd/Pixel_4_API_30.avd/snapshots/snap_2020-12-17_12-26-30");
    Target newTarget = new BootWithSnapshotTarget(new VirtualDevicePath("/home/user/.android/avd/Pixel_4_API_30.avd"), newSnapshotKey);

    TargetsForWritingSupplier supplier = new TargetsForWritingSupplier(oldTarget, newTarget);

    // Act
    Object runningDeviceTarget = supplier.getDropDownRunningDeviceTarget();
    Object target = supplier.getDropDownTarget();

    // Assert
    assertEquals(Optional.empty(), runningDeviceTarget);
    assertEquals(Optional.of(newTarget), target);
  }

  @Test
  public void targetsForWritingSupplierNewDeviceIsntRunningDevicesAreDifferent() {
    // Arrange
    Path oldSnapshotKey = myFileSystem.getPath("/home/user/.android/avd/Pixel_3_API_30.avd/snapshots/snap_2020-12-17_12-26-30");
    Target oldTarget = new BootWithSnapshotTarget(new VirtualDevicePath("/home/user/.android/avd/Pixel_3_API_30.avd"), oldSnapshotKey);

    Path newSnapshotKey = myFileSystem.getPath("/home/user/.android/avd/Pixel_4_API_30.avd/snapshots/snap_2020-12-17_12-26-30");
    Target newTarget = new BootWithSnapshotTarget(new VirtualDevicePath("/home/user/.android/avd/Pixel_4_API_30.avd"), newSnapshotKey);

    TargetsForWritingSupplier supplier = new TargetsForWritingSupplier(oldTarget, newTarget);

    // Act
    Object runningDeviceTarget = supplier.getDropDownRunningDeviceTarget();
    Object target = supplier.getDropDownTarget();

    // Assert
    assertEquals(Optional.empty(), runningDeviceTarget);
    assertEquals(Optional.of(newTarget), target);
  }

  @Test
  public void targetsForWritingSupplierNewDeviceIsRunningDevicesAreDifferent() {
    // Arrange
    Path oldSnapshotKey = myFileSystem.getPath("/home/user/.android/avd/Pixel_3_API_30.avd/snapshots/snap_2020-12-17_12-26-30");
    Target oldTarget = new BootWithSnapshotTarget(new VirtualDevicePath("/home/user/.android/avd/Pixel_3_API_30.avd"), oldSnapshotKey);

    Target newTarget = new RunningDeviceTarget(new VirtualDevicePath("/home/user/.android/avd/Pixel_4_API_30.avd"));

    TargetsForWritingSupplier supplier = new TargetsForWritingSupplier(oldTarget, newTarget);

    // Act
    Object runningDeviceTarget = supplier.getDropDownRunningDeviceTarget();
    Object target = supplier.getDropDownTarget();

    // Assert
    assertEquals(Optional.of(newTarget), runningDeviceTarget);
    assertEquals(Optional.empty(), target);
  }

  @Test
  public void targetsForWritingSupplierNewDeviceIsRunningDevicesAreSame() {
    // Arrange
    Path oldSnapshotKey = myFileSystem.getPath("/home/user/.android/avd/Pixel_4_API_30.avd/snapshots/snap_2020-12-17_12-26-30");
    Target oldTarget = new BootWithSnapshotTarget(new VirtualDevicePath("/home/user/.android/avd/Pixel_4_API_30.avd"), oldSnapshotKey);

    Target newTarget = new RunningDeviceTarget(new VirtualDevicePath("/home/user/.android/avd/Pixel_4_API_30.avd"));

    TargetsForWritingSupplier supplier = new TargetsForWritingSupplier(oldTarget, newTarget);

    // Act
    Object runningDeviceTarget = supplier.getDropDownRunningDeviceTarget();
    Object target = supplier.getDropDownTarget();

    // Assert
    assertEquals(Optional.of(newTarget), runningDeviceTarget);
    assertEquals(Optional.of(oldTarget), target);
  }
}
