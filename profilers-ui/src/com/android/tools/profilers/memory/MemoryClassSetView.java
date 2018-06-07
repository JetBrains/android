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
import com.android.tools.adtui.model.formatter.TimeAxisFormatter;
import com.android.tools.profilers.*;
import com.android.tools.profilers.memory.adapters.*;
import com.android.tools.profilers.memory.adapters.CaptureObject.InstanceAttribute;
import com.android.tools.profilers.stacktrace.CodeLocation;
import com.android.tools.profilers.stacktrace.ContextMenuItem;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static com.android.tools.adtui.common.AdtUiUtils.DEFAULT_TOP_BORDER;
import static com.android.tools.profilers.ProfilerLayout.ROW_HEIGHT_PADDING;
import static com.android.tools.profilers.ProfilerLayout.TABLE_ROW_BORDER;

final class MemoryClassSetView extends AspectObserver {
  private static final int LABEL_COLUMN_WIDTH = 500;
  private static final int DEFAULT_COLUMN_WIDTH = 80;

  @NotNull private final MemoryProfilerStage myStage;

  @NotNull private final ProfilerTimeline myTimeline;

  @NotNull private final ContextMenuInstaller myContextMenuInstaller;

  @NotNull private final Map<InstanceAttribute, AttributeColumn<MemoryObject>> myAttributeColumns = new HashMap<>();

  @NotNull private final JPanel myInstancesPanel = new JPanel(new BorderLayout());

  @Nullable private JComponent myColumnTree;

  @Nullable private JTree myTree;

  @Nullable private DefaultTreeModel myTreeModel;

  @Nullable private LazyMemoryObjectTreeNode<MemoryObject> myTreeRoot;

  @Nullable private Comparator<MemoryObjectTreeNode<MemoryObject>> myInitialComparator;

  @Nullable private CaptureObject myCaptureObject;

  @Nullable private ClassSet myClassSet;

  @Nullable private InstanceObject myInstanceObject;

  @Nullable private List<FieldObject> myFieldObjectPath;

  public MemoryClassSetView(@NotNull MemoryProfilerStage stage, @NotNull IdeProfilerComponents ideProfilerComponents) {
    myStage = stage;
    myTimeline = myStage.getStudioProfilers().getTimeline();
    myContextMenuInstaller = ideProfilerComponents.createContextMenuInstaller();

    myStage.getAspect().addDependency(this)
      .onChange(MemoryProfilerAspect.CURRENT_LOADED_CAPTURE, this::refreshCaptureObject)
      .onChange(MemoryProfilerAspect.CURRENT_CLASS, this::refreshClassSet)
      .onChange(MemoryProfilerAspect.CURRENT_INSTANCE, this::refreshSelectedInstance)
      .onChange(MemoryProfilerAspect.CURRENT_HEAP_CONTENTS, this::refreshAllInstances)
      .onChange(MemoryProfilerAspect.CURRENT_FIELD_PATH, this::refreshFieldPath);

    myAttributeColumns.put(
      InstanceAttribute.LABEL,
      new AttributeColumn<>(
        "Instance",
        ValueColumnRenderer::new,
        SwingConstants.LEFT,
        LABEL_COLUMN_WIDTH,
        SortOrder.ASCENDING,
        Comparator.comparing(o -> {
          if (!(o.getAdapter() instanceof ValueObject)) {
            return "";
          }

          ValueObject valueObject = (ValueObject)o.getAdapter();
          String comparisonString = valueObject.getName();
          if (!comparisonString.isEmpty()) {
            return comparisonString;
          }
          return valueObject.getValueText();
        })));
    myAttributeColumns.put(
      InstanceAttribute.DEPTH,
      new AttributeColumn<>(
        "Depth",
        () -> new SimpleColumnRenderer<>(value -> {
          MemoryObject node = value.getAdapter();
          if (node instanceof ValueObject) {
            ValueObject valueObject = (ValueObject)node;
            if (valueObject.getDepth() >= 0 && valueObject.getDepth() < Integer.MAX_VALUE) {
              return Integer.toString(valueObject.getDepth());
            }
          }
          return "";
        }, value -> null, SwingConstants.RIGHT),
        SwingConstants.RIGHT,
        DEFAULT_COLUMN_WIDTH,
        SortOrder.ASCENDING,
        Comparator.comparingInt(o -> ((ValueObject)o.getAdapter()).getDepth())));
    myAttributeColumns.put(
      InstanceAttribute.ALLOCATION_TIME,
      new AttributeColumn<>(
        "Alloc Time",
        () -> new SimpleColumnRenderer<>(value -> {
          MemoryObject node = value.getAdapter();
          if (node instanceof InstanceObject) {
            InstanceObject instanceObject = (InstanceObject)node;
            if (instanceObject.getAllocTime() > Long.MIN_VALUE) {
              return TimeAxisFormatter.DEFAULT.getFixedPointFormattedString(
                TimeUnit.MILLISECONDS.toMicros(1),
                myTimeline.convertToRelativeTimeUs(instanceObject.getAllocTime()));
            }
          }
          return "";
        }, value -> null, SwingConstants.RIGHT),
        SwingConstants.RIGHT,
        DEFAULT_COLUMN_WIDTH,
        SortOrder.ASCENDING,
        Comparator.comparingLong(o -> ((InstanceObject)o.getAdapter()).getAllocTime())));
    myAttributeColumns.put(
      InstanceAttribute.DEALLOCATION_TIME,
      new AttributeColumn<>(
        "Dealloc Time",
        () -> new SimpleColumnRenderer<>(value -> {
          MemoryObject node = value.getAdapter();
          if (node instanceof InstanceObject) {
            InstanceObject instanceObject = (InstanceObject)node;
            if (instanceObject.getDeallocTime() < Long.MAX_VALUE) {
              return TimeAxisFormatter.DEFAULT.getFixedPointFormattedString(
                TimeUnit.MILLISECONDS.toMicros(1),
                myTimeline.convertToRelativeTimeUs(instanceObject.getDeallocTime()));
            }
          }
          return "";
        }, value -> null, SwingConstants.RIGHT),
        SwingConstants.RIGHT,
        DEFAULT_COLUMN_WIDTH,
        SortOrder.DESCENDING,
        Comparator.comparingLong(o -> ((InstanceObject)o.getAdapter()).getDeallocTime())));
    myAttributeColumns.put(
      InstanceAttribute.NATIVE_SIZE,
      new AttributeColumn<>(
        "Native Size",
        () -> new SimpleColumnRenderer<>(value -> {
          MemoryObject node = value.getAdapter();
          return node instanceof ValueObject ? Long.toString(((ValueObject)node).getNativeSize()) : "";
        }, value -> null, SwingConstants.RIGHT),
        SwingConstants.RIGHT,
        DEFAULT_COLUMN_WIDTH,
        SortOrder.DESCENDING,
        Comparator.comparingLong(o -> ((ValueObject)o.getAdapter()).getNativeSize())));
    myAttributeColumns.put(
      InstanceAttribute.SHALLOW_SIZE,
      new AttributeColumn<>(
        "Shallow Size",
        () -> new SimpleColumnRenderer<>(value -> {
          MemoryObject node = value.getAdapter();
          return node instanceof ValueObject ? Integer.toString(((ValueObject)node).getShallowSize()) : "";
        }, value -> null, SwingConstants.RIGHT),
        SwingConstants.RIGHT,
        DEFAULT_COLUMN_WIDTH,
        SortOrder.DESCENDING,
        Comparator.comparingInt(o -> ((ValueObject)o.getAdapter()).getShallowSize())));
    myAttributeColumns.put(
      InstanceAttribute.RETAINED_SIZE,
      new AttributeColumn<>(
        "Retained Size",
        () -> new SimpleColumnRenderer<>(value -> {
          MemoryObject node = value.getAdapter();
          return node instanceof ValueObject ? Long.toString(((ValueObject)node).getRetainedSize()) : "";
        }, value -> null, SwingConstants.RIGHT),
        SwingConstants.RIGHT,
        DEFAULT_COLUMN_WIDTH,
        SortOrder.DESCENDING,
        Comparator.comparingLong(o -> ((ValueObject)o.getAdapter()).getRetainedSize())));

    JPanel headingPanel = new JPanel(new BorderLayout());
    JLabel instanceViewLabel = new JLabel("Instance View");
    instanceViewLabel.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 0));
    headingPanel.add(instanceViewLabel, BorderLayout.WEST);

    CloseButton closeButton = new CloseButton(e -> myStage.selectClassSet(null));
    headingPanel.add(closeButton, BorderLayout.EAST);

    myInstancesPanel.add(headingPanel, BorderLayout.NORTH);
    myInstancesPanel.setVisible(false);
  }

  public void reset() {
    if (myColumnTree != null) {
      myInstancesPanel.remove(myColumnTree);
    }
    myColumnTree = null;
    myTree = null;
    myTreeRoot = null;
    myTreeModel = null;
    myClassSet = null;
    myInstanceObject = null;
    myFieldObjectPath = null;
    myInstancesPanel.setVisible(false);
    myStage.selectInstanceObject(null);
  }

  @NotNull
  JComponent getComponent() {
    return myInstancesPanel;
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

  private void initializeTree() {
    assert myTree == null &&
           myColumnTree == null &&
           myTreeModel == null &&
           myTreeRoot == null &&
           myCaptureObject != null &&
           myClassSet != null;

    // Use JTree instead of IJ's tree, because IJ's tree does not happen border's Insets.
    myTree = new JTree();
    int defaultFontHeight = myTree.getFontMetrics(myTree.getFont()).getHeight();
    myTree.setRowHeight(defaultFontHeight + ROW_HEIGHT_PADDING);
    myTree.setBorder(TABLE_ROW_BORDER);
    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(true);
    myTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);

    myTree.addTreeSelectionListener(e -> {
      TreePath path = e.getPath();
      if (!e.isAddedPath()) {
        return;
      }

      assert path.getLastPathComponent() instanceof MemoryObjectTreeNode;
      //noinspection unchecked
      MemoryObjectTreeNode<MemoryObject> valueNode = (MemoryObjectTreeNode<MemoryObject>)path.getLastPathComponent();
      valueNode.select();
      MemoryObject memoryObject = valueNode.getAdapter();
      if (memoryObject instanceof InstanceObject) {
        myInstanceObject = (InstanceObject)valueNode.getAdapter();
        myStage.selectFieldObjectPath(Collections.emptyList());
        myStage.selectInstanceObject(myInstanceObject);
      }
      else if (memoryObject instanceof FieldObject) {
        assert path.getPathCount() > 2;
        MemoryObjectTreeNode instanceNode = (MemoryObjectTreeNode)path.getPathComponent(1);
        assert instanceNode.getAdapter() instanceof InstanceObject;
        myInstanceObject = (InstanceObject)instanceNode.getAdapter();
        myStage.selectInstanceObject(myInstanceObject);

        Object[] fieldNodePath = Arrays.copyOfRange(path.getPath(), 2, path.getPathCount());
        ArrayList<FieldObject> fieldObjectPath = new ArrayList<>(fieldNodePath.length);
        for (Object fieldNode : fieldNodePath) {
          if (!(fieldNode instanceof MemoryObjectTreeNode && ((MemoryObjectTreeNode)fieldNode).getAdapter() instanceof FieldObject)) {
            return;
          }
          //noinspection unchecked
          fieldObjectPath.add(((MemoryObjectTreeNode<FieldObject>)fieldNode).getAdapter());
        }
        myFieldObjectPath = fieldObjectPath;
        myStage.selectFieldObjectPath(fieldObjectPath);
      }
    });

    // Not all nodes have been populated during buildTree. Here we capture the TreeExpansionEvent to check whether any children
    // under the expanded node need to be populated.
    myTree.addTreeExpansionListener(new TreeExpansionListener() {
      @Override
      public void treeExpanded(TreeExpansionEvent event) {
        TreePath path = event.getPath();

        assert path.getLastPathComponent() instanceof LazyMemoryObjectTreeNode;
        LazyMemoryObjectTreeNode treeNode = (LazyMemoryObjectTreeNode)path.getLastPathComponent();
        if (treeNode == myTreeRoot) {
          return; // children under root have already been expanded (check in case this gets called on the root)
        }
        treeNode.expandNode();
        myTreeModel.nodeStructureChanged(treeNode);
      }

      @Override
      public void treeCollapsed(TreeExpansionEvent event) {
        // No-op. TODO remove unseen children?
      }
    });
    installTreeContextMenus();

    // Add the columns for the tree and take special care of the default sorted column.
    List<InstanceAttribute> supportedAttributes = myCaptureObject.getInstanceAttributes();
    ColumnTreeBuilder builder = new ColumnTreeBuilder(myTree);
    InstanceAttribute sortAttribute = Collections.max(supportedAttributes, Comparator.comparingInt(InstanceAttribute::getWeight));
    for (InstanceAttribute attribute : supportedAttributes) {
      AttributeColumn<MemoryObject> column = myAttributeColumns.get(attribute);
      ColumnTreeBuilder.ColumnBuilder columnBuilder = column.getBuilder();
      if (sortAttribute == attribute) {
        columnBuilder.setInitialOrder(attribute.getSortOrder());
        myInitialComparator =
          attribute.getSortOrder() == SortOrder.ASCENDING ? column.getComparator() : Collections.reverseOrder(column.getComparator());
      }
      builder.addColumn(columnBuilder);
    }
    builder.setTreeSorter((Comparator<MemoryObjectTreeNode<MemoryObject>> comparator, SortOrder sortOrder) -> {
      if (myTreeRoot != null) {
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

    myTree.addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent e) {
        if (myTree.getSelectionCount() == 0 && myTree.getRowCount() != 0) {
          myTree.setSelectionRow(0);
        }
      }
    });
    builder.setHoverColor(ProfilerColors.DEFAULT_HOVER_COLOR);
    builder.setBackground(ProfilerColors.DEFAULT_BACKGROUND);
    builder.setBorder(DEFAULT_TOP_BORDER);
    builder.setShowVerticalLines(true);
    builder.setTableIntercellSpacing(new Dimension());
    myColumnTree = builder.build();
    myInstancesPanel.add(myColumnTree, BorderLayout.CENTER);
  }

  private void installTreeContextMenus() {
    assert myTree != null;

    myContextMenuInstaller.installNavigationContextMenu(myTree, myStage.getStudioProfilers().getIdeServices().getCodeNavigator(), () -> {
      TreePath selection = myTree.getSelectionPath();
      if (selection == null || !(selection.getLastPathComponent() instanceof MemoryObjectTreeNode)) {
        return null;
      }

      MemoryObject selectedObject = ((MemoryObjectTreeNode)selection.getLastPathComponent()).getAdapter();
      if (selectedObject instanceof InstanceObject) {
        return new CodeLocation.Builder(((InstanceObject)selectedObject).getClassEntry().getClassName()).build();
      }
      else if (selectedObject instanceof FieldObject) {
        InstanceObject fieldInstance = ((FieldObject)selectedObject).getAsInstance();
        if (fieldInstance != null) {
          return new CodeLocation.Builder(fieldInstance.getClassEntry().getClassName()).build();
        }
      }
      return null;
    });

    myContextMenuInstaller.installGenericContextMenu(myTree, new ContextMenuItem() {
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
        return myInstanceObject != null && myFieldObjectPath != null && !myFieldObjectPath.isEmpty();
      }

      @Override
      public void run() {
        if (myCaptureObject == null || myInstanceObject == null || myFieldObjectPath == null || myFieldObjectPath.isEmpty()) {
          return;
        }

        FieldObject selectedField = myFieldObjectPath.get(myFieldObjectPath.size() - 1);
        if (selectedField.getValueType().getIsPrimitive() || selectedField.getValueType() == ValueObject.ValueType.NULL) {
          return;
        }

        InstanceObject selectedObject = selectedField.getAsInstance();
        assert selectedObject != null;

        HeapSet heapSet = myCaptureObject.getHeapSet(selectedObject.getHeapId());
        assert heapSet != null;
        myStage.selectHeapSet(heapSet);

        ClassifierSet classifierSet = heapSet.findContainingClassifierSet(selectedObject);
        assert classifierSet != null && classifierSet instanceof ClassSet;
        myStage.selectClassSet((ClassSet)classifierSet);
        myStage.selectInstanceObject(selectedObject);
        myStage.selectFieldObjectPath(Collections.emptyList());
      }
    });
  }

  private void populateTreeContents() {
    assert myTree != null && myCaptureObject != null && myClassSet != null;

    Comparator<MemoryObjectTreeNode<MemoryObject>> comparator = myTreeRoot == null ? myInitialComparator : myTreeRoot.getComparator();
    myTreeRoot = new LazyMemoryObjectTreeNode<MemoryObject>(myClassSet, true) {
      @Override
      public int computeChildrenCount() {
        return myClassSet.getInstancesCount();
      }

      @Override
      public void expandNode() {
        if (myMemoizedChildrenCount == myChildren.size()) {
          return;
        }

        myMemoizedChildrenCount = myClassSet.getInstancesCount();
        Stream<InstanceObject> instances = myClassSet.getInstancesStream();
        instances.forEach(subAdapter -> {
          InstanceTreeNode child = new InstanceTreeNode(subAdapter);
          child.setTreeModel(myTreeModel);
          add(child);
        });

        if (myTreeModel != null) {
          myTreeModel.nodeChanged(this);
        }
      }
    };

    if (comparator != null) {
      myTreeRoot.sort(comparator);
    }

    myTreeModel = new DefaultTreeModel(myTreeRoot);
    myTreeRoot.setTreeModel(myTreeModel);
    myTree.setModel(myTreeModel);
    myTreeRoot.expandNode();
  }

  private void refreshCaptureObject() {
    myCaptureObject = myStage.getSelectedCapture();
    reset();
  }

  private void refreshClassSet() {
    ClassSet classSet = myStage.getSelectedClassSet();
    if (classSet == myClassSet) {
      return;
    }

    if (classSet == null) {
      reset();
      return;
    }

    if (myClassSet == null) {
      myClassSet = classSet;
      initializeTree();
    }
    else {
      myClassSet = classSet;
    }

    populateTreeContents();
    myInstancesPanel.setVisible(true);
  }

  private void refreshSelectedInstance() {
    InstanceObject instanceObject = myStage.getSelectedInstanceObject();
    if (myInstanceObject == instanceObject) {
      return;
    }

    assert myTree != null;
    myInstanceObject = instanceObject;
    if (myInstanceObject == null) {
      myTree.clearSelection();
      return;
    }

    assert myTreeRoot != null && myTreeModel != null;
    for (MemoryObjectTreeNode<MemoryObject> node : myTreeRoot.getChildren()) {
      if (node.getAdapter() == myInstanceObject) {
        selectPath(node);
        break;
      }
    }
  }

  private void refreshAllInstances() {
    if (myClassSet == null) {
      return;
    }

    if (myClassSet.isEmpty()) {
      myStage.selectClassSet(ClassSet.EMPTY_SET);
      return;
    }

    populateTreeContents();
    if (myInstanceObject == null) {
      return;
    }

    assert myTreeRoot != null;

    // TODO: select node which is not visible yet
    for (MemoryObjectTreeNode<MemoryObject> node : myTreeRoot.getChildren()) {
      if (node.getAdapter() == myInstanceObject) {
        selectPath(node);
        return;
      }
    }
    myStage.selectInstanceObject(null);
  }

  private void refreshFieldPath() {
    List<FieldObject> fieldPath = myStage.getSelectedFieldObjectPath();
    if (Objects.equals(myFieldObjectPath, fieldPath)) {
      if (myFieldObjectPath != null && !myFieldObjectPath.isEmpty()) {
        assert myTree != null;
        myTree.scrollPathToVisible(myTree.getSelectionPath());
      }
      return;
    }

    myFieldObjectPath = fieldPath;
    if (myFieldObjectPath.isEmpty()) {
      if (myInstanceObject != null) {
        // If we have an instance node selected when the field path is unselected, we should reselect the instance node.
        assert myTreeRoot != null && myTreeModel != null && myTree != null;
        MemoryObjectTreeNode<MemoryObject> instanceNode = findSelectedInstanceNode();
        // We may be resetting myInstanceObject, so this might not find a relevant instanceNode.
        if (instanceNode != null) {
          selectPath(instanceNode);
        }
      }
      return;
    }

    // Since this is an memory dump that flattens the classes with no private/public division,
    // we could certainly have duplicate field names clashing within the same object.
    // Therefore, we actually need to perform a search, not just blindly find the first instance.
    assert myTreeRoot != null && myTreeModel != null && myTree != null && myInstanceObject != null;
    MemoryObjectTreeNode<MemoryObject> instanceNode = findSelectedInstanceNode();
    assert instanceNode != null;
    List<MemoryObjectTreeNode<MemoryObject>> fields = findLeafNodesForFieldPath(instanceNode, myFieldObjectPath);
    if (!fields.isEmpty()) {
      selectPath(fields.get(0));
    }
  }

  @Nullable
  private MemoryObjectTreeNode<MemoryObject> findSelectedInstanceNode() {
    assert myTree != null && myTreeModel != null && myTreeRoot != null && myInstanceObject != null;
    for (MemoryObjectTreeNode<MemoryObject> node : myTreeRoot.getChildren()) {
      if (node.getAdapter() == myInstanceObject) {
        return node;
      }
    }
    return null;
  }

  private void selectPath(@NotNull MemoryObjectTreeNode<MemoryObject> targetNode) {
    assert myTree != null && myTreeModel != null;
    TreePath path = new TreePath(myTreeModel.getPathToRoot(targetNode));
    // Refresh the expanded state of the parent path (not including last field node, since we don't want to expand that).
    myTree.expandPath(path.getParentPath());
    myTree.setSelectionPath(path);
    myTree.scrollPathToVisible(path);
  }

  /**
   * Finds the matching leaf node in {@code myTree} of the path given by {@code fieldPath}, starting from the given {@code parentNode}.
   * Note that since we could have private fields in various inheriting classes, it is possible to have multiple fields of the same name
   * pointing to the same value. In other words, we could have multiple leaf nodes corresponding to the given fieldPath.
   *
   * @param parentNode root from where to start the search
   * @param fieldPath  the path to look for
   * @return a list of leaf {@link MemoryObjectTreeNode}s matching the given {@code fieldPath}
   */
  @NotNull
  private static List<MemoryObjectTreeNode<MemoryObject>> findLeafNodesForFieldPath(@NotNull MemoryObjectTreeNode<MemoryObject> parentNode,
                                                                                    @NotNull List<FieldObject> fieldPath) {
    assert !fieldPath.isEmpty();
    FieldObject currentField = fieldPath.get(0);

    List<MemoryObjectTreeNode<MemoryObject>> results = new ArrayList<>(1);
    if (fieldPath.size() == 1) {
      // We reached the leaf node. Just find all children nodes with adapters matching the leaf FieldObject.
      //noinspection ArraysAsListWithZeroOrOneArgument
      parentNode.getChildren().stream().filter(child -> child.getAdapter().equals(currentField)).forEach(results::add);
    }
    else {
      // We're not at the leaf, so just add all the results of the recursive call.
      List<FieldObject> slice = fieldPath.subList(1, fieldPath.size());
      parentNode.getChildren().stream().filter(child -> child.getAdapter().equals(currentField))
        .forEach(child -> results.addAll(findLeafNodesForFieldPath(child, slice)));
    }
    return results;
  }

  static class InstanceTreeNode extends LazyMemoryObjectTreeNode<ValueObject> {
    public InstanceTreeNode(@NotNull InstanceObject adapter) {
      super(adapter, true);
    }

    @Override
    public int computeChildrenCount() {
      return ((InstanceObject)getAdapter()).getFieldCount();
    }

    @Override
    public void expandNode() {
      if (myMemoizedChildrenCount == myChildren.size()) {
        return;
      }

      List<FieldObject> fields = ((InstanceObject)getAdapter()).getFields();
      myMemoizedChildrenCount = fields.size();
      for (FieldObject field : fields) {
        FieldTreeNode child = new FieldTreeNode(field);
        child.setTreeModel(getTreeModel());
        add(child);
      }
    }
  }

  static class FieldTreeNode extends LazyMemoryObjectTreeNode<FieldObject> {
    public FieldTreeNode(@NotNull FieldObject adapter) {
      super(adapter, true);
    }

    @Override
    public int computeChildrenCount() {
      return getAdapter().getAsInstance() == null ? 0 : getAdapter().getAsInstance().getFieldCount();
    }

    @Override
    public void expandNode() {
      if (myMemoizedChildrenCount == myChildren.size() || getAdapter().getAsInstance() == null) {
        return;
      }

      List<FieldObject> fields = getAdapter().getAsInstance().getFields();
      myMemoizedChildrenCount = fields.size();
      for (FieldObject field : fields) {
        FieldTreeNode child = new FieldTreeNode(field);
        child.setTreeModel(getTreeModel());
        add(child);
      }
    }
  }
}
