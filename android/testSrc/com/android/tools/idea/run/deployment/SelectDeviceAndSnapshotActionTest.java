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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import com.android.tools.idea.run.AndroidDevice;
import com.google.common.collect.ImmutableList;
import java.time.Clock;
import java.time.Instant;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public final class SelectDeviceAndSnapshotActionTest {
  private ConnectionTimeService myService;
  private DeviceAndSnapshotComboBoxAction myComboBoxAction;

  @Before
  public void newService() {
    Clock clock = Mockito.mock(Clock.class);
    Mockito.when(clock.instant()).thenReturn(Instant.parse("2018-11-28T01:15:27.000Z"));

    myService = new ConnectionTimeService(clock);
  }

  @Before
  public void newComboBoxAction() {
    myComboBoxAction = new DeviceAndSnapshotComboBoxAction(
      () -> true,
      () -> true,
      Mockito.mock(AsyncDevicesGetter.class),
      Mockito.mock(Clock.class));
  }

  @Test
  public void selectDeviceAndSnapshotActionSnapshotsIsEmpty() {
    Device device = new VirtualDevice.Builder()
      .setName(Devices.PIXEL_2_XL_API_28)
      .setKey("Pixel_2_XL_API_28")
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .setSnapshots(ImmutableList.of())
      .build(null, myService);

    SelectDeviceAndSnapshotAction action = new SelectDeviceAndSnapshotAction.Builder()
      .setComboBoxAction(myComboBoxAction)
      .setDevice(device)
      .build();

    assertNull(action.getSnapshot());
  }

  @Test
  public void selectDeviceAndSnapshotActionSnapshotsEqualsDefaultSnapshotCollection() {
    Device device = new VirtualDevice.Builder()
      .setName(Devices.PIXEL_2_XL_API_28)
      .setKey("Pixel_2_XL_API_28")
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .setSnapshots(VirtualDevice.DEFAULT_SNAPSHOT_COLLECTION)
      .build(null, myService);

    SelectDeviceAndSnapshotAction action = new SelectDeviceAndSnapshotAction.Builder()
      .setComboBoxAction(myComboBoxAction)
      .setDevice(device)
      .build();

    assertEquals(VirtualDevice.DEFAULT_SNAPSHOT, action.getSnapshot());
  }

  @Test
  public void selectDeviceAndSnapshotActionThrowsIllegalArgumentException() {
    Device device = new VirtualDevice.Builder()
      .setName(Devices.PIXEL_2_XL_API_28)
      .setKey("Pixel_2_XL_API_28")
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .setSnapshots(ImmutableList.of("snap_2018-08-07_16-27-58"))
      .build(null, myService);

    try {
      new SelectDeviceAndSnapshotAction.Builder()
        .setComboBoxAction(myComboBoxAction)
        .setDevice(device)
        .build();

      fail();
    }
    catch (IllegalArgumentException ignored) {
    }
  }
}
