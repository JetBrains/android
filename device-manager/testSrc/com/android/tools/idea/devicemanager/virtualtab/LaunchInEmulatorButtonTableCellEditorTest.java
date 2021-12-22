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
package com.android.tools.idea.devicemanager.virtualtab;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.internal.avd.AvdInfo.AvdStatus;
import com.android.tools.idea.devicemanager.DeviceTables;
import com.android.tools.idea.devicemanager.virtualtab.VirtualDeviceTableModel.LaunchInEmulatorValue;
import java.awt.Component;
import javax.swing.JTable;
import javax.swing.table.TableCellEditor;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class LaunchInEmulatorButtonTableCellEditorTest {
  private final TableCellEditor myEditor = new LaunchInEmulatorButtonTableCellEditor(null);
  private final @NotNull AvdInfo myAvd = Mockito.mock(AvdInfo.class);

  @Test
  public void getTableCellEditorComponentStatusDoesntEqualOk() {
    // Arrange
    Mockito.when(myAvd.getStatus()).thenReturn(AvdStatus.ERROR_PROPERTIES);

    VirtualDevice device = new VirtualDevice.Builder()
      .setKey(new VirtualDeviceName("Pixel_5_API_31"))
      .setName("Pixel 5 API 31")
      .setTarget("Android 12.0")
      .setCpuArchitecture("arm")
      .setApi("31")
      .setAvdInfo(myAvd)
      .build();

    JTable table = DeviceTables.mock(device);

    // Act
    Component component = myEditor.getTableCellEditorComponent(table, LaunchInEmulatorValue.INSTANCE, false, 0, 3);

    // Assert
    assertFalse(component.isEnabled());
  }

  @Test
  public void getTableCellEditorComponent() {
    // Arrange
    Mockito.when(myAvd.getStatus()).thenReturn(AvdStatus.OK);
    JTable table = DeviceTables.mock(TestVirtualDevices.pixel5Api31(myAvd));

    // Act
    Component component = myEditor.getTableCellEditorComponent(table, LaunchInEmulatorValue.INSTANCE, false, 0, 3);

    // Assert
    assertTrue(component.isEnabled());
  }
}
