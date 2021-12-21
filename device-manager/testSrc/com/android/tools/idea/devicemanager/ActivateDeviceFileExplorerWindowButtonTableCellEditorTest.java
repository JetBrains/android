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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.sdklib.internal.avd.AvdInfo;
import com.android.tools.idea.devicemanager.physicaltab.PhysicalDevice;
import com.android.tools.idea.devicemanager.physicaltab.TestPhysicalDevices;
import com.android.tools.idea.devicemanager.virtualtab.TestVirtualDevices;
import com.android.tools.idea.devicemanager.virtualtab.VirtualDevice;
import com.google.wireless.android.sdk.stats.DeviceManagerEvent.EventKind;
import com.intellij.openapi.project.Project;
import java.awt.Component;
import javax.swing.table.TableCellEditor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class ActivateDeviceFileExplorerWindowButtonTableCellEditorTest {
  @Test
  public void getTableCellEditorComponentProjectIsNull() {
    // Arrange
    DeviceTable<VirtualDevice> table = DeviceTables.mock(TestVirtualDevices.pixel5Api31(Mockito.mock(AvdInfo.class)));

    TableCellEditor editor = new ActivateDeviceFileExplorerWindowButtonTableCellEditor<>(null,
                                                                                         table,
                                                                                         EventKind.VIRTUAL_DEVICE_FILE_EXPLORER_ACTION);

    // Act
    Component component = editor.getTableCellEditorComponent(table, ActivateDeviceFileExplorerWindowValue.INSTANCE, false, 0, 4);

    // Assert
    assertFalse(component.isEnabled());
  }

  @Test
  public void getTableCellEditorComponentDeviceOnline() {
    // Arrange
    DeviceTable<PhysicalDevice> table = DeviceTables.mock(TestPhysicalDevices.ONLINE_GOOGLE_PIXEL_3);

    TableCellEditor editor = new ActivateDeviceFileExplorerWindowButtonTableCellEditor<>(Mockito.mock(Project.class),
                                                                                         table,
                                                                                         EventKind.PHYSICAL_DEVICE_FILE_EXPLORER_ACTION);

    // Act
    Component component = editor.getTableCellEditorComponent(table, ActivateDeviceFileExplorerWindowValue.INSTANCE, false, 0, 3);

    // Assert
    assertTrue(component.isEnabled());
  }

  @Test
  public void getTableCellEditorComponent() {
    // Arrange
    DeviceTable<PhysicalDevice> table = DeviceTables.mock(TestPhysicalDevices.GOOGLE_PIXEL_3);

    TableCellEditor editor = new ActivateDeviceFileExplorerWindowButtonTableCellEditor<>(Mockito.mock(Project.class),
                                                                                         table,
                                                                                         EventKind.PHYSICAL_DEVICE_FILE_EXPLORER_ACTION);

    // Act
    Component component = editor.getTableCellEditorComponent(table, ActivateDeviceFileExplorerWindowValue.INSTANCE, false, 0, 3);

    // Assert
    assertFalse(component.isEnabled());
  }
}
