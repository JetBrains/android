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
import com.android.tools.adtui.RangeScrollBarUI;
import com.android.tools.adtui.TabularLayout;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBScrollBar;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.WideSelectionTreeUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.*;
import javax.swing.table.*;
import javax.swing.tree.AbstractLayoutCache;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A ColumnTree is an alternative to TreeTables with slightly less functionality but
 * with a much more simple model. A column tree is composed by two things: A tree and
 * a set of headers. Each node in the tree renders all the "columns". This is
 * achieved by doing the following:
 * - The tree is given a renderer that is a JPanel with a custom layout manager.
 * - Each component of this panel is the renderer for each "Column"
 * - The layout manager given to the panel uses the columns to decide how to align
 * the components for each column
 * - The TreeUI is changed on the tree to create components that extend the whole
 * width of the tree.
 *
 * To set up your component do this:
 * JComponent node = new ColumnTreeBuilder(myTree)
 * .addColumn(new ColumnTreeBuilder.ColumnBuilder()
 * .setName("Column 1")
 * .setRenderer(new MyColumnOneRenderer()))
 * .addColumn(new ColumnTreeBuilder.ColumnBuilder()
 * .setName("Column 2")
 * .setRenderer(new MyColumnTwoRenderer()))
 * .build()
 */
public class ColumnTreeBuilder {
  @NotNull
  private final JTree myTree;

  @NotNull
  private final ColumnTreeCellRenderer myCellRenderer;

  @NotNull
  private final JTable myTable;

  @NotNull
  private final DefaultTableModel myTableModel;

  @Nullable
  private TreeSorter myTreeSorter;

  @NotNull
  private List<ColumnBuilder> myColumnBuilders;

  @Nullable
  private Border myBorder;

  @Nullable
  private Color myBackground;

  @Nullable
  private Color myHoverColor;

  @NotNull
  private final ColumnTreeScrollPanel myHScrollBarPanel;

  private static final String LAST_TREE_PREFERRED_WIDTH = "last.tree.width";

  private static final String PREFERRED_TREE_WIDTH = "tree.width";

  private static final String TREE_OFFSET = "tree.offset";

  public ColumnTreeBuilder(@NotNull JTree tree) {
    this(tree, null);
  }

  public ColumnTreeBuilder(@NotNull JTree tree, TableColumnModel tableColumnModel) {
    myTree = tree;
    myTableModel = new DefaultTableModel();
    myTable = new JBTable(myTableModel, tableColumnModel);
    myHScrollBarPanel = new ColumnTreeScrollPanel(myTree, myTable);
    myTable.setAutoCreateColumnsFromModel(true);
    myTable.setShowVerticalLines(false);
    myTable.setFocusable(false);
    myCellRenderer = new ColumnTreeCellRenderer(myTree, myTable.getColumnModel());
    myColumnBuilders = new LinkedList<>();
  }

  /**
   * Sets the tree sorter to call when a column wants to be sorted.
   */
  public ColumnTreeBuilder setTreeSorter(@NotNull TreeSorter<?> sorter) {
    myTreeSorter = sorter;
    return this;
  }

  public ColumnTreeBuilder setBorder(@NotNull Border border) {
    myBorder = border;
    return this;
  }

  public ColumnTreeBuilder setBackground(@NotNull Color background) {
    myBackground = background;
    return this;
  }

  public ColumnTreeBuilder setHoverColor(@NotNull Color hoverColor) {
    myHoverColor = hoverColor;
    return this;
  }

  public ColumnTreeBuilder setShowVerticalLines(boolean showVerticalLines) {
    myTable.setShowVerticalLines(showVerticalLines);
    return this;
  }

  public ColumnTreeBuilder setTableFocusable(boolean focusable) {
    myTable.setFocusable(focusable);
    return this;
  }

  @NotNull
  public ColumnTreeBuilder setTableIntercellSpacing(Dimension spacing) {
    myTable.setIntercellSpacing(spacing);
    return this;
  }

  public JComponent build() {
    boolean showsRootHandles = myTree.getShowsRootHandles(); // Stash this value since it'll get stomped WideSelectionTreeUI.
    final ColumnTreeHoverListener hoverListener = myHoverColor != null ? ColumnTreeHoverListener.create(myTree) : null;
    myTree.setUI(new ColumnTreeUI(myHoverColor, hoverListener, myTable, myHScrollBarPanel));

    myTree.addPropertyChangeListener(evt -> {
      if (evt.getPropertyName().equals("UI")) {
        // We need to preserve ColumnTreeUI always,
        // Otherwise width of rows will be wrong and the table will lose its formatting.
        if (!(evt.getNewValue() instanceof ColumnTreeUI)) {
          myTree.setUI(new ColumnTreeUI(myHoverColor, hoverListener, myTable, myHScrollBarPanel));
        }
      }
    });

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
        myHScrollBarPanel.updateScrollBar();
      }

      @Override
      public void columnSelectionChanged(ListSelectionEvent listSelectionEvent) {
      }
    });

    ColumnTreeTableRowSorter rowSorter = new ColumnTreeTableRowSorter(
      myTable.getModel(), myColumnBuilders.stream().map(cb -> cb.mySortOrderPreference).collect(Collectors.toList()));

    myTable.setRowSorter(rowSorter);
    rowSorter.addRowSorterListener(event -> {
      if (myTreeSorter != null && !rowSorter.getSortKeys().isEmpty()) {
        RowSorter.SortKey key = rowSorter.getSortKeys().get(0);
        Comparator<?> comparator = rowSorter.getComparator(key.getColumn());
        Enumeration<TreePath> expanded = myTree.getExpandedDescendants(new TreePath(myTree.getModel().getRoot()));
        comparator = key.getSortOrder() == SortOrder.ASCENDING ? comparator : Collections.reverseOrder(comparator);
        myTreeSorter.sort(comparator, key.getSortOrder());
        if (expanded != null) {
          while (expanded.hasMoreElements()) {
            myTree.expandPath(expanded.nextElement());
          }
        }
      }
    });
    myTable.setAutoResizeMode(JTable.AUTO_RESIZE_NEXT_COLUMN);
    for (ColumnBuilder column : myColumnBuilders) {
      column.create(myTableModel);
    }

    for (int i = 0; i < myColumnBuilders.size(); i++) {
      ColumnBuilder column = myColumnBuilders.get(i);
      column.configure(i, myTable, rowSorter, myCellRenderer);
    }

    JPanel panel = new TreeWrapperPanel(myTable, myTree);
    if (myBackground != null) {
      panel.setBackground(myBackground);
    }

    JTableHeader header = myTable.getTableHeader();
    header.setReorderingAllowed(false);

    JViewport viewport = new JViewport();
    viewport.setView(header);

    JBScrollPane scrollPane = new JBScrollPane(panel);
    scrollPane.setColumnHeader(viewport);
    scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

    JPanel outerPanel = new JPanel(new BorderLayout());
    outerPanel.add(scrollPane, BorderLayout.CENTER);
    outerPanel.add(myHScrollBarPanel, BorderLayout.SOUTH);

    if (myBorder != null) {
      scrollPane.setBorder(myBorder);
    }
    myHScrollBarPanel.setBorder(scrollPane.getBorder());
    return outerPanel;
  }

  private static int getTreeColumnWidth(JTable table) {
    if (table == null || table.getColumnModel().getColumnCount() == 0) {
      return 0;
    }
    return table.getColumnModel().getColumn(0).getWidth();
  }


  /**
   * Returns the preferred width of the tree column. This is the maximum of the preferred width only
   * taking into account the first column.
   * Used to determine the ideal size of the first column and show it in the scrollbar.
   */
  private static int getPreferredTreeWidth(JTree tree) {
    Object value = tree.getClientProperty(PREFERRED_TREE_WIDTH);
    return value == null ? 0 : (Integer)value;
  }

  private static void setPreferredTreeWidth(JTree tree, int width) {
    tree.putClientProperty(PREFERRED_TREE_WIDTH, width);
  }

  /**
   * How much of the tree column is off screen (how much it is offset from the beginning of the column).
   */
  private static int getTreeOffset(JTree tree) {
    Object value = tree.getClientProperty(TREE_OFFSET);
    return value == null ? 0 : (Integer)value;
  }

  private static void setTreeOffset(JTree tree, int offset) {
    tree.putClientProperty(TREE_OFFSET, offset);
  }

  @NotNull
  public ColumnTreeBuilder addColumn(@NotNull ColumnBuilder column) {
    myColumnBuilders.add(column);
    return this;
  }

  public interface TreeSorter<T> {
    void sort(Comparator<T> comparator, SortOrder order);
  }

  private static class ColumnTreeScrollPanel extends JPanel {

    @NotNull
    private final ColumnTreeScrollBar myScrollbar;

    @NotNull
    private final JTree myTree;

    @NotNull
    private final JTable myTable;

    public ColumnTreeScrollPanel(@NotNull JTree tree, @NotNull JTable table) {
      super((new TabularLayout("Fit,*")));
      myTree = tree;
      myTable = table;
      myScrollbar = new ColumnTreeScrollBar(table);
      add(myScrollbar, new TabularLayout.Constraint(0, 0));
      add(new JPanel(), new TabularLayout.Constraint(0, 1));
    }

    public BoundedRangeModel getModel() {
      return myScrollbar.getModel();
    }

    public void updateScrollBar() {
      // Min is always 0
      int max = getPreferredTreeWidth(myTree);
      int value = getTreeOffset(myTree);
      int extent = getTreeColumnWidth(myTable);
      // Adjust the values so always value+extent <= max which avoids having space to show
      // the tree but still having part of the tree outside the view.
      if (value + extent > max) {
        value = max - extent;
      }
      myScrollbar.getModel().setMaximum(max);
      myScrollbar.getModel().setValue(value);
      myScrollbar.getModel().setExtent(extent);
      myScrollbar.revalidate();
      setVisible(extent < max);
    }
  }

  private static class ColumnTreeScrollBar extends JBScrollBar {

    @NotNull
    private final JTable myTable;

    @Override
    public void updateUI() {
      setUI(new RangeScrollBarUI());
    }

    public ColumnTreeScrollBar(@NotNull JTable table) {
      super(Adjustable.HORIZONTAL);
      myTable = table;
    }

    @Override
    public Dimension getMinimumSize() {
      Dimension dim = super.getMinimumSize();
      return new Dimension(getTreeColumnWidth(myTable), dim.height);
    }
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
      Insets insets = myTree.getInsets();
      assert size == columns;

      int padding = myTree.getWidth();
      for (int i = 0; i < size && i < columns; i++) {
        padding -= myColumnModel.getColumn(i).getWidth();
      }

      // The parent component has no fixed start position, but it fills the whole width, so we fill in reverse.
      int offset = parent.getWidth() - (insets.left + insets.right);
      for (int i = size - 1; i >= 0; i--) {
        Component component = parent.getComponent(i);
        int columnWidth = myColumnModel.getColumn(i).getWidth();
        if (i == size - 1) {
          // Adjust the right most column's width to account for inset
          columnWidth -= insets.right;
        }
        int width = i == 0 ? offset : Math.min(columnWidth + padding, offset);
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
      String toolTip = null;
      for (int i = 0; i < getComponentCount(); i++) {
        Component component = getComponent(i);
        if (component instanceof ColoredTreeCellRenderer) {
          Component c =
            ((ColoredTreeCellRenderer)component).getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
          if (c instanceof JComponent) {
            if (toolTip == null) {
              toolTip = ((JComponent)c).getToolTipText();
            }
          }
        }
        if (i == 0) {
          tree.putClientProperty(LAST_TREE_PREFERRED_WIDTH, component.getPreferredSize().width);
        }
      }
      setToolTipText(toolTip);
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
    private final ColumnTreeScrollPanel myHScrollBarPanel;
    private int myWidth = -1;
    private int myTreeColumnWidth = -1;
    private ArrayList<Integer> myTreeWidths = new ArrayList<>();

    @NotNull private final Color myHoverColor;
    @NotNull private final ColumnTreeHoverListener myHoverConfig;
    @Nullable private final JTable myTable;

    ColumnTreeUI() {
      this(null, null, null, null);
    }

    /**
     * @param table Used for rendering column grid lines, if {@link JTable#getShowVerticalLines()}
     *              is {@code true}.
     */
    ColumnTreeUI(@Nullable Color hoverColor, @Nullable ColumnTreeHoverListener hoverConfig,
                 @Nullable JTable table, @Nullable ColumnTreeScrollPanel hsb) {
      myHoverColor = hoverColor != null ? hoverColor : new JBColor(new Color(0, 0, 0, 0), new Color(0, 0, 0, 0));
      myHoverConfig = hoverConfig != null ? hoverConfig : ColumnTreeHoverListener.EMPTY_LISTENER;
      myTable = table;
      myHScrollBarPanel = hsb;
      hsb.getModel().addChangeListener(new ChangeListener() {
        @Override
        public void stateChanged(ChangeEvent e) {
          setTreeOffset(tree, hsb.getModel().getValue());
          treeState.invalidateSizes();
          tree.repaint();
        }
      });
    }

    @Override
    public void paint(Graphics g, JComponent c) {
      if (myWidth != c.getWidth() || myTreeColumnWidth != getTreeColumnWidth(myTable)) {
        treeState.invalidateSizes();
        myWidth = c.getWidth();
        myTreeColumnWidth = getTreeColumnWidth(myTable);
      }
      if (myTable != null && myTable.getShowVerticalLines()) {
        g.setColor(myTable.getGridColor());
        getColumnX().forEach((Integer x) -> g.drawLine(x, 0, x, c.getHeight()));
      }
      super.paint(g, c);
    }

    @Override
    protected boolean isLocationInExpandControl(TreePath path, int mouseX, int mouseY) {
      return super.isLocationInExpandControl(path, mouseX + getTreeOffset(tree), mouseY);
    }

    @Override
    protected AbstractLayoutCache.NodeDimensions createNodeDimensions() {
      return new NodeDimensionsHandler() {
        @Override
        public Rectangle getNodeDimensions(Object value, int row, int depth, boolean expanded, Rectangle size) {
          // Getting the node dimensions with a custom renderer will trigger the custom renderer, which has access to the
          // tree column preferred size.
          tree.putClientProperty(LAST_TREE_PREFERRED_WIDTH, null);
          Rectangle dimensions = super.getNodeDimensions(value, row, depth, expanded, size);
          dimensions.width = tree.getWidth() - getRowX(row, depth);
          if (row >= 0 && tree.getClientProperty(LAST_TREE_PREFERRED_WIDTH) != null) {
            int realWidth = getRowX(row, depth) + (Integer)tree.getClientProperty(LAST_TREE_PREFERRED_WIDTH);
            while (myTreeWidths.size() <= row) {
              myTreeWidths.add(0);
            }
            myTreeWidths.set(row, realWidth);
          }
          int secondColumnStart = getTreeColumnWidth(myTable) - tree.getInsets().left;
          // If the second column start point is less that the current x, we should use that.
          // This means the second column was resized to the left of the start of the node
          if (secondColumnStart < dimensions.x) {
            dimensions.width += dimensions.x - secondColumnStart;
            dimensions.x = secondColumnStart;
          }
          int offset = getTreeOffset(tree);
          dimensions.x -= offset;
          dimensions.width += offset;
          return dimensions;
        }
      };
    }

    @Override
    protected void updateCachedPreferredSize() {
      super.updateCachedPreferredSize();
      int treePreferredWidth = 0;
      for (int r = 0; r < tree.getRowCount() && r < myTreeWidths.size(); r++) {
        treePreferredWidth = Math.max(treePreferredWidth, myTreeWidths.get(r));
      }
      setPreferredTreeWidth(tree, treePreferredWidth);
      myHScrollBarPanel.updateScrollBar();
    }

    @Override
    protected void paintRow(Graphics g,
                            Rectangle clipBounds,
                            Insets insets,
                            Rectangle bounds,
                            TreePath path,
                            int row,
                            boolean isExpanded,
                            boolean hasBeenExpanded,
                            boolean isLeaf) {
      if (row == myHoverConfig.getHoveredRow() && !tree.isPathSelected(path)) {
        Color originalColor = g.getColor();
        g.setColor(myHoverColor);
        g.fillRect(0, bounds.y, tree.getWidth(), bounds.height);
        g.setColor(originalColor);
      }

      super.paintRow(g, clipBounds, insets, bounds, path, row, isExpanded, hasBeenExpanded, isLeaf);

      // Grid line color need to look like covered by hover or select highlight. Instead of painting transparent grid lines on top of
      // table hover or select background, paint a blending color of background and grid color.
      if (myTable != null && myTable.getShowVerticalLines() && tree.isPathSelected(path)) {
        Color gridBackground = getSelectionBackground(tree, false);
        Color gridColor = gridBackground == null ? myTable.getGridColor() :
                    AdtUiUtils.overlayColor(gridBackground.getRGB(), myTable.getGridColor().getRGB(), 0.25f);
        g.setColor(gridColor);
        getColumnX().forEach((Integer x) -> g.drawLine(x, bounds.y, x, bounds.y + bounds.height - 1));
      }
    }

    @Override
    protected void paintExpandControl(Graphics g, Rectangle clipBounds, Insets insets, Rectangle bounds, TreePath path,
                                      int row, boolean isExpanded, boolean hasBeenExpanded, boolean isLeaf) {
      // Because the bounds move with the column, we need to stop rendering the handle
      // one unit before the row starts moving left, or the handle will move with it.
      if (bounds.x < getTreeColumnWidth(myTable) - 1) {
        super.paintExpandControl(g, clipBounds, insets, bounds, path, row, isExpanded, hasBeenExpanded, isLeaf);
      }
    }

    private List<Integer> getColumnX() {
      if (myTable == null) {
        return new ArrayList<>();
      }
      List<Integer> columnX = new ArrayList<>();
      int x = 0;
      for (int i = 0; i < myTable.getColumnModel().getColumnCount() - 1; i++) {
        x += myTable.getColumnModel().getColumn(i).getWidth();
        // -1 so that the vertical line lines up with the header column lines
        columnX.add(x - 1);
      }
      return columnX;
    }

    /**
     * Copied from {@link WideSelectionTreeUI} to get the tree selection background color, this would not change intellij code.
     */
    @Nullable
    private static Color getSelectionBackground(@NotNull JTree tree, boolean checkProperty) {
      Object property = tree.getClientProperty(TREE_TABLE_TREE_KEY);
      if (property instanceof JTable) {
        return ((JTable)property).getSelectionBackground();
      }
      boolean selection = tree.hasFocus();
      if (!selection && checkProperty) {
        selection = Boolean.TRUE.equals(property);
      }
      return UIUtil.getTreeSelectionBackground(selection);
    }
  }

  public static class ColumnBuilder {
    private String myName;
    private int myWidth;
    private int myHeaderAlignment;
    private int myMinimumWidth;
    private Border myHeaderBorder;
    private Comparator<?> myComparator;
    private ColoredTreeCellRenderer myRenderer;
    private SortOrder myInitialOrder = SortOrder.UNSORTED;
    private SortOrder mySortOrderPreference = SortOrder.ASCENDING;

    @NotNull
    public ColumnBuilder setName(@NotNull String name) {
      myName = name;
      return this;
    }

    @NotNull
    public ColumnBuilder setPreferredWidth(int width) {
      myWidth = width;
      return this;
    }

    @NotNull
    public ColumnBuilder setMinWidth(int width) {
      myMinimumWidth = width;
      return this;
    }

    @NotNull
    public ColumnBuilder setHeaderAlignment(int alignment) {
      myHeaderAlignment = alignment;
      return this;
    }

    @NotNull
    public ColumnBuilder setHeaderBorder(Border border) {
      myHeaderBorder = border;
      return this;
    }

    /**
     * Sets the prefered {@link SortOrder} for the initial click on the column header.
     */
    @NotNull
    public ColumnBuilder setSortOrderPreference(@NotNull SortOrder sortOrderPreference) {
      assert sortOrderPreference == SortOrder.ASCENDING || sortOrderPreference == SortOrder.DESCENDING;
      mySortOrderPreference = sortOrderPreference;
      return this;
    }

    public void create(DefaultTableModel model) {
      model.addColumn(myName);
    }

    private void configure(int index, JTable table, ColumnTreeTableRowSorter sorter, ColumnTreeCellRenderer renderer) {
      TableColumn column = table.getColumnModel().getColumn(index);
      column.setPreferredWidth(myWidth);
      column.setMinWidth(myMinimumWidth);

      final TableCellRenderer tableCellRenderer = table.getTableHeader().getDefaultRenderer();
      column.setHeaderRenderer(new DefaultTableCellRenderer() {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus, int row, int column) {
          Component component = tableCellRenderer.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
          if (component instanceof JLabel) {
            ((JLabel)component).setHorizontalAlignment(myHeaderAlignment);
          }
          if (component instanceof JComponent) {
            ((JComponent)component).setBorder(myHeaderBorder);
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
    @NotNull
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
      Container parent = SwingUtilities.getUnwrappedParent(this);
      if (parent instanceof JViewport) {
        // Note: This assumes myTree extends the full width of the panel
        return parent.getWidth() > myTree.getPreferredSize().width;
      }
      return false;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
      Container parent = SwingUtilities.getUnwrappedParent(this);
      if (parent instanceof JViewport) {
        // Note: This assumes myTable has a height of 0
        return parent.getHeight() > myTree.getPreferredSize().height;
      }
      return false;
    }
  }

  private static class ColumnTreeTableRowSorter extends TableRowSorter<TableModel> {
    @NotNull private final List<SortOrder> mySortOrderPreferences;

    /**
     * @param sortOrderPreferences a {@link List} that corresponds 1:1, in order, to the {@link SortOrder} preference of each column.
     */
    private ColumnTreeTableRowSorter(@NotNull TableModel model, @NotNull List<SortOrder> sortOrderPreferences) {
      super(model);
      mySortOrderPreferences = sortOrderPreferences;
    }

    /**
     * Pretty much copied from DefaultRowSorter, except with the initial {@link SortOrder} configurable.
     *
     * @param column
     */
    @Override
    public void toggleSortOrder(int column) {
      checkColumn(column);
      if (isSortable(column)) {
        List<SortKey> keys = new ArrayList<>(getSortKeys());
        int sortIndex;
        for (sortIndex = keys.size() - 1; sortIndex >= 0; sortIndex--) {
          if (keys.get(sortIndex).getColumn() == column) {
            break;
          }
        }

        SortKey sortKey;
        if (sortIndex == -1) {
          // Key doesn't exist
          sortKey = new SortKey(column, mySortOrderPreferences.get(column));
          keys.add(0, sortKey);
        }
        else if (sortIndex == 0) {
          // It's the primary sorting key, toggle it
          keys.set(0, toggle(keys.get(0)));
        }
        else {
          // It's not the first, but was sorted on, remove old entry, insert as first with the preferred sort order preference.
          keys.remove(sortIndex);
          keys.add(0, new SortKey(column, mySortOrderPreferences.get(column)));
        }
        if (keys.size() > getMaxSortKeys()) {
          keys = keys.subList(0, getMaxSortKeys());
        }
        setSortKeys(keys);
      }
    }

    /**
     * Copied from {@link DefaultRowSorter} because it is private.
     */
    private void checkColumn(int column) {
      if (column < 0 || column >= getModelWrapper().getColumnCount()) {
        throw new IndexOutOfBoundsException(
          "column beyond range of TableModel");
      }
    }

    /**
     * Copied from {@link DefaultRowSorter} because it is private.
     */
    private static SortKey toggle(SortKey key) {
      if (key.getSortOrder() == SortOrder.ASCENDING) {
        return new SortKey(key.getColumn(), SortOrder.DESCENDING);
      }
      return new SortKey(key.getColumn(), SortOrder.ASCENDING);
    }
  }

  private static class ColumnTreeHoverListener extends MouseAdapter {
    /**
     * Empty hover config which returns the hovered row as -1 always, and it should not listen to tree.
     */
    public static final ColumnTreeHoverListener EMPTY_LISTENER = new ColumnTreeHoverListener() {
      @Override
      public int getHoveredRow() {
        return -1;
      }
    };

    public static ColumnTreeHoverListener create(@NotNull JTree tree) {
      ColumnTreeHoverListener config = new ColumnTreeHoverListener();
      tree.addMouseListener(config);
      tree.addMouseMotionListener(config);
      return config;
    }

    private int myHoveredRow = -1;

    @Override
    public void mouseMoved(MouseEvent e) {
      JTree tree = (JTree)e.getSource();
      int row = getRowForLocation(tree, e.getX(), e.getY());
      if (row != myHoveredRow) {
        int oldHoveredRow = myHoveredRow;
        myHoveredRow = row;
        if (oldHoveredRow != -1) {
          tree.repaint(getHoverBounds(tree, oldHoveredRow));
        }
        if (myHoveredRow != -1) {
          tree.repaint(getHoverBounds(tree, myHoveredRow));
        }
      }
    }

    @Override
    public void mouseEntered(MouseEvent e) {
      mouseMoved(e);
    }

    @Override
    public void mouseExited(MouseEvent e) {
      JTree tree = (JTree)e.getSource();
      if (myHoveredRow != -1) {
        int oldHoveredRow = myHoveredRow;
        myHoveredRow = -1;
        tree.repaint(getHoverBounds(tree, oldHoveredRow));
      }
    }

    public int getHoveredRow() {
      return myHoveredRow;
    }

    /**
     * Row bounds does not include handler on the left, so mark dirty area from x value zero and adjust width.
     */
    private static Rectangle getHoverBounds(JTree tree, int row) {
      Rectangle bounds = tree.getRowBounds(row);
      return new Rectangle(0, bounds.y, bounds.width + bounds.x, bounds.height);
    }

    /**
     * Point may be at tree collapse/expand handler, find closest row first and verify bounds.
     */
    private static int getRowForLocation(JTree tree, int x, int y) {
      int row = tree.getClosestRowForLocation(x, y);
      if (row != -1 && getHoverBounds(tree, row).contains(x, y)) {
        return row;
      }
      return -1;
    }
  }
}
