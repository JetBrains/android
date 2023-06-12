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
package com.android.tools.idea.devicemanager;

import static org.junit.Assert.assertEquals;

import com.android.sdklib.internal.avd.AvdInfo;
import com.android.tools.idea.devicemanager.physicaltab.PhysicalDevice;
import com.android.tools.idea.devicemanager.physicaltab.TestPhysicalDevices;
import com.android.tools.idea.devicemanager.virtualtab.TestVirtualDevices;
import com.android.tools.idea.devicemanager.virtualtab.VirtualDevice;
import icons.StudioIcons;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class DeviceIconButtonTableCellRendererTest {
  @Test
  public void getTableCellRendererComponentVirtualPhone() {
    // Arrange
    DeviceTable<VirtualDevice> table = DeviceTables.mock(TestVirtualDevices.pixel5Api31(Mockito.mock(AvdInfo.class)));
    IconButtonTableCellRenderer renderer = new DeviceIconButtonTableCellRenderer<>(table);

    // Act
    renderer.getTableCellRendererComponent(table, DeviceType.PHONE, true, true, 0, 0);

    // Assert
    assertEquals(Optional.of(StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_PHONE), renderer.myButton.getDefaultIcon());
  }

  @Test
  public void getTableCellRendererComponentPhysicalWearOs() {
    // Arrange
    DeviceTable<PhysicalDevice> table = DeviceTables.mock(TestPhysicalDevices.ONLINE_COMPAL_FALSTER);
    IconButtonTableCellRenderer renderer = new DeviceIconButtonTableCellRenderer<>(table);

    // Act
    renderer.getTableCellRendererComponent(table, DeviceType.WEAR_OS, true, true, 0, 0);

    // Assert
    assertEquals(Optional.of(StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_WEAR), renderer.myButton.getDefaultIcon());
  }
}
