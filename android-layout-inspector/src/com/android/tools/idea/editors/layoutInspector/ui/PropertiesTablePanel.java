/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.editors.layoutInspector.ui;

import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.tools.adtui.ptable.PTable;
import com.android.tools.adtui.ptable.PTableGroupItem;
import com.android.tools.adtui.ptable.PTableItem;
import com.android.tools.adtui.ptable.PTableModel;
import com.android.tools.adtui.workbench.ToolContent;
import com.android.tools.idea.editors.layoutInspector.LayoutInspectorContext;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.SpeedSearchComparator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import javax.swing.table.TableRowSorter;
import java.awt.*;

public class PropertiesTablePanel extends JScrollPane implements ToolContent<LayoutInspectorContext> {
  public static final JBColor GROUP_BACKGROUND_COLOR = new JBColor(Gray._242, new Color(60, 63, 65));
  public static final JBColor ITEM__BACKGROUND_COLOR = new JBColor(Gray._252, new Color(49, 52, 53));

  private PTable myTable;

  private TableRowSorter<PTableModel> myRowSorter;
  private MyFilter myFilter;
  private PTableModel myModel;

  public PropertiesTablePanel() {
    myFilter = new MyFilter();
    myRowSorter = new TableRowSorter<>();
  }

  @Override
  public void dispose() {

  }

  @Override
  public void setToolContext(@Nullable LayoutInspectorContext toolContext) {
    if (toolContext != null) {
      myModel = toolContext.getTableModel();
      myTable = toolContext.getPropertiesTable();
      setViewportView(myTable);
    }
  }

  @NotNull
  @TestOnly
  PTable getTable() {
    return myTable;
  }

  @Override
  @NotNull
  public JComponent getComponent() {
    return this;
  }

  @Override
  public boolean supportsFiltering() {
    return true;
  }

  @Override
  public void setFilter(@NotNull String filter) {
    int selectedRow = myTable.getSelectedRow();
    PTableItem selectedItem = myTable.getSelectedItem();
    if (filter.isEmpty()) {
      myTable.setRowSorter(null);
    }
    else {
      myFilter.setPattern(filter);
      myRowSorter.setModel(myModel);
      myRowSorter.setRowFilter(myFilter);
      myRowSorter.setSortKeys(null);
      myTable.setRowSorter(myRowSorter);
    }
    myTable.restoreSelection(selectedRow, selectedItem);
  }

  static class MyFilter extends RowFilter<PTableModel, Integer> {
    private final SpeedSearchComparator myComparator = new SpeedSearchComparator(false);
    private String myPattern = "";

    @VisibleForTesting
    void setPattern(@NotNull String pattern) {
      myPattern = pattern;
    }

    @Override
    public boolean include(Entry<? extends PTableModel, ? extends Integer> entry) {
      PTableItem item = (PTableItem)entry.getValue(0);
      if (isMatch(item.getName())) {
        return true;
      }
      if (item.getParent() != null && isMatch(item.getParent().getName())) {
        return true;
      }
      if (!(item instanceof PTableGroupItem)) {
        return false;
      }
      PTableGroupItem group = (PTableGroupItem)item;
      for (PTableItem child : group.getChildren()) {
        if (isMatch(child.getName())) {
          return true;
        }
      }
      return false;
    }

    private boolean isMatch(@NotNull String text) {
      return myComparator.matchingFragments(myPattern, text) != null;
    }
  }
}
