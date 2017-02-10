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
import com.android.tools.profilers.IdeProfilerComponents;
import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.ProfilerMode;
import com.android.tools.profilers.common.CodeLocation;
import com.android.tools.profilers.common.ContextMenuItem;
import com.android.tools.profilers.common.StackTraceView;
import com.android.tools.profilers.common.TabsPanel;
import com.android.tools.profilers.memory.adapters.ClassObject;
import com.android.tools.profilers.memory.adapters.InstanceObject;
import com.android.tools.profilers.memory.adapters.InstanceObject.InstanceAttribute;
import com.android.tools.profilers.memory.adapters.MemoryObject;
import com.android.tools.profilers.memory.adapters.ReferenceObject;
import com.google.common.annotations.VisibleForTesting;
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
import java.util.stream.Collectors;

/**
 * A view object that is responsible for displaying the callstack + references of an {@link InstanceObject} based on whether the
 * information is available. If no detailed information can be obtained from the InstanceObject, this UI is responsible
 * for automatically hiding itself.
 */
final class MemoryInstanceDetailsView extends AspectObserver {
  private static final int LABEL_COLUMN_WIDTH = 500;
  private static final int DEFAULT_COLUMN_WIDTH = 80;

  @NotNull private final MemoryProfilerStage myStage;

  @NotNull private final IdeProfilerComponents myIdeProfilerComponents;

  @NotNull private final TabsPanel myTabsPanel;

  @NotNull private final StackTraceView myStackTraceView;

  @Nullable private JComponent myReferenceColumnTree;

  @Nullable private JTree myReferenceTree;

  @NotNull private final Map<InstanceAttribute, AttributeColumn> myAttributeColumns = new HashMap<>();

  public MemoryInstanceDetailsView(@NotNull MemoryProfilerStage stage, @NotNull IdeProfilerComponents ideProfilerComponents) {
    myStage = stage;
    myStage.getAspect().addDependency(this)
      .onChange(MemoryProfilerAspect.CURRENT_INSTANCE, this::instanceChanged);
    myIdeProfilerComponents = ideProfilerComponents;

    myTabsPanel = ideProfilerComponents.createTabsPanel();
    myStackTraceView = ideProfilerComponents.createStackView(() -> myStage.setProfilerMode(ProfilerMode.NORMAL));

    myAttributeColumns.put(
      InstanceObject.InstanceAttribute.LABEL,
      new AttributeColumn(
        "Reference",
        () -> new SimpleColumnRenderer(
          value -> {
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
                builder.append(" in ");
              }
            }

            builder.append(node.getDisplayLabel());
            return builder.toString();
          },
          value -> MemoryProfilerStageView.getInstanceObjectIcon((InstanceObject)value.getAdapter()),
          SwingConstants.LEFT),
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
            int depth = instanceObject.getDepth();
            if (depth >= 0 && depth < Integer.MAX_VALUE) {
              return Integer.toString(depth);
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
        () -> new SimpleColumnRenderer(
          value -> Integer.toString(((InstanceObject)value.getAdapter()).getShallowSize()), value -> null, SwingConstants.RIGHT),
        SwingConstants.RIGHT,
        DEFAULT_COLUMN_WIDTH,
        SortOrder.UNSORTED,
        Comparator.comparingInt(o -> ((InstanceObject)o.getAdapter()).getShallowSize())));
    myAttributeColumns.put(
      InstanceObject.InstanceAttribute.RETAINED_SIZE,
      new AttributeColumn(
        "Retained Size",
        () -> new SimpleColumnRenderer(
          value -> {
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
  JComponent getComponent() {
    return myTabsPanel.getComponent();
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

  private void instanceChanged() {
    InstanceObject instance = myStage.getSelectedInstance();
    if (instance == null) {
      myReferenceTree = null;
      myReferenceColumnTree = null;
      myTabsPanel.getComponent().setVisible(false);
      return;
    }

    myTabsPanel.removeAll();
    boolean hasContent = false;

    // Populate Callstacks
    AllocationStack callStack = instance.getCallStack();
    if (callStack != null && !callStack.getStackFramesList().isEmpty()) {
      List<CodeLocation> stackFrames = callStack.getStackFramesList().stream()
        .map((frame) -> new CodeLocation(frame.getClassName(), frame.getFileName(), frame.getMethodName(), frame.getLineNumber() - 1))
        .collect(Collectors.toList());
      myStackTraceView.setStackFrames(instance.getAllocationThreadId(), stackFrames);
      myTabsPanel.addTab("Callstack", myStackTraceView.getComponent());
      hasContent = true;
    }

    // Populate references
    myReferenceColumnTree = buildReferenceColumnTree(instance);
    if (myReferenceColumnTree != null) {
      myTabsPanel.addTab("References", myReferenceColumnTree);
      hasContent = true;
    }

    myTabsPanel.getComponent().setVisible(hasContent);
  }

  @Nullable
  private JComponent buildReferenceColumnTree(@NotNull InstanceObject instance) {
    if (instance.getReferences().isEmpty()) {
      myReferenceTree = null;
      return null;
    }

    myReferenceTree = buildTree(instance);
    ColumnTreeBuilder builder = new ColumnTreeBuilder(myReferenceTree);
    for (InstanceAttribute attribute : instance.getReferenceAttributes()) {
      builder.addColumn(myAttributeColumns.get(attribute).getBuilder());
    }
    builder.setTreeSorter((Comparator<MemoryObjectTreeNode<InstanceObject>> comparator, SortOrder sortOrder) -> {
      assert myReferenceTree.getModel() instanceof DefaultTreeModel;
      DefaultTreeModel treeModel = (DefaultTreeModel)myReferenceTree.getModel();
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

    myIdeProfilerComponents.installNavigationContextMenu(tree, () -> {
      TreePath selection = tree.getSelectionPath();
      if (selection == null) {
        return null;
      }

      InstanceObject instanceObject = (InstanceObject)((MemoryObjectTreeNode)selection.getLastPathComponent()).getAdapter();
      return new CodeLocation(instanceObject.getClassName());
    }, () -> myStage.setProfilerMode(ProfilerMode.NORMAL));

    myIdeProfilerComponents.installContextMenu(tree, new ContextMenuItem() {
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
        TreePath selection = tree.getSelectionPath();
        assert selection != null && ((MemoryObjectTreeNode)selection.getLastPathComponent()).getAdapter() instanceof InstanceObject;
        InstanceObject instance = (InstanceObject)((MemoryObjectTreeNode)selection.getLastPathComponent()).getAdapter();
        ClassObject klass = instance.getClassObject();
        assert klass != null;
        myStage.selectHeap(klass.getHeapObject());
        myStage.selectClass(klass);
        myStage.selectInstance(instance);
      }
    });

    return tree;
  }

  @SuppressWarnings("MethodMayBeStatic")
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
