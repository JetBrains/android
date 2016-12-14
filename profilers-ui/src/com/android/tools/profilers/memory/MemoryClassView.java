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
import com.android.tools.profilers.memory.adapters.ClassObject;
import com.android.tools.profilers.memory.adapters.HeapObject;
import com.android.tools.profilers.memory.adapters.HeapObject.ClassAttribute;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
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

  @Nullable private HeapObject myHeapObject = null;

  @Nullable private ClassObject myClassObject = null;

  @NotNull private JPanel myPanel = new JPanel(new BorderLayout());

  @Nullable private JComponent myColumnTree;

  @Nullable private JTree myTree;

  @Nullable private DefaultTreeModel myTreeModel;

  @Nullable private MemoryObjectTreeNode<ClassObject> myTreeRoot;

  public MemoryClassView(@NotNull MemoryProfilerStage stage) {
    myStage = stage;

    myStage.getAspect().addDependency()
      .setExecutor(ApplicationManager.getApplication(), Application::invokeLater)
      .onChange(MemoryProfilerAspect.CURRENT_HEAP, this::refreshHeap)
      .onChange(MemoryProfilerAspect.CURRENT_CLASS, this::refreshClass);

    myAttributeColumns.put(
      ClassAttribute.LABEL,
      new AttributeColumn(
        "Class Name",
        () -> new DetailColumnRenderer(value -> ((ClassObject)value.getAdapter()).getName(),
                                       value -> PlatformIcons.CLASS_ICON, SwingConstants.LEFT),
        SwingConstants.LEFT,
        LABEL_COLUMN_WIDTH,
        SortOrder.ASCENDING,
        (o1, o2) -> ((ClassObject)o1.getAdapter()).getName().compareTo(((ClassObject)o2.getAdapter()).getName())));
    myAttributeColumns.put(
      ClassAttribute.CHILDREN_COUNT,
      new AttributeColumn(
        "Count",
        () -> new DetailColumnRenderer(
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
        () -> new DetailColumnRenderer(
          value -> Integer.toString(((ClassObject)value.getAdapter()).getElementSize()), value -> null, SwingConstants.RIGHT),
        SwingConstants.RIGHT,
        DEFAULT_COLUMN_WIDTH,
        SortOrder.UNSORTED,
        (o1, o2) -> ((ClassObject)o1.getAdapter()).getElementSize() - ((ClassObject)o2.getAdapter()).getElementSize()));
    myAttributeColumns.put(
      ClassAttribute.SHALLOW_SIZE,
      new AttributeColumn(
        "Shallow Size",
        () -> new DetailColumnRenderer(
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
        () -> new DetailColumnRenderer(value -> Long.toString(((ClassObject)value.getAdapter()).getRetainedSize()),
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

  @NotNull
  JComponent getComponent() {
    return myPanel;
  }

  @VisibleForTesting
  @Nullable
  JTree getTree() {
    return myTree;
  }

  /**
   * Must manually remove from parent container!
   */
  private void reset() {
    myHeapObject = null;
    myClassObject = null;
    myColumnTree = null;
    myTree = null;
    myTreeRoot = null;
    myTreeModel = null;
    myPanel.removeAll();
    myStage.selectClass(null);
  }

  private void initializeTree() {
    assert myColumnTree == null && myTreeModel == null && myTreeRoot == null && myTree == null;

    //noinspection Convert2Lambda
    myTreeRoot = new MemoryObjectTreeNode<>(new ClassObject() {
      @NotNull
      @Override
      public List<InstanceAttribute> getInstanceAttributes() {
        return Collections.emptyList();
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
      MemoryObjectTreeNode classObject = (MemoryObjectTreeNode)path.getLastPathComponent();
      assert classObject.getAdapter() instanceof ClassObject;
      myStage.selectClass((ClassObject)classObject.getAdapter());
    });

    assert myHeapObject != null;
    List<HeapObject.ClassAttribute> attributes = myHeapObject.getClassAttributes();
    ColumnTreeBuilder builder = new ColumnTreeBuilder(myTree);
    for (ClassAttribute attribute : attributes) {
      builder.addColumn(myAttributeColumns.get(attribute).getBuilder());
    }
    builder.setTreeSorter((Comparator<MemoryObjectTreeNode<ClassObject>> comparator, SortOrder sortOrder) -> {
      myTreeRoot.sort(comparator);
      myTreeModel.nodeStructureChanged(myTreeRoot);
    });
    builder.setBackground(ProfilerColors.MONITOR_BACKGROUND);
    myColumnTree = builder.build();
    myPanel.add(myColumnTree, BorderLayout.CENTER);
  }

  private void populateTreeContents() {
    assert myHeapObject != null && myTreeRoot != null && myTreeModel != null;
    myTreeRoot.removeAll();
    for (ClassObject classObject : myHeapObject.getClasses()) {
      // TODO handle package view
      myTreeRoot.add(new MemoryObjectTreeNode<>(classObject));
    }
    myTreeModel.nodeChanged(myTreeRoot);
    myTreeModel.reload();
  }

  private void refreshHeap() {
    HeapObject heapObject = myStage.getSelectedHeap();
    if (heapObject == myHeapObject) {
      return;
    }

    if (heapObject == null) {
      reset();
      return;
    }

    if (myHeapObject == null) {
      myHeapObject = heapObject;
      initializeTree();
    }
    else {
      myHeapObject = heapObject;
    }

    populateTreeContents();
  }

  private void refreshClass() {
    if (myTreeRoot == null || myTreeModel == null || myTree == null) {
      return;
    }

    myClassObject = myStage.getSelectedClass();
    for (MemoryObjectTreeNode<ClassObject> node : myTreeRoot.getChildren()) {
      if (node.getAdapter() == myClassObject) {
        myTree.setSelectionPath(new TreePath(myTreeModel.getPathToRoot(node)));
        break;
      }
    }
  }
}
