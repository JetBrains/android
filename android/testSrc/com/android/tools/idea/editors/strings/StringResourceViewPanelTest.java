/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.editors.strings;

import com.android.tools.idea.editors.strings.table.StringsCellEditor;
import com.android.tools.idea.rendering.ModuleResourceRepository;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.AndroidTestCase;
import org.mockito.Mockito;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.util.Collections;

public final class StringResourceViewPanelTest extends AndroidTestCase {
  private Disposable parentDisposable;
  private StringResourceViewPanel panel;
  private JTable table;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    parentDisposable = Mockito.mock(Disposable.class);
    panel = new StringResourceViewPanel(myFacet, parentDisposable);
    table = panel.getTable();

    VirtualFile resourceDirectory = myFixture.copyDirectoryToProject("stringsEditor/base/res", "res");
    panel.parse(ModuleResourceRepository.createForTest(myFacet, Collections.singletonList(resourceDirectory)));
  }

  @Override
  public void tearDown() throws Exception {
    Disposer.dispose(parentDisposable);
    super.tearDown();
  }

  public void testSetShowingOnlyKeysNeedingTranslations() {
    assertEquals(10, table.getRowCount());
    assertEquals("key1", table.getValueAt(0, 0));
    assertEquals("key10", table.getValueAt(1, 0));
    assertEquals("key2", table.getValueAt(2, 0));
    assertEquals("key3", table.getValueAt(3, 0));
    assertEquals("key4", table.getValueAt(4, 0));
    assertEquals("key5", table.getValueAt(5, 0));
    assertEquals("key6", table.getValueAt(6, 0));
    assertEquals("key7", table.getValueAt(7, 0));
    assertEquals("key8", table.getValueAt(8, 0));
    assertEquals("key9", table.getValueAt(9, 0));

    panel.setShowingOnlyKeysNeedingTranslations(true);

    assertEquals(7, table.getRowCount());
    assertEquals("key1", table.getValueAt(0, 0));
    assertEquals("key10", table.getValueAt(1, 0));
    assertEquals("key3", table.getValueAt(2, 0));
    assertEquals("key4", table.getValueAt(3, 0));
    assertEquals("key7", table.getValueAt(4, 0));
    assertEquals("key8", table.getValueAt(5, 0));
    assertEquals("key9", table.getValueAt(6, 0));
  }

  public void testOnTextFieldUpdate() {
    panel.setShowingOnlyKeysNeedingTranslations(true);

    selectCellAt(2, 3);
    panel.myTranslation.setText("Key 4 en");
    panel.onTextFieldUpdate(panel.myTranslation);

    assertEquals("Key 4 en", table.getModel().getValueAt(3, 3));
  }

  public void testRefilteringAfterEditingUntranslatableCell() {
    panel.setShowingOnlyKeysNeedingTranslations(true);
    editCellAt(true, 0, 2);

    assertEquals(6, table.getRowCount());
    assertEquals("key10", table.getValueAt(0, 0));
    assertEquals("key3", table.getValueAt(1, 0));
    assertEquals("key4", table.getValueAt(2, 0));
    assertEquals("key7", table.getValueAt(3, 0));
    assertEquals("key8", table.getValueAt(4, 0));
    assertEquals("key9", table.getValueAt(5, 0));
  }

  public void testRefilteringAfterEditingTranslationCells() {
    panel.setShowingOnlyKeysNeedingTranslations(true);
    editCellAt("Key 3 en-rGB", 2, 4);

    assertEquals(6, table.getRowCount());
    assertEquals("key1", table.getValueAt(0, 0));
    assertEquals("key10", table.getValueAt(1, 0));
    assertEquals("key4", table.getValueAt(2, 0));
    assertEquals("key7", table.getValueAt(3, 0));
    assertEquals("key8", table.getValueAt(4, 0));
    assertEquals("key9", table.getValueAt(5, 0));
  }

  public void testSelectingCell() {
    panel.setShowingOnlyKeysNeedingTranslations(true);
    selectCellAt(2, 1);

    assertEquals("Key 3 default", panel.myDefaultValue.getText());
  }

  private void editCellAt(Object value, int row, int column) {
    selectCellAt(row, column);
    table.editCellAt(row, column, new MouseEvent(table, 0, 0, 0, 0, 0, 2, false, MouseEvent.BUTTON1));

    CellEditor cellEditor = table.getCellEditor();

    if (column == 2) {
      Object component = ((DefaultCellEditor)cellEditor).getComponent();
      ((AbstractButton)component).setSelected((Boolean)value);
    }
    else {
      ((StringsCellEditor)cellEditor).setCellEditorValue(value);
    }

    cellEditor.stopCellEditing();
  }

  private void selectCellAt(int row, int column) {
    table.setRowSelectionInterval(row, row);
    table.setColumnSelectionInterval(column, column);
  }
}
