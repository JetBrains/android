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
import static com.google.common.math.LongMath.isPowerOfTwo;
import static com.google.wireless.android.sdk.stats.MemoryUsageReportEvent.MemoryUsageCollectionMetadata.StatusCode;

import com.android.tools.analytics.UsageTracker;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.intellij.ide.PowerSaveMode;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.LowMemoryWatcher;
import com.intellij.util.containers.WeakList;
import com.intellij.util.messages.MessageBusConnection;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class HeapSnapshotTraverse implements Disposable {

  private static final int MAX_ALLOWED_OBJECT_MAP_SIZE = 1_000_000;
  private static final int INVALID_OBJECT_ID = -1;
  private static final int INVALID_OBJECT_TAG = -1;
  private static final int MAX_DEPTH = 100_000;

  private static final long OBJECT_CREATION_ITERATION_ID_MASK = 0xFF;
  private static final long CURRENT_ITERATION_ID_MASK = 0xFF00;
  private static final long CURRENT_ITERATION_VISITED_MASK = 0x10000;
  private static final long CURRENT_ITERATION_OBJECT_ID_MASK = 0x1FFFFFFFE0000L;

  private static final int CURRENT_ITERATION_OBJECT_ID_OFFSET = 17;
  // 8(creation iteration id mask) + 8(current iteration id mask) + 1(visited mask)
  private static final int CURRENT_ITERATION_ID_OFFSET = 8;

  private static short ourIterationId = 0;

  @NotNull private final LowMemoryWatcher watcher;
  @NotNull private final MessageBusConnection messageBusConnection;

  @NotNull private final HeapTraverseChildProcessor heapTraverseChildProcessor;
  private final short iterationId;
  @NotNull private final HeapSnapshotStatistics statistics;
  private volatile boolean shouldAbortTraversal = false;
  private int lastObjectId = 0;

  public HeapSnapshotTraverse(@NotNull final HeapSnapshotStatistics statistics) {
    this(new HeapTraverseChildProcessor(statistics), statistics);
  }

  public HeapSnapshotTraverse(@NotNull final HeapTraverseChildProcessor childProcessor,
                              @NotNull final HeapSnapshotStatistics statistics) {
    watcher = LowMemoryWatcher.register(this::onLowMemorySignalReceived);
    messageBusConnection = ApplicationManager.getApplication().getMessageBus().connect();
    messageBusConnection.subscribe(PowerSaveMode.TOPIC, (PowerSaveMode.Listener)() -> {
      if (PowerSaveMode.isEnabled()) {
        shouldAbortTraversal = true;
        messageBusConnection.disconnect();
      }
    });

    heapTraverseChildProcessor = childProcessor;
    iterationId = getNextIterationId();
    this.statistics = statistics;
  }

  /**
   * The heap traversal algorithm is the following:
   * <p>
   * In the process of traversal, we associate a number of masks with each object. These masks are
   * stored in {@link HeapTraverseNode} and show which components own the corresponding
   * object(ownedByComponentMask), which components retain the object(retainedMask) etc.
   * <p>
   * On the first pass along the heap we arrange objects in topological order (in terms of
   * references). This is necessary so that during the subsequent propagation of masks, we can be
   * sure that all objects that refer to the object have already been processed and masks were
   * updated.
   * <p>
   * On the second pass, we directly update the masks and pass them to the referring objects.
   *
   * @param maxDepth the maximum depth to which we will descend when traversing the object tree.
   */
  public StatusCode walkObjects(int maxDepth) {
    if (!canTagObjects()) {
      return StatusCode.CANT_TAG_OBJECTS;
    }
    WeakList<Object> classes = new WeakList<>();
    classes.addAll(Arrays.asList(getClasses()));

    return walkObjects(maxDepth, classes);
  }

  public StatusCode walkObjects(int maxDepth, @NotNull final Collection<?> startRoots) {
    try {
      if (!canTagObjects()) {
        return StatusCode.CANT_TAG_OBJECTS;
      }
      try {
        StackNode.cacheStackNodeConstructorId(StackNode.class);
        final FieldCache fieldCache = new FieldCache(statistics);

        StackNode.clearDepthFirstSearchStack();
        HeapTraverseNode.clearObjectIdToTraverseNodeMap();
        HeapTraverseNode.cacheHeapSnapshotTraverseNodeConstructorId(HeapTraverseNode.class);
        // enumerating heap objects in topological order
        for (Object root : startRoots) {
          if (root == null) continue;
          long rootTag = depthFirstTraverseHeapObjects(root, maxDepth, fieldCache);
          if (rootTag == INVALID_OBJECT_TAG) {
            continue;
          }
          int rootObjectId = getObjectId(rootTag);
          if (rootObjectId <= 0 || rootObjectId > lastObjectId) {
            return StatusCode.WRONG_ROOT_OBJECT_ID;
          }

          HeapTraverseNode.putOrUpdateObjectIdToTraverseNodeMap(rootObjectId, root, HeapTraverseNode.RefWeight.DEFAULT.getValue(), 0L, 0L,
                                                                0,
                                                                rootTag);
        }
        // By this moment all the reachable heap objects are enumerated in topological order and
        // marked as visited. Order id, visited and the iteration id are stored in objects tags.
        // We also use this enumeration to kind of "freeze" the state of the heap, and we will ignore
        // all the newly allocated object that were allocated after the enumeration pass.

        statistics.setHeapObjectCount(lastObjectId);
        statistics.setTraverseSessionId(iterationId);

        // iterate over objects in topological order and update masks
        for (int i = lastObjectId; i > 0; i--) {
          abortTraversalIfRequested();
          int mapSize = HeapTraverseNode.getObjectIdToTraverseNodeMapSize();
          statistics.updateMaxObjectsQueueSize(mapSize);
          if (mapSize > MAX_ALLOWED_OBJECT_MAP_SIZE) {
            return StatusCode.OBJECTS_MAP_IS_TOO_BIG;
          }
          HeapTraverseNode node = HeapTraverseNode.getObjectIdToTraverseNodeMapElement(i, HeapTraverseNode.class);
          if (node == null) {
            statistics.incrementGarbageCollectedObjectsCounter();
            continue;
          }
          HeapTraverseNode.removeElementFromObjectIdToTraverseNodeMap(i);
          Object currentObject = node.getObject();
          if (currentObject == null) {
            statistics.incrementGarbageCollectedObjectsCounter();
            continue;
          }

          // Check whether the current object is a root of one of the components
          ComponentsSet.Component currentObjectComponent =
            statistics.getConfig().getComponentsSet().getComponentOfObject(currentObject);

          long currentObjectSize = getObjectSize(currentObject);
          short currentObjectCreationIterationId = getObjectCreationIterationId(node.tag);
          short currentObjectAge = (short)(iterationId - currentObjectCreationIterationId);
          String currentObjectClassName = currentObject.getClass().getName();

          statistics.addObjectToTotal(currentObjectSize, currentObjectAge);

          // if it's a root of a component
          if (currentObjectComponent != null) {
            updateComponentRootMasks(node, currentObjectComponent,
                                     HeapTraverseNode.RefWeight.DEFAULT);
          }

          // If current object is retained by any components - propagate their stats.
          processMask(node.retainedMask,
                      (index) -> statistics.addRetainedObjectSizeToComponent(index, currentObjectSize,
                                                                             currentObjectAge));
          // If current object is retained by any component categories - propagate their stats.
          processMask(node.retainedMaskForCategories,
                      (index) -> statistics.addRetainedObjectSizeToCategoryComponent(index,
                                                                                     currentObjectSize,
                                                                                     currentObjectAge));

          AtomicInteger categoricalOwnedMask = new AtomicInteger();
          processMask(node.ownedByComponentMask,
                      (index) -> categoricalOwnedMask.set(
                        categoricalOwnedMask.get() |
                        1 <<
                        statistics.getConfig().getComponentsSet().getComponents().get(index)
                          .getComponentCategory().getId()));
          if (categoricalOwnedMask.get() != 0 && isPowerOfTwo(categoricalOwnedMask.get())) {
            processMask(categoricalOwnedMask.get(),
                        (index) -> statistics.addOwnedObjectSizeToCategoryComponent(index,
                                                                                    currentObjectSize,
                                                                                    currentObjectAge, currentObjectClassName));
          }
          if (node.ownedByComponentMask == 0) {
            int uncategorizedComponentId =
              statistics.getConfig().getComponentsSet().getUncategorizedComponent().getId();
            int uncategorizedCategoryId =
              statistics.getConfig().getComponentsSet().getUncategorizedComponent().getComponentCategory()
                .getId();
            statistics.addOwnedObjectSizeToComponent(uncategorizedComponentId, currentObjectSize,
                                                     currentObjectAge, currentObjectClassName);
            statistics.addOwnedObjectSizeToCategoryComponent(uncategorizedCategoryId,
                                                             currentObjectSize, currentObjectAge, currentObjectClassName);
          }
          else if (isPowerOfTwo(node.ownedByComponentMask)) {
            // if only owned by one component
            processMask(node.ownedByComponentMask,
                        (index) -> statistics.addOwnedObjectSizeToComponent(index, currentObjectSize,
                                                                            currentObjectAge, currentObjectClassName));
          }
          else {
            // if owned by multiple components -> add to shared
            statistics.addObjectSizeToSharedComponent(node.ownedByComponentMask, currentObjectSize,
                                                      currentObjectAge, currentObjectClassName);
          }

          // propagate to referred objects
          propagateComponentMask(currentObject, node, fieldCache);
        }
      }
      finally {
        // finalization operations that involved the native agent.
        StackNode.clearDepthFirstSearchStack();
        HeapTraverseNode.clearObjectIdToTraverseNodeMap();
      }
    }
    catch (HeapSnapshotTraverseException exception) {
      return exception.getStatusCode();
    }
    finally {
      watcher.stop();
      messageBusConnection.disconnect();
    }
    return StatusCode.NO_ERROR;
  }

  private void updateComponentRootMasks(HeapTraverseNode node,
                                        ComponentsSet.Component currentObjectComponent,
                                        HeapTraverseNode.RefWeight weight) {
    node.retainedMask |= (1L << currentObjectComponent.getId());
    node.retainedMaskForCategories |= (1 << currentObjectComponent.getComponentCategory().getId());
    node.ownedByComponentMask = (1L << currentObjectComponent.getId());
    node.ownershipWeight = weight;
  }

  private void abortTraversalIfRequested() throws HeapSnapshotTraverseException {
    if (shouldAbortTraversal) {
      throw new HeapSnapshotTraverseException(StatusCode.LOW_MEMORY);
    }
  }

  private void onLowMemorySignalReceived() {
    shouldAbortTraversal = true;
  }

  /**
   * Checks that the passed tag was set during the current traverse.
   */
  @SuppressWarnings("BooleanMethodIsAlwaysInverted")
  private boolean isTagFromTheCurrentIteration(long tag) {
    return ((tag & CURRENT_ITERATION_ID_MASK) >> CURRENT_ITERATION_ID_OFFSET) == iterationId;
  }

  static short getObjectCreationIterationId(long tag) {
    return (short)(tag & OBJECT_CREATION_ITERATION_ID_MASK);
  }

  private long checkObjectCreationIterationIdAndSetIfNot(long tag) {
    int creationIterationId = (int)(tag & OBJECT_CREATION_ITERATION_ID_MASK);
    if (creationIterationId == 0) {
      tag &= ~iterationId;
      return tag | iterationId;
    }
    return tag;
  }

  private int getObjectId(long tag) {
    if (!isTagFromTheCurrentIteration(tag)) {
      return INVALID_OBJECT_ID;
    }
    return (int)(tag >> CURRENT_ITERATION_OBJECT_ID_OFFSET);
  }

  private boolean wasVisited(long tag) {
    if (!isTagFromTheCurrentIteration(tag)) {
      return false;
    }
    return (tag & CURRENT_ITERATION_VISITED_MASK) != 0;
  }

  private long setObjectId(@NotNull final Object obj, long tag, int newObjectId) {
    tag &= ~CURRENT_ITERATION_OBJECT_ID_MASK;
    tag |= (long)newObjectId << CURRENT_ITERATION_OBJECT_ID_OFFSET;
    tag &= ~CURRENT_ITERATION_ID_MASK;
    tag |= (long)iterationId << CURRENT_ITERATION_ID_OFFSET;
    setObjectTag(obj, tag);
    return tag;
  }

  private long markVisited(@NotNull final Object obj, long tag) {
    tag &= ~CURRENT_ITERATION_VISITED_MASK;
    tag |= CURRENT_ITERATION_VISITED_MASK;
    tag &= ~CURRENT_ITERATION_ID_MASK;
    tag |= (long)iterationId << CURRENT_ITERATION_ID_OFFSET;
    setObjectTag(obj, tag);
    return tag;
  }

  private void addToStack(@NotNull final StackNode stackNode,
                          int maxDepth,
                          @Nullable final Object value) {
    if (value == null) {
      return;
    }
    if (stackNode.depth + 1 > maxDepth) {
      return;
    }
    if (HeapTraverseUtil.isPrimitive(value.getClass())) {
      return;
    }
    long tag = getObjectTag(value);
    if (wasVisited(tag)) {
      return;
    }

    StackNode.pushElementToDepthFirstSearchStack(value, stackNode.depth + 1, markVisited(value, tag));
  }

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

  private int getNextObjectId() {
    return ++lastObjectId;
  }

    /*
    Object tags have the following structure (in right-most bit order):
    8bits - object creation iteration id
    8bits - current iteration id (used for validation of below fields)
    1bit - visited
    32bits - topological order id
   */

  /**
   * Traverses a subtree of the given root node and enumerates objects in the topological order.
   *
   * @return The tag of the passed root object and INVALID_OBJECT_TAG if this object was already visited on this iteration.
   */
  private long depthFirstTraverseHeapObjects(@NotNull final Object root,
                                             int maxDepth,
                                             @NotNull final FieldCache fieldCache)
    throws HeapSnapshotTraverseException {
    long rootTag = getObjectTag(root);
    if (wasVisited(rootTag)) {
      return INVALID_OBJECT_TAG;
    }
    rootTag = markVisited(root, rootTag);
    StackNode.pushElementToDepthFirstSearchStack(root, 0, rootTag);

    // DFS starting from the given root object.
    while (true) {
      int stackSize = StackNode.getDepthFirstSearchStackSize();
      if (stackSize == 0) {
        break;
      }
      if (stackSize > MAX_ALLOWED_OBJECT_MAP_SIZE) {
        StackNode.clearDepthFirstSearchStack();
        throw new HeapSnapshotTraverseException(StatusCode.OBJECTS_MAP_IS_TOO_BIG);
      }
      StackNode stackNode = StackNode.peekAndMarkProcessedDepthFirstSearchStack(StackNode.class);

      if (stackNode == null || stackNode.obj == null) {
        StackNode.popElementFromDepthFirstSearchStack();
        continue;
      }
      long tag = stackNode.tag;
      // add to the topological order when ascending from the recursive subtree.
      if (stackNode.referencesProcessed) {
        tag = checkObjectCreationIterationIdAndSetIfNot(tag);
        tag = setObjectId(stackNode.obj, tag, getNextObjectId());
        StackNode.popElementFromDepthFirstSearchStack();
        if (root == stackNode.obj) {
          rootTag = tag;
        }
        continue;
      }

      addStronglyReferencedChildrenToStack(stackNode, maxDepth, fieldCache);
      abortTraversalIfRequested();
    }
    return rootTag;
  }

  /**
   * Distributing object masks to referring objects.
   * <p>
   * Masks contain information about object ownership and retention.
   * <p>
   * By objects owned by a component CompA we mean objects that are reachable from one of the roots
   * of the CompA and not directly reachable from roots of other components (only through CompA
   * root).
   * <p>
   * By component retained objects we mean objects that are only reachable through one of the
   * component roots. Component retained objects for the component also contains objects owned by
   * other components but all of them will be unreachable from GC roots after removing the
   * component roots, so retained objects can be considered as an "additional weight" of the
   * component.
   * <p>
   * We also added weights to object references in order to separate difference types of references
   * and handle situations of shared ownership. Reference types listed in
   * {@link HeapTraverseNode.RefWeight}.
   *
   * @param parentObj  processing object
   * @param parentNode contains object-specific information (masks)
   * @param fieldCache cache that stores fields declared for the given class.
   */
  private void propagateComponentMask(@NotNull final Object parentObj,
                                      @NotNull final HeapTraverseNode parentNode,
                                      @NotNull final FieldCache fieldCache) throws HeapSnapshotTraverseException {
    heapTraverseChildProcessor.processChildObjects(parentObj, (Object value, HeapTraverseNode.RefWeight ownershipWeight) -> {
      if (value == null) {
        return;
      }
      long tag = getObjectTag(value);
      int objectId = getObjectId(tag);
      // don't process non-enumerated objects.
      // This situation may occur if array/list element or field value changed after enumeration
      // traversal. We don't process them because they can break the topological ordering.
      if (objectId == INVALID_OBJECT_ID) {
        return;
      }
      if (parentObj.getClass().isSynthetic()) {
        ownershipWeight = HeapTraverseNode.RefWeight.SYNTHETIC;
      }
      if (parentNode.ownedByComponentMask == 0) {
        ownershipWeight = HeapTraverseNode.RefWeight.NON_COMPONENT;
      }

      HeapTraverseNode currentNode = HeapTraverseNode.getObjectIdToTraverseNodeMapElement(objectId, HeapTraverseNode.class);
      if (currentNode == null) {
        currentNode = new HeapTraverseNode(value,
                                           ownershipWeight,
                                           parentNode.ownedByComponentMask,
                                           parentNode.retainedMask,
                                           parentNode.retainedMaskForCategories,
                                           tag);
      }

      currentNode.retainedMask &= parentNode.retainedMask;
      currentNode.retainedMaskForCategories &= parentNode.retainedMaskForCategories;

      if (ownershipWeight.compareTo(currentNode.ownershipWeight) > 0) {
        currentNode.ownershipWeight = ownershipWeight;
        currentNode.ownedByComponentMask = parentNode.ownedByComponentMask;
      }
      else if (ownershipWeight.compareTo(currentNode.ownershipWeight) == 0) {
        currentNode.ownedByComponentMask |= parentNode.ownedByComponentMask;
      }

      HeapTraverseNode.putOrUpdateObjectIdToTraverseNodeMap(objectId, value, currentNode.ownershipWeight.getValue(),
                                                            currentNode.ownedByComponentMask,
                                                            currentNode.retainedMask, currentNode.retainedMaskForCategories, tag);
    }, fieldCache);
  }

  @Override
  public void dispose() {
    watcher.stop();
    messageBusConnection.disconnect();
  }

  public static void collectAndWriteStats(@NotNull final Consumer<String> writer,
                                          @NotNull final HeapSnapshotStatistics stats,
                                          @NotNull final HeapSnapshotPresentationConfig presentationConfig) {
    long collectionStartTimestamp = System.nanoTime();
    new HeapSnapshotTraverse(stats).walkObjects(MAX_DEPTH);

    stats.print(writer, bytes -> HeapTraverseUtil.getObjectsStatsPresentation(bytes, presentationConfig.sizePresentation),
                presentationConfig, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - collectionStartTimestamp));
  }

  public static StatusCode collectMemoryReport() {
    HeapSnapshotStatistics stats = new HeapSnapshotStatistics(ComponentsSet.buildComponentSet());
    long startTime = System.nanoTime();
    StatusCode statusCode =
      new HeapSnapshotTraverse(stats).walkObjects(MAX_DEPTH);
    UsageTracker.log(AndroidStudioEvent.newBuilder()
                       .setKind(AndroidStudioEvent.EventKind.MEMORY_USAGE_REPORT_EVENT)
                       .setMemoryUsageReportEvent(
                         stats.buildMemoryUsageReportEvent(statusCode,
                                                           TimeUnit.NANOSECONDS.toMillis(
                                                             System.nanoTime() - startTime),
                                                           TimeUnit.NANOSECONDS.toMillis(
                                                             startTime),
                                                           ComponentsSet.getServerFlagConfiguration()
                                                             .getSharedComponentsLimit())));
    return statusCode;
  }

  private static short getNextIterationId() {
    return ++ourIterationId;
  }

  /**
   * Returns a JVM TI tag of the passed object.
   */
  private static native long getObjectTag(@NotNull final Object obj);

  /**
   * Sets a JVM TI object tag for a passed object.
   */
  private static native void setObjectTag(@NotNull final Object obj, long newTag);

  /**
   * Checks that JVM TI agent has a capability to tag objects.
   */
  private static native boolean canTagObjects();

  /**
   * @return an array of class objects initialized by the JVM.
   */
  public static native Class<?>[] getClasses();

  /**
   * @return an estimated size of the passed object in bytes.
   */
  private static native long getObjectSize(@NotNull final Object obj);

  /**
   * Checks if class was initialized by the JVM.
   */
  static native boolean isClassInitialized(@NotNull final Class<?> classToCheck);

  /**
   * Checks if class was initialized by the JVM.
   */
  static native Object[] getClassStaticFieldsValues(@NotNull final Class<?> classToCheck);

  static class HeapSnapshotPresentationConfig {
    final SizePresentationStyle sizePresentation;
    final boolean shouldLogSharedClusters;
    final boolean shouldLogRetainedSizes;

    HeapSnapshotPresentationConfig(SizePresentationStyle sizePresentation,
                                   boolean shouldLogSharedClusters,
                                   boolean shouldLogRetainedSizes) {
      this.sizePresentation = sizePresentation;
      this.shouldLogSharedClusters = shouldLogSharedClusters;
      this.shouldLogRetainedSizes = shouldLogRetainedSizes;
    }

    enum SizePresentationStyle {
      BYTES,
      OPTIMAL_UNITS,
    }
  }
}
