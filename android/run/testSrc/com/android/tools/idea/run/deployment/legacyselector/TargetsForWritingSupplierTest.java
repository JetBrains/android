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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;

import com.google.common.collect.ImmutableList;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class TargetsForWritingSupplierTest {
  @Test
  public void targetsForWritingSupplierNewDeviceIsntRunningDevicesAreSame() {
    // Arrange
    Target oldTarget = new BootWithSnapshotTarget(Keys.PIXEL_4_API_30, Keys.PIXEL_4_API_30_SNAPSHOT_2);
    Target newTarget = new BootWithSnapshotTarget(Keys.PIXEL_4_API_30, Keys.PIXEL_4_API_30_SNAPSHOT_2);

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
    Target oldTarget = new BootWithSnapshotTarget(Keys.PIXEL_3_API_30, Keys.PIXEL_3_API_30_SNAPSHOT_1);
    Target newTarget = new BootWithSnapshotTarget(Keys.PIXEL_4_API_30, Keys.PIXEL_4_API_30_SNAPSHOT_2);

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
    Target oldTarget = new BootWithSnapshotTarget(Keys.PIXEL_3_API_30, Keys.PIXEL_3_API_30_SNAPSHOT_1);
    Target newTarget = new RunningDeviceTarget(Keys.PIXEL_4_API_30);

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
    Target oldTarget = new BootWithSnapshotTarget(Keys.PIXEL_4_API_30, Keys.PIXEL_4_API_30_SNAPSHOT_2);
    Target newTarget = new RunningDeviceTarget(Keys.PIXEL_4_API_30);

    TargetsForWritingSupplier supplier = new TargetsForWritingSupplier(oldTarget, newTarget);

    // Act
    Object runningDeviceTarget = supplier.getDropDownRunningDeviceTarget();
    Object target = supplier.getDropDownTarget();

    // Assert
    assertEquals(Optional.of(newTarget), runningDeviceTarget);
    assertEquals(Optional.of(oldTarget), target);
  }

  @Test
  public void runningDeviceHasOldTarget() {
    Target oldTarget = new BootWithSnapshotTarget(Keys.PIXEL_4_API_30, Keys.PIXEL_4_API_30_SNAPSHOT_2);
    Target newTarget = new RunningDeviceTarget(Keys.PIXEL_4_API_30);
    Target defaultLaunchTarget = new QuickBootTarget(Keys.PIXEL_4_API_30);

    TargetsForWritingSupplier supplier =
      new TargetsForWritingSupplier(ImmutableList.of(oldTarget), ImmutableList.of(newTarget), ImmutableList.of(defaultLaunchTarget));

    // Default launch target is not used because we already had a launchable target
    assertThat(supplier.getDialogRunningDeviceTargets()).containsExactly(newTarget);
    assertThat(supplier.getDialogTargets()).containsExactly(oldTarget);
  }

  @Test
  public void runningDeviceHasNoOldTarget() {
    Target newTarget = new RunningDeviceTarget(Keys.PIXEL_4_API_30);
    Target defaultLaunchTarget = new QuickBootTarget(Keys.PIXEL_4_API_30);

    TargetsForWritingSupplier supplier =
      new TargetsForWritingSupplier(ImmutableList.of(), ImmutableList.of(newTarget), ImmutableList.of(defaultLaunchTarget));

    assertThat(supplier.getDialogRunningDeviceTargets()).containsExactly(newTarget);
    assertThat(supplier.getDialogTargets()).containsExactly(defaultLaunchTarget);
  }
}
