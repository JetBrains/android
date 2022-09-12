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
    myTotalStats = new ClusterObjectsStatistics.MemoryTrafficStatistics();
  @NotNull
  private final List<ComponentClusterObjectsStatistics> myComponentStats = Lists.newArrayList();
  @NotNull
  private final List<CategoryClusterObjectsStatistics> myCategoryComponentStats = Lists.newArrayList();

  @NotNull
  private final Long2ObjectMap<SharedClusterStatistics> myMaskToSharedComponentStats = new Long2ObjectOpenHashMap<>();

  @NotNull
  private final ComponentsSet myComponentsSet;
  int myMaxFieldsCacheSize = 0;
  int myMaxObjectsQueueSize = 0;
  // number of objects that were enumerated during the first traverse, but GCed after that and were not reached during the second pass
  int myEnumeratedGarbageCollectedObjects = 0;
  int myUnsuccessfulFieldAccessCounter = 0;
  int myHeapObjectCount = 0;
  private short myTraverseSessionId;

  public HeapSnapshotStatistics(@NotNull final ComponentsSet componentSet) {
    myComponentsSet = componentSet;
    for (ComponentsSet.Component component : componentSet.getComponents()) {
      myComponentStats.add(new ComponentClusterObjectsStatistics(component));
    }

    for (ComponentsSet.ComponentCategory category : componentSet.getComponentsCategories()) {
      myCategoryComponentStats.add(new CategoryClusterObjectsStatistics(category));
    }
  }

  @NotNull
  public List<ComponentClusterObjectsStatistics> getComponentStats() {
    return myComponentStats;
  }

  @NotNull
  public List<CategoryClusterObjectsStatistics> getCategoryComponentStats() {
    return myCategoryComponentStats;
  }

  public void addObjectSizeToSharedComponent(long sharedMask, long size, short objectAge) {
    if (!myMaskToSharedComponentStats.containsKey(sharedMask)) {
      List<Integer> components = Lists.newArrayList();
      processMask(sharedMask, (index) -> components.add(myComponentsSet.getComponents().get(index).getId()));
      myMaskToSharedComponentStats.put(sharedMask, new SharedClusterStatistics(components));
    }
    myMaskToSharedComponentStats.get(sharedMask).getStatistics().addObject(size, objectAge);
  }

  public void addOwnedObjectSizeToComponent(int componentId, long size, short objectAge) {
    myComponentStats.get(componentId).addOwnedObject(size, objectAge);
  }

  public void addObjectToTotal(long size, short objectAge) {
    myTotalStats.addObject(size, objectAge);
  }

  public void addRetainedObjectSizeToCategoryComponent(int categoryId, long size, short objectAge) {
    myCategoryComponentStats.get(categoryId).addRetainedObject(size, objectAge);
  }

  public void addOwnedObjectSizeToCategoryComponent(int categoryId, long size, short objectAge) {
    myCategoryComponentStats.get(categoryId).addOwnedObject(size, objectAge);
  }

  public void addRetainedObjectSizeToComponent(int componentID, long size, short objectAge) {
    myComponentStats.get(componentID).addRetainedObject(size, objectAge);
  }

  private void printClusterStats(@NotNull final PrintWriter out,
                                 @NotNull final ClusterObjectsStatistics.MemoryTrafficStatistics statistics) {
    out.printf("    [%s/%d]\n", HeapReportUtils.INSTANCE.toShortStringAsSize(statistics.myObjectsStat.getTotalSizeInBytes()),
               statistics.myObjectsStat.getObjectsCount());

    out.printf("    Newly allocated objects [%s/%d]\n",
               HeapReportUtils.INSTANCE.toShortStringAsSize(statistics.myNewObjectsStat.getTotalSizeInBytes()),
               statistics.myNewObjectsStat.getObjectsCount());

    for (int i = 0; i < ClusterObjectsStatistics.MAX_TRACKED_OBJECT_AGE; i++) {
      out.printf("    Objects allocated at least %d iterations before [%s/%d]\n", i + 1,
                 HeapReportUtils.INSTANCE.toShortStringAsSize(
                   statistics.myPreviousSnapshotsRemainedObjectsStats.get(i).getTotalSizeInBytes()),
                 statistics.myPreviousSnapshotsRemainedObjectsStats.get(i).getObjectsCount());
    }
  }

  void print(@NotNull final PrintWriter out) {
    out.print("Total:\n");
    printClusterStats(out, myTotalStats);

    out.printf("Categories:\n");
    for (CategoryClusterObjectsStatistics stat : myCategoryComponentStats) {
      out.printf("Category %s:\n", stat.getComponentCategory().getComponentCategoryLabel());
      printClusterStats(out, stat.getOwnedClusterStat());
      out.printf("  Retained stat:\n");
      printClusterStats(out, stat.getRetainedClusterStat());
    }

    for (ComponentClusterObjectsStatistics stat : myComponentStats) {
      out.printf("Component %s:\n", stat.getComponent().getComponentLabel());
      printClusterStats(out, stat.getOwnedClusterStat());
      out.printf("  Retained stat:\n");
      printClusterStats(out, stat.getRetainedClusterStat());
    }

    for (SharedClusterStatistics sharedClusterStatistics : myMaskToSharedComponentStats.values()) {
      out.printf("Shared component %s:\n",
                 sharedClusterStatistics.getComponentKinds().stream().map(i -> myComponentsSet.getComponents().get(i).getComponentLabel())
                   .collect(
                     Collectors.toList()));
      printClusterStats(out, sharedClusterStatistics.getStatistics());
    }
  }

  @NotNull
  public ComponentsSet getComponentsSet() {
    return myComponentsSet;
  }

  public void updateMaxFieldsCacheSize(int currentFieldSize) {
    myMaxFieldsCacheSize = Math.max(myMaxFieldsCacheSize, currentFieldSize);
  }

  public void updateMaxObjectsQueueSize(int currentObjectsQueueSize) {
    myMaxObjectsQueueSize = Math.max(myMaxObjectsQueueSize, currentObjectsQueueSize);
  }

  public void incrementGarbageCollectedObjectsCounter() {
    myEnumeratedGarbageCollectedObjects++;
  }

  public void incrementUnsuccessfulFieldAccessCounter() {
    myUnsuccessfulFieldAccessCounter++;
  }

  public void setHeapObjectCount(int heapObjectCount) {
    myHeapObjectCount = heapObjectCount;
  }

  @NotNull
  private MemoryUsageReportEvent.ObjectsStatistics buildObjectStatistics(@NotNull final
                                                                         ClusterObjectsStatistics.MemoryTrafficStatistics.ObjectsStatistics objectsStatistics) {
    return MemoryUsageReportEvent.ObjectsStatistics.newBuilder().setObjectsCount(objectsStatistics.getObjectsCount())
      .setTotalSizeBytes(objectsStatistics.getTotalSizeInBytes()).build();
  }

  @NotNull
  private MemoryUsageReportEvent.MemoryTrafficStatistics buildMemoryTrafficStatistics(@NotNull final ClusterObjectsStatistics.MemoryTrafficStatistics memoryTrafficStatistics) {
    return MemoryUsageReportEvent.MemoryTrafficStatistics.newBuilder()
      .setTotalStats(buildObjectStatistics(memoryTrafficStatistics.getObjectsStatistics()))
      .setNewGenerationStats(buildObjectStatistics(memoryTrafficStatistics.getNewObjectsStatistics()))
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

    for (ComponentClusterObjectsStatistics componentStat : myComponentStats) {
      builder.addComponentStats(
        MemoryUsageReportEvent.ClusterMemoryUsage.newBuilder().setLabel(componentStat.getComponent().getComponentLabel())
          .setStats(buildClusterObjectsStatistics(componentStat)));
    }

    for (SharedClusterStatistics sharedStat : myMaskToSharedComponentStats.values()) {
      builder.addSharedComponentStats(MemoryUsageReportEvent.SharedClusterMemoryUsage.newBuilder().addAllIds(sharedStat.getComponentKinds())
                                        .setStats(buildMemoryTrafficStatistics(sharedStat.getStatistics())));
    }
    for (CategoryClusterObjectsStatistics categoryStat : myCategoryComponentStats) {
      builder.addComponentCategoryStats(
        MemoryUsageReportEvent.ClusterMemoryUsage.newBuilder().setLabel(categoryStat.getComponentCategory().getComponentCategoryLabel())
          .setStats(buildClusterObjectsStatistics(categoryStat)));
    }

    builder.setMetadata(
      MemoryUsageReportEvent.MemoryUsageCollectionMetadata.newBuilder().setStatusCode(statusCode)
        .setTotalHeapObjectsStats(buildMemoryTrafficStatistics(myTotalStats))
        .setFieldCacheCountPeak(myMaxFieldsCacheSize)
        .setObjectQueueLengthPeak(myMaxObjectsQueueSize)
        .setGarbageCollectedBefore2PassCount(myEnumeratedGarbageCollectedObjects)
        .setCollectionTimeSeconds((double)executionTimeMs / (double)1000)
        .setIsInPowerSaveMode(PowerSaveMode.isEnabled())
        .setUnsuccessfulFieldAccessesCount(myUnsuccessfulFieldAccessCounter)
        .setCollectionStartTimestampSeconds((double)executionStartMs / (double)1000)
        .setCollectionIteration(myTraverseSessionId));

    return builder.build();
  }

  public void setTraverseSessionId(short traverseSessionId) {
    myTraverseSessionId = traverseSessionId;
  }

  static class SharedClusterStatistics {
    @NotNull
    private final Collection<Integer> myComponentKinds;
    @NotNull
    private final ClusterObjectsStatistics.MemoryTrafficStatistics myStatistics;

    private SharedClusterStatistics(@NotNull final Collection<Integer> components) {
      myComponentKinds = components;
      myStatistics = new ClusterObjectsStatistics.MemoryTrafficStatistics();
    }

    @NotNull
    private ClusterObjectsStatistics.MemoryTrafficStatistics getStatistics() {
      return myStatistics;
    }

    @NotNull
    Collection<Integer> getComponentKinds() {
      return myComponentKinds;
    }
  }

  static class ComponentClusterObjectsStatistics extends ClusterObjectsStatistics {
    @NotNull
    private final ComponentsSet.Component myComponent;

    private ComponentClusterObjectsStatistics(final ComponentsSet.@NotNull Component component) {
      myComponent = component;
    }

    @NotNull
    ComponentsSet.Component getComponent() {
      return myComponent;
    }
  }

  static class CategoryClusterObjectsStatistics extends ClusterObjectsStatistics {
    @NotNull
    private final ComponentsSet.ComponentCategory myComponentCategory;

    private CategoryClusterObjectsStatistics(@NotNull final ComponentsSet.ComponentCategory category) {
      myComponentCategory = category;
    }

    @NotNull
    ComponentsSet.ComponentCategory getComponentCategory() {
      return myComponentCategory;
    }
  }

  static class ClusterObjectsStatistics {

    public static final int MAX_TRACKED_OBJECT_AGE = 4;
    @NotNull
    private final MemoryTrafficStatistics myRetainedClusterStat = new MemoryTrafficStatistics();
    @NotNull
    private final MemoryTrafficStatistics myOwnedClusterStat = new MemoryTrafficStatistics();

    public void addOwnedObject(long size, short objectAge) {
      myOwnedClusterStat.addObject(size, objectAge);
    }

    public void addRetainedObject(long size, short objectAge) {
      myRetainedClusterStat.addObject(size, objectAge);
    }

    @NotNull
    public MemoryTrafficStatistics getOwnedClusterStat() {
      return myOwnedClusterStat;
    }

    @NotNull
    public MemoryTrafficStatistics getRetainedClusterStat() {
      return myRetainedClusterStat;
    }

    static class MemoryTrafficStatistics {
      @NotNull
      private final ObjectsStatistics myObjectsStat = new ObjectsStatistics();
      @NotNull
      private final ObjectsStatistics myNewObjectsStat = new ObjectsStatistics();
      @NotNull
      private final List<ObjectsStatistics> myPreviousSnapshotsRemainedObjectsStats =
        IntStream.range(0, MAX_TRACKED_OBJECT_AGE).mapToObj(x -> new ObjectsStatistics()).collect(Collectors.toList());

      public void addObject(long size, short objectAge) {
        myObjectsStat.addObject(size);

        if (objectAge == 0) {
          myNewObjectsStat.addObject(size);
          return;
        }
        if (StudioFlags.MEMORY_TRAFFIC_TRACK_OLDER_GENERATIONS.get()) {
          if (objectAge >= MAX_TRACKED_OBJECT_AGE) {
            objectAge = MAX_TRACKED_OBJECT_AGE;
          }
          myPreviousSnapshotsRemainedObjectsStats.get(objectAge - 1).addObject(size);
        }
      }

      public ObjectsStatistics getObjectsStatistics() {
        return myObjectsStat;
      }

      public ObjectsStatistics getNewObjectsStatistics() {
        return myNewObjectsStat;
      }

      public List<ObjectsStatistics> getPreviousSnapshotsRemainedObjectsStatistics() {
        return myPreviousSnapshotsRemainedObjectsStats;
      }

      static class ObjectsStatistics {
        private int myObjectsCount = 0;
        private long myTotalSizeInBytes = 0;

        private void addObject(long size) {
          myObjectsCount++;
          myTotalSizeInBytes += size;
        }

        int getObjectsCount() {
          return myObjectsCount;
        }

        long getTotalSizeInBytes() {
          return myTotalSizeInBytes;
        }
      }
    }
  }
}
