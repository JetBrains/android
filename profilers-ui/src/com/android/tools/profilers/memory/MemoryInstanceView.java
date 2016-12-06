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
import com.android.tools.profiler.proto.MemoryProfiler.AllocationStack;
import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.memory.MemoryProfilerStageView.AttributeColumn;
import com.android.tools.profilers.memory.MemoryProfilerStageView.DetailColumnRenderer;
import com.android.tools.profilers.memory.adapters.ClassObject;
import com.android.tools.profilers.memory.adapters.ClassObject.InstanceAttribute;
import com.android.tools.profilers.memory.adapters.FieldObject;
import com.android.tools.profilers.memory.adapters.InstanceObject;
import com.android.tools.profilers.memory.adapters.InstanceObject.ValueType;
import com.android.tools.profilers.memory.adapters.MemoryObject;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.Splitter;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

final class MemoryInstanceView {
  private static final int LABEL_COLUMN_WIDTH = 500;
  private static final int DEFAULT_COLUMN_WIDTH = 80;

  @NotNull private final MemoryProfilerStage myStage;

  @NotNull private final Map<InstanceAttribute, AttributeColumn> myAttributeColumns = new HashMap<>();

  @Nullable private Splitter mySplitter;

  @Nullable private JComponent myTree;

  @Nullable private DefaultTreeModel myTreeModel;

  @Nullable private MemoryObjectTreeNode<InstanceObject> myTreeRoot;

  @Nullable private ClassObject myClassObject;

  @Nullable private InstanceObject myInstanceObject;

  public MemoryInstanceView(@NotNull MemoryProfilerStage stage) {
    myStage = stage;

    myStage.getAspect().addDependency()
      .setExecutor(ApplicationManager.getApplication()::invokeLater)
      .onChange(MemoryProfilerAspect.CURRENT_INSTANCE, this::instanceChanged);

    myAttributeColumns.put(
      InstanceAttribute.LABEL,
      new AttributeColumn(
        "Instance",
        () -> new DetailColumnRenderer(value -> ((InstanceObject)value.getAdapter()).getName(),
                                       value -> {
                                         MemoryObject node = value.getAdapter();
                                         if (node instanceof FieldObject) {
                                           FieldObject field = ((FieldObject)node);
                                           if (field.getIsArray()) {
                                             return AllIcons.Debugger.Db_array;
                                           }
                                           else if (field.getIsPrimitive()) {
                                             return AllIcons.Debugger.Db_primitive;
                                           }
                                           else {
                                             return PlatformIcons.FIELD_ICON;
                                           }
                                         }
                                         else {
                                           return PlatformIcons.INTERFACE_ICON;
                                         }
                                       },
                                       SwingConstants.LEFT),
        SwingConstants.LEFT,
        LABEL_COLUMN_WIDTH,
        SortOrder.ASCENDING,
        (o1, o2) -> ((InstanceObject)o1.getAdapter()).getName().compareTo(((InstanceObject)o2.getAdapter()).getName())));
    myAttributeColumns.put(
      InstanceAttribute.DEPTH,
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
      InstanceAttribute.SHALLOW_SIZE,
      new AttributeColumn(
        "Shallow Size",
        () -> new DetailColumnRenderer(value -> Integer.toString(((InstanceObject)value.getAdapter()).getShallowSize()), value -> null,
                                       SwingConstants.RIGHT),
        SwingConstants.RIGHT,
        DEFAULT_COLUMN_WIDTH,
        SortOrder.UNSORTED,
        (o1, o2) -> ((InstanceObject)o1.getAdapter()).getShallowSize() - ((InstanceObject)o2.getAdapter()).getShallowSize()));
    myAttributeColumns.put(
      InstanceAttribute.RETAINED_SIZE,
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
  }

  public void reset() {
    myTree = null;
    myTreeRoot = null;
    myTreeModel = null;
    myClassObject = null;
    mySplitter = null;
  }

  @Nullable
  public ClassObject getCurrentClassObject() {
    return myClassObject;
  }

  @Nullable
  public JComponent buildComponent(@NotNull ClassObject targetClass) {
    myClassObject = targetClass;

    mySplitter = new Splitter(true);

    JPanel instancesPanel = new JPanel(new BorderLayout());
    JPanel headingPanel = new JPanel(new BorderLayout());
    headingPanel.add(new JLabel("Instance View"), BorderLayout.WEST);

    JButton close = new JButton(AllIcons.Actions.Close);
    close.addActionListener(e -> myStage.selectClass(null));
    headingPanel.add(close, BorderLayout.EAST);

    instancesPanel.add(headingPanel, BorderLayout.NORTH);
    buildTree(instancesPanel);

    mySplitter.setFirstComponent(instancesPanel);

    return mySplitter;
  }

  private void buildTree(@NotNull JPanel parentPanel) {
    ensureTreeInitialized(parentPanel);
    assert myTreeRoot != null && myTreeModel != null && myClassObject != null;
    myTreeRoot.removeAll();

    for (InstanceObject instanceObject : myClassObject.getInstances()) {
      MemoryObjectTreeNode instanceNode = new MemoryObjectTreeNode<>(instanceObject);
      myTreeRoot.add(instanceNode);
      populateFields(instanceNode);
    }
    myTreeModel.nodeChanged(myTreeRoot);
    myTreeModel.reload();
  }

  private static void populateFields(@NotNull MemoryObjectTreeNode parent) {
    assert parent.getAdapter() instanceof InstanceObject;

    InstanceObject adapter = (InstanceObject)parent.getAdapter();
    if (adapter.getFields() == null) {
      return;
    }

    for (InstanceObject subAdapter : adapter.getFields()) {
      MemoryObjectTreeNode child = new MemoryObjectTreeNode<>(subAdapter);
      parent.add(child);
    }
  }

  private void instanceChanged() {
    if (mySplitter == null || myInstanceObject == myStage.getSelectedInstance()) {
      return;
    }

    myInstanceObject = myStage.getSelectedInstance();
    if (myInstanceObject == null) {
      mySplitter.setSecondComponent(null);
      return;
    }

    AllocationStack callStack = myInstanceObject.getCallStack();
    if (callStack == null || callStack.getStackFramesList().size() == 0) {
      return;
    }

    DefaultListModel<StackTraceElement> model = new DefaultListModel<>();
    callStack.getStackFramesList().forEach(frame -> model
      .addElement(new StackTraceElement(frame.getClassName(), frame.getMethodName(), frame.getFileName(), frame.getLineNumber())));
    mySplitter.setSecondComponent(new JBScrollPane(new JBList(model)));
  }

  private void ensureTreeInitialized(@NotNull JPanel parentPanel) {
    if (myTree != null) {
      assert myTreeModel != null && myTreeRoot != null;
      return;
    }

    myTreeRoot = new MemoryObjectTreeNode<>(new InstanceObject() {
      @NotNull
      @Override
      public String getName() {
        return "";
      }
    });

    myTreeModel = new DefaultTreeModel(myTreeRoot);
    JTree tree = new Tree(myTreeModel);
    tree.setRootVisible(false);
    tree.setShowsRootHandles(true);
    tree.addTreeSelectionListener(e -> {
      TreePath path = e.getPath();
      if (!e.isAddedPath()) {
        return;
      }

      assert path.getLastPathComponent() instanceof MemoryObjectTreeNode;
      MemoryObjectTreeNode instanceObject = (MemoryObjectTreeNode)path.getLastPathComponent();
      assert instanceObject.getAdapter() instanceof InstanceObject;
      InstanceObject selectedInstanceObject = (InstanceObject)instanceObject.getAdapter();
      myStage.selectInstance(selectedInstanceObject);
    });
    // Not all nodes have been populated during buildTree. Here we capture the TreeExpansionEvent to check whether any children
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

          // Anything below the top level should be FieldObjects
          assert childNode.getAdapter() instanceof FieldObject;
          FieldObject childObject = (FieldObject)childNode.getAdapter();
          if (childObject.getValueType() == ValueType.OBJECT && childNode.getChildCount() == 0) {
            populateFields(childNode);
            myTreeModel.nodeStructureChanged(childNode);
          }
        }
      }

      @Override
      public void treeCollapsed(TreeExpansionEvent event) {
        // No-op. TODO remove unseen children?
      }
    });

    assert myClassObject != null;
    List<InstanceAttribute> attributes = myClassObject.getInstanceAttributes();
    ColumnTreeBuilder builder = new ColumnTreeBuilder(tree);
    for (InstanceAttribute attribute : attributes) {
      builder.addColumn(myAttributeColumns.get(attribute).getBuilder());
    }
    builder.setTreeSorter((Comparator<MemoryObjectTreeNode<InstanceObject>> comparator, SortOrder sortOrder) -> {
      myTreeRoot.sort(comparator);
      myTreeModel.nodeStructureChanged(myTreeRoot);
    });
    builder.setBackground(ProfilerColors.MONITOR_BACKGROUND);
    myTree = builder.build();
    parentPanel.add(myTree, BorderLayout.CENTER);
  }
}
