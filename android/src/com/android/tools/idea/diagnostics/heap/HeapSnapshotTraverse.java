/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.diagnostics.heap;

import static com.android.tools.idea.diagnostics.heap.HeapTraverseUtil.processMask;
import static com.android.tools.idea.diagnostics.heap.HeapTraverseUtil.sizeOf;
import static com.google.common.math.IntMath.isPowerOfTwo;

import com.google.common.collect.Sets;
import com.intellij.openapi.util.LowMemoryWatcher;
import com.intellij.testFramework.LeakHunter;
import com.intellij.util.ReflectionUtil;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class HeapSnapshotTraverse {

  private static final int MAX_HEAP_TREE_SIZE = 3 * (int)1e7;

  private volatile boolean myShouldAbortTraversal = false;
  @NotNull
  private final LowMemoryWatcher myWatcher;
  @NotNull
  private final Set<ClassLoader> myProcessedLoaders;
  @NotNull
  private final HeapTraverseChildProcessor myHeapTraverseChildProcessor;

  public HeapSnapshotTraverse() {
    this(new HeapTraverseChildProcessor());
  }

  public HeapSnapshotTraverse(@NotNull final HeapTraverseChildProcessor childProcessor) {
    myWatcher = LowMemoryWatcher.register(this::onLowMemorySignalReceived);
    myProcessedLoaders = Sets.newHashSet(LeakHunter.class.getClassLoader());
    myHeapTraverseChildProcessor = childProcessor;
  }

  private void onLowMemorySignalReceived() {
    myShouldAbortTraversal = true;
  }

  /**
   * The heap traversal algorithm is the following:
   *
   * In the process of traversal, we associate a number of masks with each object. These masks are stored in {@link HeapTraverseNode} and
   * show which components own the corresponding object(myOwnedByComponentMask), which components retain the object(myRetainedMask) etc.
   *
   * On the first pass along the heap we arrange objects in topological order (in terms of references). This is necessary so that during the
   * subsequent propagation of masks, we can be sure that all objects that refer to the object have already been processed and masks were
   * updated.
   *
   * On the second pass, we directly update the masks and pass them to the referring objects.
   *
   * @param maxDepth the maximum depth to which we will descend when traversing the object tree.
   * @param startRoots objects from which traversal is started.
   * @param stats holder for memory report
   */
  public ErrorCode walkObjects(int maxDepth,
                                      @NotNull final Collection<?> startRoots,
                                      @NotNull final HeapSnapshotStatistics stats) {
    try {
      final IntSet visited = new IntOpenHashSet(100);
      final Deque<WeakReference<?>> order = new ArrayDeque<>();
      final FieldCache fieldCache = new FieldCache();

      // collection of topological order
      for (Object root : startRoots) {
        if (root == null) continue;
        ErrorCode errorCode = depthFirstTraverseHeapObjects(root, maxDepth, visited, order, fieldCache);
        if (errorCode != ErrorCode.OK) {
          return errorCode;
        }
      }
      final Map<Integer, HeapTraverseNode> objectHashToTraverseNode = new HashMap<>();

      // iterate over objects and update masks
      while (!order.isEmpty()) {
        abortTraversalIfRequested();
        final Object currentObject = order.pop().get();
        if (currentObject == null) {
          continue;
        }
        int currentObjectHashCode = System.identityHashCode(currentObject);
        visited.remove(currentObjectHashCode);

        HeapTraverseNode node = objectHashToTraverseNode.get(currentObjectHashCode);
        objectHashToTraverseNode.remove(currentObjectHashCode);

        // Check whether the current object is a root of one of the components
        int componentId = stats.getComponentsSet().getComponentId(currentObject);
        long currentObjectSize = sizeOf(currentObject, fieldCache);
        stats.addObjectToTotal(currentObjectSize);

        if (node == null) {
          node = new HeapTraverseNode();
        }
        // if it's a root of a component
        if (componentId != HeapSnapshotStatistics.COMPONENT_NOT_FOUND) {
          node.myReachableFromComponentMask |= (1 << componentId);
          node.myRetainedMask |= (1 << componentId);
          node.myOwnedByComponentMask = (1 << componentId);
          node.myOwnershipWeight = HeapTraverseNode.RefWeight.DEFAULT;
        }

        // If current object is retained by any components - propagate their stats.
        processMask(node.myRetainedMask,
                    (index) -> stats.addRetainedObjectSizeToComponent(index, currentObjectSize));
        if (node.myOwnedByComponentMask == 0) {
          stats.addNonComponentObject(currentObjectSize);
        }
        else if (isPowerOfTwo(node.myOwnedByComponentMask)) {
          // if only owned by one component
          processMask(node.myOwnedByComponentMask,
                      (index) -> stats.addOwnedObjectSizeToComponent(index, currentObjectSize));
        }
        else {
          // if owned by multiple components -> add to shared
          stats.addObjectSizeToSharedComponent(node.myOwnedByComponentMask, currentObjectSize);
        }

        // propagate to referred objects
        propagateComponentMask(currentObject, node, visited, objectHashToTraverseNode, fieldCache);
      }
    }
    catch (HeapSnapshotTraverseException exception) {
      return exception.getErrorCode();
    }
    finally {
      myWatcher.stop();
    }
    return ErrorCode.OK;
  }

  private void abortTraversalIfRequested() throws HeapSnapshotTraverseException {
    if (myShouldAbortTraversal) {
      throw new HeapSnapshotTraverseException(ErrorCode.LOW_MEMORY);
    }
  }

  private void addToStack(@NotNull final Node node,
                                 int maxDepth,
                                 @Nullable final Object value,
                                 @NotNull final IntSet visited,
                                 @NotNull final Deque<Node> stack) {
    if (value == null) {
      return;
    }
    if (node.getDepth() + 1 > maxDepth) {
      return;
    }
    if (HeapTraverseUtil.isPrimitive(value.getClass())) {
      return;
    }
    int valueHashCode = System.identityHashCode(value);

    if (visited.contains(valueHashCode)) {
      return;
    }
    visited.add(valueHashCode);
    stack.push(new Node(value, node.getDepth() + 1));

    // check that ClassLoader that loaded the object class was already processed. In case if it wasn't - add it and all the corresponding
    // classes
    ClassLoader loader = value.getClass().getClassLoader();
    if (loader != null && !myProcessedLoaders.contains(loader)) {
      myProcessedLoaders.add(loader);
      Vector<?> allLoadedClasses = ReflectionUtil.getField(loader.getClass(), loader, Vector.class, "classes");
      for (Object aClass : allLoadedClasses) {
        valueHashCode = System.identityHashCode(aClass);
        if (!visited.contains(valueHashCode)) {
          visited.add(valueHashCode);
          stack.push(new Node(aClass, 0));
        }
      }
    }
  }

  private void addStronglyReferencedChildrenToStack(@NotNull final Node node,
                                                           int maxDepth,
                                                           @NotNull final IntSet visited,
                                                           @NotNull final Deque<Node> stack,
                                                           @NotNull final FieldCache fieldCache) {
    if (node.myDepth >= maxDepth) {
      return;
    }
    myHeapTraverseChildProcessor.processChildObjects(node.getObject(),
                        (Object value, HeapTraverseNode.RefWeight weight) -> addToStack(node, maxDepth, value,
                                                                                        visited, stack),
                        fieldCache);
  }

  private ErrorCode depthFirstTraverseHeapObjects(@NotNull final Object root,
                                                  int maxDepth,
                                                  @NotNull final IntSet visited,
                                                  final Deque<WeakReference<?>> order,
                                                  @NotNull final FieldCache fieldCache) throws HeapSnapshotTraverseException {
    Deque<Node> stack = new ArrayDeque<>(1_000_000);
    Node rootNode = new Node(root, 0);
    stack.push(rootNode);

    // DFS starting from the given root object.
    while (!stack.isEmpty()) {
      Node node = stack.peek();
      Object obj = node.getObject();
      if (obj == null) {
        stack.pop();
        continue;
      }
      // add to the topological order when ascending from the recursive subtree.
      if (node.myReferencesProcessed) {
        if (node.getObject() != null) {
          order.push(new WeakReference<>(obj));
          if (order.size() >= MAX_HEAP_TREE_SIZE) {
            return ErrorCode.HEAP_IS_TOO_BIG;
          }
        }
        stack.pop();
        continue;
      }

      addStronglyReferencedChildrenToStack(node, maxDepth, visited, stack, fieldCache);
      abortTraversalIfRequested();
      node.myReferencesProcessed = true;
    }
    return ErrorCode.OK;
  }

  /**
   * Distributing object masks to referring objects.
   *
   * Masks contain information about object ownership and retention.
   *
   * By objects owned by a component CompA we mean objects that are reachable from one of the roots of the CompA and not directly
   * reachable from roots of other components (only through CompA root).
   *
   * By component retained objects we mean objects that are only reachable through one of the component roots. Component retained objects
   * for the component also contains objects owned by other components but all of them will be unreachable from GC roots after removing the
   * component roots, so retained objects can be considered as an "additional weight" of the component.
   *
   * We also added weights to object references in order to separate difference types of references and handle situations of shared
   * ownership. Reference types listed in {@link HeapTraverseNode.RefWeight}.
   *
   * @param parentObj processing object
   * @param parentNode contains object-specific information (masks)
   * @param visited set that contains hashes of objects that we should visit
   * @param objectHashToNode map from object hash to {@link HeapTraverseNode}
   * @param fieldCache cache that stores fields declared for the given class.
   */
  private void propagateComponentMask(@NotNull final Object parentObj,
                                      @NotNull final HeapTraverseNode parentNode,
                                      @NotNull final IntSet visited,
                                      @NotNull final Map<Integer, HeapTraverseNode> objectHashToNode,
                                      @NotNull final FieldCache fieldCache) {
    myHeapTraverseChildProcessor.processChildObjects(parentObj, (Object value, HeapTraverseNode.RefWeight ownershipWeight) -> {
      if (value == null) {
        return;
      }
      int valueHashCode = System.identityHashCode(value);

      if (!visited.contains(valueHashCode)) {
        return;
      }
      if (parentObj.getClass().isSynthetic()) {
        ownershipWeight = HeapTraverseNode.RefWeight.SYNTHETIC;
      }
      if (parentNode.myOwnedByComponentMask == 0) {
        ownershipWeight = HeapTraverseNode.RefWeight.NON_COMPONENT;
      }

      HeapTraverseNode currentNode = objectHashToNode.get(valueHashCode);
      if (currentNode == null) {
        currentNode = new HeapTraverseNode();
        currentNode.myOwnershipWeight = ownershipWeight;
        currentNode.myOwnedByComponentMask = parentNode.myOwnedByComponentMask;
        currentNode.myRetainedMask = parentNode.myRetainedMask;
        objectHashToNode.put(valueHashCode, currentNode);
      }

      currentNode.myReachableFromComponentMask |= parentNode.myReachableFromComponentMask;
      currentNode.myRetainedMask &= parentNode.myRetainedMask;

      if (ownershipWeight.compareTo(currentNode.myOwnershipWeight) < 0) {
        return;
      }
      if (ownershipWeight.compareTo(currentNode.myOwnershipWeight) > 0) {
        currentNode.myOwnershipWeight = ownershipWeight;
        currentNode.myOwnedByComponentMask = parentNode.myOwnedByComponentMask;
      }
      else {
        currentNode.myOwnedByComponentMask |= parentNode.myOwnedByComponentMask;
      }
    }, fieldCache);
  }

  public enum ErrorCode {
    OK("Success"),
    HEAP_IS_TOO_BIG("Heap is too big"),
    LOW_MEMORY("LowMemory state occured during the heap traversal");

    private final String myClarification;

    ErrorCode(@NotNull final String clarification) {
      myClarification = clarification;
    }

    @NotNull
    public String getDescription() {
      return myClarification;
    }
  }

  private static final class Node {
    private final int myDepth;
    @NotNull
    private final WeakReference<Object> myObjReference;
    private boolean myReferencesProcessed = false;

    private Node(@NotNull final Object obj, int depth) {
      myObjReference = new WeakReference<>(obj);
      myDepth = depth;
    }

    @Nullable
    private Object getObject() {
      return myObjReference.get();
    }

    private int getDepth() {
      return myDepth;
    }
  }

  private static class HeapSnapshotTraverseException extends Exception {
    private final ErrorCode myErrorCode;

    HeapSnapshotTraverseException(ErrorCode errorCode) {
      super(errorCode.getDescription());
      myErrorCode = errorCode;
    }

    ErrorCode getErrorCode() {
      return myErrorCode;
    }
  }
}
