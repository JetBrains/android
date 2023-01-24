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

import static com.android.tools.adtui.common.AdtUiUtils.DEFAULT_TOP_BORDER;
import static com.android.tools.profilers.ProfilerLayout.ROW_HEIGHT_PADDING;
import static com.android.tools.profilers.ProfilerLayout.TABLE_ROW_BORDER;
import static com.android.tools.profilers.memory.ClassGrouping.ARRANGE_BY_CLASS;

import com.android.tools.adtui.common.ColoredIconGenerator;
import com.android.tools.adtui.common.ColumnTreeBuilder;
import com.android.tools.adtui.instructions.InstructionsPanel;
import com.android.tools.adtui.instructions.NewRowInstruction;
import com.android.tools.adtui.instructions.TextInstruction;
import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.adtui.model.formatter.NumberFormatter;
import com.android.tools.adtui.stdui.StandardColors;
import com.android.tools.idea.codenavigation.CodeLocation;
import com.android.tools.inspectors.common.ui.ContextMenuInstaller;
import com.android.tools.profilers.IdeProfilerComponents;
import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.ProfilerFonts;
import com.android.tools.profilers.memory.adapters.CaptureObject;
import com.android.tools.profilers.memory.adapters.CaptureObject.ClassifierAttribute;
import com.android.tools.profilers.memory.adapters.FieldObject;
import com.android.tools.profilers.memory.adapters.InstanceObject;
import com.android.tools.profilers.memory.adapters.MemoryObject;
import com.android.tools.profilers.memory.adapters.classifiers.AllHeapSet;
import com.android.tools.profilers.memory.adapters.classifiers.ClassSet;
import com.android.tools.profilers.memory.adapters.classifiers.ClassifierSet;
import com.android.tools.profilers.memory.adapters.classifiers.HeapSet;
import com.android.tools.profilers.memory.adapters.classifiers.MethodSet;
import com.android.tools.profilers.memory.adapters.classifiers.NativeAllocationMethodSet;
import com.android.tools.profilers.memory.adapters.classifiers.NativeCallStackSet;
import com.android.tools.profilers.memory.adapters.classifiers.PackageSet;
import com.android.tools.profilers.memory.adapters.classifiers.ThreadSet;
import com.android.tools.profilers.memory.adapters.instancefilters.CaptureObjectInstanceFilter;
import com.android.tools.profilers.stacktrace.LoadingPanel;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.intellij.icons.AllIcons;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.PlatformIcons;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.UIUtilities;
import icons.StudioIcons;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.SortOrder;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableColumnModel;
import javax.swing.table.TableColumnModel;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class MemoryClassifierView extends AspectObserver implements CapturePanelTabContainer {
  private static final int LABEL_COLUMN_WIDTH = 800;
  private static final int MODULE_COLUMN_WIDTH = 100;
  private static final int HEAP_UPDATING_DELAY_MS = 250;
  private static final int MIN_COLUMN_WIDTH = 16;

  private static final String HELP_TIP_HEADER_LIVE_ALLOCATION = "Selected range has no allocations or deallocations";
  private static final String HELP_TIP_DESCRIPTION_LIVE_ALLOCATION =
    "Select a valid range in the timeline where the Java memory is changing to view allocations and deallocations.";
  private static final String HELP_TIP_HEADER_EXPLICIT_CAPTURE = "Selected capture has no contents";
  private static final String HELP_TIP_DESCRIPTION_EXPLICIT_CAPTURE = "There are no allocations in the selected capture.";
  private static final String HELP_TIP_HEADER_FILTER_NO_MATCH = "Selected filters have no match";

  @NotNull private final MemoryCaptureSelection mySelection;

  @NotNull private final ContextMenuInstaller myContextMenuInstaller;

  @NotNull private final Map<ClassifierAttribute, AttributeColumn<ClassifierSet>> myAttributeColumns = new HashMap<>();

  @Nullable private CaptureObject myCaptureObject = null;

  @Nullable private HeapSet myHeapSet = null;

  @Nullable private ClassSet myClassSet = null;

  @Nullable private ClassifierSet mySelectedClassifierSet = null;

  @NotNull private final JPanel myPanel = new JPanel(new BorderLayout());

  @NotNull private final JPanel myClassifierPanel = new JPanel(new BorderLayout());

  @NotNull private final LoadingPanel myLoadingPanel;

  @Nullable private InstructionsPanel myHelpTipPanel; // Panel to let user know to select a range with allocations in it.

  @Nullable private JComponent myColumnTree;

  @Nullable private JTree myTree;

  @Nullable private DefaultTreeModel myTreeModel;

  @Nullable private TableColumnModel myTableColumnModel;

  @Nullable private MemoryClassifierTreeNode myTreeRoot;

  @Nullable private Comparator<MemoryObjectTreeNode<ClassifierSet>> myInitialComparator;

  private final CsvExporter myCsvExporter;

  public MemoryClassifierView(@NotNull MemoryCaptureSelection selection, @NotNull IdeProfilerComponents ideProfilerComponents) {
    mySelection = selection;
    myCsvExporter = new CsvExporter(() -> myTree, () -> myCaptureObject, ideProfilerComponents, selection.getIdeServices());
    myContextMenuInstaller = ideProfilerComponents.createContextMenuInstaller();
    myLoadingPanel = ideProfilerComponents.createLoadingPanel(HEAP_UPDATING_DELAY_MS);
    myLoadingPanel.setLoadingText("");

    mySelection.getAspect().addDependency(this)
      .onChange(CaptureSelectionAspect.CURRENT_LOADING_CAPTURE, this::loadCapture)
      .onChange(CaptureSelectionAspect.CURRENT_LOADED_CAPTURE, this::refreshCapture)
      .onChange(CaptureSelectionAspect.CURRENT_HEAP, this::refreshHeapSet)
      .onChange(CaptureSelectionAspect.CURRENT_HEAP_UPDATING, this::startHeapLoadingUi)
      .onChange(CaptureSelectionAspect.CURRENT_HEAP_UPDATED, this::stopHeapLoadingUi)
      .onChange(CaptureSelectionAspect.CURRENT_HEAP_CONTENTS, this::refreshTree)
      .onChange(CaptureSelectionAspect.CURRENT_CLASS, this::refreshClassSet)
      .onChange(CaptureSelectionAspect.CLASS_GROUPING, this::refreshGrouping)
      .onChange(CaptureSelectionAspect.CURRENT_FILTER, this::refreshFilter);

    myAttributeColumns.put(
      ClassifierAttribute.LABEL,
      new AttributeColumn<>(
        "Class Name", this::getNameColumnRenderer, SwingConstants.LEFT, LABEL_COLUMN_WIDTH, SortOrder.ASCENDING,
        createTreeNodeComparator(Comparator.comparing(ClassifierSet::getName), Comparator.comparing(ClassSet::getName))));
    myAttributeColumns.put(
      ClassifierAttribute.MODULE,
      new AttributeColumn<>(
        "Module Name", this::getModuleColumnRenderer, SwingConstants.LEFT, MODULE_COLUMN_WIDTH, SortOrder.ASCENDING,
        createTreeNodeComparator(Comparator.comparing(NativeCallStackSet::getModuleName))));
    myAttributeColumns.put(
      ClassifierAttribute.ALLOCATIONS,
      makeColumn("Allocations", 110, ClassifierSet::getDeltaAllocationCount));
    myAttributeColumns.put(
      ClassifierAttribute.DEALLOCATIONS,
      makeColumn("Deallocations", 130, ClassifierSet::getDeltaDeallocationCount));
    myAttributeColumns.put(
      ClassifierAttribute.TOTAL_COUNT,
      makeColumn("Total Count", 110, ClassifierSet::getTotalObjectCount));
    myAttributeColumns.put(
      ClassifierAttribute.NATIVE_SIZE,
      makeColumn("Native Size", 110, ClassifierSet::getTotalNativeSize, Comparator.comparing(ClassifierSet::getName)));
    myAttributeColumns.put(
      ClassifierAttribute.SHALLOW_SIZE,
      makeColumn("Shallow Size", 120, ClassifierSet::getTotalShallowSize, Comparator.comparing(ClassifierSet::getName)));
    myAttributeColumns.put(
      ClassifierAttribute.RETAINED_SIZE,
      makeColumn("Retained Size", 130, ClassifierSet::getTotalRetainedSize));
    myAttributeColumns.put(
      ClassifierAttribute.ALLOCATIONS_SIZE,
      makeColumn("Allocations Size", 160, ClassifierSet::getAllocationSize));
    myAttributeColumns.put(
      ClassifierAttribute.DEALLOCATIONS_SIZE,
      makeColumn("Deallocations Size", 180, ClassifierSet::getDeallocationSize));
    myAttributeColumns.put(
      ClassifierAttribute.REMAINING_SIZE,
      makeColumn("Remaining Size", 140, ClassifierSet::getTotalRemainingSize));
    myAttributeColumns.put(
      ClassifierAttribute.SHALLOW_DIFFERENCE,
      makeColumn("Shallow Size Change", 110, ClassifierSet::getDeltaShallowSize));
  }

  /**
   * Make right-aligned, descending column displaying integer property with custom order for non-ClassSet values
   */
  private AttributeColumn<ClassifierSet> makeColumn(@NotNull String name,
                                                    int width,
                                                    @NotNull ToLongFunction<ClassifierSet> prop,
                                                    @NotNull Comparator<ClassifierSet> comp) {

    Function<MemoryObjectTreeNode<ClassifierSet>, String> textGetter = node ->
      NumberFormatter.formatInteger(prop.applyAsLong(node.getAdapter()));
    // Progress-bar style background that reflects percentage contribution
    final Supplier<ColoredTreeCellRenderer> renderer = () -> new PercentColumnRenderer<>(
      textGetter, v -> null, SwingConstants.RIGHT,
      node -> {
        MemoryObjectTreeNode<ClassifierSet> parent = node.myParent;
        if (parent == null) {
          return 0;
        }
        else {
          assert myTreeRoot != null;
          // Compute relative contribution with respect to top-most parent
          long myVal = prop.applyAsLong(node.getAdapter());
          ClassifierSet root = myTreeRoot.getAdapter();
          long parentVal = prop.applyAsLong(root);
          return parentVal == 0 ? 0 : (int)(myVal * 100 / parentVal);
        }
      }
    );

    int preferredWidth = Math.max(SimpleColumnRenderer.DEFAULT_COLUMN_WIDTH, width);
    int maxWidth = preferredWidth * 4;

    return new AttributeColumn<>(
      name,
      renderer,
      SwingConstants.RIGHT,
      preferredWidth,
      maxWidth,
      SortOrder.DESCENDING,
      createTreeNodeComparator(comp, Comparator.comparingLong(prop))
    );
  }

  /**
   * Make right-aligned, descending column displaying integer property
   */
  private AttributeColumn<ClassifierSet> makeColumn(String name, int width, ToLongFunction<ClassifierSet> prop) {
    return makeColumn(name, width, prop, Comparator.comparingLong(prop));
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myPanel;
  }

  @Override
  public void onSelectionChanged(boolean selected) {
    // Default
  }

  @VisibleForTesting
  @Nullable
  public JTree getTree() {
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
    myHelpTipPanel = null;
    myColumnTree = null;
    myTree = null;
    myTreeRoot = null;
    myTreeModel = null;
    myTableColumnModel = null;
    myPanel.removeAll();
    mySelection.selectClassSet(null);
  }

  private void loadCapture() {
    if (mySelection.getSelectedCapture() == null || myCaptureObject != mySelection.getSelectedCapture()) {
      reset();
    }
  }

  private void refreshFilter() {
    if (myHeapSet != null) {
      refreshTree();
    }
  }

  @VisibleForTesting
  public void refreshCapture() {
    myCaptureObject = mySelection.getSelectedCapture();
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
    myTree.setBorder(TABLE_ROW_BORDER);
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
        mySelection.selectClassSet(myClassSet);
      }
    });

    myContextMenuInstaller.installNavigationContextMenu(myTree, mySelection.getIdeServices().getCodeNavigator(), () -> {
      TreePath selection = myTree.getSelectionPath();
      if (selection == null || !(selection.getLastPathComponent() instanceof MemoryObjectTreeNode)) {
        return null;
      }

      MemoryObject treeNodeAdapter = ((MemoryObjectTreeNode)selection.getLastPathComponent()).getAdapter();
      if (treeNodeAdapter instanceof ClassSet) {
        ClassSet classSet = (ClassSet)treeNodeAdapter;
        return new CodeLocation.Builder(classSet.getClassEntry().getClassName()).build();
      }
      if (treeNodeAdapter instanceof NativeCallStackSet) {
        NativeCallStackSet nativeSet = (NativeCallStackSet)treeNodeAdapter;
        if (!Strings.isNullOrEmpty(nativeSet.getFileName())) {
          return new CodeLocation.Builder(nativeSet.getName()) // Expects class name but we don't have that so we use the function.
            .setMethodName(nativeSet.getName())
            .setFileName(nativeSet.getFileName())
            .setLineNumber(nativeSet.getLineNumber() - 1) // Line numbers from symbolizer are 1 based UI is 0 based.
            .build();
        }
      }
      return null;
    });

    if (mySelection.getIdeServices().getFeatureConfig().isMemoryCSVExportEnabled()) {
      myContextMenuInstaller.installGenericContextMenu(myTree, myCsvExporter.makeClassExportItem());
      myContextMenuInstaller.installGenericContextMenu(myTree, myCsvExporter.makeInstanceExportItem());
    }

    List<ClassifierAttribute> attributes = myCaptureObject.getClassifierAttributes();
    myTableColumnModel = new DefaultTableColumnModel();
    ColumnTreeBuilder builder = new ColumnTreeBuilder(myTree, myTableColumnModel);
    ClassifierAttribute sortAttribute = Collections.max(attributes, Comparator.comparingInt(ClassifierAttribute::getWeight));
    for (ClassifierAttribute attribute : attributes) {
      AttributeColumn<ClassifierSet> column = myAttributeColumns.get(attribute);
      ColumnTreeBuilder.ColumnBuilder columnBuilder = column.getBuilder();
      columnBuilder.setMinWidth(MIN_COLUMN_WIDTH);
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
    builder.setHoverColor(StandardColors.HOVER_COLOR);
    builder.setBackground(ProfilerColors.DEFAULT_BACKGROUND);
    builder.setBorder(DEFAULT_TOP_BORDER);
    builder.setShowVerticalLines(true);
    builder.setTableIntercellSpacing(new Dimension());
    myColumnTree = builder.build();

    myHelpTipPanel = mySelection.getSelectedCapture().isExportable() ?
                     makeInstructionsPanel(HELP_TIP_HEADER_EXPLICIT_CAPTURE, HELP_TIP_DESCRIPTION_EXPLICIT_CAPTURE) :
                     makeInstructionsPanel(HELP_TIP_HEADER_LIVE_ALLOCATION, HELP_TIP_DESCRIPTION_LIVE_ALLOCATION);
    myPanel.add(myClassifierPanel, BorderLayout.CENTER);
  }

  private InstructionsPanel makeInstructionsPanel(String header, String desc) {
    return new InstructionsPanel.Builder(
      new TextInstruction(UIUtilities.getFontMetrics(myClassifierPanel, ProfilerFonts.H3_FONT), header),
      new NewRowInstruction(NewRowInstruction.DEFAULT_ROW_MARGIN),
      new TextInstruction(UIUtilities.getFontMetrics(myClassifierPanel, ProfilerFonts.STANDARD_FONT), desc))
      .setColors(UIUtil.getInactiveTextColor(), null)
      .build();
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
    assert myTreeRoot != null && myColumnTree != null && myHelpTipPanel != null;
    myClassifierPanel.removeAll();
    if (myTreeRoot.getAdapter().isEmpty()) {
      if (myCaptureObject != null && !myCaptureObject.getSelectedInstanceFilters().isEmpty()) {
        List<String> filterNames = myCaptureObject.getSelectedInstanceFilters().stream()
          .map(CaptureObjectInstanceFilter::getDisplayName)
          .collect(Collectors.toList());
        String msg = String.format("There are no allocations satisfying selected filter%s: %s",
                                   filterNames.size() > 1 ? "s" : "",
                                   String.join(", ", filterNames));
        myClassifierPanel.add(makeInstructionsPanel(HELP_TIP_HEADER_FILTER_NO_MATCH, msg), BorderLayout.CENTER);
      }
      else {
        myClassifierPanel.add(myHelpTipPanel, BorderLayout.CENTER);
      }
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

    if (!mySelection.getFilterHandler().getFilter().isEmpty()) {
      MemoryClassifierTreeNode treeNode = myTreeRoot;
      while (treeNode != null) {
        if (treeNode.getAdapter().isMatched()) {
          TreePath treePath = new TreePath(treeNode.getPathToRoot().toArray());
          myTree.expandPath(treePath.getParentPath());
          break;
        }

        treeNode.expandNode();
        myTreeModel.nodeStructureChanged(treeNode);
        MemoryClassifierTreeNode nextNode = null;
        for (MemoryObjectTreeNode<ClassifierSet> child : treeNode.getChildren()) {
          assert !child.getAdapter().isFiltered();
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

    HeapSet heapSet = mySelection.getSelectedHeapSet();
    if (heapSet == myHeapSet) {
      return;
    }

    myHeapSet = heapSet;

    if (myHeapSet != null) {
      refreshGrouping();
    }

    // When the root is "all"-heap, hide it
    if (myHeapSet instanceof AllHeapSet) {
      myTree.setRootVisible(false);
      myTree.setShowsRootHandles(true);
    }
    else {
      myTree.setRootVisible(true);
      myTree.setShowsRootHandles(false);
    }
  }

  /**
   * Refreshes the view based on the "group by" selection from the user.
   */
  @VisibleForTesting
  public void refreshGrouping() {
    HeapSet heapSet = mySelection.getSelectedHeapSet();
    // This gets called when a capture is loading, or we change the profiler configuration.
    // During a loading capture we adjust which configurations are available and reset set the selection to the first one.
    // This triggers this callback to be fired before we have a heapset. In this scenario we just early exit.
    if (heapSet == null || myCaptureObject == null || myTree == null) {
      return;
    }

    Comparator<MemoryObjectTreeNode<ClassifierSet>> comparator = myTreeRoot == null ? myInitialComparator : myTreeRoot.getComparator();

    heapSet.setClassGrouping(mySelection.getClassGrouping());
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
    switch (mySelection.getClassGrouping()) {
      case ARRANGE_BY_CLASS:
        headerName = "Class Name";
        break;
      case ARRANGE_BY_CALLSTACK:
      case NATIVE_ARRANGE_BY_CALLSTACK:
        headerName = "Callstack Name";
        break;
      case ARRANGE_BY_PACKAGE:
        headerName = "Package Name";
        break;
      case NATIVE_ARRANGE_BY_ALLOCATION_METHOD:
        headerName = "Allocation function";
        break;
    }
    assert myTableColumnModel != null;
    myTableColumnModel.getColumn(0).setHeaderValue(headerName);

    // Attempt to reselect the previously selected ClassSet node or FieldPath.
    ClassSet selectedClassSet = mySelection.getSelectedClassSet();
    InstanceObject selectedInstance = mySelection.getSelectedInstanceObject();
    List<FieldObject> fieldPath = mySelection.getSelectedFieldObjectPath();

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
      mySelection.selectClassSet(null);
      return;
    }

    assert myTree != null;
    TreePath treePath = new TreePath(nodeToSelect.getPathToRoot().toArray());
    myClassSet = (ClassSet)nodeToSelect.getAdapter();
    myTree.expandPath(treePath.getParentPath());
    myTree.setSelectionPath(treePath);
    myTree.scrollPathToVisible(treePath);
    mySelection.selectClassSet(myClassSet);
    mySelection.selectInstanceObject(selectedInstance);
    mySelection.selectFieldObjectPath(fieldPath);
  }

  /**
   * Scan through child {@link ClassifierSet}s for the given {@link InstanceObject}s and return the "path" containing all the target instances.
   *
   * @param rootNode  the root from where to start the search
   * @param targetSet target set of {@link InstanceObject}s to search for
   * @return the path of chained {@link ClassifierSet} that leads to the given instanceObjects, or throws an exception if not found.
   */
  @VisibleForTesting
  @Nullable
  public static MemoryObjectTreeNode<ClassifierSet> findSmallestSuperSetNode(@NotNull MemoryObjectTreeNode<ClassifierSet> rootNode,
                                                                             @NotNull ClassifierSet targetSet) {
    Set<InstanceObject> target = targetSet.getInstancesStream().collect(Collectors.toSet());
    // When `targetSet` is empty, if `rootNode` isn't empty, many of its leaves (if any) trivially count as smallest super-set nodes.
    // Because the result isn't interesting, we arbitrarily return `rootNode` itself for this special case to save some work.
    return targetSet.isEmpty() ? rootNode
         : rootNode.getAdapter().isSupersetOf(target) ? findSmallestSuperSetNode(rootNode, target)
         : null;
  }

  @NotNull
  private static MemoryObjectTreeNode<ClassifierSet> findSmallestSuperSetNode(@NotNull MemoryObjectTreeNode<ClassifierSet> rootNode,
                                                                              @NotNull Set<InstanceObject> targetSet) {
    // At any point, we maintain that `rootNode` is the only subtree that can cover non-empty `targetSet`.
    // Given that nodes' immediate instances don't overlap:
    // - If `rootNode`'s immediate instances overlap with `targetSet`, then it's also the smallest superset.
    // - If `rootNode` doesn't immediately overlap with `targetSet` but it has 2+ children that overlap with `targetSet`, it must
    //   also be the smallest superset

    // Get children first for the side-effect of possibly pushing down the node's instances to its children
    List<MemoryObjectTreeNode<ClassifierSet>> childNodes = rootNode.getChildren();

    if (rootNode.getAdapter().immediateInstancesOverlapWith(targetSet)) {
      return rootNode;
    }

    List<MemoryObjectTreeNode<ClassifierSet>> subResults = childNodes.stream()
      .filter(node -> node.getAdapter().overlapsWith(targetSet))
      .collect(Collectors.toList());
    return subResults.size() == 1 ? findSmallestSuperSetNode(subResults.get(0), targetSet) : rootNode;
  }

  /**
   * Refreshes the view based on the selected {@link ClassSet}.
   */
  private void refreshClassSet() {
    if (myTreeRoot == null || myTreeModel == null || myTree == null || myClassSet == mySelection.getSelectedClassSet()) {
      return;
    }

    myClassSet = mySelection.getSelectedClassSet();
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
        mySelection.selectClassSet(null);
      }
    }

    if (myClassSet == null) {
      mySelectedClassifierSet = null;
      myTree.clearSelection();
    }
  }

  @NotNull
  @VisibleForTesting
  ColoredTreeCellRenderer getModuleColumnRenderer() {
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
        if (node.getAdapter() instanceof NativeCallStackSet) {
          NativeCallStackSet set = (NativeCallStackSet)node.getAdapter();
          String name = set.getModuleName();
          if (!Strings.isNullOrEmpty(name) && name.contains("/")) {
            name = name.substring(name.lastIndexOf("/") + 1);
            append(name, SimpleTextAttributes.REGULAR_ATTRIBUTES, name);
          }
        }
        setTextAlign(SwingConstants.LEFT);
      }
    };
  }

  @NotNull
  @VisibleForTesting
  ColoredTreeCellRenderer getNameColumnRenderer() {
    return new ColoredTreeCellRenderer() {
      private long myLeakCount = 0;

      @Override
      protected void paintComponent(Graphics g) {
        if (myLeakCount > 0) {
          int width = getWidth();
          int height = getHeight();

          String text = String.valueOf(myLeakCount);
          ((Graphics2D)g).setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
          int textWidth = g.getFontMetrics().stringWidth(text);

          Icon i = mySelected && isFocused()
                   ? ColoredIconGenerator.generateWhiteIcon(StudioIcons.Common.WARNING)
                   : StudioIcons.Common.WARNING;
          int iconWidth = i.getIconWidth();
          int iconHeight = i.getIconHeight();
          i.paintIcon(this, g, width - iconWidth - textWidth - 6, (height - iconHeight) / 2);

          g.drawString(text, width - textWidth - 4, (height + iconHeight) / 2 - 2);
        }
        // paint real content last
        super.paintComponent(g);
      }

      private void setIconColorized(Icon icon) {
        setIcon(mySelected && isFocused() ? ColoredIconGenerator.generateWhiteIcon(icon) : icon);
      }

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

          setIconColorized(((ClassSet)node.getAdapter()).hasStackInfo()
                           ? StudioIcons.Profiler.Overlays.CLASS_STACK
                           : PlatformIcons.CLASS_ICON);

          String className = classSet.getClassEntry().getSimpleClassName();
          String packageName = classSet.getClassEntry().getPackageName();
          append(className, SimpleTextAttributes.REGULAR_ATTRIBUTES, className);
          if (mySelection.getClassGrouping() == ARRANGE_BY_CLASS) {
            if (!packageName.isEmpty()) {
              String packageText = " (" + packageName + ")";
              append(packageText, SimpleTextAttributes.GRAY_ATTRIBUTES, packageText);
            }
          }
        }
        else if (node.getAdapter() instanceof PackageSet) {
          ClassifierSet set = (ClassifierSet)node.getAdapter();
          setIconColorized(set.hasStackInfo() ? StudioIcons.Profiler.Overlays.PACKAGE_STACK : PlatformIcons.PACKAGE_ICON);
          String name = set.getName();
          append(name, SimpleTextAttributes.REGULAR_ATTRIBUTES, name);
        }
        else if (node.getAdapter() instanceof MethodSet) {
          setIconColorized(PlatformIcons.METHOD_ICON);

          MethodSet methodObject = (MethodSet)node.getAdapter();
          String name = methodObject.getMethodName();
          String className = methodObject.getClassName();

          String nameAndLine = name + "()";
          append(nameAndLine, SimpleTextAttributes.REGULAR_ATTRIBUTES, nameAndLine);

          if (!Strings.isNullOrEmpty(className)) {
            String classNameText = " (" + className + ")";
            append(classNameText, SimpleTextAttributes.GRAY_ATTRIBUTES, classNameText);
          }
        }
        else if (node.getAdapter() instanceof ThreadSet) {
          setIconColorized(AllIcons.Debugger.ThreadSuspended);
          String threadName = node.getAdapter().getName();
          append(threadName, SimpleTextAttributes.REGULAR_ATTRIBUTES, threadName);
        }
        else if (node.getAdapter() instanceof HeapSet) {
          ClassifierSet set = (ClassifierSet)node.getAdapter();
          setIconColorized(set.hasStackInfo() ? StudioIcons.Profiler.Overlays.PACKAGE_STACK : PlatformIcons.PACKAGE_ICON);
          String name = set.getName() + " heap";
          append(name, SimpleTextAttributes.REGULAR_ATTRIBUTES, name);
        }
        else if (node.getAdapter() instanceof NativeCallStackSet) {
          ClassifierSet set = (ClassifierSet)node.getAdapter();
          setIconColorized(StudioIcons.Profiler.Overlays.METHOD_STACK);
          String name = set.getName();
          append(name, SimpleTextAttributes.REGULAR_ATTRIBUTES, name);
        }
        else if (node.getAdapter() instanceof NativeAllocationMethodSet) {
          ClassifierSet set = (ClassifierSet)node.getAdapter();
          setIconColorized(StudioIcons.Profiler.Overlays.ARRAY_STACK);
          String name = set.getName();
          append(name, SimpleTextAttributes.REGULAR_ATTRIBUTES, name);
        }

        if (node.getAdapter() instanceof ClassifierSet) {
          CaptureObjectInstanceFilter leakFilter = myCaptureObject != null ? myCaptureObject.getActivityFragmentLeakFilter() : null;
          myLeakCount = leakFilter != null ?
                        ((ClassifierSet)node.getAdapter()).getInstanceFilterMatchCount(leakFilter) :
                        0;
          setToolTipText(myLeakCount > 1 ? "There are " + myLeakCount + " leaks" :
                         myLeakCount > 0 ? "There is 1 leak" :
                         null);
        }
        setTextAlign(SwingConstants.LEFT);
      }
    };
  }

  private static Comparator<MemoryObjectTreeNode<ClassifierSet>> createTreeNodeComparator(
    @NotNull Comparator<NativeCallStackSet> comparator) {
    return (o1, o2) -> {
      ClassifierSet firstArg = o1.getAdapter();
      ClassifierSet secondArg = o2.getAdapter();
      if (firstArg instanceof NativeCallStackSet && secondArg instanceof NativeCallStackSet) {
        return comparator.compare((NativeCallStackSet)firstArg, (NativeCallStackSet)secondArg);
      }
      else {
        return 0;
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
  private static Comparator<MemoryObjectTreeNode<ClassifierSet>> createTreeNodeComparator(
    @NotNull Comparator<ClassifierSet> classifierSetComparator, @NotNull Comparator<ClassSet> classSetComparator) {
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
