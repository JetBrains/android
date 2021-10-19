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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.idea.devicemanager.physicaltab.PhysicalDeviceTableModel.RemoveValue;
import com.intellij.openapi.project.Project;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.event.CellEditorListener;
import javax.swing.plaf.ColorUIResource;
import javax.swing.table.TableCellEditor;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class RemoveButtonTableCellEditorTest {
  private final @NotNull PhysicalDevicePanel myPanel;
  private final @NotNull PhysicalDeviceTable myTable;

  public RemoveButtonTableCellEditorTest() {
    myPanel = Mockito.mock(PhysicalDevicePanel.class);

    myTable = Mockito.mock(PhysicalDeviceTable.class);
    Mockito.when(myTable.getSelectionBackground()).thenReturn(new ColorUIResource(47, 101, 202));
    Mockito.when(myTable.getSelectionForeground()).thenReturn(new ColorUIResource(187, 187, 187));
  }

  @Test
  public void removeButtonTableCellEditorNotRemove() {
    // Arrange
    Mockito.when(myPanel.getProject()).thenReturn(Mockito.mock(Project.class));
    CellEditorListener listener = Mockito.mock(CellEditorListener.class);

    RemoveButtonTableCellEditor editor = new RemoveButtonTableCellEditor(myPanel, (device, project) -> false);
    editor.addCellEditorListener(listener);

    Mockito.when(myTable.getDeviceAt(0)).thenReturn(TestPhysicalDevices.GOOGLE_PIXEL_3);
    AbstractButton component = (AbstractButton)editor.getTableCellEditorComponent(myTable, RemoveValue.INSTANCE, false, 0, 4);

    // Act
    component.doClick();

    // Assert
    Mockito.verify(listener).editingCanceled(editor.getChangeEvent());
  }

  @Test
  public void removeButtonTableCellEditor() {
    // Arrange
    PhysicalDeviceTableModel model = Mockito.mock(PhysicalDeviceTableModel.class);

    Mockito.when(myTable.getDeviceAt(0)).thenReturn(TestPhysicalDevices.GOOGLE_PIXEL_3);
    Mockito.when(myTable.getModel()).thenReturn(model);

    Mockito.when(myPanel.getProject()).thenReturn(Mockito.mock(Project.class));
    Mockito.when(myPanel.getTable()).thenReturn(myTable);

    CellEditorListener listener = Mockito.mock(CellEditorListener.class);

    RemoveButtonTableCellEditor editor = new RemoveButtonTableCellEditor(myPanel, (device, project) -> true);
    editor.addCellEditorListener(listener);

    AbstractButton component = (AbstractButton)editor.getTableCellEditorComponent(myTable, RemoveValue.INSTANCE, false, 0, 4);

    // Act
    component.doClick();

    // Assert
    Mockito.verify(listener).editingStopped(editor.getChangeEvent());
    Mockito.verify(model).remove(new SerialNumber("86UX00F4R"));
  }

  @Test
  public void getTableCellEditorComponentNotOnline() {
    // Arrange
    TableCellEditor editor = new RemoveButtonTableCellEditor(myPanel);
    Mockito.when(myTable.getDeviceAt(0)).thenReturn(TestPhysicalDevices.GOOGLE_PIXEL_3);

    // Act
    JComponent component = (JComponent)editor.getTableCellEditorComponent(myTable, RemoveValue.INSTANCE, false, 0, 4);

    // Assert
    assertTrue(component.isEnabled());
    assertEquals("Remove this offline device from the list.", component.getToolTipText());
  }

  @Test
  public void getTableCellEditorComponentOnline() {
    // Arrange
    TableCellEditor editor = new RemoveButtonTableCellEditor(myPanel);

    PhysicalDevice device = new PhysicalDevice.Builder()
      .setKey(new SerialNumber("86UX00F4R"))
      .setName("Google Pixel 3")
      .setTarget("Android 12.0")
      .setApi("31")
      .addConnectionType(ConnectionType.USB)
      .build();

    Mockito.when(myTable.getDeviceAt(0)).thenReturn(device);

    // Act
    JComponent component = (JComponent)editor.getTableCellEditorComponent(myTable, RemoveValue.INSTANCE, false, 0, 4);

    // Assert
    assertFalse(component.isEnabled());
    assertEquals("Connected devices can not be removed from the list.", component.getToolTipText());
  }
}
