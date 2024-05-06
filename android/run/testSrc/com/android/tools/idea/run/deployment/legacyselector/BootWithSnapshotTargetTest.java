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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.idea.run.AndroidDevice;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class BootWithSnapshotTargetTest {
  private final @NotNull Target myTarget;

  public BootWithSnapshotTargetTest() {
    myTarget = new BootWithSnapshotTarget(Keys.PIXEL_4_API_30, Keys.PIXEL_4_API_30_SNAPSHOT_2);
  }

  @Test
  public void matchesDeviceDoesntMatchDeviceKey() {
    // Arrange
    Device device = TestDevices.buildPixel3Api30();

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
      .setKey(Keys.PIXEL_4_API_30)
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .addSnapshot(new Snapshot(Keys.PIXEL_4_API_30_SNAPSHOT_2))
      .build();

    // Act
    boolean matches = myTarget.matches(device);

    // Assert
    assertTrue(matches);
  }
}
