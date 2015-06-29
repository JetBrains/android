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
package com.android.tools.idea.editors.theme;

import com.android.tools.idea.editors.theme.attributes.AttributesTableModel;
import com.intellij.openapi.ui.JBPopupMenu;
import spantable.CellSpanModel;
import spantable.CellSpanTable;

import javax.swing.*;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.Map;

public class ThemeEditorTable extends CellSpanTable {
  private final JBPopupMenu myPopupMenu;
  private ActionListener myLastDefinitionActionListener = null;
  private ActionListener myLastResetActionListener;
  private final JMenuItem myGoToDefinitionItem;
  private final JMenuItem myResetItem;
  private Map<Class<?>, Integer> myClassHeights;

  public ThemeEditorTable() {
    myPopupMenu = new JBPopupMenu();
    myGoToDefinitionItem = myPopupMenu.add(new JMenuItem("Go to definition"));
    myGoToDefinitionItem.setVisible(false);
    myResetItem = myPopupMenu.add(new JMenuItem("Reset value"));
    myResetItem.setVisible(false);
  }

  @Override
  public JPopupMenu getComponentPopupMenu() {
    // Workaround for http://b.android.com/173610
    // getMousePosition returns null sometimes even when the component is visible and the mouse is over the component. This seems to be a
    // bug in LWWindowPeer after a modal dialog has been displayed.
    Point point = MouseInfo.getPointerInfo().getLocation();
    SwingUtilities.convertPointFromScreen(point, this);
    return getPopupMenuAtCell(rowAtPoint(point), columnAtPoint(point));
  }

  @Override
  public void setRowSorter(RowSorter<? extends TableModel> sorter) {
    super.setRowSorter(sorter);
    updateRowHeights();
  }

  public void updateRowHeights() {
    TableModel rawModel = getModel();
    if (!(rawModel instanceof CellSpanModel)) {
      return;
    }

    CellSpanModel myModel = (CellSpanModel)rawModel;

    setRowHeight(myClassHeights.get(Object.class));
    for (int row = 0; row < myModel.getRowCount(); row++) {
      final Class<?> cellClass = myModel.getCellClass(row, 0);
      final Integer rowHeight = myClassHeights.get(cellClass);
      if (rowHeight != null) {
        int viewRow = convertRowIndexToView(row);

        if (viewRow != -1) {
          setRowHeight(viewRow, rowHeight);
        }
      }
    }
  }

  public void setClassHeights(Map<Class<?>, Integer> classHeights) {
    myClassHeights = classHeights;
    updateRowHeights();
  }

  private JPopupMenu getPopupMenuAtCell(final int row, final int column) {
    if (row < 0 || column < 0) {
      return null;
    }

    myGoToDefinitionItem.setVisible(false);
    myResetItem.setVisible(false);
    TableModel model = getModel();
    if (!(model instanceof AttributesTableModel)) {
      return null;
    }

    AttributesTableModel.RowContents contents = ((AttributesTableModel) model).getRowContents(this.convertRowIndexToModel(row));
    if (contents == null) {
      return null;
    }

    ActionListener definitionCallback = contents.getGoToDefinitionCallback();
    ActionListener resetCallback = contents.getResetCallback();
    if (definitionCallback == null && resetCallback == null) {
      return null;
    }

    if (definitionCallback != null) {
      myGoToDefinitionItem.setVisible(true);
      setDefinitionActionListener(definitionCallback);
    }

    if (resetCallback != null) {
      myResetItem.setVisible(true);
      setResetActionListener(resetCallback);
    }

    return myPopupMenu;
  }

  private void setDefinitionActionListener(ActionListener callback) {
    if (myLastDefinitionActionListener != null) {
      myGoToDefinitionItem.removeActionListener(myLastDefinitionActionListener);
    }

    myGoToDefinitionItem.addActionListener(callback);
    myLastDefinitionActionListener = callback;
  }

  private void setResetActionListener(ActionListener callback) {
    if (myLastResetActionListener != null) {
      myResetItem.removeActionListener(myLastResetActionListener);
    }

    myResetItem.addActionListener(callback);
    myLastResetActionListener = callback;
  }
}
