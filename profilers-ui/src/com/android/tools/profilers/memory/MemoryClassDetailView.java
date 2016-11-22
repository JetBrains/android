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
import com.android.tools.adtui.common.ColumnTreeBuilder.ColumnBuilder;
import com.android.tools.profilers.memory.MemoryNode.Capability;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.Function;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class MemoryClassDetailView {
  @NotNull private final MemoryProfilerStage myStage;

  @NotNull private Map<Capability, CapabilityColumn> myCapabilityColumns = new HashMap<>();

  @Nullable private JComponent myClassesTree;

  @Nullable private DefaultTreeModel myClassesTreeModel;

  @Nullable private MemoryObjectTreeNode myClassesTreeRoot;

  public MemoryClassDetailView(@NotNull MemoryProfilerStage stage) {
    myStage = stage;

    myCapabilityColumns.put(
      Capability.LABEL,
      new CapabilityColumn(
        "Name",
        () -> new DetailColumnRenderer(value -> value.getAdapter().getName(), value -> null, SwingConstants.LEFT),
        SwingConstants.LEFT,
        SortOrder.ASCENDING,
        (o1, o2) -> o1.getAdapter().getName().compareTo(o2.getAdapter().getName())));
    myCapabilityColumns.put(
      Capability.CHILDREN_COUNT,
      new CapabilityColumn(
        "Count",
        () -> new DetailColumnRenderer(value -> Integer.toString(value.getAdapter().getChildrenCount()), value -> null,
                                       SwingConstants.RIGHT),
        SwingConstants.RIGHT,
        SortOrder.UNSORTED,
        (o1, o2) -> o1.getAdapter().getChildrenCount() - o2.getAdapter().getChildrenCount()));
    myCapabilityColumns.put(
      Capability.ELEMENT_SIZE,
      new CapabilityColumn(
        "Size",
        () -> new DetailColumnRenderer(value -> Integer.toString(value.getAdapter().getElementSize()), value -> null, SwingConstants.RIGHT),
        SwingConstants.RIGHT,
        SortOrder.UNSORTED,
        (o1, o2) -> o1.getAdapter().getElementSize() - o2.getAdapter().getElementSize()));
    myCapabilityColumns.put(
      Capability.SHALLOW_SIZE,
      new CapabilityColumn(
        "Shallow Size",
        () -> new DetailColumnRenderer(value -> Integer.toString(value.getAdapter().getShallowSize()), value -> null, SwingConstants.RIGHT),
        SwingConstants.RIGHT,
        SortOrder.UNSORTED,
        (o1, o2) -> o1.getAdapter().getShallowSize() - o2.getAdapter().getShallowSize()));
    myCapabilityColumns.put(
      Capability.RETAINED_SIZE,
      new CapabilityColumn(
        "Retained Size",
        () -> new DetailColumnRenderer(value -> Long.toString(value.getAdapter().getRetainedSize()), value -> null, SwingConstants.RIGHT),
        SwingConstants.RIGHT,
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
  }

  @Nullable
  public JComponent buildComponent(long startTime, long endTime) {
    MemoryObjects model = myStage.getMemoryObjects();
    if (model == null) {
      return null;
    }

    MemoryNode rootAdapter = model.getRootAdapter();

    JPanel classesPanel = new JPanel(new BorderLayout());
    JPanel headingPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
    headingPanel.add(new JLabel(rootAdapter.toString()));

    JToolBar toolBar = new JToolBar();
    toolBar.setFloatable(false);
    List<MemoryNode> itemList = rootAdapter.getSubList(startTime, endTime);

    ComboBoxModel<MemoryNode> comboBoxModel = new DefaultComboBoxModel<>(itemList.toArray(new MemoryNode[itemList.size()]));
    ComboBox<MemoryNode> comboBox = new ComboBox<>(comboBoxModel);
    comboBox.addActionListener(e -> {
      // TODO abstract out selection path so we don't need to special case
      Object item = comboBox.getSelectedItem();
      if (item != null && item instanceof MemoryNode) {
        buildTree(classesPanel, (MemoryNode)item, startTime, endTime);
      }
    });
    toolBar.add(comboBox);
    headingPanel.add(toolBar);

    classesPanel.add(headingPanel, BorderLayout.PAGE_START);
    for (MemoryNode item : itemList) {
      if (item.getName().equals("app")) {
        comboBox.setSelectedItem(item);
        break;
      }
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

  private void buildTree(@NotNull JPanel parentPanel, @NotNull MemoryNode adapter, long startTime, long endTime) {
    ensureTreeInitialized(parentPanel, adapter);
    assert myClassesTreeRoot != null && myClassesTreeModel != null;
    myClassesTreeRoot.removeAll();
    for (MemoryNode subAdapter : adapter.getSubList(startTime, endTime)) {
      myClassesTreeRoot.add(new MemoryObjectTreeNode(subAdapter));
    }
    myClassesTreeModel.nodeChanged(myClassesTreeRoot);
    myClassesTreeModel.reload();
  }

  private static class CapabilityColumn {
    private final String myName;
    private final Supplier<ColoredTreeCellRenderer> myRendererSuppier;
    private final int myHeaderAlignment;
    private final SortOrder mySortOrder;
    private final Comparator<MemoryObjectTreeNode> myComparator;

    public CapabilityColumn(@NotNull String name,
                            @NotNull Supplier<ColoredTreeCellRenderer> rendererSupplier,
                            int headerAlignment,
                            @NotNull SortOrder sortOrder,
                            @NotNull Comparator<MemoryObjectTreeNode> comparator) {
      myName = name;
      myRendererSuppier = rendererSupplier;
      myHeaderAlignment = headerAlignment;
      mySortOrder = sortOrder;
      myComparator = comparator;
    }

    @NotNull
    public ColumnBuilder getBuilder() {
      return new ColumnBuilder()
        .setName(myName)
        .setRenderer(myRendererSuppier.get())
        .setHeaderAlignment(myHeaderAlignment)
        .setInitialOrder(mySortOrder)
        .setComparator(myComparator);
    }
  }

  private static class DetailColumnRenderer extends ColoredTreeCellRenderer {
    private final Function<MemoryObjectTreeNode, String> myTextGetter;
    private final Function<MemoryObjectTreeNode, Icon> myIconGetter;
    private final int myAlignment;

    public DetailColumnRenderer(@NotNull Function<MemoryObjectTreeNode, String> textGetter,
                                @NotNull Function<MemoryObjectTreeNode, Icon> iconGetter,
                                int alignment) {
      myTextGetter = textGetter;
      myIconGetter = iconGetter;
      myAlignment = alignment;
    }

    @Override
    public void customizeCellRenderer(@NotNull JTree tree,
                                      Object value,
                                      boolean selected,
                                      boolean expanded,
                                      boolean leaf,
                                      int row,
                                      boolean hasFocus) {
      if (value instanceof MemoryObjectTreeNode) {
        append(myTextGetter.fun((MemoryObjectTreeNode)value));
        Icon icon = myIconGetter.fun((MemoryObjectTreeNode)value);
        if (icon != null) {
          setIcon(icon);
        }
        setTextAlign(myAlignment);
      }
    }
  }
}
