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

import com.android.tools.idea.editors.strings.table.NeedsTranslationsRowFilter;
import com.android.tools.idea.editors.strings.table.StringResourceTable;
import com.android.tools.idea.editors.strings.table.StringResourceTableModel;
import com.android.tools.idea.editors.strings.table.StringTableCellEditor;
import com.android.tools.idea.res.LocalResourceRepository;
import com.android.tools.idea.res.ResourcesTestsUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.Arrays;
import java.util.Collections;
import javax.swing.AbstractButton;
import javax.swing.CellEditor;
import javax.swing.DefaultCellEditor;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mockito;

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
    LocalResourceRepository parent =
      ResourcesTestsUtil.createTestModuleRepository(myFacet, Collections.singletonList(resourceDirectory));

    myPanel.getTable().setModel(new StringResourceTableModel(StringResourceRepository.create(parent), myFacet.getModule().getProject()));
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
    Object expectedColumn = Arrays.asList(
      "key1",
      "key2",
      "key3",
      "key5",
      "key6",
      "key7",
      "key8",
      "key4",
      "key9",
      "key10");

    assertEquals(expectedColumn, myTable.getColumnAt(StringResourceTableModel.KEY_COLUMN));

    myTable.setRowFilter(new NeedsTranslationsRowFilter());

    expectedColumn = Arrays.asList(
      "key1",
      "key3",
      "key7",
      "key8",
      "key4",
      "key9",
      "key10");

    assertEquals(expectedColumn, myTable.getColumnAt(StringResourceTableModel.KEY_COLUMN));
  }

  public void testTableDoesntRefilterAfterEditingUntranslatableCell() {
    myTable.setRowFilter(new NeedsTranslationsRowFilter());
    editCellAt(true, 0, StringResourceTableModel.UNTRANSLATABLE_COLUMN);

    Object expectedColumn = Arrays.asList(
      "key1",
      "key3",
      "key7",
      "key8",
      "key4",
      "key9",
      "key10");

    assertEquals(expectedColumn, myTable.getColumnAt(StringResourceTableModel.KEY_COLUMN));
  }

  public void testTableDoesntRefilterAfterEditingTranslationCell() {
    myTable.setRowFilter(new NeedsTranslationsRowFilter());
    editCellAt("Key 3 en-rGB", 2, 6);

    Object expectedColumn = Arrays.asList(
      "key1",
      "key3",
      "key7",
      "key8",
      "key4",
      "key9",
      "key10");

    assertEquals(expectedColumn, myTable.getColumnAt(StringResourceTableModel.KEY_COLUMN));
  }

  public void testSelectingCell() {
    myTable.setRowFilter(new NeedsTranslationsRowFilter());
    myTable.selectCellAt(1, StringResourceTableModel.DEFAULT_VALUE_COLUMN);

    assertEquals("Key 3 default", myPanel.myDefaultValueTextField.getTextField().getText());
  }

  private void editCellAt(@NotNull Object value, int viewRowIndex, int viewColumnIndex) {
    myTable.selectCellAt(viewRowIndex, viewColumnIndex);
    myTable.editCellAt(viewRowIndex, viewColumnIndex);

    CellEditor cellEditor = myTable.getCellEditor();

    if (viewColumnIndex == StringResourceTableModel.UNTRANSLATABLE_COLUMN) {
      Object component = ((DefaultCellEditor)cellEditor).getComponent();
      ((AbstractButton)component).setSelected((Boolean)value);
    }
    else {
      ((StringTableCellEditor)cellEditor).setCellEditorValue(value);
    }

    cellEditor.stopCellEditing();
  }
}
