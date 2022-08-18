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

import com.android.flags.junit.SetFlagRule;
import com.android.tools.idea.devicemanager.physicaltab.TestPhysicalDevices;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.wearpairing.ConnectionState;
import com.android.tools.idea.wearpairing.PairingDevice;
import com.android.tools.idea.wearpairing.WearPairingManager;
import com.android.tools.idea.wearpairing.WearPairingManager.PairingState;
import com.android.tools.idea.wearpairing.WearPairingManager.PhoneWearPair;
import com.intellij.ui.table.JBTable;
import icons.StudioIcons;
import java.util.Collections;
import javax.swing.JTable;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class DeviceTableCellRendererTest {
  @Rule
  public SetFlagRule<Boolean> setWearPairingFlag = new SetFlagRule<>(StudioFlags.WEAR_OS_VIRTUAL_DEVICE_PAIRING_ASSISTANT_ENABLED, true);

  private final JTable myTable = new JBTable();

  @Test
  public void getTableCellRendererComponentDeviceIsOnline() {
    // Arrange
    DeviceTableCellRenderer<Device> renderer = new DeviceTableCellRenderer<>(Device.class);

    // Act
    renderer.getTableCellRendererComponent(myTable, TestPhysicalDevices.ONLINE_GOOGLE_PIXEL_3, false, false, 0, 0);

    // Assert
    assertEquals(TestPhysicalDevices.ONLINE_GOOGLE_PIXEL_3.getName(), renderer.getNameLabel().getText());
    assertEquals(StudioIcons.Avd.STATUS_DECORATOR_ONLINE, renderer.getStateLabel().getIcon());
    assertEquals(TestPhysicalDevices.ONLINE_GOOGLE_PIXEL_3.getTarget(), renderer.getLine2Label().getText());
  }

  @Test
  public void getTableCellRendererComponentDeviceIsPairedAndConnected() {
    // Arrange
    PairingDevice phoneDevice =
      new PairingDevice(TestPhysicalDevices.GOOGLE_PIXEL_3.getKey().toString(), TestPhysicalDevices.GOOGLE_PIXEL_3.getName(),
                        TestPhysicalDevices.GOOGLE_PIXEL_3.getAndroidVersion()
                          .getApiLevel(), false, false, true, ConnectionState.ONLINE);
    PairingDevice wearDevice =
      new PairingDevice("wear", "wear",
                        30, true, true, true, ConnectionState.ONLINE);
    PhoneWearPair pair = new PhoneWearPair(phoneDevice, wearDevice);
    pair.setPairingStatus(PairingState.CONNECTED);

    WearPairingManager manager = Mockito.mock(WearPairingManager.class);
    Mockito.when(manager.getPairsForDevice("86UX00F4R")).thenReturn(Collections.singletonList(pair));

    DeviceTableCellRenderer<Device> renderer = new DeviceTableCellRenderer<>(Device.class, manager);
    assert renderer.getPairedLabel().getIcon() == null;

    // Act
    renderer.getTableCellRendererComponent(myTable, TestPhysicalDevices.GOOGLE_PIXEL_3, false, false, 0, 0);

    // Assert
    assertEquals(StudioIcons.DeviceExplorer.DEVICE_PAIRED_AND_CONNECTED, renderer.getPairedLabel().getIcon());
  }

  @Test
  public void getTableCellRendererComponentDeviceIsPaired() {
    // Arrange
    PairingDevice phoneDevice =
      new PairingDevice(TestPhysicalDevices.GOOGLE_PIXEL_3.getKey().toString(), TestPhysicalDevices.GOOGLE_PIXEL_3.getName(),
                        TestPhysicalDevices.GOOGLE_PIXEL_3.getAndroidVersion()
                          .getApiLevel(), false, false, true, ConnectionState.ONLINE);
    PairingDevice wearDevice =
      new PairingDevice("wear", "wear",
                        30, true, true, true, ConnectionState.ONLINE);
    PhoneWearPair pair = new PhoneWearPair(phoneDevice, wearDevice);
    pair.setPairingStatus(PairingState.UNKNOWN);

    WearPairingManager manager = Mockito.mock(WearPairingManager.class);
    Mockito.when(manager.getPairsForDevice("86UX00F4R")).thenReturn(Collections.singletonList(pair));

    DeviceTableCellRenderer<Device> renderer = new DeviceTableCellRenderer<>(Device.class, manager);
    assert renderer.getPairedLabel().getIcon() == null;

    // Act
    renderer.getTableCellRendererComponent(myTable, TestPhysicalDevices.GOOGLE_PIXEL_3, false, false, 0, 0);

    // Assert
    assertEquals(StudioIcons.LayoutEditor.Toolbar.INSERT_HORIZ_CHAIN, renderer.getPairedLabel().getIcon());
  }

  @Test
  public void getTableCellRendererComponent() {
    // Arrange
    DeviceTableCellRenderer<Device> renderer = new DeviceTableCellRenderer<>(Device.class);

    // Act
    renderer.getTableCellRendererComponent(myTable, TestPhysicalDevices.GOOGLE_PIXEL_3, false, false, 0, 0);

    // Assert
    assertEquals(TestPhysicalDevices.GOOGLE_PIXEL_3.getName(), renderer.getNameLabel().getText());
    assertNull(renderer.getStateLabel().getIcon());
    assertEquals(TestPhysicalDevices.GOOGLE_PIXEL_3.getTarget(), renderer.getLine2Label().getText());
  }

  @Test
  public void getForegroundSelected() {
    // Arrange
    DeviceTableCellRenderer<Device> renderer = new DeviceTableCellRenderer<>(Device.class);

    // Act
    renderer.getTableCellRendererComponent(myTable, TestPhysicalDevices.GOOGLE_PIXEL_3, true, false, 0, 0);

    // Assert
    assertEquals(myTable.getSelectionForeground(), renderer.getNameLabel().getForeground());
  }

  @Test
  public void getForeground() {
    // Arrange
    DeviceTableCellRenderer<Device> renderer = new DeviceTableCellRenderer<>(Device.class);

    // Act
    renderer.getTableCellRendererComponent(myTable, TestPhysicalDevices.GOOGLE_PIXEL_3, false, false, 0, 0);

    // Assert
    assertEquals(myTable.getForeground(), renderer.getNameLabel().getForeground());
  }
}
