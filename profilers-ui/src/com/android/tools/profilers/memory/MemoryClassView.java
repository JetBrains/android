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
import com.android.tools.profilers.ProfilerMode;
import com.android.tools.profilers.common.CodeLocation;
import com.android.tools.profilers.memory.adapters.ClassObject;
import com.android.tools.profilers.memory.adapters.HeapObject;
import com.android.tools.profilers.memory.adapters.HeapObject.ClassAttribute;
import com.android.tools.profilers.memory.adapters.NamespaceObject;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.android.tools.profilers.memory.MemoryProfilerConfiguration.ClassGrouping.GROUP_BY_PACKAGE;

final class MemoryClassView extends AspectObserver {
  private static final int LABEL_COLUMN_WIDTH = 800;
  private static final int DEFAULT_COLUMN_WIDTH = 80;

  @NotNull private final MemoryProfilerStage myStage;

  @NotNull private final IdeProfilerComponents myIdeProfilerComponents;

  @NotNull private final Map<ClassAttribute, AttributeColumn> myAttributeColumns = new HashMap<>();

  @Nullable private HeapObject myHeapObject = null;

  @Nullable private ClassObject myClassObject = null;

  @NotNull private JPanel myPanel = new JPanel(new BorderLayout());

  @Nullable private JComponent myColumnTree;

  @Nullable private JTree myTree;

  @Nullable private DefaultTreeModel myTreeModel;

  @Nullable private MemoryObjectTreeNode<NamespaceObject> myTreeRoot;

  public MemoryClassView(@NotNull MemoryProfilerStage stage, @NotNull IdeProfilerComponents ideProfilerComponents) {
    myStage = stage;
    myIdeProfilerComponents = ideProfilerComponents;

    myStage.getAspect().addDependency(this)
      .onChange(MemoryProfilerAspect.CURRENT_HEAP, this::refreshHeap)
      .onChange(MemoryProfilerAspect.CURRENT_CLASS, this::refreshClass)
      .onChange(MemoryProfilerAspect.CLASS_GROUPING, this::refreshGrouping);

    myAttributeColumns.put(
      ClassAttribute.LABEL,
      new AttributeColumn(
        "Class Name",
        () -> new DetailColumnRenderer(
          value -> {
            if (value.getAdapter() instanceof ClassObject && myStage.getConfiguration().getClassGrouping() == GROUP_BY_PACKAGE) {
              return ((ClassObject)value.getAdapter()).getClassName();
            }
            else {
              return ((NamespaceObject)value.getAdapter()).getName();
            }
          },
          value -> value.getAdapter() instanceof ClassObject ? PlatformIcons.CLASS_ICON : PlatformIcons.PACKAGE_ICON,
          SwingConstants.LEFT),
        SwingConstants.LEFT,
        LABEL_COLUMN_WIDTH,
        SortOrder.ASCENDING,
        createTreeNodeComparator(Comparator.comparing(NamespaceObject::getName),
                                 Comparator.comparing(ClassObject::getClassName))));
    myAttributeColumns.put(
      ClassAttribute.CHILDREN_COUNT,
      new AttributeColumn(
        "Count",
        () -> new DetailColumnRenderer(
          value -> value.getAdapter() instanceof ClassObject && ((ClassObject)value.getAdapter()).getChildrenCount() >= 0 ? Integer
            .toString(((ClassObject)value.getAdapter()).getChildrenCount()) : "",
          value -> null,
          SwingConstants.RIGHT),
        SwingConstants.RIGHT,
        DEFAULT_COLUMN_WIDTH,
        SortOrder.UNSORTED,
        createTreeNodeComparator(Comparator.comparingInt(ClassObject::getChildrenCount))));
    myAttributeColumns.put(
      ClassAttribute.ELEMENT_SIZE,
      new AttributeColumn(
        "Size",
        () -> new DetailColumnRenderer(
          value -> value.getAdapter() instanceof ClassObject && ((ClassObject)value.getAdapter()).getElementSize() >= 0 ? Integer
            .toString(((ClassObject)value.getAdapter()).getElementSize()) : "", value -> null, SwingConstants.RIGHT),
        SwingConstants.RIGHT,
        DEFAULT_COLUMN_WIDTH,
        SortOrder.UNSORTED,
        createTreeNodeComparator(Comparator.comparingInt(ClassObject::getElementSize))));
    myAttributeColumns.put(
      ClassAttribute.SHALLOW_SIZE,
      new AttributeColumn(
        "Shallow Size",
        () -> new DetailColumnRenderer(
          value -> value.getAdapter() instanceof ClassObject && ((ClassObject)value.getAdapter()).getShallowSize() >= 0 ? Integer
            .toString(((ClassObject)value.getAdapter()).getShallowSize()) : "",
          value -> null, SwingConstants.RIGHT),
        SwingConstants.RIGHT,
        DEFAULT_COLUMN_WIDTH,
        SortOrder.UNSORTED,
        createTreeNodeComparator(Comparator.comparingInt(ClassObject::getShallowSize))));
    myAttributeColumns.put(
      ClassAttribute.RETAINED_SIZE,
      new AttributeColumn(
        "Retained Size",
        () -> new DetailColumnRenderer(
          value -> value.getAdapter() instanceof ClassObject && ((ClassObject)value.getAdapter()).getRetainedSize() >= 0 ? Long
            .toString(((ClassObject)value.getAdapter()).getRetainedSize()) : "", value -> null, SwingConstants.RIGHT),
        SwingConstants.RIGHT,
        DEFAULT_COLUMN_WIDTH,
        SortOrder.UNSORTED,
        createTreeNodeComparator(Comparator.comparingLong(ClassObject::getRetainedSize))));
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

    myTreeRoot = new MemoryObjectTreeNode<>(new NamespaceObject("-invalid-"));
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
      MemoryObjectTreeNode packageNode = (MemoryObjectTreeNode)path.getLastPathComponent();
      assert packageNode.getAdapter() instanceof NamespaceObject;
      if (packageNode.getAdapter() instanceof ClassObject) {
        myStage.selectClass((ClassObject)packageNode.getAdapter());
      }
    });
    myIdeProfilerComponents.installNavigationContextMenu(myTree, () -> {
      TreePath selection = myTree.getSelectionPath();
      if (selection == null || !(selection.getLastPathComponent() instanceof MemoryObjectTreeNode)) {
        return null;
      }

      if (((MemoryObjectTreeNode)selection.getLastPathComponent()).getAdapter() instanceof ClassObject) {
        ClassObject classObject = (ClassObject)((MemoryObjectTreeNode)selection.getLastPathComponent()).getAdapter();
        return new CodeLocation(classObject.getName());
      }
      return null;
    }, () -> myStage.setProfilerMode(ProfilerMode.NORMAL));

    assert myHeapObject != null;
    List<HeapObject.ClassAttribute> attributes = myHeapObject.getClassAttributes();
    ColumnTreeBuilder builder = new ColumnTreeBuilder(myTree);
    for (ClassAttribute attribute : attributes) {
      builder.addColumn(myAttributeColumns.get(attribute).getBuilder());
    }
    builder.setTreeSorter((Comparator<MemoryObjectTreeNode<NamespaceObject>> comparator, SortOrder sortOrder) -> {
      myTreeRoot.sort(comparator);
      myTreeModel.nodeStructureChanged(myTreeRoot);
    });
    builder.setBackground(ProfilerColors.MONITOR_BACKGROUND);
    myColumnTree = builder.build();
    myPanel.add(myColumnTree, BorderLayout.CENTER);
  }

  private void refreshGrouping() {
    assert myTreeRoot != null && myTreeModel != null && myTree != null;
    if (myHeapObject == null) {
      return;
    }

    TreePath oldSelectionPath = myTree.getSelectionPath();
    Object lastSelected = oldSelectionPath == null ? null : oldSelectionPath.getLastPathComponent();
    //noinspection unchecked
    ClassObject lastSelectedClassObject = lastSelected instanceof MemoryObjectTreeNode &&
                                          ((MemoryObjectTreeNode)lastSelected).getAdapter() instanceof ClassObject
                                          ? ((MemoryObjectTreeNode<ClassObject>)lastSelected).getAdapter()
                                          : null;
    myTreeRoot.removeAll();

    PackageClassificationIndex rootIndex = null;
    switch (myStage.getConfiguration().getClassGrouping()) {
      case NO_GROUPING:
        for (ClassObject classObject : myHeapObject.getClasses()) {
          myTreeRoot.add(new MemoryObjectTreeNode<>(classObject));
        }
        break;
      case GROUP_BY_PACKAGE:
        rootIndex = createPackageView();
        break;
      default:
        throw new RuntimeException("Unimplemented grouping!");
    }

    myTreeModel.nodeChanged(myTreeRoot);
    myTreeModel.reload();

    MemoryObjectTreeNode<NamespaceObject> objectToSelect = null;
    if (lastSelectedClassObject != null) {
      // Find the path to the last selected object before the grouping changed.
      switch (myStage.getConfiguration().getClassGrouping()) {
        case NO_GROUPING:
          for (MemoryObjectTreeNode<NamespaceObject> child : myTreeRoot.getChildren()) {
            if (child.getAdapter() == lastSelectedClassObject) {
              objectToSelect = child;
              break;
            }
          }
          break;
        case GROUP_BY_PACKAGE:
          assert rootIndex != null;
          String[] splitPackages = lastSelectedClassObject.getSplitPackageName();
          PackageClassificationIndex currentIndex = rootIndex;
          for (String packageName : splitPackages) {
            if (!currentIndex.myChildPackages.containsKey(packageName)) {
              break;
            }
            currentIndex = currentIndex.myChildPackages.get(packageName);
          }
          List<MemoryObjectTreeNode<NamespaceObject>> filteredClasses = currentIndex.myPackageNode.getChildren().stream()
            .filter((packageNode) -> packageNode.getAdapter() instanceof ClassObject && packageNode.getAdapter() == lastSelectedClassObject)
            .collect(Collectors.toList());
          if (filteredClasses.size() > 0) {
            objectToSelect = filteredClasses.get(0);
          }
          break;
      }
    }

    if (myStage.getConfiguration().getClassGrouping() == GROUP_BY_PACKAGE) {
      collapsePackageNodes();
    }

    // Reselect the last selected object prior to the grouping change, if it's valid.
    if (objectToSelect != null) {
      assert myTree != null;
      TreePath pathToRoot = new TreePath(objectToSelect.getPathToRoot().toArray());
      myTree.setSelectionPath(pathToRoot);
      myTree.scrollPathToVisible(pathToRoot);
    }
  }

  @NotNull
  private PackageClassificationIndex createPackageView() {
    assert myTreeRoot != null;
    assert myHeapObject != null;

    PackageClassificationIndex rootIndex = new PackageClassificationIndex(myTreeRoot);

    // First, iteratively classify all ClassObjects into packages.
    for (ClassObject classObject : myHeapObject.getClasses()) {
      String[] splitPackages = classObject.getSplitPackageName();
      PackageClassificationIndex currentIndex = rootIndex;
      for (String packageName : splitPackages) {
        if (!currentIndex.myChildPackages.containsKey(packageName)) {
          PackageClassificationIndex nextIndex = new PackageClassificationIndex(packageName);
          currentIndex.myChildPackages.put(packageName, nextIndex);
          currentIndex.myPackageNode.add(nextIndex.myPackageNode);
          currentIndex = nextIndex;
        }
        else {
          currentIndex = currentIndex.myChildPackages.get(packageName);
        }
      }
      currentIndex.myPackageNode.add(new MemoryObjectTreeNode<>(classObject));
    }

    return rootIndex;
  }

  private void collapsePackageNodes() {
    assert myTreeRoot != null;
    List<MemoryObjectTreeNode<NamespaceObject>> children = new ArrayList<>(myTreeRoot.getChildren());
    myTreeRoot.removeAll();
    children.forEach(child -> myTreeRoot.add(collapsePackageNodesRecursively(child)));
  }

  @SuppressWarnings("MethodMayBeStatic")
  @NotNull
  private MemoryObjectTreeNode<NamespaceObject> collapsePackageNodesRecursively(@NotNull MemoryObjectTreeNode<NamespaceObject> currentNode) {
    List<MemoryObjectTreeNode<NamespaceObject>> children = new ArrayList<>(currentNode.getChildren());
    currentNode.removeAll();
    children.forEach(child -> currentNode.add(collapsePackageNodesRecursively(child)));

    children = currentNode.getChildren();
    if (children.size() != 1 || children.get(0).getAdapter() instanceof ClassObject) {
      return currentNode;
    }

    MemoryObjectTreeNode<NamespaceObject> onlyChild = children.get(0);
    List<MemoryObjectTreeNode<NamespaceObject>> childrenOfChild = new ArrayList<>(onlyChild.getChildren());
    onlyChild.removeAll();
    MemoryObjectTreeNode<NamespaceObject> collapsedNode = new MemoryObjectTreeNode<>(
      new NamespaceObject(String.join(".", currentNode.getAdapter().getName(), onlyChild.getAdapter().getName())));
    childrenOfChild.forEach(collapsedNode::add);
    return collapsedNode;
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

    refreshGrouping();
  }

  private void refreshClass() {
    if (myTreeRoot == null || myTreeModel == null || myTree == null) {
      return;
    }

    myClassObject = myStage.getSelectedClass();
    for (MemoryObjectTreeNode<NamespaceObject> node : myTreeRoot.getChildren()) {
      if (node.getAdapter() == myClassObject) {
        myTree.setSelectionPath(new TreePath(myTreeModel.getPathToRoot(node)));
        break;
      }
    }
  }

  /**
   * Creates a comparator function for the given {@link NamespaceObject}-specific and {@link ClassObject}-specific comparators.
   *
   * @param packageObjectComparator is a comparator for {@link NamespaceObject} objects, and not {@link ClassObject}
   * @return a {@link Comparator} that order all non-{@link ClassObject}s before {@link ClassObject}s, and orders according to the given
   * two params when the base class is the same
   */
  @VisibleForTesting
  static Comparator<MemoryObjectTreeNode> createTreeNodeComparator(@NotNull Comparator<NamespaceObject> packageObjectComparator,
                                                                   @NotNull Comparator<ClassObject> classObjectComparator) {
    return (o1, o2) -> {
      int compareResult;
      NamespaceObject firstArg = (NamespaceObject)o1.getAdapter();
      NamespaceObject secondArg = (NamespaceObject)o2.getAdapter();
      if (firstArg instanceof ClassObject && secondArg instanceof ClassObject) {
        compareResult = classObjectComparator.compare((ClassObject)firstArg, (ClassObject)secondArg);
      }
      else if (firstArg instanceof ClassObject) {
        compareResult = 1;
      }
      else if (secondArg instanceof ClassObject) {
        compareResult = -1;
      }
      else {
        compareResult = packageObjectComparator.compare(firstArg, secondArg);
      }
      return compareResult;
    };
  }

  /**
   * Convenience method for {@link #createTreeNodeComparator(Comparator, Comparator)}.
   */
  @VisibleForTesting
  static Comparator<MemoryObjectTreeNode> createTreeNodeComparator(@NotNull Comparator<ClassObject> classObjectComparator) {
    return createTreeNodeComparator(Comparator.comparing(NamespaceObject::getName), classObjectComparator);
  }

  /**
   * A simple class that emulates a tree structure, but contains additional tracking data to allow for node lookup by name via a hashmap.
   */
  private static class PackageClassificationIndex {
    @NotNull
    private final MemoryObjectTreeNode<NamespaceObject> myPackageNode;

    @NotNull
    private final Map<String, PackageClassificationIndex> myChildPackages = new HashMap<>();

    private PackageClassificationIndex(@NotNull String packageName) {
      myPackageNode = new MemoryObjectTreeNode<>(new NamespaceObject(packageName));
    }

    private PackageClassificationIndex(@NotNull MemoryObjectTreeNode<NamespaceObject> packageNode) {
      myPackageNode = packageNode;
    }
  }
}
