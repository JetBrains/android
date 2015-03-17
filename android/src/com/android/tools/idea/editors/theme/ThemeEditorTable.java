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
import spantable.CellSpanTable;

import javax.swing.*;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.event.ActionListener;

public class ThemeEditorTable extends CellSpanTable {
  private final JBPopupMenu myPopupMenu;
  private ActionListener myLastActionListener = null;
  private final JMenuItem myGoToDefinitionItem;

  public ThemeEditorTable() {
    myPopupMenu = new JBPopupMenu();
    myGoToDefinitionItem = myPopupMenu.add(new JMenuItem("Go to definition"));
  }

  @Override
  public JPopupMenu getComponentPopupMenu() {
    final Point point = getMousePosition();
    return getPopupMenuAtCell(rowAtPoint(point), columnAtPoint(point));
  }

  private JPopupMenu getPopupMenuAtCell(final int row, final int column) {
    TableModel model = getModel();
    if (!(model instanceof AttributesTableModel)) {
      return null;
    }

    AttributesTableModel.RowContents contents = ((AttributesTableModel) model).getRowContents(row);
    if (contents == null) {
      return null;
    }

    ActionListener callback = contents.getGoToDefinitionCallback();
    if (callback == null) {
      return null;
    }

    setActionListener(callback);
    return myPopupMenu;
  }

  private void setActionListener(ActionListener callback) {
    if (myLastActionListener != null) {
      myGoToDefinitionItem.removeActionListener(myLastActionListener);
    }

    myGoToDefinitionItem.addActionListener(callback);
    myLastActionListener = callback;
  }
}
