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
import com.android.tools.profilers.memory.adapters.ClassObject.ClassAttribute;
import com.android.tools.profilers.memory.adapters.HeapObject;
import com.android.tools.profilers.memory.adapters.NamespaceObject;
import com.android.tools.profilers.memory.adapters.PackageObject;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
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

import static com.android.tools.profilers.memory.MemoryProfilerConfiguration.ClassGrouping.ARRANGE_BY_CLASS;
import static com.android.tools.profilers.memory.MemoryProfilerConfiguration.ClassGrouping.ARRANGE_BY_PACKAGE;

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
        "Class Name", this::getNameColumnRenderer, SwingConstants.LEFT, LABEL_COLUMN_WIDTH, SortOrder.ASCENDING,
        createTreeNodeComparator(Comparator.comparing(NamespaceObject::getName), Comparator.comparing(ClassObject::getClassName))));
    myAttributeColumns.put(
      ClassAttribute.TOTAL_COUNT,
      new AttributeColumn(
        "Total Count",
        () -> new SimpleColumnRenderer(
          value -> ((NamespaceObject)value.getAdapter()).getTotalCount() >= 0 ?
                   Integer.toString(((NamespaceObject)value.getAdapter()).getTotalCount()) :
                   "",
          value -> null,
          SwingConstants.RIGHT),
        SwingConstants.RIGHT,
        DEFAULT_COLUMN_WIDTH,
        SortOrder.UNSORTED,
        createTreeNodeComparator(Comparator.comparingInt(NamespaceObject::getTotalCount),
                                 Comparator.comparingInt(NamespaceObject::getTotalCount))));
    myAttributeColumns.put(
      ClassAttribute.HEAP_COUNT,
      new AttributeColumn(
        "Heap Count",
        () -> new SimpleColumnRenderer(
          value -> ((NamespaceObject)value.getAdapter()).getHeapCount() >= 0 ?
                   Integer.toString(((NamespaceObject)value.getAdapter()).getHeapCount()) :
                   "",
          value -> null,
          SwingConstants.RIGHT),
        SwingConstants.RIGHT,
        DEFAULT_COLUMN_WIDTH,
        SortOrder.UNSORTED,
        createTreeNodeComparator(Comparator.comparingInt(NamespaceObject::getHeapCount),
                                 Comparator.comparingInt(NamespaceObject::getHeapCount))));
    myAttributeColumns.put(
      ClassAttribute.INSTANCE_SIZE,
      new AttributeColumn(
        "Sizeof",
        () -> new SimpleColumnRenderer(
          value -> value.getAdapter() instanceof ClassObject && ((ClassObject)value.getAdapter()).getInstanceSize() >= 0 ? Integer
            .toString(((ClassObject)value.getAdapter()).getInstanceSize()) : "", value -> null, SwingConstants.RIGHT),
        SwingConstants.RIGHT,
        DEFAULT_COLUMN_WIDTH,
        SortOrder.UNSORTED,
        createTreeNodeComparator(Comparator.comparingLong(ClassObject::getInstanceSize))));
    myAttributeColumns.put(
      ClassAttribute.SHALLOW_SIZE,
      new AttributeColumn(
        "Shallow Size",
        () -> new SimpleColumnRenderer(
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
        () -> new SimpleColumnRenderer(
          value -> ((NamespaceObject)value.getAdapter()).getRetainedSize() >= 0 ?
                   Long.toString(((NamespaceObject)value.getAdapter()).getRetainedSize()) :
                   "",
          value -> null, SwingConstants.RIGHT),
        SwingConstants.RIGHT,
        DEFAULT_COLUMN_WIDTH,
        SortOrder.UNSORTED,
        createTreeNodeComparator(Comparator.comparingLong(NamespaceObject::getRetainedSize),
                                 Comparator.comparingLong(NamespaceObject::getRetainedSize))));
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

  @VisibleForTesting
  @Nullable
  JComponent getColumnTree() {
    return myColumnTree;
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
      MemoryObjectTreeNode namespaceNode = (MemoryObjectTreeNode)path.getLastPathComponent();
      assert namespaceNode.getAdapter() instanceof NamespaceObject;
      if (namespaceNode.getAdapter() instanceof ClassObject) {
        myStage.selectClass((ClassObject)namespaceNode.getAdapter());
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
    List<ClassAttribute> attributes = myHeapObject.getClassAttributes();
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
      case ARRANGE_BY_CLASS:
        for (ClassObject classObject : myHeapObject.getClasses()) {
          myTreeRoot.add(new MemoryObjectTreeNode<>(classObject));
        }
        break;
      case ARRANGE_BY_PACKAGE:
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
        case ARRANGE_BY_CLASS:
          for (MemoryObjectTreeNode<NamespaceObject> child : myTreeRoot.getChildren()) {
            if (child.getAdapter() == lastSelectedClassObject) {
              objectToSelect = child;
              break;
            }
          }
          break;
        case ARRANGE_BY_PACKAGE:
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

    if (myStage.getConfiguration().getClassGrouping() == ARRANGE_BY_PACKAGE) {
      collapsePackageNodesRecursively(myTreeRoot);
    }

    refreshTreeValues(myTreeRoot);

    myTreeModel.nodeStructureChanged(myTreeRoot);

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

  @SuppressWarnings("MethodMayBeStatic")
  @NotNull
  private MemoryObjectTreeNode<NamespaceObject> collapsePackageNodesRecursively(@NotNull MemoryObjectTreeNode<NamespaceObject> currentNode) {
    List<MemoryObjectTreeNode<NamespaceObject>> children = new ArrayList<>(currentNode.getChildren());
    currentNode.removeAll();
    children.forEach(child -> currentNode.add(collapsePackageNodesRecursively(child)));

    children = currentNode.getChildren();
    if (currentNode.getAdapter() instanceof PackageObject &&
        children.size() == 1 &&
        children.get(0).getAdapter() instanceof PackageObject) {
      MemoryObjectTreeNode<NamespaceObject> onlyChild = children.get(0);
      List<MemoryObjectTreeNode<NamespaceObject>> childrenOfChild = new ArrayList<>(onlyChild.getChildren());
      onlyChild.removeAll();
      MemoryObjectTreeNode<NamespaceObject> collapsedNode = new MemoryObjectTreeNode<>(
        new PackageObject(String.join(".", currentNode.getAdapter().getName(), onlyChild.getAdapter().getName())));
      childrenOfChild.forEach(collapsedNode::add);
      return collapsedNode;
    }

    return currentNode;
  }

  private void refreshTreeValues(@NotNull MemoryObjectTreeNode<NamespaceObject> currentNode) {
    if (currentNode.getAdapter() instanceof ClassObject) {
      return;
    }

    currentNode.getChildren().forEach(this::refreshTreeValues);

    if (currentNode.getAdapter() instanceof PackageObject) {
      currentNode.getChildren().forEach(child -> {
        PackageObject packageObject = (PackageObject)currentNode.getAdapter();
        packageObject.setTotalCount(packageObject.getTotalCount() + child.getAdapter().getTotalCount());
        packageObject.setHeapCount(packageObject.getHeapCount() + child.getAdapter().getHeapCount());
        packageObject.setRetainedSize(packageObject.getRetainedSize() + child.getAdapter().getRetainedSize());
      });
    }
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
      if (node.getAdapter().equals(myClassObject)) {
        TreePath path = new TreePath(myTreeModel.getPathToRoot(node));
        myTree.scrollPathToVisible(path);
        myTree.setSelectionPath(path);
        break;
      }
    }
  }

  @NotNull
  private ColoredTreeCellRenderer getNameColumnRenderer() {
    return new ColoredTreeCellRenderer() {
      @Override
      public void customizeCellRenderer(@NotNull JTree tree,
                                        Object value,
                                        boolean selected,
                                        boolean expanded,
                                        boolean leaf,
                                        int row,
                                        boolean hasFocus) {
        if (!(value instanceof MemoryObjectTreeNode)) {
          return;
        }

        MemoryObjectTreeNode node = (MemoryObjectTreeNode)value;
        if (node.getAdapter() instanceof ClassObject) {
          ClassObject classObject = (ClassObject)node.getAdapter();
          append(classObject.getClassName(), SimpleTextAttributes.REGULAR_ATTRIBUTES, classObject.getClassName());
          if (myStage.getConfiguration().getClassGrouping() == ARRANGE_BY_CLASS) {
            if (!classObject.getPackageName().isEmpty()) {
              String packageText = " (" + classObject.getPackageName() + ")";
              append(packageText, SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES, packageText);
            }
          }
        }
        else {
          append(((NamespaceObject)node.getAdapter()).getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES,
                 ((NamespaceObject)node.getAdapter()).getName());
        }

        Icon icon = node.getAdapter() instanceof ClassObject ? PlatformIcons.CLASS_ICON : PlatformIcons.PACKAGE_ICON;
        if (icon != null) {
          setIcon(icon);
        }
        setTextAlign(SwingConstants.LEFT);
      }
    };
  }

  /**
   * Creates a comparator function for the given {@link NamespaceObject}-specific and {@link ClassObject}-specific comparators.
   *
   * @param namespaceObjectComparator is a comparator for {@link NamespaceObject} objects, and not {@link ClassObject}
   * @return a {@link Comparator} that order all non-{@link ClassObject}s before {@link ClassObject}s, and orders according to the given
   * two params when the base class is the same
   */
  @VisibleForTesting
  static Comparator<MemoryObjectTreeNode> createTreeNodeComparator(@NotNull Comparator<NamespaceObject> namespaceObjectComparator,
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
        compareResult = namespaceObjectComparator.compare(firstArg, secondArg);
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
      myPackageNode = new MemoryObjectTreeNode<>(new PackageObject(packageName));
    }

    /**
     * Special case for root node.
     */
    private PackageClassificationIndex(@NotNull MemoryObjectTreeNode<NamespaceObject> rootNode) {
      myPackageNode = rootNode;
    }
  }
}
