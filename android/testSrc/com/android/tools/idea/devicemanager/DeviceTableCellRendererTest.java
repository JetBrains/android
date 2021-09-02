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

import com.android.ddmlib.IDevice;
import com.android.tools.idea.devicemanager.physicaltab.ConnectionType;
import com.android.tools.idea.devicemanager.physicaltab.PhysicalDevice;
import com.android.tools.idea.devicemanager.physicaltab.SerialNumber;
import com.android.tools.idea.devicemanager.physicaltab.TestPhysicalDevices;
import com.android.tools.idea.wearpairing.ConnectionState;
import com.android.tools.idea.wearpairing.PairingDevice;
import com.android.tools.idea.wearpairing.WearPairingManager;
import com.intellij.ui.table.JBTable;
import icons.StudioIcons;
import javax.swing.JTable;
import javax.swing.border.Border;
import javax.swing.plaf.BorderUIResource.EmptyBorderUIResource;
import kotlinx.coroutines.BuildersKt;
import kotlinx.coroutines.GlobalScope;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class DeviceTableCellRendererTest {
  private static final Border BORDER = new EmptyBorderUIResource(2, 3, 2, 3);
  private final JTable myTable = new JBTable();

  @Test
  public void getTableCellRendererComponentDeviceIsOnline() {
    // Arrange
    DeviceTableCellRenderer<Device> renderer = new DeviceTableCellRenderer<>(Device.class, (selected, focused) -> BORDER);

    Device device = new PhysicalDevice.Builder()
      .setKey(new SerialNumber("86UX00F4R"))
      .setName("Google Pixel 3")
      .setTarget("Android 12.0")
      .setApi("S")
      .addConnectionType(ConnectionType.USB)
      .build();

    // Act
    renderer.getTableCellRendererComponent(myTable, device, false, false, 0, 0);

    // Assert
    assertEquals(device.getIcon(), renderer.getIconLabel().getIcon());
    assertEquals(device.getName(), renderer.getNameLabel().getText());
    assertEquals(StudioIcons.Avd.STATUS_DECORATOR_ONLINE, renderer.getOnlineLabel().getIcon());
    assertEquals(device.getTarget(), renderer.getLine2Label().getText());
  }

  @Test
  public void getTableCellRendererComponentDeviceIsPaired() throws InterruptedException {
    // Arrange
    DeviceTableCellRenderer<Device> renderer = new DeviceTableCellRenderer<>(Device.class, (selected, focused) -> BORDER);
    PairingDevice wearDevice = new PairingDevice("wearId1", "Wear 1", 30, true, true, true, ConnectionState.ONLINE, false);
    PairingDevice phoneDevice = new PairingDevice("86UX00F4R", "Google Pixel 3", 30, false, false, true, ConnectionState.ONLINE, false);
    IDevice device = Mockito.mock(IDevice.class);

    assert renderer.getPairedLabel().getIcon() == null;

    // Act
    BuildersKt.runBlocking(GlobalScope.INSTANCE.getCoroutineContext(), (coroutineScope, continuation) ->
      WearPairingManager.INSTANCE.createPairedDeviceBridge(phoneDevice, device, wearDevice, device, false, continuation)
    );
    renderer.getTableCellRendererComponent(myTable, TestPhysicalDevices.GOOGLE_PIXEL_3, false, false, 0, 0);

    // Assert
    assertEquals(StudioIcons.LayoutEditor.Toolbar.INSERT_HORIZ_CHAIN, renderer.getPairedLabel().getIcon());
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
