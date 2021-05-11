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

import com.android.tools.idea.devicemanager.physicaltab.PhysicalDeviceTableModel.Actions;
import com.intellij.openapi.project.Project;
import com.intellij.ui.table.JBTable;
import java.util.Collections;
import javax.swing.JTable;
import javax.swing.UIManager;
import javax.swing.table.TableCellEditor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class ActionsTableCellEditorTest {
  @Test
  public void getTableCellEditorComponent() {
    // Arrange
    TableCellEditor editor = new ActionsTableCellEditor(Mockito.mock(Project.class));
    JTable table = new JBTable(new PhysicalDeviceTableModel(Collections.singletonList(TestPhysicalDevices.GOOGLE_PIXEL_3)));

    // Act
    Object component = editor.getTableCellEditorComponent(table, Actions.INSTANCE, false, 0, 3);

    // Assert
    ActionsComponent actionsComponent = (ActionsComponent)component;

    assertEquals(TestPhysicalDevices.GOOGLE_PIXEL_3, actionsComponent.getDevice());
    assertEquals(table.getBackground(), actionsComponent.getBackground());
    assertEquals(UIManager.getBorder("Table.focusCellHighlightBorder"), actionsComponent.getBorder());
  }
}
