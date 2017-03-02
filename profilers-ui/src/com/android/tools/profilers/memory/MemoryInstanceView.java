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
import com.android.tools.profilers.IdeProfilerComponents;
import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.common.CodeLocation;
import com.android.tools.profilers.common.ContextMenuItem;
import com.android.tools.profilers.memory.adapters.ClassObject;
import com.android.tools.profilers.memory.adapters.InstanceObject;
import com.android.tools.profilers.memory.adapters.InstanceObject.InstanceAttribute;
import com.android.tools.profilers.memory.adapters.MemoryObject;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.ui.popup.IconButton;
import com.intellij.ui.InplaceButton;
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

final class MemoryInstanceView extends AspectObserver {
  private static final int LABEL_COLUMN_WIDTH = 500;
  private static final int DEFAULT_COLUMN_WIDTH = 80;

  @NotNull private final MemoryProfilerStage myStage;

  @NotNull private final IdeProfilerComponents myIdeProfilerComponents;

  @NotNull private final Map<InstanceAttribute, AttributeColumn> myAttributeColumns = new HashMap<>();

  @NotNull private final JPanel myInstancesPanel = new JPanel(new BorderLayout());

  @Nullable private JComponent myColumnTree;

  @Nullable private JTree myTree;

  @Nullable private DefaultTreeModel myTreeModel;

  @Nullable private MemoryObjectTreeNode<InstanceObject> myTreeRoot;

  @Nullable private ClassObject myClassObject;

  @Nullable private InstanceObject myInstanceObject;

  public MemoryInstanceView(@NotNull MemoryProfilerStage stage, @NotNull IdeProfilerComponents ideProfilerComponents) {
    myStage = stage;
    myIdeProfilerComponents = ideProfilerComponents;

    myStage.getAspect().addDependency(this)
      .onChange(MemoryProfilerAspect.CURRENT_CLASS, this::refreshClass)
      .onChange(MemoryProfilerAspect.CURRENT_INSTANCE, this::refreshInstance);

    myAttributeColumns.put(
      InstanceObject.InstanceAttribute.LABEL,
      new AttributeColumn(
        "Instance",
        InstanceColumnRenderer::new,
        SwingConstants.LEFT,
        LABEL_COLUMN_WIDTH,
        SortOrder.ASCENDING,
        Comparator.comparing(o -> ((InstanceObject)o.getAdapter()).getDisplayLabel())));
    myAttributeColumns.put(
      InstanceObject.InstanceAttribute.DEPTH,
      new AttributeColumn(
        "Depth",
        () -> new SimpleColumnRenderer(value -> {
          MemoryObject node = value.getAdapter();
          if (node instanceof InstanceObject) {
            InstanceObject instanceObject = (InstanceObject)value.getAdapter();
            if (instanceObject.getDepth() >= 0 && instanceObject.getDepth() < Integer.MAX_VALUE) {
              return Integer.toString(instanceObject.getDepth());
            }
          }
          return "";
        }, value -> null, SwingConstants.RIGHT),
        SwingConstants.RIGHT,
        DEFAULT_COLUMN_WIDTH,
        SortOrder.UNSORTED,
        Comparator.comparingInt(o -> ((InstanceObject)o.getAdapter()).getDepth())));
    myAttributeColumns.put(
      InstanceObject.InstanceAttribute.SHALLOW_SIZE,
      new AttributeColumn(
        "Shallow Size",
        () -> new SimpleColumnRenderer(value -> Integer.toString(((InstanceObject)value.getAdapter()).getShallowSize()), value -> null,
                                       SwingConstants.RIGHT),
        SwingConstants.RIGHT,
        DEFAULT_COLUMN_WIDTH,
        SortOrder.UNSORTED,
        Comparator.comparingInt(o -> ((InstanceObject)o.getAdapter()).getShallowSize())));
    myAttributeColumns.put(
      InstanceObject.InstanceAttribute.RETAINED_SIZE,
      new AttributeColumn(
        "Retained Size",
        () -> new SimpleColumnRenderer(value -> {
          MemoryObject node = value.getAdapter();
          return node instanceof InstanceObject ? Long.toString(((InstanceObject)value.getAdapter()).getRetainedSize()) : "";
        }, value -> null, SwingConstants.RIGHT),
        SwingConstants.RIGHT,
        DEFAULT_COLUMN_WIDTH,
        SortOrder.UNSORTED,
        (o1, o2) -> {
          long diff = ((InstanceObject)o1.getAdapter()).getRetainedSize() - ((InstanceObject)o2.getAdapter()).getRetainedSize();
          return diff > 0 ? 1 : (diff < 0 ? -1 : 0);
        }));

    JPanel headingPanel = new JPanel(new BorderLayout());
    headingPanel.setBorder(BorderFactory.createLineBorder(JBColor.border()));
    JLabel instanceViewLabel = new JLabel("Instance View");
    instanceViewLabel.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 0));
    headingPanel.add(instanceViewLabel, BorderLayout.WEST);

    IconButton closeIcon = new IconButton("Close", AllIcons.Actions.Close, AllIcons.Actions.CloseHovered);
    InplaceButton closeButton = new InplaceButton(closeIcon, e -> myStage.selectClass(null));
    closeButton.setMinimumSize(closeButton.getPreferredSize()); // Prevent layout phase from squishing this button
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
    myClassObject = null;
    myInstanceObject = null;
    myInstancesPanel.setVisible(false);
    myStage.selectInstance(null);
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
    assert myColumnTree == null && myTreeModel == null && myTreeRoot == null;

    //noinspection Convert2Lambda
    myTreeRoot = new MemoryObjectTreeNode<>(new InstanceObject() {
      @NotNull
      @Override
      public String getDisplayLabel() {
        return "";
      }

      @Nullable
      @Override
      public ClassObject getClassObject() {
        return null;
      }

      @Nullable
      @Override
      public String getClassName() {
        return null;
      }
    });

    myTreeModel = new DefaultTreeModel(myTreeRoot);
    myTree = new Tree(myTreeModel);
    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(true);
    myTree.addTreeSelectionListener(e -> {
      TreePath path = e.getPath();
      if (!e.isAddedPath()) {
        return;
      }

      assert path.getLastPathComponent() instanceof MemoryObjectTreeNode;
      MemoryObjectTreeNode instanceObject = (MemoryObjectTreeNode)path.getLastPathComponent();
      assert instanceObject.getAdapter() instanceof InstanceObject;
      myStage.selectInstance((InstanceObject)instanceObject.getAdapter());
    });
    // Not all nodes have been populated during buildTree. Here we capture the TreeExpansionEvent to check whether any children
    // under the expanded node need to be populated.
    myTree.addTreeExpansionListener(new TreeExpansionListener() {
      @Override
      public void treeExpanded(TreeExpansionEvent event) {
        TreePath path = event.getPath();

        assert path.getLastPathComponent() instanceof MemoryObjectTreeNode;
        MemoryObjectTreeNode treeNode = (MemoryObjectTreeNode)path.getLastPathComponent();
        if (treeNode == myTreeRoot) {
          return; // children under root have already been expanded (check in case this gets called on the root)
        }
        assert treeNode instanceof InstanceTreeNode;
        InstanceTreeNode instanceTreeNode = (InstanceTreeNode)treeNode;
        instanceTreeNode.expandNode();
        myTreeModel.nodeStructureChanged(instanceTreeNode);
      }

      @Override
      public void treeCollapsed(TreeExpansionEvent event) {
        // No-op. TODO remove unseen children?
      }
    });
    installTreeContextMenus();

    assert myClassObject != null;
    List<InstanceAttribute> attributes = myClassObject.getInstanceAttributes();
    ColumnTreeBuilder builder = new ColumnTreeBuilder(myTree);
    InstanceAttribute sortAttribute = Collections.max(attributes, Comparator.comparingInt(InstanceAttribute::getWeight));
    for (InstanceAttribute attribute : attributes) {
      ColumnTreeBuilder.ColumnBuilder columnBuilder = myAttributeColumns.get(attribute).getBuilder();
      if (sortAttribute == attribute) {
        columnBuilder.setInitialOrder(SortOrder.DESCENDING);
      }
      builder.addColumn(columnBuilder);
    }
    builder.setTreeSorter((Comparator<MemoryObjectTreeNode<InstanceObject>> comparator, SortOrder sortOrder) -> {
      myTreeRoot.sort(comparator);
      myTreeModel.nodeStructureChanged(myTreeRoot);
    });
    builder.setBackground(ProfilerColors.MONITOR_BACKGROUND);
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

      if (((MemoryObjectTreeNode)selection.getLastPathComponent()).getAdapter() instanceof InstanceObject) {
        InstanceObject instanceObject = (InstanceObject)((MemoryObjectTreeNode)selection.getLastPathComponent()).getAdapter();
        return new CodeLocation.Builder(instanceObject.getClassName()).build();
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
        return myInstanceObject != null && myInstanceObject.getClassObject() != null;
      }

      @Override
      public void run() {
        assert myInstanceObject != null;
        InstanceObject instance = myInstanceObject;
        ClassObject klass = instance.getClassObject();
        assert klass != null;
        myStage.selectHeap(klass.getHeapObject());
        myStage.selectClass(klass);
        myStage.selectInstance(instance);
      }
    });
  }

  private void populateTreeContents() {
    assert myClassObject != null && myTreeRoot != null && myTreeModel != null;
    myTreeRoot.removeAll();

    for (InstanceObject instanceObject : myClassObject.getInstances()) {
      InstanceTreeNode instanceNode = new InstanceTreeNode(instanceObject);
      myTreeRoot.add(instanceNode);
    }
    myTreeModel.nodeChanged(myTreeRoot);
    myTreeModel.reload();
  }

  private void refreshClass() {
    ClassObject classObject = myStage.getSelectedClass();
    if (classObject == myClassObject) {
      return;
    }

    if (classObject == null) {
      reset();
      return;
    }

    if (myClassObject == null) {
      myClassObject = classObject;
      initializeTree();
    }
    else {
      myClassObject = classObject;
    }

    populateTreeContents();
    myInstancesPanel.setVisible(true);
  }

  private void refreshInstance() {
    InstanceObject instanceObject = myStage.getSelectedInstance();
    if (myInstanceObject == instanceObject) {
      return;
    }

    if (instanceObject == null) {
      myInstanceObject = null;
      return;
    }

    if (myTreeRoot == null || myTreeModel == null || myTree == null) {
      return;
    }

    myInstanceObject = instanceObject;
    for (MemoryObjectTreeNode<InstanceObject> node : myTreeRoot.getChildren()) {
      if (node.getAdapter().equals(myInstanceObject)) {
        TreePath path = new TreePath(myTreeModel.getPathToRoot(node));
        myTree.scrollPathToVisible(path);
        myTree.setSelectionPath(path);
        break;
      }
    }
  }

  static class InstanceTreeNode extends LazyMemoryObjectTreeNode<InstanceObject> {
    public InstanceTreeNode(@NotNull InstanceObject adapter) {
      super(adapter);
    }

    @Override
    public int computeChildrenCount() {
      return getAdapter().getFieldCount();
    }

    @Override
    public void expandNode() {
      if (myMemoizedChildrenCount == myChildren.size()) {
        return;
      }

      InstanceObject adapter = getAdapter();
      myMemoizedChildrenCount = 0;
      for (InstanceObject subAdapter : adapter.getFields()) {
        MemoryObjectTreeNode child = new MemoryObjectTreeNode<>(subAdapter);
        add(child);
        myMemoizedChildrenCount++;
      }

      assert myMemoizedChildrenCount == myChildren.size();
      if (myComparator != null) {
        sort(myComparator);
      }
    }
  }
}
