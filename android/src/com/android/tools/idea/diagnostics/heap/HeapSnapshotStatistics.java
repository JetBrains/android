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
import static com.google.wireless.android.sdk.stats.MemoryUsageReportEvent.MemoryUsageCollectionMetadata.StatusCode;

import com.android.tools.idea.flags.StudioFlags;
import com.google.common.collect.Lists;
import com.google.wireless.android.sdk.stats.MemoryUsageReportEvent;
import com.intellij.diagnostic.hprof.util.HeapReportUtils;
import com.intellij.ide.PowerSaveMode;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.jetbrains.annotations.NotNull;

final class HeapSnapshotStatistics {

  @NotNull
  private final ClusterObjectsStatistics.MemoryTrafficStatistics
    totalStats = new ClusterObjectsStatistics.MemoryTrafficStatistics();
  @NotNull
  private final List<ComponentClusterObjectsStatistics> componentStats = Lists.newArrayList();
  @NotNull
  private final List<CategoryClusterObjectsStatistics> categoryComponentStats =
    Lists.newArrayList();
  @NotNull
  private final Long2ObjectMap<SharedClusterStatistics> maskToSharedComponentStats =
    new Long2ObjectOpenHashMap<>();

  @NotNull
  private final ComponentsSet componentsSet;
  int maxFieldsCacheSize = 0;
  int maxObjectsQueueSize = 0;
  // number of objects that were enumerated during the first traverse, but GCed after that and were
  // not reached during the second pass
  int enumeratedGarbageCollectedObjects = 0;
  int unsuccessfulFieldAccessCounter = 0;
  int heapObjectCount = 0;
  private short traverseSessionId;

  public HeapSnapshotStatistics(@NotNull final ComponentsSet componentSet) {
    componentsSet = componentSet;
    for (ComponentsSet.Component component : componentSet.getComponents()) {
      componentStats.add(new ComponentClusterObjectsStatistics(component));
    }

    for (ComponentsSet.ComponentCategory category : componentSet.getComponentsCategories()) {
      categoryComponentStats.add(new CategoryClusterObjectsStatistics(category));
    }
  }

  @NotNull
  public List<ComponentClusterObjectsStatistics> getComponentStats() {
    return componentStats;
  }

  @NotNull
  public List<CategoryClusterObjectsStatistics> getCategoryComponentStats() {
    return categoryComponentStats;
  }

  public void addObjectSizeToSharedComponent(long sharedMask, long size, short objectAge) {
    if (!maskToSharedComponentStats.containsKey(sharedMask)) {
      List<Integer> components = Lists.newArrayList();
      processMask(sharedMask,
                  (index) -> components.add(componentsSet.getComponents().get(index).getId()));
      maskToSharedComponentStats.put(sharedMask, new SharedClusterStatistics(components));
    }
    maskToSharedComponentStats.get(sharedMask).getStatistics().addObject(size, objectAge);
  }

  public void addOwnedObjectSizeToComponent(int componentId, long size, short objectAge) {
    componentStats.get(componentId).addOwnedObject(size, objectAge);
  }

  public void addObjectToTotal(long size, short objectAge) {
    totalStats.addObject(size, objectAge);
  }

  public void addRetainedObjectSizeToCategoryComponent(int categoryId, long size, short objectAge) {
    categoryComponentStats.get(categoryId).addRetainedObject(size, objectAge);
  }

  public void addOwnedObjectSizeToCategoryComponent(int categoryId, long size, short objectAge) {
    categoryComponentStats.get(categoryId).addOwnedObject(size, objectAge);
  }

  public void addRetainedObjectSizeToComponent(int componentID, long size, short objectAge) {
    componentStats.get(componentID).addRetainedObject(size, objectAge);
  }

  private void printClusterStats(@NotNull final PrintWriter out,
                                 @NotNull final ClusterObjectsStatistics.MemoryTrafficStatistics statistics) {
    out.printf("    [%s/%d]\n", HeapReportUtils.INSTANCE.toShortStringAsCount(
                 statistics.objectsStat.getTotalSizeInBytes()),
               statistics.objectsStat.getObjectsCount());

    out.printf("    Newly allocated objects [%s/%d]\n",
               HeapReportUtils.INSTANCE.toShortStringAsCount(
                 statistics.newObjectsStat.getTotalSizeInBytes()),
               statistics.newObjectsStat.getObjectsCount());

    for (int i = 0; i < ClusterObjectsStatistics.MAX_TRACKED_OBJECT_AGE; i++) {
      out.printf("    Objects allocated at least %d iterations before [%s/%d]\n", i + 1,
                 HeapReportUtils.INSTANCE.toShortStringAsCount(
                   statistics.previousSnapshotsRemainedObjectsStats.get(i).getTotalSizeInBytes()),
                 statistics.previousSnapshotsRemainedObjectsStats.get(i).getObjectsCount());
    }
  }

  void print(@NotNull final PrintWriter out) {
    out.print("Total:\n");
    printClusterStats(out, totalStats);

    out.printf("Categories:\n");
    for (CategoryClusterObjectsStatistics stat : categoryComponentStats) {
      out.printf("Category %s:\n", stat.getComponentCategory().getComponentCategoryLabel());
      printClusterStats(out, stat.getOwnedClusterStat());
      out.printf("  Retained stat:\n");
      printClusterStats(out, stat.getRetainedClusterStat());
    }

    for (ComponentClusterObjectsStatistics stat : componentStats) {
      out.printf("Component %s:\n", stat.getComponent().getComponentLabel());
      printClusterStats(out, stat.getOwnedClusterStat());
      out.printf("  Retained stat:\n");
      printClusterStats(out, stat.getRetainedClusterStat());
    }

    for (SharedClusterStatistics sharedClusterStatistics : maskToSharedComponentStats.values()) {
      out.printf("Shared component %s:\n",
                 sharedClusterStatistics.getComponentKinds().stream()
                   .map(i -> componentsSet.getComponents().get(i).getComponentLabel())
                   .collect(
                     Collectors.toList()));
      printClusterStats(out, sharedClusterStatistics.getStatistics());
    }
  }

  @NotNull
  public ComponentsSet getComponentsSet() {
    return componentsSet;
  }

  public void updateMaxFieldsCacheSize(int currentFieldSize) {
    maxFieldsCacheSize = Math.max(maxFieldsCacheSize, currentFieldSize);
  }

  public void updateMaxObjectsQueueSize(int currentObjectsQueueSize) {
    maxObjectsQueueSize = Math.max(maxObjectsQueueSize, currentObjectsQueueSize);
  }

  public void incrementGarbageCollectedObjectsCounter() {
    enumeratedGarbageCollectedObjects++;
  }

  public void incrementUnsuccessfulFieldAccessCounter() {
    unsuccessfulFieldAccessCounter++;
  }

  public void setHeapObjectCount(int heapObjectCount) {
    this.heapObjectCount = heapObjectCount;
  }

  @NotNull
  private MemoryUsageReportEvent.ObjectsStatistics buildObjectStatistics(@NotNull final
                                                                         ClusterObjectsStatistics.MemoryTrafficStatistics.ObjectsStatistics objectsStatistics) {
    return MemoryUsageReportEvent.ObjectsStatistics.newBuilder()
      .setObjectsCount(objectsStatistics.getObjectsCount())
      .setTotalSizeBytes(objectsStatistics.getTotalSizeInBytes()).build();
  }

  @NotNull
  private MemoryUsageReportEvent.MemoryTrafficStatistics buildMemoryTrafficStatistics(@NotNull final ClusterObjectsStatistics.MemoryTrafficStatistics memoryTrafficStatistics) {
    return MemoryUsageReportEvent.MemoryTrafficStatistics.newBuilder()
      .setTotalStats(buildObjectStatistics(memoryTrafficStatistics.getObjectsStatistics()))
      .setNewGenerationStats(
        buildObjectStatistics(memoryTrafficStatistics.getNewObjectsStatistics()))
      .build();
  }

  @NotNull
  private MemoryUsageReportEvent.ClusterObjectsStatistics buildClusterObjectsStatistics(@NotNull final ClusterObjectsStatistics componentStatistics) {
    return MemoryUsageReportEvent.ClusterObjectsStatistics.newBuilder()
      .setOwnedClusterStats(buildMemoryTrafficStatistics(componentStatistics.getOwnedClusterStat()))
      .setRetainedClusterStats(buildMemoryTrafficStatistics(
        componentStatistics.getRetainedClusterStat())).build();
  }

  @NotNull
  public MemoryUsageReportEvent buildMemoryUsageReportEvent(StatusCode statusCode,
                                                            long executionTimeMs,
                                                            long executionStartMs) {
    MemoryUsageReportEvent.Builder builder = MemoryUsageReportEvent.newBuilder();

    for (ComponentClusterObjectsStatistics componentStat : componentStats) {
      builder.addComponentStats(
        MemoryUsageReportEvent.ClusterMemoryUsage.newBuilder()
          .setLabel(componentStat.getComponent().getComponentLabel())
          .setStats(buildClusterObjectsStatistics(componentStat)));
    }

    for (SharedClusterStatistics sharedStat : maskToSharedComponentStats.values()) {
      builder.addSharedComponentStats(MemoryUsageReportEvent.SharedClusterMemoryUsage.newBuilder()
                                        .addAllIds(sharedStat.getComponentKinds())
                                        .setStats(buildMemoryTrafficStatistics(
                                          sharedStat.getStatistics())));
    }
    for (CategoryClusterObjectsStatistics categoryStat : categoryComponentStats) {
      builder.addComponentCategoryStats(
        MemoryUsageReportEvent.ClusterMemoryUsage.newBuilder()
          .setLabel(categoryStat.getComponentCategory().getComponentCategoryLabel())
          .setStats(buildClusterObjectsStatistics(categoryStat)));
    }

    builder.setMetadata(
      MemoryUsageReportEvent.MemoryUsageCollectionMetadata.newBuilder().setStatusCode(statusCode)
        .setTotalHeapObjectsStats(buildMemoryTrafficStatistics(totalStats))
        .setFieldCacheCountPeak(maxFieldsCacheSize)
        .setObjectQueueLengthPeak(maxObjectsQueueSize)
        .setGarbageCollectedBefore2PassCount(enumeratedGarbageCollectedObjects)
        .setCollectionTimeSeconds((double)executionTimeMs / (double)1000)
        .setIsInPowerSaveMode(PowerSaveMode.isEnabled())
        .setUnsuccessfulFieldAccessesCount(unsuccessfulFieldAccessCounter)
        .setCollectionStartTimestampSeconds((double)executionStartMs / (double)1000)
        .setCollectionIteration(traverseSessionId));

    return builder.build();
  }

  public void setTraverseSessionId(short traverseSessionId) {
    this.traverseSessionId = traverseSessionId;
  }

  static class SharedClusterStatistics {
    @NotNull
    private final Collection<Integer> componentKinds;
    @NotNull
    private final ClusterObjectsStatistics.MemoryTrafficStatistics statistics;

    private SharedClusterStatistics(@NotNull final Collection<Integer> components) {
      componentKinds = components;
      statistics = new ClusterObjectsStatistics.MemoryTrafficStatistics();
    }

    @NotNull
    private ClusterObjectsStatistics.MemoryTrafficStatistics getStatistics() {
      return statistics;
    }

    @NotNull
    Collection<Integer> getComponentKinds() {
      return componentKinds;
    }
  }

  static class ComponentClusterObjectsStatistics extends ClusterObjectsStatistics {
    @NotNull
    private final ComponentsSet.Component component;

    private ComponentClusterObjectsStatistics(final ComponentsSet.@NotNull Component component) {
      this.component = component;
    }

    @NotNull
    ComponentsSet.Component getComponent() {
      return component;
    }
  }

  static class CategoryClusterObjectsStatistics extends ClusterObjectsStatistics {
    @NotNull
    private final ComponentsSet.ComponentCategory componentCategory;

    private CategoryClusterObjectsStatistics(@NotNull final ComponentsSet.ComponentCategory category) {
      componentCategory = category;
    }

    @NotNull
    ComponentsSet.ComponentCategory getComponentCategory() {
      return componentCategory;
    }
  }

  static class ClusterObjectsStatistics {

    public static final int MAX_TRACKED_OBJECT_AGE = 4;
    @NotNull
    private final MemoryTrafficStatistics retainedClusterStat = new MemoryTrafficStatistics();
    @NotNull
    private final MemoryTrafficStatistics ownedClusterStat = new MemoryTrafficStatistics();

    public void addOwnedObject(long size, short objectAge) {
      ownedClusterStat.addObject(size, objectAge);
    }

    public void addRetainedObject(long size, short objectAge) {
      retainedClusterStat.addObject(size, objectAge);
    }

    @NotNull
    public MemoryTrafficStatistics getOwnedClusterStat() {
      return ownedClusterStat;
    }

    @NotNull
    public MemoryTrafficStatistics getRetainedClusterStat() {
      return retainedClusterStat;
    }

    static class MemoryTrafficStatistics {
      @NotNull
      private final ObjectsStatistics objectsStat = new ObjectsStatistics();
      @NotNull
      private final ObjectsStatistics newObjectsStat = new ObjectsStatistics();
      @NotNull
      private final List<ObjectsStatistics> previousSnapshotsRemainedObjectsStats =
        IntStream.range(0, MAX_TRACKED_OBJECT_AGE).mapToObj(x -> new ObjectsStatistics())
          .collect(Collectors.toList());

      public void addObject(long size, short objectAge) {
        objectsStat.addObject(size);

        if (objectAge == 0) {
          newObjectsStat.addObject(size);
          return;
        }
        if (StudioFlags.MEMORY_TRAFFIC_TRACK_OLDER_GENERATIONS.get()) {
          if (objectAge >= MAX_TRACKED_OBJECT_AGE) {
            objectAge = MAX_TRACKED_OBJECT_AGE;
          }
          previousSnapshotsRemainedObjectsStats.get(objectAge - 1).addObject(size);
        }
      }

      public ObjectsStatistics getObjectsStatistics() {
        return objectsStat;
      }

      public ObjectsStatistics getNewObjectsStatistics() {
        return newObjectsStat;
      }

      public List<ObjectsStatistics> getPreviousSnapshotsRemainedObjectsStatistics() {
        return previousSnapshotsRemainedObjectsStats;
      }

      static class ObjectsStatistics {
        private int objectsCount = 0;
        private long totalSizeInBytes = 0;

        private void addObject(long size) {
          objectsCount++;
          totalSizeInBytes += size;
        }

        int getObjectsCount() {
          return objectsCount;
        }

        long getTotalSizeInBytes() {
          return totalSizeInBytes;
        }
      }
    }
  }
}
