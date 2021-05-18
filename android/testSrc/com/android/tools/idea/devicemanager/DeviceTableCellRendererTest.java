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
import static org.junit.Assert.assertNull;

import com.android.tools.idea.devicemanager.physicaltab.PhysicalDevice;
import com.android.tools.idea.devicemanager.physicaltab.PhysicalDevice.ConnectionType;
import com.android.tools.idea.devicemanager.physicaltab.TestPhysicalDevices;
import com.intellij.ui.table.JBTable;
import icons.StudioIcons;
import javax.swing.JTable;
import javax.swing.border.Border;
import javax.swing.plaf.BorderUIResource.EmptyBorderUIResource;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class DeviceTableCellRendererTest {
  private static final Border BORDER = new EmptyBorderUIResource(2, 3, 2, 3);
  private final JTable myTable = new JBTable();

  @Test
  public void getTableCellRendererComponentDeviceIsOnline() {
    // Arrange
    DeviceTableCellRenderer<Device> renderer = new DeviceTableCellRenderer<>(Device.class, (selected, focused) -> BORDER);

    Device device = new PhysicalDevice.Builder()
      .setSerialNumber("86UX00F4R")
      .setName("Google Pixel 3")
      .setTarget("Android 12 Preview")
      .setApi("S")
      .addConnectionType(ConnectionType.USB)
      .build();

    // Act
    renderer.getTableCellRendererComponent(myTable, device, false, false, 0, 0);

    // Assert
    assertEquals(device.getIcon(), renderer.getIconLabel().getIcon());
    assertEquals(device.getName(), renderer.getNameLabel().getText());
    assertEquals(StudioIcons.Common.CIRCLE_GREEN, renderer.getOnlineLabel().getIcon());
    assertEquals(device.getTarget(), renderer.getLine2Label().getText());
  }

  @Test
  public void getTableCellRendererComponent() {
    // Arrange
    DeviceTableCellRenderer<Device> renderer = new DeviceTableCellRenderer<>(Device.class, (selected, focused) -> BORDER);

    // Act
    renderer.getTableCellRendererComponent(myTable, TestPhysicalDevices.GOOGLE_PIXEL_3, false, false, 0, 0);

    // Assert
    assertEquals(TestPhysicalDevices.GOOGLE_PIXEL_3.getIcon(), renderer.getIconLabel().getIcon());
    assertEquals(TestPhysicalDevices.GOOGLE_PIXEL_3.getName(), renderer.getNameLabel().getText());
    assertNull(renderer.getOnlineLabel().getIcon());
    assertEquals(TestPhysicalDevices.GOOGLE_PIXEL_3.getTarget(), renderer.getLine2Label().getText());
  }

  @Test
  public void getForegroundSelected() {
    // Arrange
    DeviceTableCellRenderer<Device> renderer = new DeviceTableCellRenderer<>(Device.class, (selected, focused) -> BORDER);

    // Act
    renderer.getTableCellRendererComponent(myTable, TestPhysicalDevices.GOOGLE_PIXEL_3, true, false, 0, 0);

    // Assert
    assertEquals(myTable.getSelectionForeground(), renderer.getNameLabel().getForeground());
  }

  @Test
  public void getForeground() {
    // Arrange
    DeviceTableCellRenderer<Device> renderer = new DeviceTableCellRenderer<>(Device.class, (selected, focused) -> BORDER);

    // Act
    renderer.getTableCellRendererComponent(myTable, TestPhysicalDevices.GOOGLE_PIXEL_3, false, false, 0, 0);

    // Assert
    assertEquals(myTable.getForeground(), renderer.getNameLabel().getForeground());
  }
}
