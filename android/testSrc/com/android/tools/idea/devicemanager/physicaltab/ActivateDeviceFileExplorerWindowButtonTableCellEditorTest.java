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
package com.android.tools.idea.devicemanager.physicaltab;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.idea.devicemanager.ActivateDeviceFileExplorerWindowValue;
import com.intellij.openapi.project.Project;
import java.awt.Component;
import javax.swing.JTable;
import javax.swing.table.TableCellEditor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class ActivateDeviceFileExplorerWindowButtonTableCellEditorTest {
  private final TableCellEditor myEditor = new ActivateDeviceFileExplorerWindowButtonTableCellEditor(Mockito.mock(Project.class));

  @Test
  public void getTableCellEditorComponentDeviceOnline() {
    // Arrange
    JTable table = PhysicalDeviceTables.mock(TestPhysicalDevices.ONLINE_GOOGLE_PIXEL_3);

    // Act
    Component component = myEditor.getTableCellEditorComponent(table, ActivateDeviceFileExplorerWindowValue.INSTANCE, false, 0, 3);

    // Assert
    assertTrue(component.isEnabled());
  }

  @Test
  public void getTableCellEditorComponent() {
    // Arrange
    JTable table = PhysicalDeviceTables.mock(TestPhysicalDevices.GOOGLE_PIXEL_3);

    // Act
    Component component = myEditor.getTableCellEditorComponent(table, ActivateDeviceFileExplorerWindowValue.INSTANCE, false, 0, 3);

    // Assert
    assertFalse(component.isEnabled());
  }
}
