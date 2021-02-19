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

import com.android.tools.idea.run.AndroidDevice;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class TargetsForReadingSupplierTest {
  private static final Key DEVICE_KEY = new VirtualDevicePath("/home/user/.android/avd/Pixel_4_API_30.avd");
  private final Target myTarget;

  public TargetsForReadingSupplierTest() {
    FileSystem fileSystem = Jimfs.newFileSystem(Configuration.unix());
    Path snapshotKey = fileSystem.getPath("/home/user/.android/avd/Pixel_4_API_30.avd/snapshots/snap_2020-12-17_12-26-30");

    myTarget = new BootWithSnapshotTarget(DEVICE_KEY, snapshotKey);
  }

  @Test
  public void targetsForReadingSupplierNoTargetMatches() {
    // Arrange
    Collection<Device> devices = Collections.emptyList();
    RunningDeviceTarget runningDeviceTarget = new RunningDeviceTarget(DEVICE_KEY);

    // Act
    Object optionalTarget = new TargetsForReadingSupplier(devices, runningDeviceTarget, null).getDropDownTarget();

    // Assert
    assertEquals(Optional.of(runningDeviceTarget), optionalTarget);
  }

  @Test
  public void targetsForReadingSupplier() {
    // Arrange
    Device device = new VirtualDevice.Builder()
      .setName("Pixel 4 API 30")
      .setKey(DEVICE_KEY)
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    Collection<Device> devices = Collections.singletonList(device);

    // Act
    Object optionalTarget = new TargetsForReadingSupplier(devices, null, myTarget).getDropDownTarget();

    // Assert
    assertEquals(Optional.of(myTarget), optionalTarget);
  }

  @Test
  public void targetsForReadingSupplierDeviceIsRunning() {
    // Arrange
    Device device = new VirtualDevice.Builder()
      .setName("Pixel 4 API 30")
      .setKey(DEVICE_KEY)
      .setConnectionTime(Instant.parse("2018-11-28T01:15:27Z"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    Collection<Device> devices = Collections.singletonList(device);

    // Act
    Object optionalTarget = new TargetsForReadingSupplier(devices, null, myTarget).getDropDownTarget();

    // Assert
    assertEquals(Optional.of(new RunningDeviceTarget(DEVICE_KEY)), optionalTarget);
  }
}
