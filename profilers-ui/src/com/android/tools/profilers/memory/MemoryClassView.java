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
import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.memory.MemoryProfilerStageView.AttributeColumn;
import com.android.tools.profilers.memory.adapters.CaptureObject;
import com.android.tools.profilers.memory.adapters.ClassObject;
import com.android.tools.profilers.memory.adapters.HeapObject;
import com.android.tools.profilers.memory.adapters.HeapObject.ClassAttribute;
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
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

final class MemoryClassView {
  private static final int LABEL_COLUMN_WIDTH = 800;
  private static final int DEFAULT_COLUMN_WIDTH = 80;

  @NotNull private final MemoryProfilerStage myStage;

  @NotNull private final Map<ClassAttribute, AttributeColumn> myAttributeColumns = new HashMap<>();

  @Nullable private JComponent myClassesTree;

  @Nullable private DefaultTreeModel myClassesTreeModel;

  @Nullable private MemoryObjectTreeNode<ClassObject> myClassesTreeRoot;

  @Nullable private CaptureObject myCaptureObject;

  public MemoryClassView(@NotNull MemoryProfilerStage stage) {
    myStage = stage;

    myAttributeColumns.put(
      ClassAttribute.LABEL,
      new AttributeColumn(
        "Class Name",
        () -> new MemoryProfilerStageView.DetailColumnRenderer(value -> ((ClassObject)value.getAdapter()).getName(),
                                                               value -> PlatformIcons.CLASS_ICON, SwingConstants.LEFT),
        SwingConstants.LEFT,
        LABEL_COLUMN_WIDTH,
        SortOrder.ASCENDING,
        (o1, o2) -> ((ClassObject)o1.getAdapter()).getName().compareTo(((ClassObject)o2.getAdapter()).getName())));
    myAttributeColumns.put(
      ClassAttribute.CHILDREN_COUNT,
      new AttributeColumn(
        "Count",
        () -> new MemoryProfilerStageView.DetailColumnRenderer(
          value -> Integer.toString(((ClassObject)value.getAdapter()).getChildrenCount()),
          value -> null,
          SwingConstants.RIGHT),
        SwingConstants.RIGHT,
        DEFAULT_COLUMN_WIDTH,
        SortOrder.UNSORTED,
        (o1, o2) -> ((ClassObject)o1.getAdapter()).getChildrenCount() - ((ClassObject)o2.getAdapter()).getChildrenCount()));
    myAttributeColumns.put(
      ClassAttribute.ELEMENT_SIZE,
      new AttributeColumn(
        "Size",
        () -> new MemoryProfilerStageView.DetailColumnRenderer(
          value -> Integer.toString(((ClassObject)value.getAdapter()).getElementSize()), value -> null, SwingConstants.RIGHT),
        SwingConstants.RIGHT,
        DEFAULT_COLUMN_WIDTH,
        SortOrder.UNSORTED,
        (o1, o2) -> ((ClassObject)o1.getAdapter()).getElementSize() - ((ClassObject)o2.getAdapter()).getElementSize()));
    myAttributeColumns.put(
      ClassAttribute.SHALLOW_SIZE,
      new AttributeColumn(
        "Shallow Size",
        () -> new MemoryProfilerStageView.DetailColumnRenderer(
          value -> Integer.toString(((ClassObject)value.getAdapter()).getShallowSize()),
          value -> null, SwingConstants.RIGHT),
        SwingConstants.RIGHT,
        DEFAULT_COLUMN_WIDTH,
        SortOrder.UNSORTED,
        (o1, o2) -> ((ClassObject)o1.getAdapter()).getShallowSize() - ((ClassObject)o2.getAdapter()).getShallowSize()));
    myAttributeColumns.put(
      ClassAttribute.RETAINED_SIZE,
      new AttributeColumn(
        "Retained Size",
        () -> new MemoryProfilerStageView.DetailColumnRenderer(value -> Long.toString(((ClassObject)value.getAdapter()).getRetainedSize()),
                                                               value -> null,
                                                               SwingConstants.RIGHT),
        SwingConstants.RIGHT,
        DEFAULT_COLUMN_WIDTH,
        SortOrder.UNSORTED,
        (o1, o2) -> {
          long diff = ((ClassObject)o1.getAdapter()).getRetainedSize() - ((ClassObject)o2.getAdapter()).getRetainedSize();
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
    myCaptureObject = null;
  }

  @Nullable
  public CaptureObject getCurrentCapture() {
    return myCaptureObject;
  }

  @Nullable
  public JComponent buildComponent(@NotNull CaptureObject captureObject) {
    myCaptureObject = captureObject;

    JPanel classesPanel = new JPanel(new BorderLayout());
    JPanel headingPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
    headingPanel.add(new JLabel(captureObject.toString()));

    JToolBar toolBar = new JToolBar();
    toolBar.setFloatable(false);
    List<HeapObject> heaps = captureObject.getHeaps();

    ComboBoxModel<HeapObject> comboBoxModel = new DefaultComboBoxModel<>(heaps.toArray(new HeapObject[heaps.size()]));
    ComboBox<HeapObject> comboBox = new ComboBox<>(comboBoxModel);
    comboBox.addActionListener(e -> {
      // TODO abstract out selection path so we don't need to special case
      Object item = comboBox.getSelectedItem();
      if (item != null && item instanceof HeapObject) {
        HeapObject heap = (HeapObject)item;
        buildTree(classesPanel, heap);
        myStage.selectHeap(heap);
      }
    });
    toolBar.add(comboBox);
    headingPanel.add(toolBar);

    classesPanel.add(headingPanel, BorderLayout.PAGE_START);
    boolean selected = false;
    // TODO provide a default selection in the model API?
    for (HeapObject heap : heaps) {
      if (heap.getHeapName().equals("app")) {
        comboBox.setSelectedItem(heap);
        myStage.selectHeap(heap);
        selected = true;
        break;
      }
    }
    if (!selected) {
      HeapObject heap = heaps.get(0);
      comboBox.setSelectedItem(heap);
      myStage.selectHeap(heap);
    }

    return classesPanel;
  }

  private void ensureTreeInitialized(@NotNull JPanel parentPanel, @NotNull HeapObject heapObject) {
    if (myClassesTree != null) {
      assert myClassesTreeModel != null && myClassesTreeRoot != null;
      return;
    }

    myClassesTreeRoot = new MemoryObjectTreeNode<>(new ClassObject() {
      @NotNull
      @Override
      public List<InstanceAttribute> getInstanceAttributes() {
        return Collections.emptyList();
      }
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
      MemoryObjectTreeNode classObject = (MemoryObjectTreeNode)path.getLastPathComponent();
      assert classObject.getAdapter() instanceof ClassObject;
      myStage.selectClass((ClassObject)classObject.getAdapter());
    });

    List<HeapObject.ClassAttribute> attributes = heapObject.getClassAttributes();
    ColumnTreeBuilder builder = new ColumnTreeBuilder(tree);
    for (ClassAttribute attribute : attributes) {
      builder.addColumn(myAttributeColumns.get(attribute).getBuilder());
    }
    builder.setTreeSorter((Comparator<MemoryObjectTreeNode<ClassObject>> comparator, SortOrder sortOrder) -> {
      myClassesTreeRoot.sort(comparator);
      myClassesTreeModel.nodeStructureChanged(myClassesTreeRoot);
    });
    builder.setBackground(ProfilerColors.MONITOR_BACKGROUND);
    myClassesTree = builder.build();
    parentPanel.add(myClassesTree, BorderLayout.CENTER);
  }

  private void buildTree(@NotNull JPanel parentPanel, @NotNull HeapObject heapObject) {
    ensureTreeInitialized(parentPanel, heapObject);
    assert myClassesTreeRoot != null && myClassesTreeModel != null;
    myClassesTreeRoot.removeAll();
    for (ClassObject classObject : heapObject.getClasses()) {
      // TODO handle package view
      myClassesTreeRoot.add(new MemoryObjectTreeNode<>(classObject));
    }
    myClassesTreeModel.nodeChanged(myClassesTreeRoot);
    myClassesTreeModel.reload();
  }
}
