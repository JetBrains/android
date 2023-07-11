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
import com.intellij.util.containers.WeakList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

abstract class DepthFirstSearchTraverse {
  private final int maxDepth;
  @NotNull
  private final FieldCache fieldCache;
  @NotNull
  private final HeapTraverseChildProcessor heapTraverseChildProcessor;
  @NotNull
  protected final MemoryReportCollector collector;
  protected final short iterationId;

  public DepthFirstSearchTraverse(int maxDepth,
                                  @NotNull final FieldCache fieldCache,
                                  @NotNull final MemoryReportCollector collector) {
    this.maxDepth = maxDepth;
    this.fieldCache = fieldCache;
    this.heapTraverseChildProcessor = collector.heapTraverseChildProcessor;
    this.collector = collector;
    this.iterationId = collector.getIterationId();
  }

  /**
   *
   */
  public void start(@NotNull final WeakList<Object> roots)
    throws HeapSnapshotTraverseException {
    for (Object root : roots) {
      if (root == null) continue;
      depthFirstTraverseHeapObjects(root, maxDepth, fieldCache);
    }
  }

  /**
   * Traverses a subtree of the given root node and enumerates objects in the postorder.
   */
  private void depthFirstTraverseHeapObjects(@NotNull final Object root,
                                             int maxDepth,
                                             @NotNull final FieldCache fieldCache)
    throws HeapSnapshotTraverseException {
    long rootTag = getObjectTag(root);
    if (ObjectTagUtil.wasVisited(rootTag, iterationId)) {
      return;
    }
    rootTag = ObjectTagUtil.markVisited(root, rootTag, iterationId);
    StackNode.pushElementToDepthFirstSearchStack(root, 0, rootTag);

    // DFS starting from the given root object.
    while (true) {
      int stackSize = StackNode.getDepthFirstSearchStackSize();
      if (stackSize == 0) {
        break;
      }
      if (stackSize > MAX_ALLOWED_OBJECT_MAP_SIZE) {
        StackNode.clearDepthFirstSearchStack();
        throw new HeapSnapshotTraverseException(MemoryUsageReportEvent.MemoryUsageCollectionMetadata.StatusCode.OBJECTS_MAP_IS_TOO_BIG);
      }
      StackNode stackNode = StackNode.peekAndMarkProcessedDepthFirstSearchStack(StackNode.class);

      if (stackNode == null || stackNode.obj == null) {
        StackNode.popElementFromDepthFirstSearchStack();
        continue;
      }

      if (processNode(stackNode, root)) {
        addStronglyReferencedChildrenToStack(stackNode, maxDepth, fieldCache);
      }
      collector.abortTraversalIfRequested();
    }
  }

  /**
   * @return if the objects referred from the passed {@code stackNode.obj} should be added to the stack
   */
  protected abstract boolean processNode(@NotNull final StackNode stackNode, @NotNull final Object root);

  private void addStronglyReferencedChildrenToStack(@NotNull final StackNode stackNode,
                                                    int maxDepth,
                                                    @NotNull final FieldCache fieldCache)
    throws HeapSnapshotTraverseException {
    if (stackNode.depth >= maxDepth) {
      return;
    }
    heapTraverseChildProcessor.processChildObjects(stackNode.getObject(),
                                                   (Object value, HeapTraverseNode.RefWeight weight) -> addToStack(
                                                     stackNode, maxDepth, value), fieldCache);
  }


  private void addToStack(@NotNull final StackNode parentStackNode,
                          int maxDepth,
                          @Nullable final Object value) {
    if (value == null) {
      return;
    }
    if (parentStackNode.depth + 1 > maxDepth) {
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

    if (!shouldAddObjectToStack(parentStackNode, tag)) {
      return;
    }

    StackNode.pushElementToDepthFirstSearchStack(value, parentStackNode.depth + 1, ObjectTagUtil.markVisited(value, tag, iterationId));
  }

  /**
   * @return if the object with a specified {@code childTag} should be added to the DFS stack. It was already checked that the `visited` bit
   * is not set and the referenced object is not of a root type(Class/ClassLoader/Thread).
   */
  protected abstract boolean shouldAddObjectToStack(@NotNull final StackNode stackNode, long childTag);

  static class ObjectsEnumerationTraverse extends DepthFirstSearchTraverse {

    private int lastObjectId = 0;

    ObjectsEnumerationTraverse(int maxDepth, @NotNull FieldCache fieldCache, @NotNull MemoryReportCollector collector) {
      super(maxDepth, fieldCache, collector);
    }

    @Override
    protected boolean processNode(@NotNull final StackNode stackNode, @NotNull final Object root) {
      long tag = stackNode.tag;
      // add to the postorder when ascending from the recursive subtree.
      if (stackNode.referencesProcessed) {
        assert stackNode.obj != null;
        ObjectTagUtil.setObjectId(stackNode.obj, tag, ++lastObjectId, iterationId);
        StackNode.popElementFromDepthFirstSearchStack();
        if (stackNode.obj == root) {
          collector.putOrUpdateObjectIdToTraverseNodeMap(lastObjectId, root,
                                                         HeapTraverseNode.RefWeight.DEFAULT.getValue(), 0L, 0L,
                                                         0,
                                                         false, false);
        }
        return false;
      }
      return true;
    }

    @Override
    protected boolean shouldAddObjectToStack(@NotNull final StackNode stackNode, long childTag) {
      return true;
    }

    public int getLastObjectId() {
      return lastObjectId;
    }
  }
}
