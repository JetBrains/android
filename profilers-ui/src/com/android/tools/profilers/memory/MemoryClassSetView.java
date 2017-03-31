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
import com.android.tools.profilers.CloseButton;
import com.android.tools.profilers.IdeProfilerComponents;
import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.memory.adapters.*;
import com.android.tools.profilers.memory.adapters.CaptureObject.InstanceAttribute;
import com.android.tools.profilers.stacktrace.CodeLocation;
import com.android.tools.profilers.stacktrace.ContextMenuItem;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.ui.JBColor;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

final class MemoryClassSetView extends AspectObserver {
  private static final int LABEL_COLUMN_WIDTH = 500;
  private static final int DEFAULT_COLUMN_WIDTH = 80;

  @NotNull private final MemoryProfilerStage myStage;

  @NotNull private final IdeProfilerComponents myIdeProfilerComponents;

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

  public MemoryClassSetView(@NotNull MemoryProfilerStage stage, @NotNull IdeProfilerComponents ideProfilerComponents) {
    myStage = stage;
    myIdeProfilerComponents = ideProfilerComponents;

    myStage.getAspect().addDependency(this)
      .onChange(MemoryProfilerAspect.CURRENT_LOADED_CAPTURE, this::refreshCaptureObject)
      .onChange(MemoryProfilerAspect.CURRENT_CLASS, this::refreshClassSet)
      .onChange(MemoryProfilerAspect.CURRENT_INSTANCE, this::refreshInstance);

    myAttributeColumns.put(
      InstanceAttribute.LABEL,
      new AttributeColumn<>(
        "Instance",
        ValueColumnRenderer::new,
        SwingConstants.LEFT,
        LABEL_COLUMN_WIDTH,
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
        Comparator.comparingInt(o -> ((ValueObject)o.getAdapter()).getDepth())));
    myAttributeColumns.put(
      InstanceAttribute.SHALLOW_SIZE,
      new AttributeColumn<>(
        "Shallow Size",
        () -> new SimpleColumnRenderer<>(value -> {
          MemoryObject node = value.getAdapter();
          return node instanceof ValueObject ? Integer.toString(((ValueObject)value.getAdapter()).getShallowSize()) : "";
        }, value -> null, SwingConstants.RIGHT),
        SwingConstants.RIGHT,
        DEFAULT_COLUMN_WIDTH,
        Comparator.comparingInt(o -> ((ValueObject)o.getAdapter()).getShallowSize())));
    myAttributeColumns.put(
      InstanceAttribute.RETAINED_SIZE,
      new AttributeColumn<>(
        "Retained Size",
        () -> new SimpleColumnRenderer<>(value -> {
          MemoryObject node = value.getAdapter();
          return node instanceof ValueObject ? Long.toString(((ValueObject)value.getAdapter()).getRetainedSize()) : "";
        }, value -> null, SwingConstants.RIGHT),
        SwingConstants.RIGHT,
        DEFAULT_COLUMN_WIDTH,
        Comparator.comparingLong(o -> ((ValueObject)o.getAdapter()).getRetainedSize())));

    JPanel headingPanel = new JPanel(new BorderLayout());
    headingPanel.setBorder(BorderFactory.createLineBorder(JBColor.border()));
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

    myTree = new Tree();
    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(true);

    myTree.addTreeSelectionListener(e -> {
      TreePath path = e.getPath();
      if (!e.isAddedPath()) {
        return;
      }

      assert path.getLastPathComponent() instanceof MemoryObjectTreeNode;
      MemoryObjectTreeNode instanceNode = (MemoryObjectTreeNode)path.getLastPathComponent();
      instanceNode.select();
      MemoryObject memoryObject = instanceNode.getAdapter();
      if (memoryObject instanceof InstanceObject) {
        myInstanceObject = (InstanceObject)instanceNode.getAdapter();
        myStage.selectInstanceObject(myInstanceObject);
      }
      // don't change the instance selection if the user selects a field object
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
        myTree.setSelectionPath(selectionPath);
        myTree.scrollPathToVisible(selectionPath);
      }
    });

    builder.setBackground(ProfilerColors.DEFAULT_BACKGROUND);
    myColumnTree = builder.build();
    myInstancesPanel.add(myColumnTree, BorderLayout.CENTER);
  }

  private void installTreeContextMenus() {
    assert myTree != null;

    myIdeProfilerComponents.installNavigationContextMenu(myTree, myStage.getStudioProfilers().getIdeServices().getCodeNavigator(), () -> {
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

    myIdeProfilerComponents.installContextMenu(myTree, new ContextMenuItem() {
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
        return myInstanceObject != null && myInstanceObject.getValueType() != ValueObject.ValueType.NULL;
      }

      @Override
      public void run() {
        if (myInstanceObject == null || myCaptureObject == null) {
          return;
        }
        HeapSet heapSet = myCaptureObject.getHeapSet(myInstanceObject.getHeapId());
        assert heapSet != null;
        myStage.selectHeapSet(heapSet);
        ClassifierSet classifierSet = heapSet.findContainingClassifierSet(myInstanceObject);
        assert classifierSet != null && classifierSet instanceof ClassSet;
        myClassSet = (ClassSet)classifierSet;
        myStage.selectClassSet(myClassSet);
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

  private void refreshInstance() {
    InstanceObject instanceObject = myStage.getSelectedInstanceObject();
    if (myInstanceObject == instanceObject) {
      return;
    }

    if (instanceObject == null) {
      myInstanceObject = null;
      return;
    }

    assert myTreeRoot != null && myTreeModel != null && myTree != null;
    myInstanceObject = instanceObject;
    for (MemoryObjectTreeNode<MemoryObject> node : myTreeRoot.getChildren()) {
      if (node.getAdapter() == myInstanceObject) {
        TreePath path = new TreePath(myTreeModel.getPathToRoot(node));
        myTree.scrollPathToVisible(path);
        myTree.setSelectionPath(path);
        break;
      }
    }
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
