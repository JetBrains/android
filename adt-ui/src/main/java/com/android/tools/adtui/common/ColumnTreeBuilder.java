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
package com.android.tools.adtui.common;

import com.android.annotations.Nullable;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.tree.WideSelectionTreeUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.*;
import javax.swing.tree.AbstractLayoutCache;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * A ColumnTree is an alternative to TreeTables with slightly less functionality but
 * with a much more simple model. A column tree is composed by two things: A tree and
 * a set of headers. Each node in the tree renders all the "columns". This is
 * achieved by doing the following:
 *  - The tree is given a renderer that is a JPanel with a custom layout manager.
 *  - Each component of this panel is the renderer for each "Column"
 *  - The layout manager given to the panel uses the columns to decide how to align
 *    the components for each column
 *  - The TreeUI is changed on the tree to create components that extend the whole
 *    width of the tree.
 *
 * To set up your component do this:
 *    JComponent node = new ColumnTreeBuilder(myTree)
 *        .addColumn(new ColumnTreeBuilder.ColumnBuilder()
 *            .setName("Column 1")
 *            .setRenderer(new MyColumnOneRenderer()))
 *        .addColumn(new ColumnTreeBuilder.ColumnBuilder()
 *            .setName("Column 2")
 *            .setRenderer(new MyColumnTwoRenderer()))
 *        .build()
 */
public class ColumnTreeBuilder {
  @NotNull
  private final JTree myTree;

  @NotNull
  private final ColumnTreeCellRenderer myCellRenderer;

  @NotNull
  private final JTable myTable;

  @NotNull
  private final TableRowSorter<TableModel> myRowSorter;

  @NotNull
  private final DefaultTableModel myTableModel;

  @Nullable
  private TreeSorter myTreeSorter;

  @NotNull
  private List<ColumnBuilder> myColumBuilders;

  public ColumnTreeBuilder(@NotNull JTree tree) {
    myTree = tree;
    myTableModel = new DefaultTableModel();
    myTable = new JBTable(myTableModel);
    myCellRenderer = new ColumnTreeCellRenderer(myTree, myTable.getColumnModel());
    myRowSorter = new TableRowSorter<TableModel>(myTable.getModel());
    myColumBuilders = new LinkedList<ColumnBuilder>();
  }

  /**
   * Sets the tree sorter to call when a column wants to be sorted.
   */
  public ColumnTreeBuilder setTreeSorter(@NotNull TreeSorter sorter) {
    myTreeSorter = sorter;
    return this;
  }

  public JComponent build() {
    boolean showsRootHandles = myTree.getShowsRootHandles(); // Stash this value since it'll get stomped WideSelectionTreeUI.
    myTree.setUI(new ColumnTreeUI());
    myTree.setShowsRootHandles(showsRootHandles);
    myTree.setCellRenderer(myCellRenderer);

    myTable.getColumnModel().addColumnModelListener(new TableColumnModelListener() {
      @Override
      public void columnAdded(TableColumnModelEvent tableColumnModelEvent) {
      }

      @Override
      public void columnRemoved(TableColumnModelEvent tableColumnModelEvent) {
      }

      @Override
      public void columnMoved(TableColumnModelEvent tableColumnModelEvent) {
      }

      @Override
      public void columnMarginChanged(ChangeEvent changeEvent) {
        myTree.revalidate();
        myTree.repaint();
      }

      @Override
      public void columnSelectionChanged(ListSelectionEvent listSelectionEvent) {
      }
    });

    myTable.setRowSorter(myRowSorter);
    myRowSorter.addRowSorterListener(new RowSorterListener() {
      @Override
      public void sorterChanged(RowSorterEvent event) {
        if (myTreeSorter != null && !myRowSorter.getSortKeys().isEmpty()) {
          RowSorter.SortKey key = myRowSorter.getSortKeys().get(0);
          Comparator<?> comparator = myRowSorter.getComparator(key.getColumn());
          Enumeration<TreePath> expanded = myTree.getExpandedDescendants(new TreePath(myTree.getModel().getRoot()));
          comparator = key.getSortOrder() == SortOrder.ASCENDING ? comparator : Collections.reverseOrder(comparator);
          myTreeSorter.sort(comparator, key.getSortOrder());
          if (expanded != null) {
            while (expanded.hasMoreElements()) {
              myTree.expandPath(expanded.nextElement());
            }
          }
        }
      }
    });
    myTable.setAutoResizeMode(JTable.AUTO_RESIZE_NEXT_COLUMN);
    for (ColumnBuilder column : myColumBuilders) {
      column.create(myTableModel);
    }
    for (ColumnBuilder column : myColumBuilders) {
      column.configure(myTable, myRowSorter, myCellRenderer);
    }

    JPanel panel = new TreeWrapperPanel(myTable, myTree);

    JTableHeader header = myTable.getTableHeader();
    header.setReorderingAllowed(false);

    JViewport viewport = new JViewport();
    viewport.setView(header);

    JBScrollPane scrollPane = new JBScrollPane(panel);
    scrollPane.setColumnHeader(viewport);
    scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    return scrollPane;
  }

  public ColumnTreeBuilder addColumn(ColumnBuilder column) {
    myColumBuilders.add(column);
    return this;
  }

  public interface TreeSorter<T> {
    void sort(Comparator<T> comparator, SortOrder order);
  }

  /**
   * A custom layout manager to use while rendering a tree node.
   * It uses the given column model to know the size of the columns.
   */
  private static class ColumnLayout implements LayoutManager {
    @NotNull
    private final TableColumnModel myColumnModel;
    @NotNull
    private final JTree myTree;

    public ColumnLayout(@NotNull JTree tree, @NotNull TableColumnModel columnModel) {
      myTree = tree;
      myColumnModel = columnModel;
    }

    @Override
    public void addLayoutComponent(String name, Component comp) {
    }

    @Override
    public void removeLayoutComponent(Component comp) {
    }

    @Override
    public Dimension preferredLayoutSize(Container parent) {
      return parent.getSize();
    }

    @Override
    public Dimension minimumLayoutSize(Container parent) {
      return parent.getSize();
    }

    @Override
    public void layoutContainer(Container parent) {
      int size = parent.getComponentCount();
      int columns = myColumnModel.getColumnCount();
      assert size == columns;

      int padding = myTree.getWidth();
      for (int i = 0; i < size && i < columns; i++) {
        padding -= myColumnModel.getColumn(i).getWidth();
      }

      // The parent component has no fixed start position, but it fills the whole width, so we fill in reverse.
      int offset = parent.getWidth();
      for (int i = size - 1; i >= 0; i--) {
        Component component = parent.getComponent(i);
        TableColumn column = myColumnModel.getColumn(i);
        int width = Math.min(column.getWidth() + padding, offset);

        component.setBounds(offset - width, 0, width, parent.getHeight());
        offset -= width;
        padding = 0;
      }
    }
  }

  /**
   * The cell renderer to use on the tree. It's a simple panel that uses the ColumnLayout
   * to align its "columns". When rendered, it calls to its child renderers to set up their
   * widgets.
   */
  private static class ColumnTreeCellRenderer extends JPanel implements TreeCellRenderer {
    public ColumnTreeCellRenderer(JTree tree, TableColumnModel columnModel) {
      super(new ColumnLayout(tree, columnModel));
    }

    @Override
    public Component getTreeCellRendererComponent(JTree tree,
                                                  Object value,
                                                  boolean selected,
                                                  boolean expanded,
                                                  boolean leaf,
                                                  int row,
                                                  boolean hasFocus) {
      setFont(tree.getFont());
      for (int i = 0; i < getComponentCount(); i++) {
        Component component = getComponent(i);
        if (component instanceof ColoredTreeCellRenderer) {
          ((ColoredTreeCellRenderer)component).getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
        }
      }
      return this;
    }

    @Override
    public Dimension getPreferredSize() {
      Dimension dimension = new Dimension();
      for (int i = 0; i < getComponentCount(); i++) {
        Dimension size = getComponent(i).getPreferredSize();
        dimension.width += size.width;
        dimension.height = Math.max(dimension.height, size.height);
      }
      return dimension;
    }
  }

  /**
   * A custom TreeUI that is needed to adjust the components created by each node.
   * The width of each component on the tree must be all the way to the right, which
   * means that we need to know how much indentation is added at which point, and
   * this is known by the UI (see getRowX).
   */
  private static class ColumnTreeUI extends WideSelectionTreeUI {

    private int myWidth = -1;

    @Override
    public void paint(Graphics g, JComponent c) {
      if (myWidth != c.getWidth()) {
        treeState.invalidateSizes();
        myWidth = c.getWidth();
      }
      super.paint(g, c);
    }

    @Override
    protected AbstractLayoutCache.NodeDimensions createNodeDimensions() {
      return new NodeDimensionsHandler() {
        @Override
        public Rectangle getNodeDimensions(Object value, int row, int depth, boolean expanded, Rectangle size) {
          Rectangle dimensions = super.getNodeDimensions(value, row, depth, expanded, size);
          dimensions.width = tree.getWidth() - getRowX(row, depth);
          return dimensions;
        }
      };
    }
  }

  public static class ColumnBuilder {
    private String myName;
    private int myWidth;
    private int myHeaderAlignment;
    private Comparator<?> myComparator;
    private ColoredTreeCellRenderer myRenderer;
    private SortOrder myInitialOrder = SortOrder.UNSORTED;

    public ColumnBuilder setName(String name) {
      myName = name;
      return this;
    }

    public ColumnBuilder setPreferredWidth(int width) {
      myWidth = width;
      return this;
    }

    public ColumnBuilder setHeaderAlignment(int alignment) {
      myHeaderAlignment = alignment;
      return this;
    }

    public void create(DefaultTableModel model) {
      model.addColumn(myName);
    }

    public void configure(JTable table, TableRowSorter<TableModel> sorter, ColumnTreeCellRenderer renderer) {
      TableColumn column = table.getColumn(myName);
      column.setPreferredWidth(myWidth);

      final TableCellRenderer tableCellRenderer = table.getTableHeader().getDefaultRenderer();
      column.setHeaderRenderer(new DefaultTableCellRenderer() {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus, int row, int column) {
          Component component = tableCellRenderer.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
          if (component instanceof JLabel) {
            ((JLabel)component).setHorizontalAlignment(myHeaderAlignment);
          }
          return component;
        }
      });

      if (myComparator != null) {
        sorter.setComparator(column.getModelIndex(), myComparator);
        if (myInitialOrder != SortOrder.UNSORTED) {
          sorter.setSortKeys(Collections.singletonList(new RowSorter.SortKey(column.getModelIndex(), myInitialOrder)));
        }
      }
      else {
        sorter.setSortable(column.getModelIndex(), false);
      }
      assert myRenderer != null;
      renderer.add(myRenderer);
    }

    public ColumnBuilder setComparator(@NotNull Comparator<?> comparator) {
      myComparator = comparator;
      return this;
    }

    public ColumnBuilder setRenderer(@NotNull ColoredTreeCellRenderer renderer) {
      myRenderer = renderer;
      return this;
    }

    public ColumnBuilder setInitialOrder(@NotNull SortOrder initialOrder) {
      myInitialOrder = initialOrder;
      return this;
    }
  }

  private static class TreeWrapperPanel extends JPanel implements Scrollable {
    private final JTree myTree;

    public TreeWrapperPanel(JTable table, JTree tree) {
      super(new BorderLayout());

      myTree = tree;

      add(table, BorderLayout.NORTH);
      add(myTree, BorderLayout.CENTER);
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
      return myTree.getPreferredScrollableViewportSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
      return myTree.getScrollableUnitIncrement(visibleRect, orientation, direction);
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
      return myTree.getScrollableUnitIncrement(visibleRect, orientation, direction);
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
      return myTree.getScrollableTracksViewportWidth();
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
      return myTree.getScrollableTracksViewportHeight();
    }
  }
}
