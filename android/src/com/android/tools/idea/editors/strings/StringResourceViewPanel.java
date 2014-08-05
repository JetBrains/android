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

import com.android.tools.idea.editors.strings.table.CellSelectionListener;
import com.android.tools.idea.editors.strings.table.HeaderCellSelectionListener;
import com.android.tools.idea.editors.strings.table.TableResizeListener;
import com.android.tools.idea.rendering.StringResourceData;
import com.android.tools.idea.editors.strings.table.StringResourceTableUtil;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.TableSpeedSearch;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.MouseAdapter;

public class StringResourceViewPanel {
  private JBSplitter myContainer;

  private JPanel myCellDetailPanel;
  private JBTextField myKey;
  private JTextArea myDefaultValue;
  private JTextArea myTranslation;

  private JBScrollPane myTablePane;
  private JBTable myTable;

  public StringResourceViewPanel() {
    initCellDetailPanel();
    myContainer.setFirstComponent(myCellDetailPanel);
    initTable();
    myContainer.setSecondComponent(myTablePane);
    myContainer.setProportion(0f);
  }

  private void initCellDetailPanel() {
    myCellDetailPanel.setBorder(IdeBorderFactory.createEmptyBorder(5));

    Font font = myKey.getFont();
    formatTextComponent(myDefaultValue, font);
    formatTextComponent(myTranslation, font);
  }

  private static void formatTextComponent(@NotNull JTextComponent component, @NotNull Font font) {
    component.setFont(font);
    if (component.getParent() instanceof JViewport) {
      component.getParent().setMinimumSize(new Dimension(0, 2 * component.getFontMetrics(font).getHeight()));
    }
  }

  private void initTable() {
    myTable.setCellSelectionEnabled(true);

    MouseAdapter headerListener = new HeaderCellSelectionListener(myTable);
    myTable.getTableHeader().addMouseListener(headerListener);
    myTable.getTableHeader().addMouseMotionListener(headerListener);

    CellSelectionListener selectionListener = new CellSelectionListener(myTable, myKey, myDefaultValue, myTranslation);
    myTable.getSelectionModel().addListSelectionListener(selectionListener);
    myTable.getColumnModel().getSelectionModel().addListSelectionListener(selectionListener);

    myTable.getParent().addComponentListener(new TableResizeListener(myTable));

    new TableSpeedSearch(myTable) {
      @Override
      public int getElementCount() {
        // TableSpeedSearch computes the element count from the underlying model, which is problematic when not all cells are visible
        return myComponent.getRowCount() * myComponent.getColumnCount();
      }
    };
  }

  public void setStringResourceData(@NotNull StringResourceData data) {
    StringResourceTableUtil.initData(myTable, data);
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
