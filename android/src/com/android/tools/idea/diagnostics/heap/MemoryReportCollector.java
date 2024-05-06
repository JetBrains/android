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

import static com.android.tools.idea.diagnostics.heap.HeapTraverseConfig.DEFAULT_SUMMARY_REQUIRED_SUBTREE_SIZE;
import static com.android.tools.idea.diagnostics.heap.HeapTraverseNode.minDepthKindFromByte;
import static com.android.tools.idea.diagnostics.heap.HeapTraverseUtil.isPrimitive;
import static com.android.tools.idea.diagnostics.heap.HeapTraverseUtil.processMask;
import static com.android.tools.idea.diagnostics.heap.ObjectTagUtil.INVALID_OBJECT_ID;
import static com.google.common.math.LongMath.isPowerOfTwo;
import static com.google.common.math.LongMath.log2;
import static com.google.wireless.android.sdk.stats.MemoryUsageReportEvent.MemoryUsageCollectionMetadata.StatusCode;

import com.android.annotations.Nullable;
import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.diagnostics.crash.StudioCrashReporter;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.intellij.ide.PowerSaveMode;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.LowMemoryWatcher;
import com.intellij.util.containers.WeakList;
import com.intellij.util.messages.MessageBusConnection;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

public final class MemoryReportCollector implements Disposable {

  static final Computable<WeakList<Object>> getLoadedClassesComputable = () -> {
    WeakList<Object> roots = new WeakList<>();
    Object[] classes = MemoryReportJniHelper.getClasses();
    roots.addAll(Arrays.asList(classes));
    roots.addAll(Thread.getAllStackTraces().keySet());
    // We don't process ClassLoader during the HeapTraverse, so they are added as a traverse roots after
    // class objects and threads.
    // By the moment of starting DFS from them all the class objects are already processed, but fields of
    // ClassLoaders other than ClassLoader#classes still need to be processed.
    roots.addAll(
      Arrays.stream(classes)
        .filter(c -> c instanceof Class<?>)
        .map(c -> ((Class<?>)c).getClassLoader())
        .filter(Objects::nonNull)
        .collect(Collectors.toSet()));
    return roots;
  };

  static final int MAX_ALLOWED_OBJECT_MAP_SIZE = 1_000_000;

  private static short ourIterationId = 0;

  @NotNull private final LowMemoryWatcher watcher;
  @NotNull private final MessageBusConnection messageBusConnection;

  @NotNull final HeapTraverseChildProcessor heapTraverseChildProcessor;
  private final short iterationId;
  @NotNull private final HeapSnapshotStatistics statistics;
  private volatile boolean shouldAbortTraversal = false;

  public MemoryReportCollector(@NotNull final HeapSnapshotStatistics statistics) {
    this(new HeapTraverseChildProcessor(statistics), statistics);
  }

  public MemoryReportCollector(@NotNull final HeapTraverseChildProcessor childProcessor,
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

  @TestOnly
  StatusCode walkObjects(@NotNull final List<Object> roots) {
    WeakList<Object> classes = new WeakList<>();
    classes.addAll(roots);
    return walkObjects(() -> classes);
  }

  StatusCode walkObjects() {
    return walkObjects(getLoadedClassesComputable);
  }

  /**
   * The heap traversal algorithm is the following:
   * <p>
   * In the process of traversal, we associate a number of masks with each object. These masks are
   * stored in {@link HeapTraverseNode} and show which components own the corresponding
   * object(ownedByComponentMask), which components retain the object(retainedMask) etc.
   * <p>
   * On the first pass along the heap we arrange objects in postorder (in terms of
   * references). This is necessary so that during the subsequent propagation of masks, we can be
   * sure that all objects that refer to the object have already been processed and masks were
   * updated.
   * <p>
   * On the second pass, we directly update the masks and pass them to the referring objects.
   *
   * @param rootsComputable computable that return the list of roots for heap traversal.
   */
  public StatusCode walkObjects(@NotNull final Computable<WeakList<Object>> rootsComputable) {
    try {
      if (!MemoryReportJniHelper.canTagObjects()) {
        return StatusCode.CANT_TAG_OBJECTS;
      }
      try {
        StackNode.cacheStackNodeConstructorId(StackNode.class);
        final FieldCache fieldCache = new FieldCache(statistics);

        StackNode.clearDepthFirstSearchStack();
        clearObjectIdToTraverseNodeMap();
        HeapTraverseNode.cacheHeapSnapshotTraverseNodeConstructorId(HeapTraverseNode.class);
        WeakList<Object> startRoots = rootsComputable.compute();

        DepthFirstSearchTraverse.ObjectsEnumerationTraverse traverse =
          new DepthFirstSearchTraverse.ObjectsEnumerationTraverse(fieldCache, this);
        // enumerating heap objects in postorder
        traverse.start(startRoots);

        // By this moment all the reachable heap objects are enumerated in preorder and
        // marked as visited. Order id, visited and the iteration id are stored in objects tags.
        // We also use this enumeration to kind of "freeze" the state of the heap, and we will ignore
        // all the newly allocated object that were allocated after the enumeration pass.

        statistics.setHeapObjectCount(traverse.getLastObjectId());
        statistics.setTraverseSessionId(iterationId);

        Map<String, ExtendedReportStatistics.ClassObjectsStatistics> nameToClassObjectsStatistics = Maps.newHashMap();

        // iterate over objects in preorder and update masks
        for (int currentObjectId = traverse.getLastObjectId(); currentObjectId > 0; currentObjectId--) {
          abortTraversalIfRequested();
          int mapSize = HeapTraverseNode.getObjectIdToTraverseNodeMapSize();
          statistics.updateMaxObjectsQueueSize(mapSize);
          if (mapSize > MAX_ALLOWED_OBJECT_MAP_SIZE) {
            return StatusCode.OBJECTS_MAP_IS_TOO_BIG;
          }
          HeapTraverseNode node = getObjectIdToTraverseNodeMapElement(currentObjectId);
          removeElementFromObjectIdToTraverseNodeMap(currentObjectId);
          if (node == null) {
            statistics.incrementGarbageCollectedObjectsCounter();
            continue;
          }
          Object currentObject = node.getObject();
          if (currentObject == null) {
            statistics.incrementGarbageCollectedObjectsCounter();
            continue;
          }

          // Check whether the current object is a root of one of the components
          ComponentsSet.Component currentObjectComponent =
            statistics.getConfig().getComponentsSet().getComponentOfObject(currentObject);

          long currentObjectSize = MemoryReportJniHelper.getObjectSize(currentObject);
          String currentObjectClassName = currentObject.getClass().getName();

          boolean isPlatformObject = isPlatformObject(currentObject, currentObjectClassName);
          if (isPlatformObject) {
            node.isRetainedByPlatform = true;
          }
          statistics.addObjectToTotal(currentObjectSize, isPlatformObject, node.isRetainedByPlatform);
          statistics.checkClassIsTrackedAndAdd(currentObjectClassName);

          boolean isDisposedButReferenced = false;
          if (statistics.getConfig().collectDisposerTreeInfo && currentObject instanceof Disposable) {
            //noinspection deprecation
            if (Disposer.isDisposed((Disposable)currentObject)) {
              statistics.addDisposedButReferencedObject(currentObjectSize, currentObjectClassName);
              isDisposedButReferenced = true;
            }
          }

          // if it's a root of a component
          boolean objectIsAComponentRoot = currentObjectComponent != null;
          if (objectIsAComponentRoot) {
            updateComponentRootMasks(node, currentObjectComponent, HeapTraverseNode.RefWeight.DEFAULT);
          }

          // If current object is retained by any components - propagate their stats.
          processMask(node.retainedMask,
                      (index) -> statistics.addRetainedObjectSizeToComponent(index, currentObjectSize, isPlatformObject,
                                                                             node.isRetainedByPlatform));
          // If current object is retained by any component categories - propagate their stats.
          processMask(node.retainedMaskForCategories,
                      (index) -> statistics.addRetainedObjectSizeToCategoryComponent(index,
                                                                                     currentObjectSize, isPlatformObject,
                                                                                     node.isRetainedByPlatform
                      ));

          ComponentsSet.ComponentCategory category = getCategoryByComponentMask(node.ownedByComponentMask);
          if (category != null) {
            statistics.addOwnedObjectSizeToCategoryComponent(category.getId(),
                                                             currentObjectSize,
                                                             currentObjectClassName, objectIsAComponentRoot, isPlatformObject,
                                                             node.isRetainedByPlatform,
                                                             isDisposedButReferenced);
          }

          if (node.ownedByComponentMask == 0) {
            int uncategorizedComponentId =
              statistics.getConfig().getComponentsSet().getUncategorizedComponent().getId();
            int uncategorizedCategoryId =
              statistics.getConfig().getComponentsSet().getUncategorizedComponent().getComponentCategory()
                .getId();
            statistics.addOwnedObjectSizeToComponent(uncategorizedComponentId, currentObjectSize,
                                                     currentObjectClassName, false, isPlatformObject, node.isRetainedByPlatform,
                                                     isDisposedButReferenced);
            statistics.addOwnedObjectSizeToCategoryComponent(uncategorizedCategoryId,
                                                             currentObjectSize, currentObjectClassName, false, isPlatformObject,
                                                             node.isRetainedByPlatform, isDisposedButReferenced);
          }
          else if (isPowerOfTwo(node.ownedByComponentMask)) {
            int componentId = log2(node.ownedByComponentMask, RoundingMode.UP);
            currentObjectComponent = statistics.getConfig().getComponentsSet().getComponents().get(componentId);
            // if only owned by one component
            statistics.addOwnedObjectSizeToComponent(componentId, currentObjectSize,
                                                     currentObjectClassName, objectIsAComponentRoot, isPlatformObject,
                                                     node.isRetainedByPlatform, isDisposedButReferenced);
          }
          else {
            // if owned by multiple components -> add to shared
            statistics.addObjectSizeToSharedComponent(node.ownedByComponentMask, currentObjectSize,
                                                      currentObjectClassName, node.isMergePoint, isPlatformObject,
                                                      node.isRetainedByPlatform, isDisposedButReferenced);
          }

          processObjectClassLoader(currentObject, currentObjectComponent, nameToClassObjectsStatistics);
          processObjectTagPreorderTraverse(currentObject, currentObjectId, node, currentObjectComponent);

          // propagate to referred objects
          if (isDisposedButReferenced) {
            node.minDepthKind = HeapTraverseNode.MinDepthKind.USING_DISPOSED_OBJECTS;
          }
          propagateComponentMask(currentObject, node, currentObjectId, fieldCache);
        }

        statistics.calculateExtendedReportDataIfNeeded(fieldCache, this, startRoots, nameToClassObjectsStatistics);
      }
      finally {
        // finalization operations that involved the native agent.
        StackNode.clearDepthFirstSearchStack();
        clearObjectIdToTraverseNodeMap();
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

  private void clearObjectIdToTraverseNodeMap() {
    HeapTraverseNode.clearObjectIdToTraverseNodeMap();
    if (statistics.getExtendedReportStatistics() != null) {
      statistics.getExtendedReportStatistics().objectIdToMinDepth.clear();
      statistics.getExtendedReportStatistics().objectIdToMinDepthKind.clear();
    }
  }

  private void removeElementFromObjectIdToTraverseNodeMap(int objectId) {
    HeapTraverseNode.removeElementFromObjectIdToTraverseNodeMap(objectId);
    if (statistics.getExtendedReportStatistics() != null) {
      statistics.getExtendedReportStatistics().objectIdToMinDepth.remove(objectId);
      statistics.getExtendedReportStatistics().objectIdToMinDepthKind.remove(objectId);
    }
  }

  private HeapTraverseNode getObjectIdToTraverseNodeMapElement(int objectId) {
    HeapTraverseNode node = HeapTraverseNode.getObjectIdToTraverseNodeMapElement(objectId, HeapTraverseNode.class);
    if (node == null || statistics.getExtendedReportStatistics() == null) {
      return node;
    }

    if (statistics.getExtendedReportStatistics().objectIdToMinDepth.containsKey(objectId)) {
      node.minDepth = statistics.getExtendedReportStatistics().objectIdToMinDepth.get(objectId);
      node.minDepthKind = minDepthKindFromByte(statistics.getExtendedReportStatistics().objectIdToMinDepthKind.get(objectId));
    }

    return node;
  }

  private void processObjectClassLoader(@NotNull final Object obj,
                                        @Nullable final ComponentsSet.Component currentObjectComponent,
                                        @NotNull final Map<String, ExtendedReportStatistics.ClassObjectsStatistics> nameToClassObjectsStatistics) {
    if (statistics.getExtendedReportStatistics() == null || obj.getClass() == null || obj.getClass().getClassLoader() == null) {
      return;
    }
    Class<?> objClass = obj.getClass();
    nameToClassObjectsStatistics.putIfAbsent(objClass.getName(), new ExtendedReportStatistics.ClassObjectsStatistics());
    ExtendedReportStatistics.ClassObjectsStatistics classObjectsStatistics = nameToClassObjectsStatistics.get(objClass.getName());
    classObjectsStatistics.classLoaders.add(obj.getClass().getClassLoader());
    classObjectsStatistics.classObjects.add(objClass);

    if (currentObjectComponent == null ||
        !statistics.getExtendedReportStatistics().componentToExceededClustersStatistics.containsKey(currentObjectComponent)) {
      return;
    }
    ClassLoader loader = objClass.getClassLoader();
    String currentObjectClassLoader = loader.getClass().getName();

    if (currentObjectComponent.customClassLoaders.contains(currentObjectClassLoader)) {
      statistics.getExtendedReportStatistics().componentToExceededClustersStatistics.get(currentObjectComponent)
        .addNominatedClassLoader(loader);
    }
  }

  private void processObjectTagPreorderTraverse(@NotNull final Object obj, int currentObjectId, @NotNull final HeapTraverseNode node,
                                                @Nullable final ComponentsSet.Component currentObjectComponent) {
    if (statistics.getExtendedReportStatistics() == null || node.minDepth == null) {
      MemoryReportJniHelper.setObjectTag(obj, 0);
      return;
    }

    boolean isOwnedByExceededComponent = false;
    int owningExceededClusterIndex = 0;

    if (currentObjectComponent != null &&
        statistics.getExtendedReportStatistics().componentToExceededClustersStatistics.containsKey(currentObjectComponent)) {
      isOwnedByExceededComponent = true;
      owningExceededClusterIndex =
        statistics.getExtendedReportStatistics().componentToExceededClustersStatistics.get(currentObjectComponent).exceededClusterIndex;
    }
    MemoryReportJniHelper.setObjectTag(obj,
                                       ObjectTagUtil.constructTag(currentObjectId, node.minDepth, node.minDepthKind, iterationId,
                                                                  isOwnedByExceededComponent, owningExceededClusterIndex));
  }

  public short getIterationId() {
    return iterationId;
  }

  static boolean isPlatformObject(@NotNull final String className) {
    return className.startsWith("org.jetbrains") ||
           className.startsWith("com.intellij") ||
           className.startsWith("com.jetbrains") ||
           className.startsWith("org.intellij");
  }

  static boolean isPlatformObject(@NotNull final Object obj, @NotNull final String objClassName) {
    if (obj instanceof Class<?>) {
      return isPlatformObject(((Class<?>)obj).getName());
    }
    return isPlatformObject(objClassName);
  }

  void putOrUpdateObjectIdToTraverseNodeMap(int id,
                                            @NotNull final Object obj,
                                            @NotNull final HeapTraverseNode node) {
    HeapTraverseNode.putOrUpdateObjectIdToTraverseNodeMap(id, obj,
                                                          node.ownershipWeight.getValue(), node.ownedByComponentMask,
                                                          node.retainedMask,
                                                          node.retainedMaskForCategories,
                                                          node.isMergePoint,
                                                          node.isRetainedByPlatform);
    if (statistics.getExtendedReportStatistics() != null && node.minDepth != null) {
      statistics.getExtendedReportStatistics().objectIdToMinDepth.put(id, node.minDepth.intValue());
      if (node.minDepthKind != null) {
        statistics.getExtendedReportStatistics().objectIdToMinDepthKind.put(id, node.minDepthKind.getValue());
      }
    }
  }

  // Returns null if componentMask is empty, or set bits belong to different categories
  private ComponentsSet.ComponentCategory getCategoryByComponentMask(long componentMask) {
    ComponentsSet.ComponentCategory result = null;
    int trailingZeros = Long.numberOfTrailingZeros(componentMask);
    componentMask >>= Long.numberOfTrailingZeros(componentMask);
    for (int i = trailingZeros; componentMask != 0; i++, componentMask >>= 1) {
      if ((componentMask & 1) != 0) {
        ComponentsSet.ComponentCategory currentCategory = statistics.getComponentStats().get(i).getCluster().getComponentCategory();
        if (result == null) {
          result = currentCategory;
        }
        else if (result != currentCategory) {
          return null;
        }
      }
    }
    return result;
  }

  private void updateComponentRootMasks(HeapTraverseNode node,
                                        ComponentsSet.Component currentObjectComponent,
                                        HeapTraverseNode.RefWeight weight) {
    node.retainedMask |= (1L << currentObjectComponent.getId());
    node.retainedMaskForCategories |= (1 << currentObjectComponent.getComponentCategory().getId());
    node.ownedByComponentMask = (1L << currentObjectComponent.getId());
    node.ownershipWeight = weight;
  }

  void abortTraversalIfRequested() throws HeapSnapshotTraverseException {
    if (shouldAbortTraversal) {
      throw new HeapSnapshotTraverseException(StatusCode.LOW_MEMORY);
    }
  }

  private void onLowMemorySignalReceived() {
    shouldAbortTraversal = true;
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
                                      int parentId,
                                      @NotNull final FieldCache fieldCache) throws HeapSnapshotTraverseException {
    heapTraverseChildProcessor.processChildObjects(parentObj, (Object value, HeapTraverseNode.RefWeight ownershipWeight, String label) -> {
      if (value == null ||
          isPrimitive(value.getClass()) ||
          value instanceof Thread ||
          value instanceof Class<?> ||
          value instanceof ClassLoader) {
        return;
      }
      long tag = MemoryReportJniHelper.getObjectTag(value);
      if (tag == 0) {
        return;
      }
      int objectId = ObjectTagUtil.getObjectId(tag, iterationId);
      // don't process non-enumerated objects.
      // This situation may occur if array/list element or field value changed after enumeration
      // traversal. We don't process them because they can break the topological ordering.
      if (objectId == INVALID_OBJECT_ID || objectId >= parentId) {
        return;
      }
      if (parentObj.getClass().isSynthetic()) {
        ownershipWeight = HeapTraverseNode.RefWeight.SYNTHETIC;
      }
      if (parentNode.ownedByComponentMask == 0) {
        ownershipWeight = HeapTraverseNode.RefWeight.NON_COMPONENT;
      }

      HeapTraverseNode currentNode =
        HeapTraverseNode.getObjectIdToTraverseNodeMapElement(objectId,
                                                             HeapTraverseNode.class);
      if (currentNode == null) {
        currentNode = createHeapTraverseNodeFromParent(value, ownershipWeight, parentNode);
      }

      currentNode.retainedMask &= parentNode.retainedMask;
      currentNode.retainedMaskForCategories &= parentNode.retainedMaskForCategories;
      currentNode.isRetainedByPlatform &= parentNode.isRetainedByPlatform;
      if (currentNode.minDepth != null &&
          currentNode.minDepthKind != null &&
          parentNode.minDepth != null &&
          parentNode.minDepthKind != null) {
        if (parentNode.minDepthKind.getValue() == currentNode.minDepthKind.getValue()) {
          currentNode.minDepth = Math.min(currentNode.minDepth, parentNode.minDepth + 1);
        }

        if (parentNode.minDepthKind.getValue() < currentNode.minDepthKind.getValue()) {
          currentNode.minDepth = parentNode.minDepth + 1;
          currentNode.minDepthKind = parentNode.minDepthKind;
        }
      }

      if (ownershipWeight.compareTo(currentNode.ownershipWeight) > 0) {
        currentNode.ownershipWeight = ownershipWeight;
        currentNode.ownedByComponentMask = parentNode.ownedByComponentMask;
        currentNode.isMergePoint = false;
      }
      else if (ownershipWeight.compareTo(currentNode.ownershipWeight) == 0) {
        if (currentNode.ownedByComponentMask != 0 && parentNode.ownedByComponentMask != currentNode.ownedByComponentMask) {
          currentNode.isMergePoint = true;
        }
        currentNode.ownedByComponentMask |= parentNode.ownedByComponentMask;
      }

      putOrUpdateObjectIdToTraverseNodeMap(objectId, value, currentNode);
    }, fieldCache);
  }

  private HeapTraverseNode createHeapTraverseNodeFromParent(@Nullable final Object obj,
                                                            @NotNull HeapTraverseNode.RefWeight ownershipWeight,
                                                            @NotNull final HeapTraverseNode parentNode) {
    HeapTraverseNode node = new HeapTraverseNode(obj, ownershipWeight, parentNode.ownedByComponentMask, parentNode.retainedMask,
                                                 parentNode.retainedMaskForCategories, false, parentNode.isRetainedByPlatform);
    if (statistics.getExtendedReportStatistics() != null && parentNode.minDepth != null) {
      node.minDepth = parentNode.minDepth + 1;
      node.minDepthKind = parentNode.minDepthKind;
    }

    return node;
  }

  HeapTraverseNode createRootNode(@Nullable final Object root) {
    HeapTraverseNode node = new HeapTraverseNode(root,
                                                 HeapTraverseNode.RefWeight.DEFAULT.getValue(), 0L, 0L,
                                                 0,
                                                 false, false);
    if (statistics.getExtendedReportStatistics() != null) {
      node.minDepth = 0;
      node.minDepthKind = HeapTraverseNode.MinDepthKind.DEFAULT;
    }

    return node;
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
    new MemoryReportCollector(stats).walkObjects(getLoadedClassesComputable);

    stats.print(writer, bytes -> HeapTraverseUtil.getObjectsStatsPresentation(bytes, presentationConfig.sizePresentation),
                presentationConfig, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - collectionStartTimestamp));
  }

  public static StatusCode collectMemoryReport(@NotNull final HeapSnapshotStatistics stats,
                                               @NotNull final Computable<WeakList<Object>> rootsComputable) {
    long startTime = System.nanoTime();
    StatusCode statusCode =
      new MemoryReportCollector(stats).walkObjects(rootsComputable);
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
    List<ComponentsSet.Component> exceededComponents = getComponentsThatExceededThreshold(stats);
    if (!exceededComponents.isEmpty()) {
      collectAndSendExtendedMemoryReport(stats.getConfig().getComponentsSet(), exceededComponents, rootsComputable, DEFAULT_SUMMARY_REQUIRED_SUBTREE_SIZE);
    }

    return statusCode;
  }

  @NotNull
  private static List<ComponentsSet.Component> getComponentsThatExceededThreshold(@NotNull final HeapSnapshotStatistics stats) {
    List<ComponentsSet.Component> result = Lists.newArrayList();

    for (ComponentsSet.Component component : stats.getConfig().getComponentsSet().getComponents()) {
      if (stats.getComponentStats().get(component.getId()).getOwnedClusterStat().getObjectsStatistics().getTotalSizeInBytes() >
          component.getExtendedReportCollectionThresholdBytes()) {
        result.add(component);
        if (result.size() >= ObjectTagUtil.EXCEEDED_COMPONENTS_LIMIT) {
          break;
        }
      }
    }

    return result;
  }

  public static void collectAndSendExtendedMemoryReport(@NotNull final ComponentsSet componentsSet,
                                                        @NotNull final List<ComponentsSet.Component> exceededClusters,
                                                        @NotNull final Computable<WeakList<Object>> rootsComputable,
                                                        long summaryRequiredSubtreeSize) {
    HeapSnapshotStatistics extendedReportStats =
      new HeapSnapshotStatistics(
        new HeapTraverseConfig(componentsSet, /*collectHistograms=*/true, /*collectDisposerTreeInfo=*/true, 10, /*collectObjectTreesData=*/
                               true, summaryRequiredSubtreeSize, exceededClusters));
    StatusCode statusCode = new MemoryReportCollector(extendedReportStats).walkObjects(rootsComputable);

    for (ComponentsSet.Component cluster : exceededClusters) {
      StudioCrashReporter.getInstance().submit(extendedReportStats.asCrashReport(exceededClusters, cluster, statusCode), true);
    }
  }

  private static short getNextIterationId() {
    return ++ourIterationId;
  }

  static class HeapSnapshotPresentationConfig {
    final PresentationStyle sizePresentation;
    final boolean shouldLogSharedClusters;
    final boolean shouldLogRetainedSizes;

    HeapSnapshotPresentationConfig(PresentationStyle sizePresentation,
                                   boolean shouldLogSharedClusters,
                                   boolean shouldLogRetainedSizes) {
      this.sizePresentation = sizePresentation;
      this.shouldLogSharedClusters = shouldLogSharedClusters;
      this.shouldLogRetainedSizes = shouldLogRetainedSizes;
    }

    enum PresentationStyle {
      PLAIN_VALUES,
      OPTIMAL_UNITS,
    }
  }
}
