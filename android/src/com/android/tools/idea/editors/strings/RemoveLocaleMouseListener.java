/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.tools.idea.editors.strings.table.StringResourceTableModel;
import com.android.tools.idea.rendering.Locale;
import com.intellij.openapi.ui.JBMenuItem;
import com.intellij.openapi.ui.JBPopupMenu;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

final class RemoveLocaleMouseListener extends MouseAdapter {
  private final StringResourceViewPanel myPanel;

  RemoveLocaleMouseListener(@NotNull StringResourceViewPanel panel) {
    myPanel = panel;
  }

  @Override
  public void mousePressed(@NotNull MouseEvent event) {
    showRemoveLocalePopupMenu(event);
  }

  @Override
  public void mouseReleased(@NotNull MouseEvent event) {
    showRemoveLocalePopupMenu(event);
  }

  private void showRemoveLocalePopupMenu(@NotNull MouseEvent event) {
    if (!event.isPopupTrigger()) {
      return;
    }

    JTableHeader header = (JTableHeader)event.getSource();
    Point point = event.getPoint();
    int column = header.columnAtPoint(point);

    if (column == -1) {
      return;
    }

    JTable table = header.getTable();
    Locale locale = ((StringResourceTableModel)table.getModel()).getLocale(table.convertColumnIndexToModel(column));

    if (locale == null) {
      return;
    }

    JMenuItem item = new JBMenuItem("Remove Locale");
    item.setName("removeLocaleMenuItem");

    item.addActionListener(e -> {
      StringsWriteUtils.removeLocale(locale, myPanel.getFacet(), this);
      myPanel.reloadData();
    });

    JPopupMenu menu = new JBPopupMenu();

    menu.add(item);
    menu.show(header, point.x, point.y);
  }
}
