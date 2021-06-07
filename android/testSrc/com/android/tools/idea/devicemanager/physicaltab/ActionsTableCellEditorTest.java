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

import com.android.tools.idea.devicemanager.physicaltab.PhysicalDevice.ConnectionType;
import com.android.tools.idea.devicemanager.physicaltab.PhysicalDeviceTableModel.Actions;
import com.android.tools.idea.explorer.DeviceExplorerToolWindowFactory;
import com.google.common.collect.Lists;
import com.intellij.openapi.project.Project;
import com.intellij.ui.table.JBTable;
import java.util.Collections;
import java.util.function.BiConsumer;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.UIManager;
import javax.swing.table.TableCellEditor;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.MethodRule;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;

@RunWith(JUnit4.class)
public final class ActionsTableCellEditorTest {
  @Rule
  public final @NotNull MethodRule myRule = MockitoJUnit.rule();

  @Mock
  private @NotNull Project myProject;

  @Mock
  private @NotNull BiConsumer<@NotNull Project, @NotNull String> myOpenAndShowDevice;

  @Mock
  private @NotNull NewEditDeviceNameDialog myNewEditDeviceNameDialog;

  @Mock
  private @NotNull EditDeviceNameDialog myDialog;

  @Test
  public void activateDeviceFileExplorerWindow() {
    // Arrange
    PhysicalDevice device = new PhysicalDevice.Builder()
      .setKey(new SerialNumber("86UX00F4R"))
      .setName("Google Pixel 3")
      .setTarget("Android 12 Preview")
      .setApi("S")
      .addConnectionType(ConnectionType.USB)
      .build();

    PhysicalDeviceTableModel model = new PhysicalDeviceTableModel(Collections.singletonList(device));

    TableCellEditor editor = new ActionsTableCellEditor(myProject, model, myOpenAndShowDevice, EditDeviceNameDialog::new);
    Object component = editor.getTableCellEditorComponent(new JBTable(model), Actions.INSTANCE, false, 0, 3);
    AbstractButton button = ((ActionsComponent)component).getActivateDeviceFileExplorerWindowButton();

    // Act
    button.doClick();

    // Assert
    Mockito.verify(myOpenAndShowDevice).accept(myProject, "86UX00F4R");
  }

  @Test
  public void editDeviceNameNotDialogShowAndGet() {
    // Arrange
    PhysicalDeviceTableModel model = new PhysicalDeviceTableModel(Collections.singletonList(TestPhysicalDevices.GOOGLE_PIXEL_3));
    Mockito.when(myNewEditDeviceNameDialog.apply(myProject, "", "Google Pixel 3")).thenReturn(myDialog);

    TableCellEditor editor = new ActionsTableCellEditor(myProject,
                                                        model,
                                                        DeviceExplorerToolWindowFactory::openAndShowDevice,
                                                        myNewEditDeviceNameDialog);

    Object component = editor.getTableCellEditorComponent(new JBTable(model), Actions.INSTANCE, false, 0, 3);
    AbstractButton button = ((ActionsComponent)component).getEditDeviceNameButton();

    // Act
    button.doClick();

    // Assert
    assertEquals(Collections.singletonList(TestPhysicalDevices.GOOGLE_PIXEL_3), model.getCombinedDevices());
  }

  @Test
  public void editDeviceName() {
    // Arrange
    PhysicalDeviceTableModel model = new PhysicalDeviceTableModel(Lists.newArrayList(TestPhysicalDevices.GOOGLE_PIXEL_3));

    Mockito.when(myDialog.showAndGet()).thenReturn(true);
    Mockito.when(myDialog.getNameOverride()).thenReturn("Name Override");

    Mockito.when(myNewEditDeviceNameDialog.apply(myProject, "", "Google Pixel 3")).thenReturn(myDialog);

    TableCellEditor editor = new ActionsTableCellEditor(myProject,
                                                        model,
                                                        DeviceExplorerToolWindowFactory::openAndShowDevice,
                                                        myNewEditDeviceNameDialog);

    Object component = editor.getTableCellEditorComponent(new JBTable(model), Actions.INSTANCE, false, 0, 3);
    AbstractButton button = ((ActionsComponent)component).getEditDeviceNameButton();

    // Act
    button.doClick();

    // Assert
    Object device = new PhysicalDevice.Builder()
      .setKey(new SerialNumber("86UX00F4R"))
      .setName("Google Pixel 3")
      .setNameOverride("Name Override")
      .setTarget("Android 12 Preview")
      .setApi("S")
      .build();

    assertEquals(Collections.singletonList(device), model.getCombinedDevices());
  }

  @Test
  public void getTableCellEditorComponent() {
    // Arrange
    PhysicalDeviceTableModel model = new PhysicalDeviceTableModel(Collections.singletonList(TestPhysicalDevices.GOOGLE_PIXEL_3));
    ActionsTableCellEditor editor = new ActionsTableCellEditor(myProject, model);
    JTable table = new JBTable(model);

    // Act
    JComponent component = (JComponent)editor.getTableCellEditorComponent(table, Actions.INSTANCE, false, 0, 3);

    // Assert
    assertEquals(TestPhysicalDevices.GOOGLE_PIXEL_3, editor.getDevice());
    assertEquals(table.getBackground(), component.getBackground());
    assertEquals(UIManager.getBorder("Table.focusCellHighlightBorder"), component.getBorder());
  }
}
