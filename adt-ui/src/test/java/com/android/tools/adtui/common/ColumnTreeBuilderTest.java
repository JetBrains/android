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
package com.android.tools.adtui.common;

import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.LoadingNode;
import com.intellij.ui.treeStructure.Tree;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.TreeUI;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;

import static com.google.common.truth.Truth.assertThat;

@RunWith(JUnit4.class)
public class ColumnTreeBuilderTest {

  @Test
  public void testTreeFillsViewportIfSmallerThanViewport() throws Exception {
    // Prepare
    ColumnTreeTestInfo tableInfo = createTestTable(null);
    JTree tree = tableInfo.getTree();
    JScrollPane columnTreePane = tableInfo.getScrollPane();

    // Assert: Check the tree height has been extended to the whole viewport
    assertThat(tree.getHeight()).isGreaterThan(0);
    assertThat(tree.getHeight()).isEqualTo(columnTreePane.getViewport().getHeight());
  }

  @Test
  public void testSetColumnHeaderBorderWorks() throws Exception {
    int left = 10;
    int right = 6;
    int top = 5;
    int bottom = 8;

    // Prepare
    TableHeaderSizes sizesNormal = getTestTableSizes(null);
    TableHeaderSizes sizesWithBorder = getTestTableSizes(BorderFactory.createEmptyBorder(top, left, bottom, right));

    // Assert: Check the tree height has been extended to the whole viewport
    assertThat(sizesNormal.headerSize.width).isEqualTo(sizesWithBorder.headerSize.width);
    assertThat(sizesNormal.headerSize.height).isEqualTo(sizesWithBorder.headerSize.height - top - bottom);

    assertThat(sizesNormal.column0Size.width).isEqualTo(sizesWithBorder.column0Size.width - left - right);
    assertThat(sizesNormal.column0Size.height).isEqualTo(sizesWithBorder.column0Size.height - top - bottom);

    assertThat(sizesNormal.column1Size.width).isEqualTo(sizesWithBorder.column1Size.width - left - right);
    assertThat(sizesNormal.column1Size.height).isEqualTo(sizesWithBorder.column1Size.height - top - bottom);
  }

  @Test
  public void testTreeToolTipIsNullByDefault() throws Exception {
    ColumnTreeTestInfo info = createTestTable(null);
    JTree tree = info.getTree();

    Component component =
      tree.getCellRenderer().getTreeCellRendererComponent(tree, tree.getModel().getRoot(), false, false, true, 0, false);

    assertThat(component).isInstanceOf(JComponent.class);
    assertThat(((JComponent)component).getToolTipText()).isNull();
  }

  @Test
  public void testTreeToolTipIsSetFromColumnRenderer() throws Exception {
    String toolTipTestText = "This is a test";

    ColumnTreeTestInfo info = createTestTable(null, new MyEmptyRenderer() {
      @Override
      public String getToolTipText() {
        return toolTipTestText;
      }
    });

    JTree tree = info.getTree();
    Component component =
      tree.getCellRenderer().getTreeCellRendererComponent(tree, tree.getModel().getRoot(), false, false, true, 0, false);

    assertThat(component).isInstanceOf(JComponent.class);
    assertThat(((JComponent)component).getToolTipText()).isEqualTo(toolTipTestText);
  }

  @Test
  public void updatingUIShouldPreserveColumnTreeUI() {
    ColumnTreeTestInfo info = createTestTable(null);
    JTree tree = info.getTree();
    TreeUI ui = tree.getUI();
    tree.updateUI();
    assertThat(tree.getUI().getClass()).isSameAs(ui.getClass());
    // ColumnTreeBuilder should create a new ui.
    assertThat(tree.getUI()).isNotSameAs(ui);
  }

  private ColumnTreeTestInfo createTestTable(Border headerBorder) {
    return createTestTable(headerBorder, new MyEmptyRenderer());
  }

  private ColumnTreeTestInfo createTestTable(Border headerBorder, ColoredTreeCellRenderer cellRenderer) {
    DefaultTreeModel treeModel = new DefaultTreeModel(new LoadingNode());
    Tree tree = new Tree(treeModel);

    // Setup
    ColumnTreeBuilder builder = new ColumnTreeBuilder(tree)
      .addColumn(new ColumnTreeBuilder.ColumnBuilder()
                   .setName("A")
                   .setPreferredWidth(30)
                   .setHeaderAlignment(SwingConstants.LEADING)
                   .setHeaderBorder(headerBorder)
                   .setRenderer(cellRenderer))
      .addColumn(new ColumnTreeBuilder.ColumnBuilder()
                   .setName("B")
                   .setPreferredWidth(60)
                   .setHeaderAlignment(SwingConstants.TRAILING)
                   .setHeaderBorder(headerBorder)
                   .setRenderer(cellRenderer));

    JScrollPane columnTreePane = (JScrollPane)builder.build();
    columnTreePane.setPreferredSize(new Dimension(100, 100));
    ColumnTreeTestInfo info = new ColumnTreeTestInfo(tree, columnTreePane);
    info.simulateLayout(new Dimension(100, 100));
    return info;
  }

  private TableHeaderSizes getTestTableSizes(Border headerBorder) {
    ColumnTreeTestInfo builder = createTestTable(headerBorder);

    // Retrieve sizes
    JTable table = builder.getTable();
    TableHeaderSizes sizes = new TableHeaderSizes();
    sizes.headerSize = table.getTableHeader().getPreferredSize();
    sizes.column0Size = getColumnHeaderComponent(table.getTableHeader(), 0).getPreferredSize();
    sizes.column1Size = getColumnHeaderComponent(table.getTableHeader(), 1).getPreferredSize();

    return sizes;
  }

  private Component getColumnHeaderComponent(JTableHeader header, int columnIndex) {
    TableColumn column = header.getColumnModel().getColumn(columnIndex);
    TableCellRenderer renderer = column.getHeaderRenderer();
    if (renderer == null) {
      renderer = header.getDefaultRenderer();
    }

    return renderer.getTableCellRendererComponent(header.getTable(), column.getHeaderValue(), false, false, -1, columnIndex);
  }

  private class TableHeaderSizes {
    public Dimension headerSize;
    public Dimension column0Size;
    public Dimension column1Size;
  }

  private class MyEmptyRenderer extends ColoredTreeCellRenderer {
    @Override
    public void customizeCellRenderer(@NotNull JTree tree,
                                      Object value,
                                      boolean selected,
                                      boolean expanded,
                                      boolean leaf,
                                      int row,
                                      boolean hasFocus) {
    }
  }
}