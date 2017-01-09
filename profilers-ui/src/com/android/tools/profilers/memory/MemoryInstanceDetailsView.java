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
import com.android.tools.profiler.proto.MemoryProfiler.AllocationStack;
import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.memory.adapters.InstanceObject;
import com.android.tools.profilers.memory.adapters.InstanceObject.InstanceAttribute;
import com.android.tools.profilers.memory.adapters.MemoryObject;
import com.android.tools.profilers.memory.adapters.ReferenceObject;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * A view object that is responsible for displaying the callstack + references of an {@link InstanceObject} based on whether the
 * information is available. If no detailed information can be obtained from the InstanceObject, this UI is responsible
 * for automatically hiding itself.
 */
final class MemoryInstanceDetailsView extends AspectObserver {
  private static final int LABEL_COLUMN_WIDTH = 500;
  private static final int DEFAULT_COLUMN_WIDTH = 80;

  @NotNull private final MemoryProfilerStage myStage;

  @NotNull private final JTabbedPane myTabbedPane;

  @NotNull private final Map<InstanceAttribute, AttributeColumn> myAttributeColumns = new HashMap<>();

  public MemoryInstanceDetailsView(@NotNull MemoryProfilerStage stage) {
    myStage = stage;
    myStage.getAspect().addDependency(this)
      .onChange(MemoryProfilerAspect.CURRENT_INSTANCE, this::instanceChanged);

    // TODO fix tab styling. Currently tabs appear in the middle with too much padding.
    myTabbedPane = new JBTabbedPane();

    myAttributeColumns.put(
      InstanceObject.InstanceAttribute.LABEL,
      new AttributeColumn(
        "Reference",
        () -> new DetailColumnRenderer(value -> {
          StringBuilder builder = new StringBuilder();
          assert value.getAdapter() instanceof InstanceObject;
          InstanceObject node = (InstanceObject)value.getAdapter();
          if (node instanceof ReferenceObject) {
            ReferenceObject referrer = (ReferenceObject)node;
            List<String> fieldNames = referrer.getReferenceFieldNames();
            if (fieldNames.size() > 0) {
              if (referrer.getIsArray()) {
                builder.append("Index ");
              }
              builder.append(String.join(",", fieldNames));
              builder.append(" in  ");
            }
          }

          builder.append(node.getName());
          return builder.toString();
        },
                                       value -> MemoryProfilerStageView.getInstanceObjectIcon((InstanceObject)value.getAdapter()),
                                       SwingConstants.LEFT),
        SwingConstants.LEFT,
        LABEL_COLUMN_WIDTH,
        SortOrder.ASCENDING,
        (o1, o2) -> ((InstanceObject)o1.getAdapter()).getName().compareTo(((InstanceObject)o2.getAdapter()).getName())));
    myAttributeColumns.put(
      InstanceObject.InstanceAttribute.DEPTH,
      new AttributeColumn(
        "Depth",
        () -> new DetailColumnRenderer(value -> {
          MemoryObject node = value.getAdapter();
          if (node instanceof InstanceObject) {
            InstanceObject instanceObject = (InstanceObject)value.getAdapter();
            if (instanceObject.getDepth() >= 0) {
              return Integer.toString(instanceObject.getDepth());
            }
          }
          return "";
        }, value -> null, SwingConstants.RIGHT),
        SwingConstants.RIGHT,
        DEFAULT_COLUMN_WIDTH,
        SortOrder.UNSORTED,
        (o1, o2) -> ((InstanceObject)o1.getAdapter()).getDepth() - ((InstanceObject)o2.getAdapter()).getDepth()));
    myAttributeColumns.put(
      InstanceObject.InstanceAttribute.SHALLOW_SIZE,
      new AttributeColumn(
        "Shallow Size",
        () -> new DetailColumnRenderer(value -> Integer.toString(((InstanceObject)value.getAdapter()).getShallowSize()), value -> null,
                                       SwingConstants.RIGHT),
        SwingConstants.RIGHT,
        DEFAULT_COLUMN_WIDTH,
        SortOrder.UNSORTED,
        (o1, o2) -> ((InstanceObject)o1.getAdapter()).getShallowSize() - ((InstanceObject)o2.getAdapter()).getShallowSize()));
    myAttributeColumns.put(
      InstanceObject.InstanceAttribute.RETAINED_SIZE,
      new AttributeColumn(
        "Retained Size",
        () -> new DetailColumnRenderer(value -> {
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

    // Fires the handler once at the beginning to ensure we are sync'd with the latest selection state in the MemoryProfilerStage.
    instanceChanged();
  }

  @NotNull
  public JComponent getComponent() {
    return myTabbedPane;
  }

  private void instanceChanged() {
    InstanceObject instance = myStage.getSelectedInstance();
    if (instance == null) {
      myTabbedPane.setVisible(false);
      return;
    }

    myTabbedPane.removeAll();
    boolean hasContent = false;

    // Populate Callstacks
    AllocationStack callStack = instance.getCallStack();
    if (callStack != null && !callStack.getStackFramesList().isEmpty()) {
      DefaultListModel<StackTraceElement> model = new DefaultListModel<>();
      callStack.getStackFramesList().forEach(frame -> model
        .addElement(new StackTraceElement(frame.getClassName(), frame.getMethodName(), frame.getFileName(), frame.getLineNumber())));
      myTabbedPane.add("Callstack", new JBScrollPane(new JBList(model)));
      hasContent = true;
    }

    // Populate references
    JComponent tree = buildReferenceColumnTree(instance);
    if (tree != null) {
      myTabbedPane.add("References", tree);
      hasContent = true;
    }

    myTabbedPane.setVisible(hasContent);
  }

  @Nullable
  private JComponent buildReferenceColumnTree(@NotNull InstanceObject instance) {
    if (instance.getReferences().isEmpty()) {
      return null;
    }

    JTree tree = buildTree(instance);
    ColumnTreeBuilder builder = new ColumnTreeBuilder(tree);
    for (InstanceAttribute attribute : instance.getReferenceAttributes()) {
      builder.addColumn(myAttributeColumns.get(attribute).getBuilder());
    }
    builder.setTreeSorter((Comparator<MemoryObjectTreeNode<InstanceObject>> comparator, SortOrder sortOrder) -> {
      assert tree.getModel() instanceof DefaultTreeModel;
      DefaultTreeModel treeModel = (DefaultTreeModel)tree.getModel();
      assert treeModel.getRoot() instanceof MemoryObjectTreeNode;
      assert ((MemoryObjectTreeNode)treeModel.getRoot()).getAdapter() instanceof InstanceObject;
      //noinspection unchecked
      MemoryObjectTreeNode<InstanceObject> root = (MemoryObjectTreeNode<InstanceObject>)treeModel.getRoot();
      root.sort(comparator);
      treeModel.nodeStructureChanged(root);
    });
    builder.setBackground(ProfilerColors.MONITOR_BACKGROUND);
    return builder.build();
  }

  @VisibleForTesting
  @NotNull
  JTree buildTree(@NotNull InstanceObject instance) {
    final MemoryObjectTreeNode<InstanceObject> treeRoot = new MemoryObjectTreeNode<>(instance);
    populateReferenceNodesRecursive(treeRoot, instance, 2);
    final DefaultTreeModel treeModel = new DefaultTreeModel(treeRoot);
    final JTree tree = new Tree(treeModel);
    tree.setRootVisible(true);
    tree.setShowsRootHandles(true);

    // Not all nodes have been populated during buildReferenceColumnTree. Here we capture the TreeExpansionEvent to check whether any children
    // under the expanded node need to be populated.
    tree.addTreeExpansionListener(new TreeExpansionListener() {
      @Override
      public void treeExpanded(TreeExpansionEvent event) {
        TreePath path = event.getPath();

        assert path.getLastPathComponent() instanceof MemoryObjectTreeNode;
        MemoryObjectTreeNode treeNode = (MemoryObjectTreeNode)path.getLastPathComponent();

        for (int i = 0; i < treeNode.getChildCount(); i++) {
          assert treeNode.getChildAt(i) instanceof MemoryObjectTreeNode;
          MemoryObjectTreeNode childNode = (MemoryObjectTreeNode)treeNode.getChildAt(i);
          assert childNode.getAdapter() instanceof InstanceObject;
          InstanceObject childObject = (InstanceObject)childNode.getAdapter();
          if (childNode.getChildCount() == 0) {
            populateReferenceNodesRecursive(childNode, childObject, 2);
            treeModel.nodeStructureChanged(childNode);
          }
        }
      }

      @Override
      public void treeCollapsed(TreeExpansionEvent event) {
      }
    });

    return tree;
  }

  private void populateReferenceNodesRecursive(@NotNull MemoryObjectTreeNode parent,
                                               @NotNull InstanceObject parentObject,
                                               int depth) {
    if (depth == 0) {
      return;
    }
    depth--;

    assert parent.getAdapter() == parentObject;
    for (InstanceObject child : parentObject.getReferences()) {
      MemoryObjectTreeNode childNode = new MemoryObjectTreeNode<>(child);
      parent.add(childNode);
      populateReferenceNodesRecursive(childNode, child, depth);
    }
  }
}
