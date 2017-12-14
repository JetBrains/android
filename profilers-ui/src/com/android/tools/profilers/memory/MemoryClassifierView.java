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
package com.android.tools.profilers.memory;

import com.android.tools.adtui.common.ColumnTreeBuilder;
import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.profilers.*;
import com.android.tools.profilers.memory.adapters.*;
import com.android.tools.profilers.memory.adapters.CaptureObject.ClassifierAttribute;
import com.android.tools.profilers.stacktrace.CodeLocation;
import com.android.tools.profilers.stacktrace.LoadingPanel;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.icons.AllIcons;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.PlatformIcons;
import icons.StudioIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.DefaultTableColumnModel;
import javax.swing.table.TableColumnModel;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.*;
import java.util.List;

import static com.android.tools.adtui.common.AdtUiUtils.DEFAULT_TOP_BORDER;
import static com.android.tools.profilers.ProfilerLayout.ROW_HEIGHT_PADDING;
import static com.android.tools.profilers.memory.MemoryProfilerConfiguration.ClassGrouping.ARRANGE_BY_CLASS;

final class MemoryClassifierView extends AspectObserver {
  private static final int LABEL_COLUMN_WIDTH = 800;
  private static final int DEFAULT_COLUMN_WIDTH = 80;
  private static final int HEAP_UPDATING_DELAY_MS = 250;

  private static final String HELP_TIP_HEADER_LIVE_ALLOCATION = "Selected range has no allocations or deallocations";
  private static final String HELP_TIP_DESCRIPTION_LIVE_ALLOCATION =
    "Select a valid range in the timeline where the Java memory is changing to view allocations and deallocations.";
  private static final String HELP_TIP_HEADER_EXPLICIT_CAPTURE = "Selected capture has no contents";
  private static final String HELP_TIP_DESCRIPTION_EXPLICIT_CAPTURE =
    "There are no allocations in the selected capture.";

  @NotNull private final MemoryProfilerStage myStage;

  @NotNull private final ContextMenuInstaller myContextMenuInstaller;

  @NotNull private final Map<ClassifierAttribute, AttributeColumn<ClassifierSet>> myAttributeColumns = new HashMap<>();

  @Nullable private CaptureObject myCaptureObject = null;

  @Nullable private HeapSet myHeapSet = null;

  @Nullable private ClassSet myClassSet = null;

  @Nullable private ClassifierSet mySelectedClassifierSet = null;

  @NotNull private final JPanel myPanel = new JPanel(new BorderLayout());

  @NotNull private final JPanel myClassifierPanel = new JPanel(new BorderLayout());

  @NotNull private final LoadingPanel myLoadingPanel;

  @Nullable private InfoMessagePanel myHelpTipInfoMessagePanel; // Panel to let user know to select a range with allocations in it.

  @Nullable private JComponent myColumnTree;

  @Nullable private JTree myTree;

  @Nullable private DefaultTreeModel myTreeModel;

  @Nullable private TableColumnModel myTableColumnModel;

  @Nullable private MemoryClassifierTreeNode myTreeRoot;

  @Nullable private Comparator<MemoryObjectTreeNode<ClassifierSet>> myInitialComparator;

  public MemoryClassifierView(@NotNull MemoryProfilerStage stage, @NotNull IdeProfilerComponents ideProfilerComponents) {
    myStage = stage;
    myContextMenuInstaller = ideProfilerComponents.createContextMenuInstaller();
    myLoadingPanel = ideProfilerComponents.createLoadingPanel(HEAP_UPDATING_DELAY_MS);
    myLoadingPanel.setLoadingText("");

    myStage.getAspect().addDependency(this)
      .onChange(MemoryProfilerAspect.CURRENT_LOADING_CAPTURE, this::loadCapture)
      .onChange(MemoryProfilerAspect.CURRENT_LOADED_CAPTURE, this::refreshCapture)
      .onChange(MemoryProfilerAspect.CURRENT_HEAP, this::refreshHeapSet)
      .onChange(MemoryProfilerAspect.CURRENT_HEAP_UPDATING, this::startHeapLoadingUi)
      .onChange(MemoryProfilerAspect.CURRENT_HEAP_UPDATED, this::stopHeapLoadingUi)
      .onChange(MemoryProfilerAspect.CURRENT_HEAP_CONTENTS, this::refreshTree)
      .onChange(MemoryProfilerAspect.CURRENT_CLASS, this::refreshClassSet)
      .onChange(MemoryProfilerAspect.CLASS_GROUPING, this::refreshGrouping)
      .onChange(MemoryProfilerAspect.CURRENT_FILTER, this::refreshFilter);

    myAttributeColumns.put(
      ClassifierAttribute.LABEL,
      new AttributeColumn<>(
        "Class Name", this::getNameColumnRenderer, SwingConstants.LEFT, LABEL_COLUMN_WIDTH, SortOrder.ASCENDING,
        createTreeNodeComparator(Comparator.comparing(ClassifierSet::getName), Comparator.comparing(ClassSet::getName))));
    myAttributeColumns.put(
      ClassifierAttribute.ALLOCATIONS,
      new AttributeColumn<>(
        "Allocations",
        () -> new SimpleColumnRenderer<ClassifierSet>(
          value -> Integer.toString(value.getAdapter().getDeltaAllocationCount()),
          value -> null,
          SwingConstants.RIGHT),
        SwingConstants.RIGHT,
        DEFAULT_COLUMN_WIDTH,
        SortOrder.DESCENDING,
        createTreeNodeComparator(Comparator.comparingInt(ClassifierSet::getDeltaAllocationCount),
                                 Comparator.comparingInt(ClassSet::getDeltaAllocationCount))));
    myAttributeColumns.put(
      ClassifierAttribute.DEALLOCATIONS,
      new AttributeColumn<>(
        "Deallocations",
        () -> new SimpleColumnRenderer<ClassifierSet>(
          value -> Integer.toString(value.getAdapter().getDeltaDeallocationCount()),
          value -> null,
          SwingConstants.RIGHT),
        SwingConstants.RIGHT,
        DEFAULT_COLUMN_WIDTH,
        SortOrder.DESCENDING,
        createTreeNodeComparator(Comparator.comparingInt(ClassifierSet::getDeltaDeallocationCount),
                                 Comparator.comparingInt(ClassSet::getDeltaDeallocationCount))));
    myAttributeColumns.put(
      ClassifierAttribute.TOTAL_COUNT,
      new AttributeColumn<>(
        "Total Count",
        () -> new SimpleColumnRenderer<ClassifierSet>(
          value -> Integer.toString(value.getAdapter().getTotalObjectCount()),
          value -> null,
          SwingConstants.RIGHT),
        SwingConstants.RIGHT,
        DEFAULT_COLUMN_WIDTH,
        SortOrder.DESCENDING,
        createTreeNodeComparator(Comparator.comparingInt(ClassifierSet::getTotalObjectCount),
                                 Comparator.comparingInt(ClassSet::getTotalObjectCount))));
    myAttributeColumns.put(
      ClassifierAttribute.NATIVE_SIZE,
      new AttributeColumn<>(
        "Native Size",
        () -> new SimpleColumnRenderer<ClassifierSet>(
          value -> Long.toString(value.getAdapter().getTotalNativeSize()),
          value -> null, SwingConstants.RIGHT),
        SwingConstants.RIGHT,
        DEFAULT_COLUMN_WIDTH,
        SortOrder.DESCENDING,
        createTreeNodeComparator(Comparator.comparingLong(ClassSet::getTotalNativeSize))));
    myAttributeColumns.put(
      ClassifierAttribute.SHALLOW_SIZE,
      new AttributeColumn<>(
        "Shallow Size",
        () -> new SimpleColumnRenderer<ClassifierSet>(
          value -> Long.toString(value.getAdapter().getTotalShallowSize()),
          value -> null, SwingConstants.RIGHT),
        SwingConstants.RIGHT,
        DEFAULT_COLUMN_WIDTH,
        SortOrder.DESCENDING,
        createTreeNodeComparator(Comparator.comparingLong(ClassSet::getTotalShallowSize))));
    myAttributeColumns.put(
      ClassifierAttribute.RETAINED_SIZE,
      new AttributeColumn<>(
        "Retained Size",
        () -> new SimpleColumnRenderer<ClassifierSet>(
          value -> Long.toString(value.getAdapter().getTotalRetainedSize()),
          value -> null, SwingConstants.RIGHT),
        SwingConstants.RIGHT,
        DEFAULT_COLUMN_WIDTH,
        SortOrder.DESCENDING,
        createTreeNodeComparator(Comparator.comparingLong(ClassifierSet::getTotalRetainedSize),
                                 Comparator.comparingLong(ClassSet::getTotalRetainedSize))));
  }

  @NotNull
  JComponent getComponent() {
    return myPanel;
  }

  @VisibleForTesting
  @Nullable
  JTree getTree() {
    return myTree;
  }

  @VisibleForTesting
  @Nullable
  JComponent getColumnTree() {
    return myColumnTree;
  }

  @VisibleForTesting
  @Nullable
  TableColumnModel getTableColumnModel() {
    return myTableColumnModel;
  }

  @VisibleForTesting
  @NotNull
  JPanel getClassifierPanel() {
    return myClassifierPanel;
  }

  /**
   * Must manually remove from parent container!
   */
  private void reset() {
    myCaptureObject = null;
    myHeapSet = null;
    myClassSet = null;
    myClassifierPanel.removeAll();
    myHelpTipInfoMessagePanel = null;
    myColumnTree = null;
    myTree = null;
    myTreeRoot = null;
    myTreeModel = null;
    myPanel.removeAll();
    myStage.selectClassSet(null);
  }

  private void loadCapture() {
    if (myStage.getSelectedCapture() == null || myCaptureObject != myStage.getSelectedCapture()) {
      reset();
    }
  }

  private void refreshFilter() {
    if (myHeapSet != null) {
      refreshTree();
    }
  }

  private void refreshCapture() {
    myCaptureObject = myStage.getSelectedCapture();
    if (myCaptureObject == null) {
      reset();
      return;
    }

    assert myColumnTree == null && myTreeModel == null && myTreeRoot == null && myTree == null;

    // Use JTree instead of IJ's tree, because IJ's tree does not happen border's Insets.
    //noinspection UndesirableClassUsage
    myTree = new JTree();
    int defaultFontHeight = myTree.getFontMetrics(myTree.getFont()).getHeight();
    myTree.setRowHeight(defaultFontHeight + ROW_HEIGHT_PADDING);
    myTree.setBorder(ProfilerLayout.TABLE_ROW_BORDER);
    myTree.setRootVisible(true);
    myTree.setShowsRootHandles(false);
    myTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    myTree.addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent e) {
        if (myTree.getSelectionCount() == 0) {
          myTree.setSelectionRow(0);
        }
      }
    });

    myTree.addTreeSelectionListener(e -> {
      TreePath path = e.getPath();
      if (!e.isAddedPath()) {
        return;
      }

      assert path.getLastPathComponent() instanceof MemoryClassifierTreeNode;
      MemoryClassifierTreeNode classifierNode = (MemoryClassifierTreeNode)path.getLastPathComponent();

      mySelectedClassifierSet = classifierNode.getAdapter();

      if (classifierNode.getAdapter() instanceof ClassSet && myClassSet != classifierNode.getAdapter()) {
        myClassSet = (ClassSet)classifierNode.getAdapter();
        myStage.selectClassSet(myClassSet);
      }
    });

    myContextMenuInstaller.installNavigationContextMenu(myTree, myStage.getStudioProfilers().getIdeServices().getCodeNavigator(), () -> {
      TreePath selection = myTree.getSelectionPath();
      if (selection == null || !(selection.getLastPathComponent() instanceof MemoryObjectTreeNode)) {
        return null;
      }

      if (((MemoryObjectTreeNode)selection.getLastPathComponent()).getAdapter() instanceof ClassSet) {
        ClassSet classSet = (ClassSet)((MemoryObjectTreeNode)selection.getLastPathComponent()).getAdapter();
        return new CodeLocation.Builder(classSet.getClassEntry().getClassName()).build();
      }
      return null;
    });

    List<ClassifierAttribute> attributes = myCaptureObject.getClassifierAttributes();
    myTableColumnModel = new DefaultTableColumnModel();
    ColumnTreeBuilder builder = new ColumnTreeBuilder(myTree, myTableColumnModel);
    ClassifierAttribute sortAttribute = Collections.max(attributes, Comparator.comparingInt(ClassifierAttribute::getWeight));
    for (ClassifierAttribute attribute : attributes) {
      AttributeColumn<ClassifierSet> column = myAttributeColumns.get(attribute);
      ColumnTreeBuilder.ColumnBuilder columnBuilder = column.getBuilder();
      if (sortAttribute == attribute) {
        columnBuilder.setInitialOrder(attribute.getSortOrder());
        myInitialComparator =
          attribute.getSortOrder() == SortOrder.ASCENDING ? column.getComparator() : Collections.reverseOrder(column.getComparator());
      }
      builder.addColumn(columnBuilder);
    }
    builder.setTreeSorter((Comparator<MemoryObjectTreeNode<ClassifierSet>> comparator, SortOrder sortOrder) -> {
      if (myTreeRoot != null && myTreeModel != null) {
        TreePath selectionPath = myTree.getSelectionPath();
        myTreeRoot.sort(comparator);
        myTreeModel.nodeStructureChanged(myTreeRoot);
        if (selectionPath != null) {
          myTree.expandPath(selectionPath.getParentPath());
          myTree.setSelectionPath(selectionPath);
          myTree.scrollPathToVisible(selectionPath);
        }
      }
    });
    builder.setHoverColor(ProfilerColors.DEFAULT_HOVER_COLOR);
    builder.setBackground(ProfilerColors.DEFAULT_BACKGROUND);
    builder.setBorder(DEFAULT_TOP_BORDER);
    builder.setShowVerticalLines(true);
    builder.setTableIntercellSpacing(new Dimension());
    myColumnTree = builder.build();

    if (myStage.getSelectedCapture().isExportable()) {
      myHelpTipInfoMessagePanel = new InfoMessagePanel(HELP_TIP_HEADER_EXPLICIT_CAPTURE, HELP_TIP_DESCRIPTION_EXPLICIT_CAPTURE, null);
    }
    else {
      myHelpTipInfoMessagePanel = new InfoMessagePanel(HELP_TIP_HEADER_LIVE_ALLOCATION, HELP_TIP_DESCRIPTION_LIVE_ALLOCATION, null);
    }
    myPanel.add(myClassifierPanel, BorderLayout.CENTER);
  }

  private void startHeapLoadingUi() {
    if (myColumnTree == null) {
      return;
    }
    myPanel.remove(myClassifierPanel);
    myPanel.add(myLoadingPanel.getComponent(), BorderLayout.CENTER);
    myLoadingPanel.setChildComponent(myClassifierPanel);
    myLoadingPanel.startLoading();
  }

  private void stopHeapLoadingUi() {
    if (myColumnTree == null) {
      return;
    }

    myPanel.remove(myLoadingPanel.getComponent());
    myPanel.add(myClassifierPanel, BorderLayout.CENTER);
    // Loading panel is registered with the project. Be extra careful not have it reference anything when we are done with it.
    myLoadingPanel.setChildComponent(null);
    myLoadingPanel.stopLoading();
  }

  private void refreshClassifierPanel() {
    assert myTreeRoot != null && myColumnTree != null && myHelpTipInfoMessagePanel != null;
    myClassifierPanel.removeAll();
    if (myTreeRoot.getAdapter().isEmpty()) {
      myClassifierPanel.add(myHelpTipInfoMessagePanel, BorderLayout.CENTER);
    }
    else {
      myClassifierPanel.add(myColumnTree, BorderLayout.CENTER);
    }
    myClassifierPanel.revalidate();
    myClassifierPanel.repaint();
  }

  private void refreshTree() {
    if (myHeapSet == null) {
      return;
    }

    assert myTreeRoot != null && myTreeModel != null && myTree != null;
    refreshClassifierPanel();

    myTreeRoot.reset();
    myTreeRoot.expandNode();
    myTreeModel.nodeStructureChanged(myTreeRoot);

    // re-select ClassifierSet
    if (mySelectedClassifierSet != null) {
      if (!mySelectedClassifierSet.isEmpty()) {
        MemoryObjectTreeNode nodeToSelect = findSmallestSuperSetNode(myTreeRoot, mySelectedClassifierSet);
        if (nodeToSelect != null && nodeToSelect.getAdapter().equals(mySelectedClassifierSet)) {
          TreePath treePath = new TreePath(nodeToSelect.getPathToRoot().toArray());
          myTree.expandPath(treePath.getParentPath());
          myTree.setSelectionPath(treePath);
          myTree.scrollPathToVisible(treePath);
        }
        else {
          mySelectedClassifierSet = null;
        }
      }
      else {
        mySelectedClassifierSet = null;
      }
    }

    if (myStage.getCaptureFilter() != null) {
      MemoryClassifierTreeNode treeNode = myTreeRoot;
      while (treeNode != null) {
        if (treeNode.getAdapter().getIsMatched()) {
          TreePath treePath = new TreePath(treeNode.getPathToRoot().toArray());
          myTree.expandPath(treePath.getParentPath());
          break;
        }

        treeNode.expandNode();
        myTreeModel.nodeStructureChanged(treeNode);
        MemoryClassifierTreeNode nextNode = null;
        for (MemoryObjectTreeNode<ClassifierSet> child : treeNode.getChildren()) {
          assert !child.getAdapter().getIsFiltered();
          assert child instanceof MemoryClassifierTreeNode;
          nextNode = (MemoryClassifierTreeNode)child;
          break;
        }
        treeNode = nextNode;
      }
    }
  }

  private void refreshHeapSet() {
    assert myCaptureObject != null && myTree != null;

    HeapSet heapSet = myStage.getSelectedHeapSet();
    if (heapSet == myHeapSet) {
      return;
    }

    myHeapSet = heapSet;

    if (myHeapSet != null) {
      refreshGrouping();
    }
  }

  /**
   * Refreshes the view based on the "group by" selection from the user.
   */
  private void refreshGrouping() {
    assert myCaptureObject != null && myTree != null;

    Comparator<MemoryObjectTreeNode<ClassifierSet>> comparator = myTreeRoot == null ? myInitialComparator : myTreeRoot.getComparator();
    HeapSet heapSet = myStage.getSelectedHeapSet();
    assert heapSet != null;
    heapSet.setClassGrouping(myStage.getConfiguration().getClassGrouping());
    myTreeRoot = new MemoryClassifierTreeNode(heapSet);
    myTreeRoot.expandNode(); // Expand it once to get all the children, since we won't display the tree root (HeapSet) by default.
    if (comparator != null) {
      myTreeRoot.sort(comparator);
    }

    myTreeModel = new DefaultTreeModel(myTreeRoot);
    myTree.setModel(myTreeModel);

    // Rename class column depending on group by mechanism
    assert myColumnTree != null;
    String headerName = null;
    switch (myStage.getConfiguration().getClassGrouping()) {
      case ARRANGE_BY_CLASS:
        headerName = "Class Name";
        break;
      case ARRANGE_BY_CALLSTACK:
        headerName = "Callstack Name";
        break;
      case ARRANGE_BY_PACKAGE:
        headerName = "Package Name";
    }
    assert myTableColumnModel != null;
    myTableColumnModel.getColumn(0).setHeaderValue(headerName);

    // Attempt to reselect the previously selected ClassSet node or FieldPath.
    ClassSet selectedClassSet = myStage.getSelectedClassSet();
    InstanceObject selectedInstance = myStage.getSelectedInstanceObject();
    List<FieldObject> fieldPath = myStage.getSelectedFieldObjectPath();

    refreshClassifierPanel();

    if (selectedClassSet == null) {
      return;
    }

    MemoryObjectTreeNode<ClassifierSet> nodeToSelect = findSmallestSuperSetNode(myTreeRoot, selectedClassSet);
    if ((nodeToSelect == null || !(nodeToSelect.getAdapter() instanceof ClassSet)) && selectedInstance != null) {
      ClassifierSet classifierSet = myTreeRoot.getAdapter().findContainingClassifierSet(selectedInstance);
      if (classifierSet != null) {
        nodeToSelect = findSmallestSuperSetNode(myTreeRoot, classifierSet);
      }
    }

    if (nodeToSelect == null || !(nodeToSelect.getAdapter() instanceof ClassSet)) {
      myStage.selectClassSet(null);
      return;
    }

    assert myTree != null;
    TreePath treePath = new TreePath(nodeToSelect.getPathToRoot().toArray());
    myClassSet = (ClassSet)nodeToSelect.getAdapter();
    myTree.expandPath(treePath.getParentPath());
    myTree.setSelectionPath(treePath);
    myTree.scrollPathToVisible(treePath);
    myStage.selectClassSet(myClassSet);
    myStage.selectInstanceObject(selectedInstance);
    myStage.selectFieldObjectPath(fieldPath);
  }

  /**
   * Scan through child {@link ClassifierSet}s for the given {@link InstanceObject}s and return the "path" containing all the target instances.
   *
   * @param rootNode  the root from where to start the search
   * @param targetSet target set of {@link InstanceObject}s to search for
   * @return the path of chained {@link ClassifierSet} that leads to the given instanceObjects, or throws an exception if not found.
   */
  @Nullable
  private static MemoryObjectTreeNode<ClassifierSet> findSmallestSuperSetNode(@NotNull MemoryObjectTreeNode<ClassifierSet> rootNode,
                                                                              @NotNull ClassifierSet targetSet) {
    if (rootNode.getAdapter().isSupersetOf(targetSet)) {
      for (MemoryObjectTreeNode<ClassifierSet> child : rootNode.getChildren()) {
        MemoryObjectTreeNode<ClassifierSet> result = findSmallestSuperSetNode(child, targetSet);
        if (result != null) {
          return result;
        }
      }

      return rootNode;
    }

    return null;
  }

  /**
   * Refreshes the view based on the selected {@link ClassSet}.
   */
  private void refreshClassSet() {
    if (myTreeRoot == null || myTreeModel == null || myTree == null || myClassSet == myStage.getSelectedClassSet()) {
      return;
    }

    myClassSet = myStage.getSelectedClassSet();
    if (myClassSet != null && !myClassSet.isEmpty()) {
      MemoryObjectTreeNode<ClassifierSet> node = findSmallestSuperSetNode(myTreeRoot, myClassSet);
      if (node != null) {
        TreePath treePath = new TreePath(node.getPathToRoot().toArray());
        myTree.expandPath(treePath.getParentPath());
        myTree.setSelectionPath(treePath);
        myTree.scrollPathToVisible(treePath);
      }
      else {
        myClassSet = null;
        myStage.selectClassSet(null);
      }
    }

    if (myClassSet == null) {
      mySelectedClassifierSet = null;
      myTree.clearSelection();
    }
  }

  @NotNull
  private ColoredTreeCellRenderer getNameColumnRenderer() {
    return new ColoredTreeCellRenderer() {
      @Override
      public void customizeCellRenderer(@NotNull JTree tree,
                                        Object value,
                                        boolean selected,
                                        boolean expanded,
                                        boolean leaf,
                                        int row,
                                        boolean hasFocus) {
        if (!(value instanceof MemoryObjectTreeNode)) {
          return;
        }

        MemoryObjectTreeNode node = (MemoryObjectTreeNode)value;
        if (node.getAdapter() instanceof ClassSet) {
          ClassSet classSet = (ClassSet)node.getAdapter();

          setIcon(((ClassSet)node.getAdapter()).hasStackInfo() ? StudioIcons.Profiler.Overlays.CLASS_STACK : PlatformIcons.CLASS_ICON);

          String className = classSet.getClassEntry().getSimpleClassName();
          String packageName = classSet.getClassEntry().getPackageName();
          append(className, SimpleTextAttributes.REGULAR_ATTRIBUTES, className);
          if (myStage.getConfiguration().getClassGrouping() == ARRANGE_BY_CLASS) {
            if (!packageName.isEmpty()) {
              String packageText = " (" + packageName + ")";
              append(packageText, SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES, packageText);
            }
          }
        }
        else if (node.getAdapter() instanceof PackageSet) {
          ClassifierSet set = (ClassifierSet)node.getAdapter();
          setIcon(set.hasStackInfo() ? StudioIcons.Profiler.Overlays.PACKAGE_STACK : PlatformIcons.PACKAGE_ICON);
          String name = set.getName();
          append(name, SimpleTextAttributes.REGULAR_ATTRIBUTES, name);
        }
        else if (node.getAdapter() instanceof MethodSet) {
          setIcon(PlatformIcons.METHOD_ICON);

          MethodSet methodObject = (MethodSet)node.getAdapter();
          String name = methodObject.getMethodName();
          String className = methodObject.getClassName();

          String nameAndLine = name + "()";
          append(nameAndLine, SimpleTextAttributes.REGULAR_ATTRIBUTES, nameAndLine);

          String classNameText = " (" + className + ")";
          append(classNameText, SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES, classNameText);
        }
        else if (node.getAdapter() instanceof ThreadSet) {
          setIcon(AllIcons.Debugger.ThreadSuspended);
          String threadName = node.getAdapter().getName();
          append(threadName, SimpleTextAttributes.REGULAR_ATTRIBUTES, threadName);
        }
        else if (node.getAdapter() instanceof HeapSet) {
          ClassifierSet set = (ClassifierSet)node.getAdapter();
          setIcon(set.hasStackInfo() ? StudioIcons.Profiler.Overlays.PACKAGE_STACK : PlatformIcons.PACKAGE_ICON);
          String name = set.getName() + " heap";
          append(name, SimpleTextAttributes.REGULAR_ATTRIBUTES, name);
        }

        setTextAlign(SwingConstants.LEFT);
      }
    };
  }

  /**
   * Creates a comparator function for the given {@link ClassifierSet}-specific and {@link ClassSet}-specific comparators.
   *
   * @param classifierSetComparator is a comparator for {@link ClassifierSet} objects, and not {@link ClassSet}
   * @return a {@link Comparator} that order all non-{@link ClassSet}s before {@link ClassSet}s, and orders according to the given
   * two params when the base class is the same
   */
  private static Comparator<MemoryObjectTreeNode<ClassifierSet>> createTreeNodeComparator(@NotNull Comparator<ClassifierSet> classifierSetComparator,
                                                                                          @NotNull Comparator<ClassSet> classSetComparator) {
    return (o1, o2) -> {
      int compareResult;
      ClassifierSet firstArg = o1.getAdapter();
      ClassifierSet secondArg = o2.getAdapter();
      if (firstArg instanceof ClassSet && secondArg instanceof ClassSet) {
        compareResult = classSetComparator.compare((ClassSet)firstArg, (ClassSet)secondArg);
      }
      else if (firstArg instanceof ClassSet) {
        compareResult = 1;
      }
      else if (secondArg instanceof ClassSet) {
        compareResult = -1;
      }
      else {
        compareResult = classifierSetComparator.compare(firstArg, secondArg);
      }
      return compareResult;
    };
  }

  /**
   * Convenience method for {@link #createTreeNodeComparator(Comparator, Comparator)}.
   */
  private static Comparator<MemoryObjectTreeNode<ClassifierSet>> createTreeNodeComparator(@NotNull Comparator<ClassSet> classObjectComparator) {
    return createTreeNodeComparator(Comparator.comparing(ClassifierSet::getName), classObjectComparator);
  }

  private static class MemoryClassifierTreeNode extends LazyMemoryObjectTreeNode<ClassifierSet> {
    private MemoryClassifierTreeNode(@NotNull ClassifierSet classifierSet) {
      super(classifierSet, false);
    }

    @Override
    public void add(@NotNull MemoryObjectTreeNode child) {
      if (myMemoizedChildrenCount == myChildren.size()) {
        super.add(child);
        myMemoizedChildrenCount++;
      }
    }

    @Override
    public void remove(@NotNull MutableTreeNode child) {
      if (myMemoizedChildrenCount == myChildren.size()) {
        super.remove(child);
        myMemoizedChildrenCount--;
      }
    }

    @Override
    public int computeChildrenCount() {
      return getAdapter().getChildrenClassifierSets().size();
    }

    @Override
    public void expandNode() {
      if (myMemoizedChildrenCount == myChildren.size()) {
        return;
      }

      getChildCount();
      getAdapter().getChildrenClassifierSets().forEach(set -> {
        MemoryClassifierTreeNode node = new MemoryClassifierTreeNode(set);
        node.setTreeModel(getTreeModel());
        insert(node, myChildren.size());
      });
    }
  }
}
