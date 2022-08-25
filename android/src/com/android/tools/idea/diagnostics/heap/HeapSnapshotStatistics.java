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

import com.google.common.collect.Lists;
import com.google.wireless.android.sdk.stats.MemoryUsageReportEvent;
import com.intellij.diagnostic.hprof.util.HeapReportUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

final class HeapSnapshotStatistics {

  @NotNull
  private final HeapSnapshotStatistics.ClusterObjectsStatistics myNonComponentStats = new ClusterObjectsStatistics();

  @NotNull
  private final HeapSnapshotStatistics.ClusterObjectsStatistics myTotalStats = new ClusterObjectsStatistics();
  @NotNull
  private final List<ComponentClusterObjectsStatistics> myComponentStats = Lists.newArrayList();
  @NotNull
  private final List<CategoryClusterObjectsStatistics> myCategoryComponentStats = Lists.newArrayList();

  @NotNull
  private final Int2ObjectMap<SharedClusterStatistics> myMaskToSharedComponentStats = new Int2ObjectOpenHashMap<>();

  @NotNull
  private final ComponentsSet myComponentsSet;
  int myMaxFieldsCacheSize = 0;
  int myMaxObjectsQueueSize = 0;
  // number of objects that were enumerated during the first traverse, but GCed after that and were not reached during the second pass
  int myEnumeratedGarbageCollectedObjects = 0;
  int myUnsuccessfulFieldAccessCounter = 0;
  int myHeapObjectCount = 0;

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

  public void addObjectSizeToSharedComponent(int sharedMask, long size) {
    if (!myMaskToSharedComponentStats.containsKey(sharedMask)) {
      List<Integer> components = Lists.newArrayList();
      processMask(sharedMask, (index) -> components.add(myComponentsSet.getComponents().get(index).getId()));
      myMaskToSharedComponentStats.put(sharedMask, new SharedClusterStatistics(components));
    }
    myMaskToSharedComponentStats.get(sharedMask).getStatistics().addOwnedObject(size);
  }

  public void addOwnedObjectSizeToComponent(int componentId, long size) {
    myComponentStats.get(componentId).addOwnedObject(size);
  }

  public void addObjectToTotal(long size) {
    myTotalStats.addOwnedObject(size);
  }

  public void addRetainedObjectSizeToCategoryComponent(int categoryId, long size) {
    myCategoryComponentStats.get(categoryId).addRetainedObject(size);
  }

  public void addOwnedObjectSizeToCategoryComponent(int categoryId, long size) {
    myCategoryComponentStats.get(categoryId).addOwnedObject(size);
  }

  public void addRetainedObjectSizeToComponent(int componentID, long size) {
    myComponentStats.get(componentID).addRetainedObject(size);
  }

  public void addNonComponentObject(long size) {
    myNonComponentStats.addOwnedObject(size);
  }

  private void printClusterStats(@NotNull final PrintWriter out,
                                 @NotNull final HeapSnapshotStatistics.ClusterObjectsStatistics.ObjectsStatistics statistics) {
    out.printf("    [%s/%d]\n", HeapReportUtils.INSTANCE.toShortStringAsCount(statistics.getTotalSizeOfObjects()),
               statistics.getObjectsNumber());
  }

  void print(@NotNull final PrintWriter out) {
    out.print("Total:\n");
    printClusterStats(out, myTotalStats.myOwnedClusterStat);

    out.printf("Categories:\n");
    for (CategoryClusterObjectsStatistics stat : myCategoryComponentStats) {
      out.printf("Category %s:\n", stat.getComponentCategory().getComponentCategoryLabel());
      printClusterStats(out, stat.getOwnedClusterStat());
      out.printf("  Retained stat:\n");
      printClusterStats(out, stat.getRetainedClusterStat());
    }

    out.print("Non component:\n");
    printClusterStats(out, myNonComponentStats.myOwnedClusterStat);

    for (ComponentClusterObjectsStatistics stat : myComponentStats) {
      out.printf("Component %s:\n", stat.getComponent().getComponentLabel());
      printClusterStats(out, stat.getOwnedClusterStat());
      out.printf("  Retained stat:\n");
      printClusterStats(out, stat.getRetainedClusterStat());
    }

    for (Map.Entry<Integer, SharedClusterStatistics> entry : myMaskToSharedComponentStats.entrySet()) {
      out.printf("Shared component %s:\n",
                 entry.getValue().getComponentKinds().stream().map(i -> myComponentsSet.getComponents().get(i).getComponentLabel()).collect(
                   Collectors.toList()));
      printClusterStats(out, entry.getValue().getStatistics().getOwnedClusterStat());
      out.printf("  Retained stat:\n");
      printClusterStats(out, entry.getValue().getStatistics().getRetainedClusterStat());
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
  public MemoryUsageReportEvent buildMemoryUsageReportEvent(StatusCode statusCode,
                                                            long executionTimeMs) {
    // TODO(viuginick): finish when studio_stats MemoryUsageReportEvent proto will be approved and cherry picked.
    MemoryUsageReportEvent.Builder builder = MemoryUsageReportEvent.newBuilder();
    return builder.build();
  }

  static class SharedClusterStatistics {
    @NotNull
    private final Collection<Integer> myComponentKinds;
    @NotNull
    private final ClusterObjectsStatistics myStatistics;

    private SharedClusterStatistics(@NotNull final Collection<Integer> components) {
      myComponentKinds = components;
      myStatistics = new ClusterObjectsStatistics();
    }

    @NotNull
    private ClusterObjectsStatistics getStatistics() {
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

    @NotNull
    private final ObjectsStatistics myRetainedClusterStat = new ObjectsStatistics();
    @NotNull
    private final ObjectsStatistics myOwnedClusterStat = new ObjectsStatistics();

    public void addOwnedObject(long size) {
      myOwnedClusterStat.addObject(size);
    }

    public void addRetainedObject(long size) {
      myRetainedClusterStat.addObject(size);
    }

    @NotNull
    public ObjectsStatistics getOwnedClusterStat() {
      return myOwnedClusterStat;
    }

    @NotNull
    public ObjectsStatistics getRetainedClusterStat() {
      return myRetainedClusterStat;
    }

    static class ObjectsStatistics {
      private int myObjectsNumber = 0;
      private long myTotalSizeOfObjects = 0;

      private void addObject(long size) {
        myObjectsNumber++;
        myTotalSizeOfObjects += size;
      }

      int getObjectsNumber() {
        return myObjectsNumber;
      }

      long getTotalSizeOfObjects() {
        return myTotalSizeOfObjects;
      }
    }
  }
}
