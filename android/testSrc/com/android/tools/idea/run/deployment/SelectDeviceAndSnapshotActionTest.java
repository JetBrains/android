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

import com.google.common.collect.ImmutableList;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.*;

public final class SelectDeviceAndSnapshotActionTest {
  private DeviceAndSnapshotComboBoxAction myComboBoxAction;

  @Before
  public void newComboBoxAction() {
    myComboBoxAction = new DeviceAndSnapshotComboBoxAction(() -> false, Mockito.mock(AsyncDevicesGetter.class));
  }

  @Test
  public void selectDeviceAndSnapshotActionSnapshotsIsEmpty() {
    Device device = new VirtualDevice(false, Devices.PIXEL_2_XL_API_28);
    SelectDeviceAndSnapshotAction action = new SelectDeviceAndSnapshotAction(myComboBoxAction, device);
    assertNull(action.getSnapshot());
  }

  @Test
  public void selectDeviceAndSnapshotActionSnapshotsEqualsDefaultSnapshotCollection() {
    Device device = new VirtualDevice(false, Devices.PIXEL_2_XL_API_28, VirtualDevice.DEFAULT_SNAPSHOT_COLLECTION);
    SelectDeviceAndSnapshotAction action = new SelectDeviceAndSnapshotAction(myComboBoxAction, device);
    assertEquals(VirtualDevice.DEFAULT_SNAPSHOT, action.getSnapshot());
  }

  @Test
  public void selectDeviceAndSnapshotActionThrowsIllegalArgumentException() {
    Device device = new VirtualDevice(false, Devices.PIXEL_2_XL_API_28, ImmutableList.of("snap_2018-08-07_16-27-58"));

    try {
      new SelectDeviceAndSnapshotAction(myComboBoxAction, device);
      fail();
    }
    catch (IllegalArgumentException ignored) {
    }
  }
}
