/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.editors.strings.table;

import com.android.tools.idea.editors.strings.StringResourceData;
import com.android.tools.idea.ui.TableUtils;
import com.intellij.ide.PasteProvider;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.ui.TableSpeedSearch;
import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.CellEditorListener;
import javax.swing.event.ChangeEvent;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import java.awt.datatransfer.Transferable;
import java.awt.event.MouseAdapter;
import java.util.Arrays;

public final class StringResourceTable extends JBTable implements DataProvider, PasteProvider {
  public StringResourceTable() {
    super(new StringResourceTableModel());

    CellEditorListener editorListener = new CellEditorListener() {
      @Override
      public void editingStopped(ChangeEvent event) {
        refilter();
      }

      @Override
      public void editingCanceled(ChangeEvent event) {
      }
    };

    getDefaultEditor(Boolean.class).addCellEditorListener(editorListener);

    JTableHeader header = getTableHeader();
    MouseAdapter mouseListener = new HeaderCellSelectionListener(this);

    header.setReorderingAllowed(false);
    header.addMouseListener(mouseListener);
    header.addMouseMotionListener(mouseListener);

    TableCellEditor editor = new StringsCellEditor();
    editor.addCellEditorListener(editorListener);

    setCellSelectionEnabled(true);
    setDefaultEditor(String.class, editor);

    new TableSpeedSearch(this);
  }

  public void refilter() {
    @SuppressWarnings("unchecked")
    DefaultRowSorter<StringResourceTableModel, Integer> rowSorter = (DefaultRowSorter<StringResourceTableModel, Integer>)getRowSorter();

    if (rowSorter != null) {
      rowSorter.sort();
    }
  }

  @Nullable
  public StringResourceData getData() {
    return ((StringResourceTableModel)getModel()).getData();
  }

  public int getSelectedRowModelIndex() {
    return convertRowIndexToModel(getSelectedRow());
  }

  @NotNull
  public int[] getSelectedRowModelIndices() {
    return Arrays.stream(getSelectedRows())
      .map(this::convertRowIndexToModel)
      .toArray();
  }

  public int getSelectedColumnModelIndex() {
    return convertColumnIndexToModel(getSelectedColumn());
  }

  public void setShowingOnlyKeysNeedingTranslations(boolean showingOnlyKeysNeedingTranslations) {
    DefaultRowSorter<TableModel, Integer> rowSorter;

    if (showingOnlyKeysNeedingTranslations) {
      rowSorter = new TableRowSorter<>(getModel());
      rowSorter.setRowFilter(new NeedsTranslationsRowFilter());
    }
    else {
      rowSorter = null;
    }

    setRowSorter(rowSorter);
  }

  private static final class NeedsTranslationsRowFilter extends RowFilter<TableModel, Integer> {
    @Override
    public boolean include(@NotNull Entry<? extends TableModel, ? extends Integer> entry) {
      if ((Boolean)entry.getValue(StringResourceTableModel.UNTRANSLATABLE_COLUMN)) {
        return false;
      }

      for (int i = StringResourceTableModel.FIXED_COLUMN_COUNT; i < entry.getValueCount(); i++) {
        if (entry.getValue(i).equals("")) {
          return true;
        }
      }

      return false;
    }
  }

  @Nullable
  @Override
  public Object getData(@NotNull String dataId) {
    return dataId.equals(PlatformDataKeys.PASTE_PROVIDER.getName()) ? this : null;
  }

  @Override
  public boolean isPastePossible(@NotNull DataContext dataContext) {
    if (getSelectedRowCount() != 1 || getSelectedColumnCount() != 1) {
      return false;
    }
    else {
      int column = getSelectedColumn();
      return column != StringResourceTableModel.KEY_COLUMN && column != StringResourceTableModel.UNTRANSLATABLE_COLUMN;
    }
  }

  @Override
  public boolean isPasteEnabled(@NotNull DataContext dataContext) {
    return isPastePossible(dataContext);
  }

  @Override
  public void performPaste(@NotNull DataContext dataContext) {
    Transferable transferable = CopyPasteManager.getInstance().getContents();

    if (transferable != null) {
      TableUtils.paste(this, transferable);
    }
  }
}
