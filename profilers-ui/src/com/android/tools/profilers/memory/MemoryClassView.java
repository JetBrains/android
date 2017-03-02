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
import com.android.tools.profilers.ProfilerIcons;
import com.android.tools.profilers.common.CodeLocation;
import com.android.tools.profilers.common.ThreadId;
import com.android.tools.profilers.memory.adapters.*;
import com.android.tools.profilers.memory.adapters.ClassObject.ClassAttribute;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.intellij.icons.AllIcons;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.*;
import java.util.List;
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
          value -> ((NamespaceObject)value.getAdapter()).getInstanceSize() >= 0 ?
                   Integer.toString(((NamespaceObject)value.getAdapter()).getInstanceSize()) :
                   "",
          value -> null, SwingConstants.RIGHT),
        SwingConstants.RIGHT,
        DEFAULT_COLUMN_WIDTH,
        SortOrder.UNSORTED,
        createTreeNodeComparator(Comparator.comparingLong(ClassObject::getInstanceSize))));
    myAttributeColumns.put(
      ClassAttribute.SHALLOW_SIZE,
      new AttributeColumn(
        "Shallow Size",
        () -> new SimpleColumnRenderer(
          value -> ((NamespaceObject)value.getAdapter()).getShallowSize() >= 0 ?
                   Integer.toString(((NamespaceObject)value.getAdapter()).getShallowSize()) :
                   "",
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

    myIdeProfilerComponents.installNavigationContextMenu(myTree, myStage.getStudioProfilers().getIdeServices().getCodeNavigator(), () -> {
      TreePath selection = myTree.getSelectionPath();
      if (selection == null || !(selection.getLastPathComponent() instanceof MemoryObjectTreeNode)) {
        return null;
      }

      if (((MemoryObjectTreeNode)selection.getLastPathComponent()).getAdapter() instanceof ClassObject) {
        ClassObject classObject = (ClassObject)((MemoryObjectTreeNode)selection.getLastPathComponent()).getAdapter();
        return new CodeLocation.Builder(classObject.getName()).build();
      }
      return null;
    });

    assert myHeapObject != null;
    List<ClassAttribute> attributes = myHeapObject.getClassAttributes();
    ColumnTreeBuilder builder = new ColumnTreeBuilder(myTree);
    ClassAttribute sortAttribute = Collections.max(attributes, Comparator.comparingInt(ClassAttribute::getWeight));
    for (ClassAttribute attribute : attributes) {
      ColumnTreeBuilder.ColumnBuilder columnBuilder = myAttributeColumns.get(attribute).getBuilder();
      if (sortAttribute == attribute) {
        columnBuilder.setInitialOrder(SortOrder.DESCENDING);
      }
      builder.addColumn(columnBuilder);
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

    ClassObject lastSelectedClassObject = myStage.getSelectedClass();
    if (lastSelectedClassObject instanceof ProxyClassObject) {
      lastSelectedClassObject = ((ProxyClassObject)lastSelectedClassObject).getClassObject();
    }
    myTreeRoot.removeAll();

    ClassificationIndex<String> rootIndex = null;
    switch (myStage.getConfiguration().getClassGrouping()) {
      case ARRANGE_BY_CLASS:
        for (ClassObject classObject : myHeapObject.getClasses()) {
          myTreeRoot.add(new MemoryObjectTreeNode<>(classObject));
        }
        break;
      case ARRANGE_BY_PACKAGE:
        rootIndex = formatPackageView(); // Keep a copy of the index as we'll need it later to find our previously selected ClassObject.
        break;
      case ARRANGE_BY_CALLSTACK:
        formatCallstackView();
        break;
      default:
        throw new RuntimeException("Unimplemented grouping!");
    }

    myTreeModel.nodeChanged(myTreeRoot);
    myTreeModel.reload();

    // Find the previously selected ClassObject node.
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
          objectToSelect = rootIndex.find(lastSelectedClassObject, Iterators.forArray(splitPackages));
          break;
        case ARRANGE_BY_CALLSTACK:
          // TODO if an instance is selected, then find the class as well?
          break;
      }
    }

    if (myStage.getConfiguration().getClassGrouping() == ARRANGE_BY_PACKAGE) {
      collapsePackageNodesRecursively(myTreeRoot);
    }

    myTreeModel.nodeStructureChanged(myTreeRoot);

    // Reselect the last selected object prior to the grouping change, if it's valid.
    if (objectToSelect != null) {
      assert myTree != null;
      TreePath pathToRoot = new TreePath(objectToSelect.getPathToRoot().toArray());
      myTree.setSelectionPath(pathToRoot);
      myTree.scrollPathToVisible(pathToRoot);
    }
    else {
      myStage.selectClass(null);
    }
  }

  @NotNull
  private ClassificationIndex<String> formatPackageView() {
    assert myTreeRoot != null;
    assert myHeapObject != null;

    ClassificationIndex<String> rootIndex =
      new ClassificationIndex<>(myTreeRoot, name -> new MemoryObjectTreeNode<>(new PackageObject(name)));

    for (ClassObject classObject : myHeapObject.getClasses()) {
      String[] splitPackages = classObject.getSplitPackageName();
      rootIndex.classify(
        new MemoryObjectTreeNode<>(classObject),
        namespaceObject -> namespaceObject.accumulateNamespaceObject(classObject),
        Iterators.forArray(splitPackages));
    }

    return rootIndex;
  }

  private void formatCallstackView() {
    assert myTreeRoot != null;
    assert myHeapObject != null;

    List<InstanceObject> instancesWithStack = new ArrayList<>();
    Set<ClassObject> stacklessClasses = new HashSet<>();
    for (ClassObject classObject : myHeapObject.getClasses()) {
      for (InstanceObject instanceObject : classObject.getInstances()) {
        assert instanceObject.getClassObject() != null;

        AllocationStack stack = instanceObject.getCallStack();
        if (stack != null && stack.getStackFramesCount() > 0) {
          instancesWithStack.add(instanceObject);
        }
        else {
          stacklessClasses.add(instanceObject.getClassObject());
        }
      }
    }

    Map<ThreadId, ClassificationIndex<CodeLocation>> threadedIndices = new HashMap<>();
    ClassificationIndex<CodeLocation> rootIndex =
      new ClassificationIndex<>(myTreeRoot, codeLocation -> new MemoryObjectTreeNode<>(new MethodObject(codeLocation)));
    for (InstanceObject instanceObject : instancesWithStack) {
      AllocationStack callStack = instanceObject.getCallStack();
      assert callStack != null;
      // TODO this is potentially super slow, so we might need to speed this up
      List<CodeLocation> stackFrames = Lists.reverse(callStack.getStackFramesList()).stream()
        .map((frame) -> new CodeLocation.Builder(frame.getClassName())
          .setFileName(frame.getFileName())
          .setMethodName(frame.getMethodName())
          .setLineNumber(frame.getLineNumber() - 1)
          .build())
        .collect(Collectors.toList());

      ThreadId threadId = instanceObject.getAllocationThreadId();

      assert instanceObject.getClassObject() != null;
      MemoryObjectTreeNode<NamespaceObject> targetNode =
        new MemoryObjectTreeNode<>(new ProxyClassObject(instanceObject.getClassObject(), instanceObject));

      if (ThreadId.INVALID_THREAD_ID == threadId) {
        rootIndex.classify(targetNode, namespaceObject -> namespaceObject.accumulateInstanceObject(instanceObject), stackFrames.iterator());
      }
      else {
        threadedIndices.computeIfAbsent(
          threadId,
          key -> new ClassificationIndex<>(
            new MemoryObjectTreeNode<>(new ThreadObject(threadId)),
            codeLocation -> new MemoryObjectTreeNode<>(new MethodObject(codeLocation)))
        ).classify(targetNode, namespaceObject -> namespaceObject.accumulateInstanceObject(instanceObject), stackFrames.iterator());
      }
    }

    for (ClassificationIndex<CodeLocation> classificationIndex : threadedIndices.values()) {
      myTreeRoot.add(classificationIndex.getTreeNode());
    }

    for (ClassObject classObject : stacklessClasses) {
      myTreeRoot.add(new MemoryObjectTreeNode<>(classObject));
    }
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
      collapsedNode.getAdapter().accumulateNamespaceObject(currentNode.getAdapter());
      childrenOfChild.forEach(collapsedNode::add);
      return collapsedNode;
    }

    return currentNode;
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
          setIcon(((ClassObject)node.getAdapter()).hasStackInfo() ? ProfilerIcons.CLASS_STACK : PlatformIcons.CLASS_ICON);
          ClassObject classObject;
          if (node.getAdapter() instanceof ClassObject) {
            classObject = (ClassObject)node.getAdapter();
          }
          else {
            classObject = ((ProxyClassObject)node.getAdapter()).getClassObject();
          }
          String className = classObject.getClassName();
          String packageName = classObject.getPackageName();
          append(className, SimpleTextAttributes.REGULAR_ATTRIBUTES, className);
          if (myStage.getConfiguration().getClassGrouping() == ARRANGE_BY_CLASS) {
            if (!packageName.isEmpty()) {
              String packageText = " (" + packageName + ")";
              append(packageText, SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES, packageText);
            }
          }
        }
        else if (node.getAdapter() instanceof PackageObject) {
          NamespaceObject adapter = (NamespaceObject)node.getAdapter();
          setIcon(adapter.hasStackInfo() ? ProfilerIcons.PACKAGE_STACK : PlatformIcons.PACKAGE_ICON);
          String name = adapter.getName();
          append(name, SimpleTextAttributes.REGULAR_ATTRIBUTES, name);
        }
        else if (node.getAdapter() instanceof MethodObject) {
          setIcon(PlatformIcons.METHOD_ICON);

          MethodObject methodObject = (MethodObject)node.getAdapter();
          String name = methodObject.getCodeLocation().getMethodName();
          int lineNumber = methodObject.getCodeLocation().getLineNumber();
          String className = methodObject.getCodeLocation().getClassName();

          if (name != null) {
            String nameAndLine = name + "()" + (lineNumber == CodeLocation.INVALID_LINE_NUMBER ? "" : ":" + lineNumber);
            append(nameAndLine, SimpleTextAttributes.REGULAR_ATTRIBUTES, nameAndLine);
          }
          else {
            name = "<unknown method>";
            append(name, SimpleTextAttributes.REGULAR_ATTRIBUTES, name);
          }

          if (className != null) {
            String classNameText = " (" + className + ")";
            append(classNameText, SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES, classNameText);
          }
        }
        else if (node.getAdapter() instanceof ThreadObject) {
          setIcon(AllIcons.Debugger.ThreadSuspended);
          String threadName = ((ThreadObject)node.getAdapter()).getName();
          append(threadName, SimpleTextAttributes.REGULAR_ATTRIBUTES, threadName);
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
}
