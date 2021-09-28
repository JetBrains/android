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

import com.android.tools.idea.devicemanager.physicaltab.PhysicalDeviceTableModel.Actions;
import com.android.tools.idea.explorer.DeviceExplorerViewService;
import com.google.common.collect.Lists;
import com.intellij.openapi.project.Project;
import java.util.Collections;
import java.util.function.BiConsumer;
import java.util.function.Function;
import javax.swing.AbstractButton;
import javax.swing.JTable;
import javax.swing.event.CellEditorListener;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.MethodRule;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;

@RunWith(JUnit4.class)
public final class ActionsTableCellEditorTest {
  @Rule
  public final @NotNull MethodRule myRule = MockitoJUnit.rule();

  @Mock
  private @NotNull PhysicalDevicePanel myPanel;

  @Mock
  private @NotNull Project myProject;

  @Mock
  private @NotNull Function<@NotNull Project, @NotNull DeviceExplorerViewService> myDeviceExplorerViewServiceGetInstance;

  @Mock
  private @NotNull DeviceExplorerViewService myService;

  @Mock
  private @NotNull NewEditDeviceNameDialog myNewEditDeviceNameDialog;

  @Mock
  private @NotNull EditDeviceNameDialog myDialog;

  @Mock
  private @NotNull TableCellRenderer myDeviceRenderer;

  @Mock
  private @NotNull TableCellRenderer myActionsRenderer;

  @Mock
  private @NotNull CellEditorListener myListener;

  @Test
  public void activateDeviceFileExplorerWindow() {
    // Arrange
    Mockito.when(myPanel.getProject()).thenReturn(myProject);
    Mockito.when(myDeviceExplorerViewServiceGetInstance.apply(myProject)).thenReturn(myService);

    TableCellEditor editor = new ActionsTableCellEditor(myPanel,
                                                        myDeviceExplorerViewServiceGetInstance,
                                                        EditDeviceNameDialog::new,
                                                        ActionsTableCellEditor::askWithRemoveDeviceDialog);

    PhysicalDevice device = new PhysicalDevice.Builder()
      .setKey(new SerialNumber("86UX00F4R"))
      .setName("Google Pixel 3")
      .setTarget("Android 12.0")
      .setApi("S")
      .addConnectionType(ConnectionType.USB)
      .build();

    JTable table = new PhysicalDeviceTable(myPanel, new PhysicalDeviceTableModel(Collections.singletonList(device)));
    ActionsComponent component = (ActionsComponent)editor.getTableCellEditorComponent(table, Actions.INSTANCE, false, 0, 3);
    AbstractButton button = component.getActivateDeviceFileExplorerWindowButton();

    // Act
    button.doClick();

    // Assert
    Mockito.verify(myService).openAndShowDevice("86UX00F4R");
  }

  @Test
  public void editDeviceNameNotDialogShowAndGet() {
    // Arrange
    Mockito.when(myPanel.getProject()).thenReturn(myProject);
    Mockito.when(myNewEditDeviceNameDialog.apply(myProject, "", "Google Pixel 3")).thenReturn(myDialog);

    TableCellEditor editor = new ActionsTableCellEditor(myPanel,
                                                        DeviceExplorerViewService::getInstance,
                                                        myNewEditDeviceNameDialog,
                                                        ActionsTableCellEditor::askWithRemoveDeviceDialog);

    PhysicalDeviceTableModel model = new PhysicalDeviceTableModel(Collections.singletonList(TestPhysicalDevices.GOOGLE_PIXEL_3));
    JTable table = new PhysicalDeviceTable(myPanel, model);
    ActionsComponent component = (ActionsComponent)editor.getTableCellEditorComponent(table, Actions.INSTANCE, false, 0, 3);
    AbstractButton button = component.getEditDeviceNameButton();

    // Act
    button.doClick();

    // Assert
    assertEquals(Collections.singletonList(TestPhysicalDevices.GOOGLE_PIXEL_3), model.getCombinedDevices());
  }

  @Test
  public void editDeviceName() {
    // Arrange
    PhysicalDeviceTableModel model = new PhysicalDeviceTableModel(Lists.newArrayList(TestPhysicalDevices.GOOGLE_PIXEL_3));

    BiConsumer<JTable, Integer> sizeWidthToFit = (t, v) -> {
    };

    PhysicalDeviceTable table = new PhysicalDeviceTable(myPanel, model, sizeWidthToFit, () -> myDeviceRenderer, () -> myActionsRenderer);

    Mockito.when(myPanel.getProject()).thenReturn(myProject);
    Mockito.when(myPanel.getTable()).thenReturn(table);

    Mockito.when(myDialog.showAndGet()).thenReturn(true);
    Mockito.when(myDialog.getNameOverride()).thenReturn("Name Override");

    Mockito.when(myNewEditDeviceNameDialog.apply(myProject, "", "Google Pixel 3")).thenReturn(myDialog);

    TableCellEditor editor = new ActionsTableCellEditor(myPanel,
                                                        DeviceExplorerViewService::getInstance,
                                                        myNewEditDeviceNameDialog,
                                                        ActionsTableCellEditor::askWithRemoveDeviceDialog);

    ActionsComponent component = (ActionsComponent)editor.getTableCellEditorComponent(table, Actions.INSTANCE, false, 0, 3);
    AbstractButton button = component.getEditDeviceNameButton();

    // Act
    button.doClick();

    // Assert
    Object device = new PhysicalDevice.Builder()
      .setKey(new SerialNumber("86UX00F4R"))
      .setName("Google Pixel 3")
      .setNameOverride("Name Override")
      .setTarget("Android 12.0")
      .setApi("S")
      .build();

    assertEquals(Collections.singletonList(device), model.getCombinedDevices());
  }

  @Test
  public void removeCancel() {
    // Arrange
    Mockito.when(myPanel.getProject()).thenReturn(myProject);

    TableCellEditor editor = new ActionsTableCellEditor(myPanel,
                                                        DeviceExplorerViewService::getInstance,
                                                        EditDeviceNameDialog::new,
                                                        (device, project) -> false);

    editor.addCellEditorListener(myListener);

    PhysicalDeviceTableModel model = new PhysicalDeviceTableModel(Collections.singletonList(TestPhysicalDevices.GOOGLE_PIXEL_3));
    JTable table = new PhysicalDeviceTable(myPanel, model);
    AbstractButton button = ((ActionsComponent)editor.getTableCellEditorComponent(table, Actions.INSTANCE, false, 0, 3)).getRemoveButton();

    // Act
    button.doClick();

    // Assert
    Mockito.verify(myListener).editingCanceled(ArgumentMatchers.argThat(event -> event.getSource().equals(editor)));
    assertEquals(Collections.singletonList(TestPhysicalDevices.GOOGLE_PIXEL_3), model.getCombinedDevices());
  }

  @Test
  public void remove() {
    // Arrange
    PhysicalDeviceTableModel model = new PhysicalDeviceTableModel(Lists.newArrayList(TestPhysicalDevices.GOOGLE_PIXEL_3));
    PhysicalDeviceTable table = new PhysicalDeviceTable(myPanel, model);

    Mockito.when(myPanel.getProject()).thenReturn(myProject);
    Mockito.when(myPanel.getTable()).thenReturn(table);

    TableCellEditor editor = new ActionsTableCellEditor(myPanel,
                                                        DeviceExplorerViewService::getInstance,
                                                        EditDeviceNameDialog::new,
                                                        (device, project) -> true);

    editor.addCellEditorListener(myListener);

    AbstractButton button = ((ActionsComponent)editor.getTableCellEditorComponent(table, Actions.INSTANCE, false, 0, 3)).getRemoveButton();

    // Act
    button.doClick();

    // Assert
    Mockito.verify(myListener).editingStopped(ArgumentMatchers.argThat(event -> event.getSource().equals(editor)));
    assertEquals(Collections.emptyList(), model.getCombinedDevices());
  }

  @Test
  public void getTableCellEditorComponentDeviceIsntOnline() {
    // Arrange
    ActionsTableCellEditor editor = new ActionsTableCellEditor(myPanel,
                                                               DeviceExplorerViewService::getInstance,
                                                               EditDeviceNameDialog::new,
                                                               ActionsTableCellEditor::askWithRemoveDeviceDialog);

    PhysicalDeviceTableModel model = new PhysicalDeviceTableModel(Collections.singletonList(TestPhysicalDevices.GOOGLE_PIXEL_3));
    JTable table = new PhysicalDeviceTable(myPanel, model);

    // Act
    ActionsComponent component = (ActionsComponent)editor.getTableCellEditorComponent(table, Actions.INSTANCE, false, 0, 3);

    // Assert
    assertEquals(TestPhysicalDevices.GOOGLE_PIXEL_3, editor.getDevice());

    assertFalse(component.getActivateDeviceFileExplorerWindowButton().isEnabled());
    assertTrue(component.getRemoveButton().isEnabled());
    assertEquals(table.getBackground(), component.getBackground());
  }

  @Test
  public void getTableCellEditorComponent() {
    // Arrange
    TableCellEditor editor = new ActionsTableCellEditor(myPanel,
                                                        DeviceExplorerViewService::getInstance,
                                                        EditDeviceNameDialog::new,
                                                        ActionsTableCellEditor::askWithRemoveDeviceDialog);

    PhysicalDevice device = new PhysicalDevice.Builder()
      .setKey(new SerialNumber("86UX00F4R"))
      .setName("Google Pixel 3")
      .setTarget("Android 12.0")
      .setApi("S")
      .addConnectionType(ConnectionType.USB)
      .build();

    JTable table = new PhysicalDeviceTable(myPanel, new PhysicalDeviceTableModel(Collections.singletonList(device)));

    // Act
    ActionsComponent component = (ActionsComponent)editor.getTableCellEditorComponent(table, Actions.INSTANCE, false, 0, 3);

    // Assert
    assertTrue(component.getActivateDeviceFileExplorerWindowButton().isEnabled());
    assertFalse(component.getRemoveButton().isEnabled());
  }
}
