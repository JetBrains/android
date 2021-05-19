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

import static com.android.tools.adtui.common.AdtUiUtils.DEFAULT_BORDER_COLOR;
import static com.android.tools.adtui.common.AdtUiUtils.DEFAULT_TOP_BORDER;
import static com.android.tools.profilers.ProfilerLayout.ROW_HEIGHT_PADDING;
import static com.android.tools.profilers.ProfilerLayout.TABLE_ROW_BORDER;
import static com.android.tools.profilers.memory.SimpleColumnRenderer.makeIntColumn;
import static com.android.tools.profilers.memory.SimpleColumnRenderer.makeSizeColumn;
import static com.android.tools.profilers.memory.SimpleColumnRenderer.onSubclass;

import com.android.tools.adtui.common.ColumnTreeBuilder;
import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.adtui.model.StreamingTimeline;
import com.android.tools.adtui.model.formatter.NumberFormatter;
import com.android.tools.adtui.model.formatter.TimeFormatter;
import com.android.tools.adtui.stdui.CommonTabbedPane;
import com.android.tools.adtui.stdui.StandardColors;
import com.android.tools.profilers.CloseButton;
import com.android.tools.profilers.ContextMenuInstaller;
import com.android.tools.profilers.IdeProfilerComponents;
import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.analytics.FeatureTracker;
import com.android.tools.profilers.memory.adapters.CaptureObject;
import com.android.tools.profilers.memory.adapters.CaptureObject.InstanceAttribute;
import com.android.tools.profilers.memory.adapters.FieldObject;
import com.android.tools.profilers.memory.adapters.InstanceObject;
import com.android.tools.profilers.memory.adapters.MemoryObject;
import com.android.tools.profilers.memory.adapters.ReferenceObject;
import com.android.tools.profilers.memory.adapters.ValueObject;
import com.android.tools.profilers.memory.adapters.classifiers.ClassSet;
import com.android.tools.profilers.memory.adapters.classifiers.ClassifierSet;
import com.android.tools.profilers.memory.adapters.classifiers.HeapSet;
import com.android.tools.profilers.memory.instanceviewers.BitmapViewer;
import com.android.tools.profilers.memory.instanceviewers.InstanceViewer;
import com.android.tools.profilers.stacktrace.CodeLocation;
import com.android.tools.profilers.stacktrace.ContextMenuItem;
import com.android.tools.profilers.stacktrace.StackTraceView;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.ui.JBEmptyBorder;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.ItemEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongFunction;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTree;
import javax.swing.SortOrder;
import javax.swing.SwingConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A view object that is responsible for displaying the callstack + references of an {@link InstanceObject} based on whether the
 * information is available. If no detailed information can be obtained from the InstanceObject, this UI is responsible
 * for automatically hiding itself.
 */
public final class MemoryInstanceDetailsView extends AspectObserver {
  private static final String TITLE_TAB_FIELDS = "Fields";
  private static final String TITLE_TAB_REFERENCES = "References";
  private static final String TITLE_TAB_ALLOCATION_CALLSTACK = "Allocation Call Stack";
  private static final String TITLE_TAB_DEALLOCATION_CALLSTACK = "Deallocation Call Stack";
  private static final int LABEL_COLUMN_WIDTH = 500;

  @NotNull private final MemoryCaptureSelection mySelection;

  @NotNull private final IdeProfilerComponents myIdeProfilerComponents;

  @NotNull private final JTabbedPane myTabsPanel;

  @NotNull private final StackTraceView myAllocationStackTraceView;

  @NotNull private final StackTraceView myDeallocationStackTraceView;

  @Nullable private JComponent myReferenceColumnTree;

  @Nullable private JTree myReferenceTree;

  @Nullable private JTree myFieldTree;

  @NotNull private final JBCheckBox myGCRootCheckBox = new JBCheckBox("Show nearest GC root only", false);

  @NotNull private final JPanel myRefPanel = new JPanel(new BorderLayout());

  @NotNull private final JBLabel myTitle = new JBLabel();

  @NotNull private final JBPanel myPanel = new JBPanel(new BorderLayout());

  @NotNull private final Map<InstanceAttribute, AttributeColumn> myAttributeColumns = new HashMap<>();

  @NotNull private final List<InstanceViewer> myInstanceViewers = new ArrayList<>();

  MemoryInstanceDetailsView(@NotNull MemoryCaptureSelection selection,
                            @NotNull IdeProfilerComponents ideProfilerComponents,
                            @NotNull StreamingTimeline timeline) {
    mySelection = selection;
    mySelection.getAspect().addDependency(this)
      .onChange(CaptureSelectionAspect.CURRENT_INSTANCE, this::instanceChanged)
      .onChange(CaptureSelectionAspect.CURRENT_FIELD_PATH, this::instanceChanged);
    myIdeProfilerComponents = ideProfilerComponents;

    myTabsPanel = new CommonTabbedPane();
    myTabsPanel.addChangeListener(this::trackActiveTab);
    myAllocationStackTraceView = ideProfilerComponents.createStackView(selection.getAllocationStackTraceModel());
    myDeallocationStackTraceView = ideProfilerComponents.createStackView(selection.getDeallocationStackTraceModel());

    JPanel titleWrapper = new JPanel(new BorderLayout());
    titleWrapper.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, DEFAULT_BORDER_COLOR));
    myTitle.setBorder(new JBEmptyBorder(4, 4, 4, 0));
    titleWrapper.add(myTitle, BorderLayout.CENTER);
    titleWrapper.add(new CloseButton(e -> mySelection.selectInstanceObject(null)), BorderLayout.EAST);
    myPanel.add(myTabsPanel, BorderLayout.CENTER);
    if (selection.getIdeServices().getFeatureConfig().isSeparateHeapDumpUiEnabled()) {
      myPanel.add(titleWrapper, BorderLayout.NORTH);
    }
    myPanel.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, DEFAULT_BORDER_COLOR));

    myInstanceViewers.add(new BitmapViewer());

    myGCRootCheckBox.addItemListener(e -> {
      instanceChanged();
      myTabsPanel.setSelectedComponent(myRefPanel);
      if (e.getStateChange() == ItemEvent.SELECTED) {
        repeatedlyExpandFirstReference();
      }
    });

    LongFunction<String> timeFormatter = t ->
      TimeFormatter.getSemiSimplifiedClockString(timeline.convertToRelativeTimeUs(t));

    myAttributeColumns.put(
      InstanceAttribute.LABEL,
      new AttributeColumn<ValueObject>(
        "Reference",
        () -> new SimpleColumnRenderer<ValueObject>(
          onSubclass(ReferenceObject.class,
                     v -> v.getName() + " in " + v.getValueText(),
                     ValueObject::getValueText),
          value -> ValueColumnRenderer.getValueObjectIcon(value.getAdapter()),
          SwingConstants.LEFT),
        SwingConstants.LEFT,
        LABEL_COLUMN_WIDTH,
        SortOrder.ASCENDING,
        Comparator.comparing(o -> o.getAdapter().getName())));
    myAttributeColumns.put(
      InstanceAttribute.DEPTH,
      makeIntColumn("Depth",
                    150,
                    ValueObject.class,
                    ValueObject::getDepth,
                    d -> 0 <= d && d < Integer.MAX_VALUE,
                    NumberFormatter::formatInteger,
                    SortOrder.ASCENDING));
    myAttributeColumns.put(
      InstanceAttribute.ALLOCATION_TIME,
      makeIntColumn("Alloc Time",
                    100,
                    InstanceObject.class,
                    InstanceObject::getAllocTime,
                    t -> t > Long.MIN_VALUE,
                    timeFormatter,
                    SortOrder.ASCENDING));
    myAttributeColumns.put(
      InstanceAttribute.DEALLOCATION_TIME,
      makeIntColumn("Dealloc Time",
                    120,
                    InstanceObject.class,
                    InstanceObject::getDeallocTime,
                    t -> t < Long.MAX_VALUE,
                    timeFormatter,
                    SortOrder.DESCENDING));
    myAttributeColumns.put(
      InstanceAttribute.NATIVE_SIZE,
      makeSizeColumn("Native Size", 110, ValueObject::getNativeSize));
    myAttributeColumns.put(
      InstanceAttribute.SHALLOW_SIZE,
      makeSizeColumn("Shallow Size", 120, ValueObject::getShallowSize));
    myAttributeColumns.put(
      InstanceAttribute.RETAINED_SIZE,
      makeSizeColumn("Retained Size", 130, ValueObject::getRetainedSize));

    // Fires the handler once at the beginning to ensure we are sync'd with the latest selection state in the MemoryProfilerStage.
    instanceChanged();
  }

  private void trackActiveTab(ChangeEvent event) {
    if (myTabsPanel.getSelectedIndex() < 0) {
      return;
    }

    FeatureTracker featureTracker = mySelection.getIdeServices().getFeatureTracker();
    switch (myTabsPanel.getTitleAt(myTabsPanel.getSelectedIndex())) {
      case TITLE_TAB_REFERENCES:
        featureTracker.trackSelectMemoryReferences();
        break;
      case TITLE_TAB_ALLOCATION_CALLSTACK:
      case TITLE_TAB_DEALLOCATION_CALLSTACK:
        featureTracker.trackSelectMemoryStack();
        break;
      default:
        // Intentional no-op
        break;
    }
  }

  @NotNull
  JComponent getComponent() {
    return myPanel;
  }

  @VisibleForTesting
  @Nullable
  JTree getReferenceTree() {
    return myReferenceTree;
  }

  @VisibleForTesting
  @Nullable
  JComponent getReferenceColumnTree() {
    return myReferenceColumnTree;
  }

  @VisibleForTesting
  @Nullable
  JTree getFieldTree() {
    return myFieldTree;
  }

  @VisibleForTesting
  JBCheckBox getGCRootCheckBox() {
    return myGCRootCheckBox;
  }

  private void instanceChanged() {
    CaptureObject capture = mySelection.getSelectedCapture();
    InstanceObject instance = mySelection.getSelectedInstanceObject();
    List<FieldObject> fieldPath = mySelection.getSelectedFieldObjectPath();

    if (capture == null || instance == null) {
      myFieldTree = null;
      myReferenceTree = null;
      myReferenceColumnTree = null;
      getComponent().setVisible(false);
      return;
    }

    myTitle.setText("Instance Details - " + (instance.getName().isEmpty() ? instance.getValueText() : instance.getName()));
    myTabsPanel.removeAll();
    boolean hasContent = false;

    if (!fieldPath.isEmpty()) {
      InstanceObject fieldInstance = fieldPath.get(fieldPath.size() - 1).getAsInstance();
      if (fieldInstance != null) {
        instance = fieldInstance;
      }
    }

    // Populate fields
    if (mySelection.getIdeServices().getFeatureConfig().isSeparateHeapDumpUiEnabled() &&
        instance.getFieldCount() > 0) {
      JComponent fieldColumnTree = buildFieldColumnTree(capture, instance);
      myTabsPanel.addTab(TITLE_TAB_FIELDS, fieldColumnTree);
      hasContent = true;
    }

    // Populate references
    myReferenceColumnTree = buildReferenceColumnTree(capture, instance);
    if (myReferenceColumnTree != null) {
      myRefPanel.removeAll();
      myRefPanel.add(myReferenceColumnTree, BorderLayout.CENTER);
      myRefPanel.add(myGCRootCheckBox, BorderLayout.NORTH);
      myTabsPanel.addTab(TITLE_TAB_REFERENCES, myRefPanel);
      hasContent = true;
    }

    // Populate Callstacks
    List<CodeLocation> allocCallStack = instance.getAllocationCodeLocations();
    if (!allocCallStack.isEmpty()) {
      myAllocationStackTraceView.getModel().setStackFrames(instance.getAllocationThreadId(), allocCallStack);
      JComponent stackTraceView = myAllocationStackTraceView.getComponent();
      stackTraceView.setBorder(DEFAULT_TOP_BORDER);
      myTabsPanel.addTab(TITLE_TAB_ALLOCATION_CALLSTACK, stackTraceView);
      hasContent = true;
    }

    List<CodeLocation> deallocCallStack = instance.getDeallocationCodeLocations();
    if (!deallocCallStack.isEmpty()) {
      myDeallocationStackTraceView.getModel().setStackFrames(instance.getDeallocationThreadId(), deallocCallStack);
      JComponent stackTraceView = myDeallocationStackTraceView.getComponent();
      stackTraceView.setBorder(DEFAULT_TOP_BORDER);
      myTabsPanel.addTab(TITLE_TAB_DEALLOCATION_CALLSTACK, stackTraceView);
      hasContent = true;
    }

    final InstanceObject finalInstance = instance;
    myInstanceViewers.forEach(viewer -> {
      JComponent component = viewer.createComponent(myIdeProfilerComponents, capture, finalInstance);
      if (component != null) {
        myTabsPanel.addTab(viewer.getTitle(), component);
      }
    });

    getComponent().setVisible(hasContent);
  }

  private JComponent buildFieldColumnTree(@NotNull CaptureObject captureObject, @NotNull InstanceObject instance) {
    myFieldTree = buildFieldTree(instance);
    // Add the columns for the tree and take special care of the default sorted column.
    List<InstanceAttribute> supportedAttributes = captureObject.getInstanceAttributes();
    InstanceAttribute sortAttribute = Collections.max(supportedAttributes, Comparator.comparingInt(InstanceAttribute::getWeight));
    ColumnTreeBuilder builder = new ColumnTreeBuilder(myFieldTree);
    for (InstanceAttribute attribute : supportedAttributes) {
      final AttributeColumn<MemoryObject> column = // TODO(philnguyen) refactor
        attribute == InstanceAttribute.LABEL ?
        new AttributeColumn<>(
          "Instance",
          ValueColumnRenderer::new,
          SwingConstants.LEFT,
          LABEL_COLUMN_WIDTH,
          SortOrder.ASCENDING,
          Comparator.comparing(onSubclass(ValueObject.class,
                                          o -> o.getName().isEmpty() ? o.getValueText() : o.getName(),
                                          o -> ""))) :
        myAttributeColumns.get(attribute);
      ColumnTreeBuilder.ColumnBuilder columnBuilder = column.getBuilder();
      if (sortAttribute == attribute) {
        columnBuilder.setInitialOrder(attribute.getSortOrder());
      }
      builder.addColumn(columnBuilder);
    }
    builder.setTreeSorter((Comparator<MemoryObjectTreeNode<MemoryObject>> comparator, SortOrder order) -> {
      assert myFieldTree != null;
      DefaultTreeModel treeModel = (DefaultTreeModel) myFieldTree.getModel();
      MemoryObjectTreeNode<MemoryObject> root = (MemoryObjectTreeNode<MemoryObject>) treeModel.getRoot();
      root.sort(comparator);
      treeModel.nodeStructureChanged(root);
    });
    return builder
      .setHoverColor(StandardColors.HOVER_COLOR)
      .setBackground(ProfilerColors.DEFAULT_BACKGROUND)
      .setBorder(DEFAULT_TOP_BORDER)
      .setShowVerticalLines(true)
      .setTableIntercellSpacing(new Dimension())
      .setShowHeaderTooltips(true)
      .build();
  }

  private JTree buildFieldTree(@NotNull InstanceObject instance) {
    LazyMemoryObjectTreeNode<InstanceObject> fieldTreeRoot = new InstanceDetailsTreeNode(instance);
    DefaultTreeModel fieldTreeModel = new DefaultTreeModel(fieldTreeRoot);
    fieldTreeRoot.setTreeModel(fieldTreeModel);
    fieldTreeRoot.expandNode();

    // Use JTree instead of IJ's tree, because IJ's tree does not happen border's Insets.
    JTree tree = new JTree();
    int defaultFontHeight = tree.getFontMetrics(tree.getFont()).getHeight();
    tree.setRowHeight(defaultFontHeight + ROW_HEIGHT_PADDING);
    tree.setBorder(TABLE_ROW_BORDER);
    tree.setRootVisible(false);
    tree.setShowsRootHandles(true);
    tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);

    // Not all nodes have been populated during buildTree. Here we capture the TreeExpansionEvent to check whether any children
    // under the expanded node need to be populated.
    tree.addTreeExpansionListener(new TreeExpansionListener() {
      @Override
      public void treeExpanded(TreeExpansionEvent event) {
        TreePath path = event.getPath();

        assert path.getLastPathComponent() instanceof LazyMemoryObjectTreeNode;
        LazyMemoryObjectTreeNode treeNode = (LazyMemoryObjectTreeNode)path.getLastPathComponent();
        // children under root have already been expanded (check in case this gets called on the root)
        if (treeNode != fieldTreeRoot) {
          treeNode.expandNode();
          fieldTreeModel.nodeStructureChanged(treeNode);
        }
      }

      @Override
      public void treeCollapsed(TreeExpansionEvent event) {
        // No-op. TODO remove unseen children?
      }
    });

    tree.addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent e) {
        if (tree.getSelectionCount() == 0 && tree.getRowCount() != 0) {
          tree.setSelectionRow(0);
        }
      }
    });

    tree.setModel(fieldTreeModel);

    return tree;
  }

  @Nullable
  private JComponent buildReferenceColumnTree(@NotNull CaptureObject captureObject, @NotNull InstanceObject instance) {
    if (instance.getReferences().isEmpty()) {
      myReferenceTree = null;
      return null;
    }

    myReferenceTree = buildReferenceTree(instance);
    ColumnTreeBuilder builder = new ColumnTreeBuilder(myReferenceTree);
    for (InstanceAttribute attribute : captureObject.getInstanceAttributes()) {
      ColumnTreeBuilder.ColumnBuilder column = myAttributeColumns.get(attribute).getBuilder();
      if (attribute == InstanceAttribute.DEPTH) {
        column.setInitialOrder(attribute.getSortOrder());
      }
      builder.addColumn(column);
    }
    builder.setTreeSorter((Comparator<MemoryObjectTreeNode<MemoryObject>> comparator, SortOrder sortOrder) -> {
      assert myReferenceTree.getModel() instanceof DefaultTreeModel;
      DefaultTreeModel treeModel = (DefaultTreeModel)myReferenceTree.getModel();
      assert treeModel.getRoot() instanceof MemoryObjectTreeNode;
      //noinspection unchecked
      MemoryObjectTreeNode<MemoryObject> root = (MemoryObjectTreeNode<MemoryObject>)treeModel.getRoot();
      root.sort(comparator);
      treeModel.nodeStructureChanged(root);
    });
    builder.setHoverColor(StandardColors.HOVER_COLOR);
    builder.setBackground(ProfilerColors.DEFAULT_BACKGROUND);
    builder.setBorder(DEFAULT_TOP_BORDER);
    builder.setShowVerticalLines(true);
    builder.setTableIntercellSpacing(new Dimension());
    builder.setShowHeaderTooltips(true);
    return builder.build();
  }

  @VisibleForTesting
  @NotNull
  JTree buildReferenceTree(@NotNull InstanceObject instance) {
    Comparator<MemoryObjectTreeNode<ValueObject>> comparator = null;
    if (myReferenceTree != null && myReferenceTree.getModel() != null && myReferenceTree.getModel().getRoot() != null) {
      Object root = myReferenceTree.getModel().getRoot();
      if (root instanceof ReferenceTreeNode) {
        comparator = ((ReferenceTreeNode)root).getComparator();
      }
    }

    final ReferenceTreeNode treeRoot = myGCRootCheckBox.isSelected()
                                       ? new NearestGCRootTreeNode(instance)
                                       : new ReferenceTreeNode(instance);
    treeRoot.expandNode();

    if (comparator != null) {
      treeRoot.sort(comparator);
    }

    final DefaultTreeModel treeModel = new DefaultTreeModel(treeRoot);
    // Use JTree instead of IJ's tree, because IJ's tree does not happen border's Insets.
    final JTree tree = new JTree(treeModel);
    int defaultFontHeight = tree.getFontMetrics(tree.getFont()).getHeight();
    tree.setRowHeight(defaultFontHeight + ROW_HEIGHT_PADDING);
    tree.setBorder(TABLE_ROW_BORDER);
    tree.setRootVisible(true);
    tree.setShowsRootHandles(true);
    tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    // Not all nodes have been populated during buildReferenceColumnTree. Here we capture the TreeExpansionEvent to check whether any children
    // under the expanded node need to be populated.
    tree.addTreeExpansionListener(new TreeExpansionListener() {
      @Override
      public void treeExpanded(TreeExpansionEvent event) {
        TreePath path = event.getPath();

        assert path.getLastPathComponent() instanceof MemoryObjectTreeNode;
        ReferenceTreeNode treeNode = (ReferenceTreeNode)path.getLastPathComponent();
        treeNode.expandNode();
        treeModel.nodeStructureChanged(treeNode);
      }

      @Override
      public void treeCollapsed(TreeExpansionEvent event) {
      }
    });

    ContextMenuInstaller contextMenuInstaller = myIdeProfilerComponents.createContextMenuInstaller();
    contextMenuInstaller.installNavigationContextMenu(tree, mySelection.getIdeServices().getCodeNavigator(), () -> {
      TreePath selection = tree.getSelectionPath();
      if (selection == null) {
        return null;
      }

      MemoryObject memoryObject = ((MemoryObjectTreeNode)selection.getLastPathComponent()).getAdapter();
      if (memoryObject instanceof InstanceObject) {
        return new CodeLocation.Builder(((InstanceObject)memoryObject).getClassEntry().getClassName()).build();
      }
      else {
        assert memoryObject instanceof ReferenceObject;
        return new CodeLocation.Builder(((ReferenceObject)memoryObject).getReferenceInstance().getClassEntry().getClassName()).build();
      }
    });

    contextMenuInstaller.installGenericContextMenu(tree, new ContextMenuItem() {
      @NotNull
      @Override
      public String getText() {
        return "Go to Instance";
      }

      @Nullable
      @Override
      public Icon getIcon() {
        return null;
      }

      @Override
      public boolean isEnabled() {
        return tree.getSelectionPath() != null;
      }

      @Override
      public void run() {
        CaptureObject captureObject = mySelection.getSelectedCapture();
        TreePath selection = tree.getSelectionPath();
        assert captureObject != null && selection != null;
        MemoryObject memoryObject = ((MemoryObjectTreeNode)selection.getLastPathComponent()).getAdapter();
        if (memoryObject instanceof InstanceObject) {
          assert memoryObject == mySelection.getSelectedInstanceObject();
          // don't do anything because the only instance object in the tree is the one already selected
        }
        else {
          assert memoryObject instanceof ReferenceObject;
          InstanceObject targetInstance = ((ReferenceObject)memoryObject).getReferenceInstance();
          HeapSet heapSet = captureObject.getHeapSet(targetInstance.getHeapId());
          assert heapSet != null;
          mySelection.selectHeapSet(heapSet);
          ClassifierSet classifierSet = heapSet.findContainingClassifierSet(targetInstance);
          assert classifierSet instanceof ClassSet;
          mySelection.selectClassSet((ClassSet)classifierSet);
          mySelection.selectInstanceObject(targetInstance);
        }
      }
    });

    return tree;
  }

  private void repeatedlyExpandFirstReference() {
    assert myReferenceTree != null;
    ReferenceTreeNode node = (ReferenceTreeNode)myReferenceTree.getModel().getRoot();
    node.expandNode();
    while (!node.myChildren.isEmpty()) {
      node = (ReferenceTreeNode)node.myChildren.get(0);
      node.expandNode();
    }
    TreePath path = new TreePath(node.getPathToRoot().toArray());
    myReferenceTree.expandPath(path);
    myReferenceTree.setSelectionPath(path);
  }
}
