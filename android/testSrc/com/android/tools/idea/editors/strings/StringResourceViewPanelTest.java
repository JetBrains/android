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

import com.android.tools.adtui.TableUtils;
import com.android.tools.idea.editors.strings.table.NeedsTranslationsRowFilter;
import com.android.tools.idea.editors.strings.table.StringResourceTable;
import com.android.tools.idea.editors.strings.table.StringResourceTableModel;
import com.android.tools.idea.editors.strings.table.StringTableCellEditor;
import com.android.tools.idea.res.ModuleResourceRepository;
import com.android.tools.idea.res.MultiResourceRepository;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mockito;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.util.Collections;

public final class StringResourceViewPanelTest extends AndroidTestCase {
  private Disposable myParentDisposable;
  private StringResourceViewPanel myPanel;
  private StringResourceTable myTable;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myParentDisposable = Mockito.mock(Disposable.class);
    myPanel = new StringResourceViewPanel(myFacet, myParentDisposable);
    myTable = myPanel.getTable();

    VirtualFile resourceDirectory = myFixture.copyDirectoryToProject("stringsEditor/base/res", "res");
    MultiResourceRepository parent = ModuleResourceRepository.createForTest(myFacet, Collections.singletonList(resourceDirectory));

    myPanel.getTable().setModel(new StringResourceTableModel(StringResourceRepository.create(parent), myFacet));

    myTable.getRowSorter().setSortKeys(Collections.singletonList(new RowSorter.SortKey(0, SortOrder.ASCENDING)));
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      myTable = null;
      myPanel = null;
      Disposer.dispose(myParentDisposable);
    }
    finally {
      super.tearDown();
    }
  }

  public void testSetShowingOnlyKeysNeedingTranslations() {
    assertEquals(10, myTable.getRowCount());
    assertEquals("key1", getValueAt(0, 0));
    assertEquals("key10", getValueAt(1, 0));
    assertEquals("key2", getValueAt(2, 0));
    assertEquals("key3", getValueAt(3, 0));
    assertEquals("key4", getValueAt(4, 0));
    assertEquals("key5", getValueAt(5, 0));
    assertEquals("key6", getValueAt(6, 0));
    assertEquals("key7", getValueAt(7, 0));
    assertEquals("key8", getValueAt(8, 0));
    assertEquals("key9", getValueAt(9, 0));

    myTable.setRowFilter(new NeedsTranslationsRowFilter());

    assertEquals(7, myTable.getRowCount());
    assertEquals("key1", getValueAt(0, 0));
    assertEquals("key10", getValueAt(1, 0));
    assertEquals("key3", getValueAt(2, 0));
    assertEquals("key4", getValueAt(3, 0));
    assertEquals("key7", getValueAt(4, 0));
    assertEquals("key8", getValueAt(5, 0));
    assertEquals("key9", getValueAt(6, 0));
  }

  public void testTableDoesntRefilterAfterEditingUntranslatableCell() {
    myTable.setRowFilter(new NeedsTranslationsRowFilter());
    editCellAt(true, 0, StringResourceTableModel.UNTRANSLATABLE_COLUMN);

    assertEquals(7, myTable.getRowCount());
    assertEquals("key1", getValueAt(0, 0));
    assertEquals("key10", getValueAt(1, 0));
    assertEquals("key3", getValueAt(2, 0));
    assertEquals("key4", getValueAt(3, 0));
    assertEquals("key7", getValueAt(4, 0));
    assertEquals("key8", getValueAt(5, 0));
    assertEquals("key9", getValueAt(6, 0));
  }

  public void testTableDoesntRefilterAfterEditingTranslationCell() {
    myTable.setRowFilter(new NeedsTranslationsRowFilter());
    editCellAt("Key 3 en-rGB", 2, 6);

    assertEquals(7, myTable.getRowCount());
    assertEquals("key1", getValueAt(0, 0));
    assertEquals("key10", getValueAt(1, 0));
    assertEquals("key3", getValueAt(2, 0));
    assertEquals("key4", getValueAt(3, 0));
    assertEquals("key7", getValueAt(4, 0));
    assertEquals("key8", getValueAt(5, 0));
    assertEquals("key9", getValueAt(6, 0));
  }

  public void testSelectingCell() {
    myTable.setRowFilter(new NeedsTranslationsRowFilter());
    TableUtils.selectCellAt(myTable, 2, 1);

    assertEquals("Key 3 default", myPanel.myDefaultValueTextField.getTextField().getText());
  }

  private void editCellAt(@NotNull Object value, int viewRowIndex, int modelColumnIndex) {
    int viewColumnIndex = myTable.convertColumnIndexToView(modelColumnIndex);
    TableUtils.selectCellAt(myTable, viewRowIndex, viewColumnIndex);
    myTable.editCellAt(viewRowIndex, viewColumnIndex, new MouseEvent(myTable, 0, 0, 0, 0, 0, 2, false, MouseEvent.BUTTON1));

    CellEditor cellEditor = myTable.getCellEditor();

    if (modelColumnIndex == StringResourceTableModel.UNTRANSLATABLE_COLUMN) {
      Object component = ((DefaultCellEditor)cellEditor).getComponent();
      ((AbstractButton)component).setSelected((Boolean)value);
    }
    else {
      ((StringTableCellEditor)cellEditor).setCellEditorValue(value);
    }

    cellEditor.stopCellEditing();
  }

  private Object getValueAt(int row, int column) {
    int fixed = myTable.getTotalColumnCount() - myTable.getColumnCount();
    if (column < fixed) {
      return myTable.getFixedColumnValueAt(row, column);
    }
    return myTable.getValueAt(row, column - fixed);
  }
}
