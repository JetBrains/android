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
import com.android.tools.idea.ui.TableUtils;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.AndroidTestCase;
import org.mockito.Mockito;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.util.Collections;

public final class StringResourceViewPanelTest extends AndroidTestCase {
  private Disposable myParentDisposable;
  private StringResourceViewPanel myPanel;
  private JTable myTable;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myParentDisposable = Mockito.mock(Disposable.class);
    myPanel = new StringResourceViewPanel(myFacet, myParentDisposable);
    myTable = myPanel.getTable();

    VirtualFile resourceDirectory = myFixture.copyDirectoryToProject("stringsEditor/base/res", "res");
    myPanel.parse(ModuleResourceRepository.createForTest(myFacet, Collections.singletonList(resourceDirectory)));
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      Disposer.dispose(myParentDisposable);
    }
    finally {
      super.tearDown();
    }
  }

  public void testSetShowingOnlyKeysNeedingTranslations() {
    assertEquals(10, myTable.getRowCount());
    assertEquals("key1", myTable.getValueAt(0, 0));
    assertEquals("key10", myTable.getValueAt(1, 0));
    assertEquals("key2", myTable.getValueAt(2, 0));
    assertEquals("key3", myTable.getValueAt(3, 0));
    assertEquals("key4", myTable.getValueAt(4, 0));
    assertEquals("key5", myTable.getValueAt(5, 0));
    assertEquals("key6", myTable.getValueAt(6, 0));
    assertEquals("key7", myTable.getValueAt(7, 0));
    assertEquals("key8", myTable.getValueAt(8, 0));
    assertEquals("key9", myTable.getValueAt(9, 0));

    myPanel.setShowingOnlyKeysNeedingTranslations(true);

    assertEquals(7, myTable.getRowCount());
    assertEquals("key1", myTable.getValueAt(0, 0));
    assertEquals("key10", myTable.getValueAt(1, 0));
    assertEquals("key3", myTable.getValueAt(2, 0));
    assertEquals("key4", myTable.getValueAt(3, 0));
    assertEquals("key7", myTable.getValueAt(4, 0));
    assertEquals("key8", myTable.getValueAt(5, 0));
    assertEquals("key9", myTable.getValueAt(6, 0));
  }

  public void testOnTextFieldUpdate() {
    myPanel.setShowingOnlyKeysNeedingTranslations(true);

    TableUtils.selectCellAt(myTable, 2, 3);
    myPanel.myTranslation.setText("Key 4 en");
    myPanel.onTextFieldUpdate(myPanel.myTranslation);

    assertEquals("Key 4 en", myTable.getModel().getValueAt(3, 3));
  }

  public void testRefilteringAfterEditingUntranslatableCell() {
    myPanel.setShowingOnlyKeysNeedingTranslations(true);
    editCellAt(true, 0, 2);

    assertEquals(6, myTable.getRowCount());
    assertEquals("key10", myTable.getValueAt(0, 0));
    assertEquals("key3", myTable.getValueAt(1, 0));
    assertEquals("key4", myTable.getValueAt(2, 0));
    assertEquals("key7", myTable.getValueAt(3, 0));
    assertEquals("key8", myTable.getValueAt(4, 0));
    assertEquals("key9", myTable.getValueAt(5, 0));
  }

  public void testRefilteringAfterEditingTranslationCells() {
    myPanel.setShowingOnlyKeysNeedingTranslations(true);
    editCellAt("Key 3 en-rGB", 2, 4);

    assertEquals(6, myTable.getRowCount());
    assertEquals("key1", myTable.getValueAt(0, 0));
    assertEquals("key10", myTable.getValueAt(1, 0));
    assertEquals("key4", myTable.getValueAt(2, 0));
    assertEquals("key7", myTable.getValueAt(3, 0));
    assertEquals("key8", myTable.getValueAt(4, 0));
    assertEquals("key9", myTable.getValueAt(5, 0));
  }

  public void testSelectingCell() {
    myPanel.setShowingOnlyKeysNeedingTranslations(true);
    TableUtils.selectCellAt(myTable, 2, 1);

    assertEquals("Key 3 default", myPanel.myDefaultValue.getText());
  }

  private void editCellAt(Object value, int row, int column) {
    TableUtils.selectCellAt(myTable, row, column);
    myTable.editCellAt(row, column, new MouseEvent(myTable, 0, 0, 0, 0, 0, 2, false, MouseEvent.BUTTON1));

    CellEditor cellEditor = myTable.getCellEditor();

    if (column == 2) {
      Object component = ((DefaultCellEditor)cellEditor).getComponent();
      ((AbstractButton)component).setSelected((Boolean)value);
    }
    else {
      ((StringsCellEditor)cellEditor).setCellEditorValue(value);
    }

    cellEditor.stopCellEditing();
  }
}
