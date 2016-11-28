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
import com.intellij.icons.AllIcons;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

final class MemoryInstanceView {
  private static final int LABEL_COLUMN_WIDTH = 500;
  private static final int DEFAULT_COLUMN_WIDTH = 80;

  @NotNull private final MemoryProfilerStage myStage;

  @NotNull private final Map<Capability, MemoryProfilerStageView.CapabilityColumn> myCapabilityColumns = new HashMap<>();

  @Nullable private JComponent myTree;

  @Nullable private DefaultTreeModel myTreeModel;

  @Nullable private MemoryObjectTreeNode myTreeRoot;

  @Nullable private MemoryObjects myMemoryObjects;

  public MemoryInstanceView(@NotNull MemoryProfilerStage stage) {
    myStage = stage;

    myCapabilityColumns.put(
      MemoryNode.Capability.LABEL,
      new MemoryProfilerStageView.CapabilityColumn(
        "Instance",
        () -> new MemoryProfilerStageView.DetailColumnRenderer(value -> value.getAdapter().getName(),
                                                               value -> PlatformIcons.FIELD_ICON,
                                                               SwingConstants.LEFT),
        SwingConstants.LEFT,
        LABEL_COLUMN_WIDTH,
        SortOrder.ASCENDING,
        (o1, o2) -> o1.getAdapter().getName().compareTo(o2.getAdapter().getName())));
    myCapabilityColumns.put(
      MemoryNode.Capability.DEPTH,
      new MemoryProfilerStageView.CapabilityColumn(
        "Depth",
        () -> new MemoryProfilerStageView.DetailColumnRenderer(value -> Integer.toString(value.getAdapter().getDepth()), value -> null,
                                                               SwingConstants.RIGHT),
        SwingConstants.RIGHT,
        DEFAULT_COLUMN_WIDTH,
        SortOrder.UNSORTED,
        (o1, o2) -> o1.getAdapter().getDepth() - o2.getAdapter().getDepth()));
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

  public void reset() {
    myTree = null;
    myTreeRoot = null;
    myTreeModel = null;
    myMemoryObjects = null;
  }

  @Nullable
  public MemoryObjects getCurrentClassObject() {
    return myMemoryObjects;
  }

  @Nullable
  public JComponent buildComponent(@NotNull MemoryObjects model) {
    myMemoryObjects = model;
    MemoryNode rootNode = model.getRootNode();

    JPanel instancesPanel = new JPanel(new BorderLayout());
    JPanel headingPanel = new JPanel(new BorderLayout());
    headingPanel.add(new JLabel("Instance View"), BorderLayout.WEST);

    JButton close = new JButton(AllIcons.Actions.Close);
    close.addActionListener(e -> myStage.selectClass(null));
    headingPanel.add(close, BorderLayout.EAST);

    instancesPanel.add(headingPanel, BorderLayout.NORTH);
    buildTree(instancesPanel, rootNode);

    return instancesPanel;
  }

  private void buildTree(@NotNull JPanel parentPanel, @NotNull MemoryNode adapter) {
    ensureTreeInitialized(parentPanel, adapter);
    assert myTreeRoot != null && myTreeModel != null;
    myTreeRoot.removeAll();
    for (MemoryNode subAdapter : adapter.getSubList()) {
      // TODO populate fields
      myTreeRoot.add(new MemoryObjectTreeNode(subAdapter));
    }
    myTreeModel.nodeChanged(myTreeRoot);
    myTreeModel.reload();
  }

  private void ensureTreeInitialized(@NotNull JPanel parentPanel, @NotNull MemoryNode adapter) {
    if (myTree != null) {
      assert myTreeModel != null && myTreeRoot != null;
      return;
    }

    myTreeRoot = new MemoryObjectTreeNode(new MemoryNode() {
      // Place holder MemoryNode as root.
    });
    myTreeModel = new DefaultTreeModel(myTreeRoot);
    JTree tree = new Tree(myTreeModel);
    tree.setRootVisible(false);
    tree.setShowsRootHandles(true);

    List<Capability> capabilities = adapter.getCapabilities();
    ColumnTreeBuilder builder = new ColumnTreeBuilder(tree);
    for (Capability capability : capabilities) {
      builder.addColumn(myCapabilityColumns.get(capability).getBuilder());
    }
    builder.setTreeSorter((Comparator<MemoryObjectTreeNode> comparator, SortOrder sortOrder) -> {
      myTreeRoot.sort(comparator);
      myTreeModel.nodeStructureChanged(myTreeRoot);
    });
    myTree = builder.build();
    parentPanel.add(myTree, BorderLayout.CENTER);
  }
}
