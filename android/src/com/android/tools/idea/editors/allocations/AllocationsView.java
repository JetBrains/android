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
package com.android.tools.idea.editors.allocations;

import com.android.ddmlib.AllocationInfo;
import com.android.tools.adtui.Choreographer;
import com.android.tools.adtui.chart.SunburstChart;
import com.android.tools.adtui.ValuedTreeNode;
import com.android.tools.adtui.common.ColumnTreeBuilder;
import com.android.tools.idea.actions.BrowserHelpAction;
import com.android.tools.idea.actions.EditMultipleSourcesAction;
import com.android.tools.idea.actions.PsiClassNavigation;
import com.android.tools.idea.editors.allocations.nodes.*;
import com.android.utils.HtmlBuilder;
import com.google.common.collect.ImmutableMap;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.*;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.Alarm;
import com.intellij.util.PlatformIcons;
import com.intellij.util.ui.UIUtil;
import icons.AndroidIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

import static com.intellij.ui.SimpleTextAttributes.STYLE_PLAIN;

public class AllocationsView implements SunburstChart.SliceSelectionListener {
  @NotNull private static final String ALLOCATION_VIEW_ID = "AllocationView";
  @NotNull private static final String GROUP_BY_NAME = ".GroupBy";
  @NotNull private static final String SHOW_CHART_STATE = ".ShowChart";

  private static final int MAX_AUTO_EXPANSION_DEPTH = 5;

  @NotNull private final Project myProject;

  @NotNull private final AllocationInfo[] myAllocations;
  private final DefaultTableModel myInfoTableModel;
  private final SearchTextFieldWithStoredHistory myPackageFilter;

  @NotNull private MainTreeNode myTreeNode;

  @NotNull private final JTree myTree;

  @NotNull private final DefaultTreeModel myTreeModel;

  @NotNull private JBSplitter mySplitter;

  @NotNull private JComponent myChartPane;

  @NotNull private final Component myComponent;

  private final Map<String, GroupBy> myGroupBy = new ImmutableMap.Builder<String, GroupBy>()
    .put(GroupByMethod.NAME, new GroupByMethod())
    .put(GroupByAllocator.NAME, new GroupByAllocator())
    .build();
  private String myGroupByName;
  private final SunburstChart myLayout;
  private String myChartOrientation;
  private String myChartUnit;
  private final JLabel myInfoLabel;
  private Alarm myAlarm;
  private final JBTable myInfoTable;

  public AllocationsView(@NotNull Project project, @NotNull final AllocationInfo[] allocations) {
    myProject = project;
    myAllocations = allocations;
    myGroupByName = PropertiesComponent.getInstance(project).getValue(ALLOCATION_VIEW_ID + GROUP_BY_NAME, GroupByMethod.NAME);
    myPackageFilter = new SearchTextFieldWithStoredHistory("alloc.package.filter");
    myPackageFilter.addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        myAlarm.cancelAllRequests();
        myAlarm.addRequest(() -> setGroupByName(GroupByAllocator.NAME), 1000);
      }
    });
    myTreeNode = generateTree();
    myTreeModel = new DefaultTreeModel(myTreeNode);
    myAlarm = new Alarm(project);

    myTree = new Tree(myTreeModel);
    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(true);

    myTree.putClientProperty(DataManager.CLIENT_PROPERTY_DATA_PROVIDER, this.new TreeDataProvider());
    final DefaultActionGroup popupGroup = new DefaultActionGroup();
    popupGroup.add(new EditMultipleSourcesAction());
    myTree.addMouseListener(new PopupHandler() {
      @Override
      public void invokePopup(Component comp, int x, int y) {
        ActionManager.getInstance().createActionPopupMenu(ActionPlaces.UNKNOWN, popupGroup).getComponent().show(comp, x, y);
      }
    });

    myTree.addTreeExpansionListener(new TreeExpansionListener() {
      private boolean myIsCurrentlyExpanding = false;

      @Override
      public void treeExpanded(TreeExpansionEvent event) {
        if (myIsCurrentlyExpanding) {
          return;
        }

        myIsCurrentlyExpanding = true;
        // Do a fast expansion of descendant nodes if they only have one child each.
        AbstractTreeNode node = (AbstractTreeNode)event.getPath().getLastPathComponent();
        AbstractTreeNode currentNode = node;
        int recursiveDepth = 0;
        List<Object> expandedPath = new ArrayList<>(Arrays.asList(event.getPath().getPath()));
        while (currentNode.getChildCount() == 1 && recursiveDepth < MAX_AUTO_EXPANSION_DEPTH) {
          AbstractTreeNode childNode = (AbstractTreeNode)currentNode.getChildAt(0);
          if (childNode.isLeaf()) {
            break;
          }
          expandedPath.add(childNode);
          currentNode = childNode;
          ++recursiveDepth;
        }

        if (node != currentNode) {
          myTree.expandPath(new TreePath(expandedPath.toArray()));
        }
        myIsCurrentlyExpanding = false;
      }

      @Override
      public void treeCollapsed(TreeExpansionEvent event) {
      }
    });

    ColumnTreeBuilder builder = new ColumnTreeBuilder(myTree).addColumn(
      new ColumnTreeBuilder.ColumnBuilder().setName("Method").setPreferredWidth(600).setHeaderAlignment(SwingConstants.LEFT)
        .setComparator((a, b) -> {
          if (a instanceof ThreadNode && b instanceof ThreadNode) {
            return ((ThreadNode)a).getThreadId() - ((ThreadNode)b).getThreadId();
          }
          else if (a instanceof StackNode && b instanceof StackNode) {
            StackTraceElement ea = ((StackNode)a).getStackTraceElement();
            StackTraceElement eb = ((StackNode)b).getStackTraceElement();
            int value = ea.getMethodName().compareTo(eb.getMethodName());
            if (value == 0) value = ea.getLineNumber() - eb.getLineNumber();
            return value;
          }
          else {
            return a.getClass().toString().compareTo(b.getClass().toString());
          }
        }).setRenderer(new NodeTreeCellRenderer())
    ).addColumn(
      new ColumnTreeBuilder.ColumnBuilder().setName("Count").setPreferredWidth(150).setHeaderAlignment(SwingConstants.RIGHT)
        .setComparator((AbstractTreeNode a, AbstractTreeNode b) -> a.getCount() - b.getCount()).setRenderer(new ColoredTreeCellRenderer() {
          @Override
          public void customizeCellRenderer(@NotNull JTree tree,
                                            Object value,
                                            boolean selected,
                                            boolean expanded,
                                            boolean leaf,
                                            int row,
                                            boolean hasFocus) {
            if (value instanceof ValuedTreeNode) {
              int v = ((ValuedTreeNode)value).getCount();
              int total = myTreeNode.getCount();
              setTextAlign(SwingConstants.RIGHT);
              append(String.valueOf(v));
              append(String.format(" (%.2f%%)", 100.0f * v / total), new SimpleTextAttributes(STYLE_PLAIN, JBColor.GRAY));
            }
          }
        })
    ).addColumn(
      new ColumnTreeBuilder.ColumnBuilder().setName("Total Size").setPreferredWidth(150).setHeaderAlignment(SwingConstants.RIGHT)
        .setInitialOrder(SortOrder.DESCENDING).setComparator((AbstractTreeNode a, AbstractTreeNode b) -> a.getValue() - b.getValue())
        .setRenderer(new ColoredTreeCellRenderer() {
          @Override
          public void customizeCellRenderer(@NotNull JTree tree,
                                            Object value,
                                            boolean selected,
                                            boolean expanded,
                                            boolean leaf,
                                            int row,
                                            boolean hasFocus) {
            if (value instanceof ValuedTreeNode) {
              int v = ((ValuedTreeNode)value).getValue();
              int total = myTreeNode.getValue();
              setTextAlign(SwingConstants.RIGHT);
              append(String.valueOf(v));
              append(String.format(" (%.2f%%)", 100.0f * v / total), new SimpleTextAttributes(STYLE_PLAIN, JBColor.GRAY));
            }
          }
        })
    );

    builder.setTreeSorter((Comparator<AbstractTreeNode> comparator, SortOrder sortOrder) -> {
      myTreeNode.sort(comparator);
      myTreeModel.nodeStructureChanged(myTreeNode);
    });
    JComponent columnTree = builder.build();

    mySplitter = new JBSplitter(true);

    new TreeSpeedSearch(myTree, e -> {
      Object o = e.getLastPathComponent();
      if (o instanceof StackNode) {
        StackTraceElement ee = ((StackNode)o).getStackTraceElement();
        return ee.toString();
      }
      return o.toString();
    }, true);

    JPanel panel = new JPanel(new BorderLayout());

    JPanel topPanel = new JPanel(new BorderLayout());
    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("AndroidAllocationsView", getMainActions(), true);
    topPanel.add(toolbar.getComponent(), BorderLayout.CENTER);
    myPackageFilter.setVisible(false);
    topPanel.add(myPackageFilter, BorderLayout.EAST);
    panel.add(topPanel, BorderLayout.NORTH);
    panel.add(columnTree, BorderLayout.CENTER);

    mySplitter.setFirstComponent(panel);

    myChartPane = new JPanel(new BorderLayout());
    myLayout = new SunburstChart(myTreeNode);
    myLayout.setAngle(360.0f);
    myLayout.setAutoSize(true);
    myLayout.setSeparator(1.0f);
    myLayout.setGap(20.0f);
    myLayout.addSelectionListener(this);
    myLayout.setBorder(IdeBorderFactory.createBorder());
    myLayout.setBackground(UIUtil.getTreeBackground());
    Choreographer.animate(myLayout);

    toolbar = ActionManager.getInstance().createActionToolbar("AndroidAllocationsViewSunburst", getChartActions(), true);
    myChartOrientation = "Sunburst";
    myChartUnit = "Size";

    myChartPane = new JPanel(new BorderLayout());
    myChartPane.add(toolbar.getComponent(), BorderLayout.NORTH);
    JBSplitter chartSplitter = new JBSplitter();
    myChartPane.add(chartSplitter, BorderLayout.CENTER);
    chartSplitter.setFirstComponent(myLayout);
    if (PropertiesComponent.getInstance().getBoolean(ALLOCATION_VIEW_ID + SHOW_CHART_STATE, false)) {
      mySplitter.setSecondComponent(myChartPane);
    }

    JPanel infoPanel = new JPanel(new BorderLayout());
    infoPanel.setBorder(IdeBorderFactory.createBorder());

    myInfoLabel = new JLabel();
    myInfoLabel.setBackground(UIUtil.getTreeBackground());
    myInfoLabel.setOpaque(true);
    myInfoLabel.setVerticalAlignment(SwingConstants.TOP);
    infoPanel.add(myInfoLabel, BorderLayout.NORTH);
    myInfoTableModel = new DefaultTableModel() {
      @Override
      public boolean isCellEditable(int i, int i1) {
        return false;
      }
    };
    myInfoTableModel.addColumn("Data");
    myInfoTable = new JBTable(myInfoTableModel);
    myInfoTable.putClientProperty(DataManager.CLIENT_PROPERTY_DATA_PROVIDER, this.new TableDataProvider());
    myInfoTable.addMouseListener(new PopupHandler() {
      @Override
      public void invokePopup(Component comp, int x, int y) {
        ActionManager.getInstance().createActionPopupMenu(ActionPlaces.UNKNOWN, popupGroup).getComponent().show(comp, x, y);
      }

      @Override
      public void mousePressed(MouseEvent e) {
        super.mousePressed(e);

        if (e.getClickCount() == 2) {
          Object value = myInfoTable.getValueAt(myInfoTable.getSelectedRow(), 0);
          if (value instanceof TreeNode) {
            TreeNode[] nodes = myTreeModel.getPathToRoot((TreeNode)value);
            TreePath path = new TreePath(nodes);
            myTree.setSelectionPath(path);
            myTree.scrollPathToVisible(path);
          }
        }
      }
    });
    myInfoTable.setTableHeader(null);
    myInfoTable.setShowGrid(false);
    myInfoTable.setDefaultRenderer(Object.class, new NodeTableCellRenderer());
    JBScrollPane scroll = new JBScrollPane(myInfoTable);
    scroll.setBorder(BorderFactory.createEmptyBorder());
    infoPanel.add(scroll, BorderLayout.CENTER);
    chartSplitter.setSecondComponent(infoPanel);
    chartSplitter.setProportion(0.7f);

    myComponent = mySplitter;

    setGroupByName(myGroupByName);
  }

  @NotNull
  public Component getComponent() {
    return myComponent;
  }

  private void setGroupByName(@NotNull String groupByName) {
    if (myGroupBy.containsKey(groupByName)) {
      myGroupByName = groupByName;
      myTreeNode = generateTree();
      myTreeModel.setRoot(myTreeNode);
      myLayout.setData(myTreeNode);
      myLayout.resetZoom();
      myTreeModel.nodeStructureChanged(myTreeNode);
      myPackageFilter.setVisible(myGroupBy.get(myGroupByName) instanceof GroupByAllocator);
      PropertiesComponent.getInstance(myProject).setValue(ALLOCATION_VIEW_ID + GROUP_BY_NAME, myGroupByName);
    }
  }

  private ActionGroup getMainActions() {
    DefaultActionGroup group = new DefaultActionGroup();

    group.add(new ComboBoxAction() {
      @NotNull
      @Override
      protected DefaultActionGroup createPopupActionGroup(JComponent button) {
        DefaultActionGroup group = new DefaultActionGroup();
        group.add(new ChangeGroupAction(GroupByMethod.NAME));
        group.add(new ChangeGroupAction(GroupByAllocator.NAME));
        return group;
      }

      @Override
      public void update(AnActionEvent e) {
        super.update(e);
        getTemplatePresentation().setText(myGroupBy.get(myGroupByName).getName());
        e.getPresentation().setText(myGroupBy.get(myGroupByName).getName());
      }
    });
    group.add(new EditMultipleSourcesAction());
    group.add(new ShowChartAction());
    group.add(new Separator());
    group.add(new BrowserHelpAction("Hprof Viewer", "http://developer.android.com/r/studio-ui/am-allocation.html"));

    return group;
  }

  @Nullable
  public Object getData(@NonNls String dataId, Object selectionData) {
    if (CommonDataKeys.NAVIGATABLE_ARRAY.is(dataId)) {
      return getTargetFiles(selectionData);
    }
    else if (CommonDataKeys.PROJECT.is(dataId)) {
      return myProject;
    }
    return null;
  }

  private ActionGroup getChartActions() {
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new ComboBoxAction() {
      @NotNull
      @Override
      protected DefaultActionGroup createPopupActionGroup(JComponent button) {
        DefaultActionGroup group = new DefaultActionGroup();
        group.add(new AnAction("Sunburst") {
          @Override
          public void actionPerformed(AnActionEvent e) {
            myChartOrientation = "Sunburst";
            myLayout.setAngle(360.0f);
          }
        });
        group.add(new AnAction("Layout") {
          @Override
          public void actionPerformed(AnActionEvent e) {
            myChartOrientation = "Layout";
            myLayout.setAngle(0.0f);
          }
        });
        return group;
      }

      @Override
      public void update(AnActionEvent e) {
        super.update(e);
        getTemplatePresentation().setText(myChartOrientation);
        e.getPresentation().setText(myChartOrientation);
      }
    });
    group.add(new ComboBoxAction() {
      @NotNull
      @Override
      protected DefaultActionGroup createPopupActionGroup(JComponent button) {
        DefaultActionGroup group = new DefaultActionGroup();
        group.add(new AnAction("Size") {
          @Override
          public void actionPerformed(AnActionEvent e) {
            myChartUnit = "Size";
            myLayout.setUseCount(false);
          }
        });
        group.add(new AnAction("Count") {
          @Override
          public void actionPerformed(AnActionEvent e) {
            myChartUnit = "Count";
            myLayout.setUseCount(true);
          }
        });
        return group;
      }

      @Override
      public void update(AnActionEvent e) {
        super.update(e);
        getTemplatePresentation().setText(myChartUnit);
        e.getPresentation().setText(myChartUnit);
      }
    });
    return group;
  }

  @NotNull
  private MainTreeNode generateTree() {
    MainTreeNode tree = myGroupBy.get(myGroupByName).create();
    for (AllocationInfo alloc : myAllocations) {
      tree.insert(alloc);
    }
    return tree;
  }

  @Override
  public void valueChanged(SunburstChart.SliceSelectionEvent e) {
    ValuedTreeNode node = e == null ? null : e.getNode();
    HtmlBuilder builder = new HtmlBuilder();
    builder.openHtmlBody();
    if (node == null) {
      node = myTreeNode;
    }
    builder.add("Total allocations:").addNbsp().addBold(Integer.toString(node.getCount())).newline().add("Total size:").addNbsp()
      .addBold(StringUtil.formatFileSize(node.getValue())).newline().newline();
    if (node instanceof AbstractTreeNode) {
      TreeNode[] path = myTreeModel.getPathToRoot(node);
      myInfoTableModel.setRowCount(path.length);
      // Start at 1 to avoid adding the root node.
      for (int i = 1; i < path.length; i++) {
        myInfoTableModel.setValueAt(path[i], i - 1, 0);
      }
      myInfoTableModel.fireTableDataChanged();
    }
    builder.closeHtmlBody();
    myInfoLabel.setText(builder.getHtml());
  }

  private static void customizeColoredRenderer(SimpleColoredComponent renderer, Object value) {
    renderer.setTransparentIconBackground(true);
    if (value instanceof ThreadNode) {
      renderer.setIcon(AllIcons.Debugger.ThreadSuspended);
      renderer.append("< Thread " + ((ThreadNode)value).getThreadId() + " >");
    }
    else if (value instanceof StackNode) {
      StackTraceElement element = ((StackNode)value).getStackTraceElement();
      String name = element.getClassName();
      String pkg = null;
      int ix = name.lastIndexOf('.');
      if (ix != -1) {
        pkg = name.substring(0, ix);
        name = name.substring(ix + 1);
      }

      renderer.setIcon(PlatformIcons.METHOD_ICON);
      renderer.append(element.getMethodName() + "()");
      renderer.append(":" + element.getLineNumber() + ", ");
      renderer.append(name);
      if (pkg != null) {
        renderer.append(" (" + pkg + ")", new SimpleTextAttributes(STYLE_PLAIN, JBColor.GRAY));
      }
    }
    else if (value instanceof AllocNode) {
      AllocationInfo allocation = ((AllocNode)value).getAllocation();
      renderer.setIcon(AllIcons.FileTypes.JavaClass);
      renderer.append(allocation.getAllocatedClass());
    }
    else if (value instanceof ClassNode) {
      renderer.setIcon(PlatformIcons.CLASS_ICON);
      renderer.append(((PackageNode)value).getName());
    }
    else if (value instanceof PackageNode) {
      String name = ((PackageNode)value).getName();
      if (!name.isEmpty()) {
        renderer.setIcon(AllIcons.Modules.SourceFolder);
        renderer.append(name);
      }
    }
    else //noinspection StatementWithEmptyBody
      if (value instanceof StackTraceRootNode) {
      }
      else if (value != null) {
        renderer.append(value.toString());
      }
  }

  @Nullable
  private PsiClassNavigation[] getTargetFiles(Object node) {
    if (node == null) {
      return null;
    }

    String className = null;
    int lineNumber = 0;
    if (node instanceof ClassNode) {
      className = ((ClassNode)node).getQualifiedName();
    }
    else {
      StackTraceElement element = null;
      if (node instanceof StackNode) {
        element = ((StackNode)node).getStackTraceElement();
      }
      else if (node instanceof AllocNode) {
        StackTraceElement[] stack = ((AllocNode)node).getAllocation().getStackTrace();
        if (stack.length > 0) {
          element = stack[0];
        }
      }
      if (element != null) {
        lineNumber = element.getLineNumber() - 1; // Line numbers are shown as 1-based, but are 0-based in the psi.
        className = element.getClassName();
        int ix = className.indexOf('$');
        if (ix >= 0) {
          className = className.substring(0, ix);
        }
      }
    }

    return PsiClassNavigation.getNavigationForClass(myProject, className, lineNumber);
  }

  public static class NodeTableCellRenderer extends ColoredTableCellRenderer {
    @Override
    protected void customizeCellRenderer(JTable table, Object value, boolean selected, boolean hasFocus, int row, int column) {
      customizeColoredRenderer(this, value);
    }
  }

  private static class NodeTreeCellRenderer extends ColoredTreeCellRenderer {
    @Override
    public void customizeCellRenderer(@NotNull JTree tree,
                                      Object value,
                                      boolean selected,
                                      boolean expanded,
                                      boolean leaf,
                                      int row,
                                      boolean hasFocus) {
      customizeColoredRenderer(this, value);
    }


    @Override
    protected boolean shouldDrawBackground() {
      return false;
    }
  }

  interface GroupBy {
    String getName();

    MainTreeNode create();
  }

  static class GroupByMethod implements GroupBy {
    public static final String NAME = "Group by Method";

    @Override
    public String getName() {
      return NAME;
    }

    @Override
    public MainTreeNode create() {
      return new StackTraceRootNode();
    }
  }

  class GroupByAllocator implements GroupBy {
    public static final String NAME = "Group by Allocator";

    @Override
    public String getName() {
      return NAME;
    }

    @Override
    public MainTreeNode create() {
      return new PackageRootNode("", myPackageFilter.getText());
    }
  }

  class ChangeGroupAction extends AnAction {
    @NotNull
    private String myGroupByName;

    public ChangeGroupAction(@NotNull String groupByName) {
      super(groupByName);
      myGroupByName = groupByName;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      setGroupByName(myGroupByName);
    }
  }

  class ShowChartAction extends ToggleAction {
    public ShowChartAction() {
      super("Show/Hide Chart", "Shows/hides the allocation chart and stack trace", AndroidIcons.Sunburst);
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return mySplitter.getSecondComponent() != null;
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      PropertiesComponent.getInstance().setValue(ALLOCATION_VIEW_ID + SHOW_CHART_STATE, state);
      if (state) {
        mySplitter.setSecondComponent(myChartPane);
      }
      else {
        mySplitter.setSecondComponent(null);
      }
      valueChanged(null);
    }
  }

  private class TreeDataProvider implements DataProvider {
    @Nullable
    @Override
    public Object getData(@NonNls String dataId) {
      return AllocationsView.this.getData(dataId, myTree.getLastSelectedPathComponent());
    }
  }

  private class TableDataProvider implements DataProvider {
    @Nullable
    @Override
    public Object getData(@NonNls String dataId) {
      int selectedRow = myInfoTable.getSelectedRow();
      if (selectedRow < 0) {
        return null;
      }
      return AllocationsView.this.getData(dataId, myInfoTable.getValueAt(selectedRow, 0));
    }
  }
}
