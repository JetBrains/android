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
package com.android.tools.idea.run.deployment.legacyselector;

import static org.junit.Assert.assertEquals;

import com.android.tools.idea.run.AndroidDevice;
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
  private final Target myTarget;

  public TargetsForReadingSupplierTest() {
    myTarget = new BootWithSnapshotTarget(Keys.PIXEL_4_API_30, Keys.PIXEL_4_API_30_SNAPSHOT_2);
  }

  @Test
  public void targetsForReadingSupplierDeviceIsntRunningAndRunningDeviceTargetIsntNull() {
    // Arrange
    Collection<Device> devices = Collections.emptyList();
    RunningDeviceTarget runningDeviceTarget = new RunningDeviceTarget(Keys.PIXEL_4_API_30);

    // Act
    Object optionalTarget = new TargetsForReadingSupplier(devices, runningDeviceTarget, null).getDropDownTarget();

    // Assert
    assertEquals(Optional.empty(), optionalTarget);
  }

  @Test
  public void targetsForReadingSupplier() {
    // Arrange
    Collection<Device> devices = Collections.singletonList(TestDevices.buildPixel4Api30());

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
      .setKey(Keys.PIXEL_4_API_30)
      .setConnectionTime(Instant.parse("2018-11-28T01:15:27Z"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    Collection<Device> devices = Collections.singletonList(device);

    // Act
    Object optionalTarget = new TargetsForReadingSupplier(devices, null, myTarget).getDropDownTarget();

    // Assert
    assertEquals(Optional.of(new RunningDeviceTarget(Keys.PIXEL_4_API_30)), optionalTarget);
  }
}
