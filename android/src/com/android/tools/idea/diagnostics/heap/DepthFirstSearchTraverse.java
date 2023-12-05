/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static com.android.tools.idea.diagnostics.heap.MemoryReportCollector.MAX_ALLOWED_OBJECT_MAP_SIZE;
import static com.android.tools.idea.diagnostics.heap.MemoryReportJniHelper.getObjectTag;

import com.google.wireless.android.sdk.stats.MemoryUsageReportEvent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.containers.WeakList;
import java.util.List;
import java.util.Stack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

abstract class DepthFirstSearchTraverse {

  // Should not be increased above 2^17-1. 17 bits is reserved for the object depth in the 64-bit JVM TI object tag.
  private static final int MAX_DEPTH = 100_000;
  @NotNull
  private final FieldCache fieldCache;
  @NotNull
  private final HeapTraverseChildProcessor heapTraverseChildProcessor;
  @NotNull
  protected final MemoryReportCollector collector;
  protected final short iterationId;

  public DepthFirstSearchTraverse(@NotNull final FieldCache fieldCache,
                                  @NotNull final MemoryReportCollector collector) {
    this.fieldCache = fieldCache;
    this.heapTraverseChildProcessor = collector.heapTraverseChildProcessor;
    this.collector = collector;
    this.iterationId = collector.getIterationId();
  }

  /**
   * Performs a depth first traverse starting from the passed root objects.
   */
  public void start(@NotNull final WeakList<Object> roots)
    throws HeapSnapshotTraverseException {
    try {
      for (Object root : roots) {
        if (root == null) continue;
        depthFirstTraverseHeapObjects(root);
      }
    }
    finally {
      cleanup();
    }
  }

  protected void cleanup() {
    StackNode.clearDepthFirstSearchStack();
  }

  /**
   * Traverses a subtree of the given root node and enumerates objects in the postorder.
   */
  private void depthFirstTraverseHeapObjects(@NotNull final Object root)
    throws HeapSnapshotTraverseException {
    long rootTag = getObjectTag(root);
    if (ObjectTagUtil.wasVisited(rootTag, iterationId)) {
      return;
    }
    rootTag = ObjectTagUtil.markVisited(root, rootTag, iterationId);
    pushElementToDepthFirstSearchStack(root, 0, rootTag, "(root)");

    // DFS starting from the given root object.
    while (true) {
      collector.abortTraversalIfRequested();
      int stackSize = StackNode.getDepthFirstSearchStackSize();
      if (stackSize == 0) {
        break;
      }
      if (stackSize > MAX_ALLOWED_OBJECT_MAP_SIZE) {
        throw new HeapSnapshotTraverseException(MemoryUsageReportEvent.MemoryUsageCollectionMetadata.StatusCode.OBJECTS_MAP_IS_TOO_BIG);
      }
      StackNode stackNode = StackNode.peekAndMarkProcessedDepthFirstSearchStack(StackNode.class);
      if (stackNode == null) {
        continue;
      }

      // tag objects with their postorder number when ascending from the recursive subtree.
      if (stackNode.referencesProcessed) {
        handleProcessedNode(stackNode, root);
        StackNode.popElementFromDepthFirstSearchStack();
        continue;
      }

      handleNode(stackNode);
      addStronglyReferencedChildrenToStack(stackNode, fieldCache);
    }
  }

  protected abstract void handleProcessedNode(@NotNull final StackNode stackNode, @NotNull final Object root);

  private void addStronglyReferencedChildrenToStack(@NotNull final StackNode stackNode,
                                                    @NotNull final FieldCache fieldCache)
    throws HeapSnapshotTraverseException {
    if (stackNode.getObject() == null) {
      return;
    }
    if (stackNode.depth >= MAX_DEPTH) {
      return;
    }
    heapTraverseChildProcessor.processChildObjects(stackNode.getObject(),
                                                   (Object value, HeapTraverseNode.RefWeight weight, String label) -> addToStack(
                                                     stackNode, value, label), fieldCache);
  }


  private void addToStack(@NotNull final StackNode parentStackNode,
                          @Nullable final Object value,
                          @NotNull final String label) {
    if (value == null) {
      return;
    }
    if (parentStackNode.depth + 1 > MAX_DEPTH) {
      return;
    }
    if (HeapTraverseUtil.isPrimitive(value.getClass()) ||
        value instanceof Thread ||
        value instanceof Class<?> ||
        value instanceof ClassLoader) {
      return;
    }
    long tag = getObjectTag(value);
    if (ObjectTagUtil.wasVisited(tag, iterationId)) {
      return;
    }

    if (!shouldAddObjectToStack(parentStackNode, tag, value, label)) {
      return;
    }

    pushElementToDepthFirstSearchStack(value, parentStackNode.depth + 1, ObjectTagUtil.markVisited(value, tag, iterationId), label);
  }

  protected void pushElementToDepthFirstSearchStack(@NotNull final Object obj, int depth, long tag, @NotNull final String label) {
    StackNode.pushElementToDepthFirstSearchStack(obj, depth, tag);
  }

  protected void handleNode(@NotNull final StackNode stackNode) { }

  /**
   * @return if the object with a specified {@code childTag} should be added to the DFS stack. It was already checked that the `visited` bit
   * is not set and the referenced object is not of a root type(Class/ClassLoader/Thread).
   */
  protected boolean shouldAddObjectToStack(@NotNull final StackNode stackNode,
                                           long childTag,
                                           @NotNull final Object childObject,
                                           @NotNull final String label) {
    return true;
  }

  static class ObjectsEnumerationTraverse extends DepthFirstSearchTraverse {

    private int lastObjectId = 0;

    ObjectsEnumerationTraverse(@NotNull FieldCache fieldCache, @NotNull MemoryReportCollector collector) {
      super(fieldCache, collector);
    }

    @Override
    protected void handleProcessedNode(@NotNull final StackNode stackNode, @NotNull final Object root) {
      if (stackNode.getObject() == null) {
        return;
      }
      long tag = stackNode.tag;
      ObjectTagUtil.setObjectId(stackNode.getObject(), tag, ++lastObjectId, iterationId);
      if (stackNode.getObject() == root) {
        collector.putOrUpdateObjectIdToTraverseNodeMap(lastObjectId, root, collector.createRootNode(root));
      }
    }

    public int getLastObjectId() {
      return lastObjectId;
    }
  }

  static class ExtendedReportCollectionTraverse extends DepthFirstSearchTraverse {

    @NotNull
    private final ExtendedReportStatistics extendedReportStatistics;
    @NotNull
    private final Stack<ExtendedStackNode> extendedNodesStack = new Stack<>();
    @NotNull
    private final Stack<RootPathTree.RootPathElement> pathToRoot = new Stack<>();
    private boolean trackNominatedClassLoaders = true;
    boolean disposedObjectInPathToRoot = false;
    int disposedObjectInPathToRootPosition;
    boolean objectLoadedWithNominatedLoaderInPathToRoot = false;
    int objectLoadedWithNominatedLoaderInPathToRootPosition;
    boolean currentObjectIsLoadedWithNominatedLoader;

    public ExtendedReportCollectionTraverse(@NotNull final FieldCache fieldCache,
                                            @NotNull final MemoryReportCollector collector,
                                            @NotNull final ExtendedReportStatistics extendedReportStatistics) {
      super(fieldCache, collector);
      this.extendedReportStatistics = extendedReportStatistics;
    }

    private RootPathTree.RootPathElement pathToRootPop() {
      if (disposedObjectInPathToRoot && disposedObjectInPathToRootPosition == pathToRoot.size() - 1) {
        disposedObjectInPathToRoot = false;
      }
      if (objectLoadedWithNominatedLoaderInPathToRoot && objectLoadedWithNominatedLoaderInPathToRootPosition == pathToRoot.size() - 1) {
        objectLoadedWithNominatedLoaderInPathToRoot = false;
      }
      return pathToRoot.pop();
    }

    @Override
    protected void handleProcessedNode(@NotNull StackNode stackNode, @NotNull Object root) {
      if (stackNode.getObject() == null) {
        pathToRootPop();
        return;
      }
      if (ObjectTagUtil.isOwnedByExceededComponent(stackNode.tag)) {
        ExceededClusterStatistics exceededClusterStatistics =
          extendedReportStatistics.exceededClustersEnumeration.get(ObjectTagUtil.getOwningExceededClusterIndex(stackNode.tag));
        String currentObjectClassName = stackNode.getObject().getClass().getName();
        if (exceededClusterStatistics.isClassNominated(currentObjectClassName)) {
          extendedReportStatistics.rootPathTree.addObjectWithPathToRoot(pathToRoot, exceededClusterStatistics,
                                                                        exceededClusterStatistics.nominatedClassesEnumeration.getInt(
                                                                          currentObjectClassName));
        }
        if (pathToRoot.peek().isDisposedButReferenced() && disposedObjectInPathToRootPosition == pathToRoot.size() - 1) {
          extendedReportStatistics.rootPathTree.addDisposedReferencedObjectWithPathToRoot(pathToRoot, exceededClusterStatistics);
        }
      }

      RootPathTree.RootPathElement element = pathToRootPop();
      if (!pathToRoot.empty() && !element.extendedStackNode.equals(pathToRoot.peek().extendedStackNode)) {
        element.update();
        pathToRoot.peek().addSubtreeSize(element.getSubtreeSize());
      }
      // update root
      if (pathToRoot.empty()) {
        element.update();
      }

      MemoryReportJniHelper.setObjectTag(stackNode.getObject(), 0);
    }

    @Override
    protected void pushElementToDepthFirstSearchStack(@NotNull Object obj, int depth, long tag, @NotNull String label) {
      super.pushElementToDepthFirstSearchStack(obj, depth, tag, label);

      extendedNodesStack.push(
        new ExtendedStackNode(getObjectClassNameLabel(obj), label, isDisposedButReferenced(obj), currentObjectIsLoadedWithNominatedLoader));
    }

    public void disableClassLoaderTracking() {
      trackNominatedClassLoaders = false;
    }

    private static boolean isDisposedButReferenced(@NotNull Object obj) {
      //noinspection deprecation
      return obj instanceof Disposable && Disposer.isDisposed((Disposable)obj);
    }

    @NotNull
    private String getObjectClassNameLabel(@NotNull final Object obj) {
      if (obj instanceof Class<?>) {
        return String.format("%s(%s)", obj.getClass().getName(), ((Class<?>)obj).getName());
      }
      return obj.getClass().getName();
    }

    private void pathToRootAdd(@NotNull ExtendedStackNode extendedStackNode,
                               long size,
                               boolean isArray) {
      if (!disposedObjectInPathToRoot && extendedStackNode.isDisposedButReferenced()) {
        disposedObjectInPathToRoot = true;
        disposedObjectInPathToRootPosition = pathToRoot.size();
      }
      if (!objectLoadedWithNominatedLoaderInPathToRoot && extendedStackNode.isLoadedWithNominatedLoader()) {
        objectLoadedWithNominatedLoaderInPathToRoot = true;
        objectLoadedWithNominatedLoaderInPathToRootPosition = pathToRoot.size();
      }
      pathToRoot.add(new RootPathTree.RootPathElement(extendedStackNode, size, extendedReportStatistics, isArray));
    }

    @Override
    protected void handleNode(@NotNull final StackNode stackNode) {
      super.handleNode(stackNode);

      ExtendedStackNode extendedStackNode = extendedNodesStack.pop();
      pathToRootAdd(extendedStackNode, stackNode.getObject() == null
                                       ? 0
                                       : MemoryReportJniHelper.getObjectSize(stackNode.getObject()),
                    stackNode.getObject() != null &&
                    stackNode.getObject().getClass().isArray());
    }

    private boolean checkReferenceIsHoldingNominatedClassLoader(@NotNull final Object childObject,
                                                                @NotNull final String label) {
      if (childObject.getClass() == null ||
          !trackNominatedClassLoaders ||
          disposedObjectInPathToRoot ||
          objectLoadedWithNominatedLoaderInPathToRoot) {
        return false;
      }
      ClassLoader childObjectClassLoader = childObject.getClass().getClassLoader();
      if (childObjectClassLoader == null) {
        return false;
      }

      List<ExceededClusterStatistics> statistics = extendedReportStatistics.componentToExceededClustersStatistics.values().stream().filter(
        c -> c.isClassLoaderNominated(childObjectClassLoader) ||
             extendedReportStatistics.globalNominatedClassLoaders.contains(childObjectClassLoader)).toList();
      if (statistics.isEmpty()) {
        return false;
      }
      pathToRoot.add(new RootPathTree.RootPathElement(
        new ExtendedStackNode(getObjectClassNameLabel(childObject), label, isDisposedButReferenced(childObject), true),
        MemoryReportJniHelper.getObjectSize(childObject),
        extendedReportStatistics, childObject.getClass().isArray()));
      statistics.forEach(c -> extendedReportStatistics.rootPathTree.addClassLoaderPath(pathToRoot, c));
      pathToRootPop();
      return true;
    }

    @Override
    protected boolean shouldAddObjectToStack(@NotNull StackNode stackNode,
                                             long childTag,
                                             @NotNull final Object childObject,
                                             @NotNull final String label) {
      currentObjectIsLoadedWithNominatedLoader = checkReferenceIsHoldingNominatedClassLoader(childObject, label);

      int childObjectDepth = ObjectTagUtil.getDepth(childTag, iterationId);
      HeapTraverseNode.MinDepthKind childMinDepthKind = ObjectTagUtil.getDepthKind(childTag, iterationId);
      HeapTraverseNode.MinDepthKind parentMinDepthKind = ObjectTagUtil.getDepthKind(stackNode.tag, iterationId);

      if (childObjectDepth == ObjectTagUtil.INVALID_OBJECT_DEPTH || childMinDepthKind == null) {
        return false;
      }

      if (pathToRoot.peek().isDisposedButReferenced()) {
        parentMinDepthKind = HeapTraverseNode.MinDepthKind.USING_DISPOSED_OBJECTS;
      }

      return stackNode.depth + 1 == childObjectDepth && parentMinDepthKind == childMinDepthKind;
    }

    @Override
    protected void cleanup() {
      super.cleanup();
      extendedNodesStack.clear();
      pathToRoot.clear();
    }
  }
}
