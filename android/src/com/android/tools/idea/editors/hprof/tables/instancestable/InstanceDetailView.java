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
package com.android.tools.idea.editors.hprof.tables.instancestable;

import com.android.tools.idea.editors.hprof.tables.HprofTreeTable;
import com.intellij.ui.treeStructure.treetable.ListTreeTableModelOnColumns;
import com.intellij.ui.treeStructure.treetable.TreeTableModel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.plaf.TreeUI;
import javax.swing.table.TableCellRenderer;
import javax.swing.tree.TreeNode;
import java.awt.*;

public class InstanceDetailView extends HprofTreeTable {
  @NotNull private TreeToTableCellRendererAdapter myTreeToTableCellRendererAdapter;

  public InstanceDetailView(@NotNull ListTreeTableModelOnColumns columns) {
    super(columns);
    setTreeCellRenderer(new InstanceDetailTreeRenderer());
    myTreeToTableCellRendererAdapter = new TreeToTableCellRendererAdapter(getTree(), columns);
  }

  @Override
  public void setModel(@NotNull TreeTableModel model) {
    super.setModel(model);
    assert (model instanceof InstanceDetailModel);
    prettifyTable();
  }

  @Override
  public TableCellRenderer getCellRenderer(int row, int column) {
    if (column == 0) {
      return myTreeToTableCellRendererAdapter;
    }
    else {
      return super.getCellRenderer(row, column);
    }
  }

  private static class TreeToTableCellRendererAdapter implements TableCellRenderer {
    @NotNull JTree myTree;
    @NotNull ListTreeTableModelOnColumns myColumns;

    public TreeToTableCellRendererAdapter(@NotNull JTree tree, @NotNull ListTreeTableModelOnColumns columns) {
      myTree = tree;
      myColumns = columns;
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      TreeNode node = (TreeNode)myColumns.getRowValue(row);
      return myTree.getCellRenderer()
        .getTreeCellRendererComponent(myTree, node, isSelected, myTree.isExpanded(row), node.isLeaf(), row, hasFocus);
    }
  }
}
