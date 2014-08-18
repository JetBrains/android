/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.android.tools.idea.editors.strings.table.*;
import com.android.tools.idea.editors.strings.table.ColumnUtil;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.TableSpeedSearch;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.FocusListener;
import java.awt.event.MouseAdapter;

public class StringResourceViewPanel {
  private JBSplitter myContainer;

  private JPanel myEditPanel;
  private JBTextField myKey;
  private JTextArea myDefaultValue;
  private JTextArea myTranslation;

  private JBScrollPane myTablePane;
  private JBTable myTable;

  public StringResourceViewPanel() {
    initEditPanel();
    myContainer.setFirstComponent(myEditPanel);
    initTable();
    myContainer.setSecondComponent(myTablePane);
    myContainer.setProportion(0f);
  }

  private void initEditPanel() {
    myEditPanel.setBorder(IdeBorderFactory.createEmptyBorder(5));
    TextComponentUtil.formatTextComponent(myKey);
    TextComponentUtil.formatTextComponent(myDefaultValue);
    TextComponentUtil.formatTextComponent(myTranslation);

    FocusListener editFocusListener = new EditFocusListener(myTable, myKey, myDefaultValue, myTranslation);
    myKey.addFocusListener(editFocusListener);
    myDefaultValue.addFocusListener(editFocusListener);
    myTranslation.addFocusListener(editFocusListener);
  }

  private void initTable() {
    myTable.setCellSelectionEnabled(true);
    myTable.getTableHeader().setReorderingAllowed(false);

    MouseAdapter headerListener = new HeaderCellSelectionListener(myTable);
    myTable.getTableHeader().addMouseListener(headerListener);
    myTable.getTableHeader().addMouseMotionListener(headerListener);

    CellSelectionListener selectionListener = new CellSelectionListener(myTable, myKey, myDefaultValue, myTranslation);
    myTable.getSelectionModel().addListSelectionListener(selectionListener);
    myTable.getColumnModel().getSelectionModel().addListSelectionListener(selectionListener);

    myTable.setDefaultEditor(String.class, new MultilineCellEditor());
    myTable.getParent().addComponentListener(new ResizeListener(myTable));
    new TableSpeedSearch(myTable) {
      @Override
      public int getElementCount() {
        // TableSpeedSearch computes the element count from the underlying model, which is problematic when not all cells are visible
        return myComponent.getRowCount() * myComponent.getColumnCount();
      }
    };
  }

  public void initDataController(@NotNull StringResourceDataController controller) {
    myTable.setModel(new StringResourceTableModel(controller));
    ColumnUtil.setColumns(myTable);
  }

  public void onDataUpdated() {
    StringResourceTableModel model = (StringResourceTableModel) myTable.getModel();
    model.fireTableStructureChanged();
    ColumnUtil.setColumns(myTable);
  }

  @NotNull
  public JPanel getComponent() {
    return myContainer;
  }

  @NotNull
  public JBTable getPreferredFocusedComponent() {
    return myTable;
  }
}
