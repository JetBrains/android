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
import com.android.tools.profilers.memory.MemoryNode.Capability;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

final class MemoryClassView {
  private static final int LABEL_COLUMN_WIDTH = 800;
  private static final int DEFAULT_COLUMN_WIDTH = 80;

  @NotNull private final MemoryProfilerStage myStage;

  @NotNull private final Map<Capability, MemoryProfilerStageView.CapabilityColumn> myCapabilityColumns = new HashMap<>();

  @Nullable private JComponent myClassesTree;

  @Nullable private DefaultTreeModel myClassesTreeModel;

  @Nullable private MemoryObjectTreeNode myClassesTreeRoot;

  @Nullable private MemoryObjects myMemoryObjects;

  public MemoryClassView(@NotNull MemoryProfilerStage stage) {
    myStage = stage;

    myCapabilityColumns.put(
      MemoryNode.Capability.LABEL,
      new MemoryProfilerStageView.CapabilityColumn(
        "Class Name",
        () -> new MemoryProfilerStageView.DetailColumnRenderer(value -> value.getAdapter().getName(),
                                                               value -> PlatformIcons.CLASS_ICON,
                                                               SwingConstants.LEFT),
        SwingConstants.LEFT,
        LABEL_COLUMN_WIDTH,
        SortOrder.ASCENDING,
        (o1, o2) -> o1.getAdapter().getName().compareTo(o2.getAdapter().getName())));
    myCapabilityColumns.put(
      MemoryNode.Capability.CHILDREN_COUNT,
      new MemoryProfilerStageView.CapabilityColumn(
        "Count",
        () -> new MemoryProfilerStageView.DetailColumnRenderer(value -> Integer.toString(value.getAdapter().getChildrenCount()),
                                                               value -> null,
                                                               SwingConstants.RIGHT),
        SwingConstants.RIGHT,
        DEFAULT_COLUMN_WIDTH,
        SortOrder.UNSORTED,
        (o1, o2) -> o1.getAdapter().getChildrenCount() - o2.getAdapter().getChildrenCount()));
    myCapabilityColumns.put(
      MemoryNode.Capability.ELEMENT_SIZE,
      new MemoryProfilerStageView.CapabilityColumn(
        "Size",
        () -> new MemoryProfilerStageView.DetailColumnRenderer(value -> Integer.toString(value.getAdapter().getElementSize()),
                                                               value -> null, SwingConstants.RIGHT),
        SwingConstants.RIGHT,
        DEFAULT_COLUMN_WIDTH,
        SortOrder.UNSORTED,
        (o1, o2) -> o1.getAdapter().getElementSize() - o2.getAdapter().getElementSize()));
    myCapabilityColumns.put(
      MemoryNode.Capability.SHALLOW_SIZE,
      new MemoryProfilerStageView.CapabilityColumn(
        "Shallow Size",
        () -> new MemoryProfilerStageView.DetailColumnRenderer(value -> Integer.toString(value.getAdapter().getShallowSize()),
                                                               value -> null, SwingConstants.RIGHT),
        SwingConstants.RIGHT,
        DEFAULT_COLUMN_WIDTH,
        SortOrder.UNSORTED,
        (o1, o2) -> o1.getAdapter().getShallowSize() - o2.getAdapter().getShallowSize()));
    myCapabilityColumns.put(
      MemoryNode.Capability.RETAINED_SIZE,
      new MemoryProfilerStageView.CapabilityColumn(
        "Retained Size",
        () -> new MemoryProfilerStageView.DetailColumnRenderer(value -> Long.toString(value.getAdapter().getRetainedSize()), value -> null,
                                                               SwingConstants.RIGHT),
        SwingConstants.RIGHT,
        DEFAULT_COLUMN_WIDTH,
        SortOrder.UNSORTED,
        (o1, o2) -> {
          long diff = o1.getAdapter().getRetainedSize() - o2.getAdapter().getRetainedSize();
          return diff > 0 ? 1 : (diff < 0 ? -1 : 0);
        }));
  }

  /**
   * Must manually remove from parent container!
   */
  public void reset() {
    myClassesTree = null;
    myClassesTreeRoot = null;
    myClassesTreeModel = null;
    myMemoryObjects = null;
  }

  @Nullable
  public MemoryObjects getCurrentHeapObject() {
    return myMemoryObjects;
  }

  @Nullable
  public JComponent buildComponent(@NotNull MemoryObjects model) {
    myMemoryObjects = model;
    MemoryNode rootNode = myMemoryObjects.getRootNode();

    JPanel classesPanel = new JPanel(new BorderLayout());
    JPanel headingPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
    headingPanel.add(new JLabel(rootNode.toString()));

    JToolBar toolBar = new JToolBar();
    toolBar.setFloatable(false);
    List<MemoryNode> itemList = rootNode.getSubList();

    ComboBoxModel<MemoryNode> comboBoxModel = new DefaultComboBoxModel<>(itemList.toArray(new MemoryNode[itemList.size()]));
    ComboBox<MemoryNode> comboBox = new ComboBox<>(comboBoxModel);
    comboBox.addActionListener(e -> {
      // TODO abstract out selection path so we don't need to special case
      Object item = comboBox.getSelectedItem();
      if (item != null && item instanceof MemoryNode) {
        buildTree(classesPanel, (MemoryNode)item);
      }
    });
    toolBar.add(comboBox);
    headingPanel.add(toolBar);

    classesPanel.add(headingPanel, BorderLayout.PAGE_START);
    boolean selected = false;
    // TODO provide a default selection in the model API?
    for (MemoryNode item : itemList) {
      if (item.getName().equals("app")) {
        comboBox.setSelectedItem(item);
        selected = true;
        break;
      }
    }
    if (!selected) {
      comboBox.setSelectedItem(itemList.get(0));
    }

    return classesPanel;
  }

  private void ensureTreeInitialized(@NotNull JPanel parentPanel, @NotNull MemoryNode adapter) {
    if (myClassesTree != null) {
      assert myClassesTreeModel != null && myClassesTreeRoot != null;
      return;
    }

    myClassesTreeRoot = new MemoryObjectTreeNode(new MemoryNode() {
    });
    myClassesTreeModel = new DefaultTreeModel(myClassesTreeRoot);
    JTree tree = new Tree(myClassesTreeModel);
    tree.setRootVisible(false);
    tree.setShowsRootHandles(true);
    tree.addTreeSelectionListener(e -> {
      TreePath path = e.getPath();
      if (!e.isAddedPath()) {
        return;
      }

      assert path.getLastPathComponent() instanceof MemoryObjectTreeNode;
      MemoryObjectTreeNode classNode = (MemoryObjectTreeNode)path.getLastPathComponent();
      ClassObjects klass = new ClassObjects(classNode.getAdapter());
      myStage.selectClass(klass);
    });

    List<Capability> capabilities = adapter.getCapabilities();
    ColumnTreeBuilder builder = new ColumnTreeBuilder(tree);
    for (Capability capability : capabilities) {
      builder.addColumn(myCapabilityColumns.get(capability).getBuilder());
    }
    builder.setTreeSorter((Comparator<MemoryObjectTreeNode> comparator, SortOrder sortOrder) -> {
      myClassesTreeRoot.sort(comparator);
      myClassesTreeModel.nodeStructureChanged(myClassesTreeRoot);
    });
    myClassesTree = builder.build();
    parentPanel.add(myClassesTree, BorderLayout.CENTER);
  }

  private void buildTree(@NotNull JPanel parentPanel, @NotNull MemoryNode adapter) {
    ensureTreeInitialized(parentPanel, adapter);
    assert myClassesTreeRoot != null && myClassesTreeModel != null;
    myClassesTreeRoot.removeAll();
    for (MemoryNode subAdapter : adapter.getSubList()) {
      // TODO handle package view
      myClassesTreeRoot.add(new MemoryObjectTreeNode(subAdapter));
    }
    myClassesTreeModel.nodeChanged(myClassesTreeRoot);
    myClassesTreeModel.reload();
  }
}
