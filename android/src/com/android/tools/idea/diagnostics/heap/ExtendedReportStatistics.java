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

import static com.android.tools.idea.diagnostics.heap.HeapSnapshotTraverse.HeapSnapshotPresentationConfig.SizePresentationStyle.OPTIMAL_UNITS;

import com.intellij.openapi.util.Pair;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;

public class ExtendedReportStatistics {

  @NotNull final List<ClusterHistogram> componentHistograms;

  @NotNull final List<CategoryHistogram> categoryHistograms;

  @NotNull final Long2ObjectMap<ClusterHistogram> sharedClustersHistograms;

  private int totalNumberOfDisposedButReferencedObjects = 0;
  private int totalDisposerTreeSize = 0;

  final Int2ObjectMap<List<Integer>> categoryRootOwnershipSets = new Int2ObjectOpenHashMap<>();

  @NotNull
  private final Map<String, ObjectsStatistics> disposedButReferencedObjectsClasses = new HashMap<>();

  public ExtendedReportStatistics(@NotNull final HeapTraverseConfig config) {
    this.componentHistograms = new ArrayList<>();
    this.categoryHistograms = new ArrayList<>();
    this.sharedClustersHistograms = new Long2ObjectOpenHashMap<>();

    int componentIndex = 0;
    for (ComponentsSet.Component component : config.getComponentsSet().getComponents()) {
      componentHistograms.add(new ClusterHistogram(ClusterHistogram.ClusterType.COMPONENT));
      assert component.getId() == componentIndex;
      componentIndex++;
    }
    int componentCategoryIndex = 0;
    for (ComponentsSet.ComponentCategory componentCategory : config.getComponentsSet().getComponentsCategories()) {
      categoryHistograms.add(new CategoryHistogram());
      assert componentCategory.getId() == componentCategoryIndex;
      componentCategoryIndex++;
    }

    categoryRootOwnershipSets.put(0, Collections.emptyList());
  }

  void addClassNameToComponentOwnedHistogram(@NotNull final ComponentsSet.Component component,
                                             @NotNull final String className,
                                             long size,
                                             boolean isRoot) {
    componentHistograms.get(component.getId()).addObjectClassName(className, size, isRoot);
  }

  void addClassNameToCategoryOwnedHistogram(@NotNull final ComponentsSet.ComponentCategory componentCategory,
                                            @NotNull final String className,
                                            long size,
                                            boolean isRoot) {
    categoryHistograms.get(componentCategory.getId()).addObjectClassName(className, size, isRoot);
  }

  public void addClassNameToSharedClusterHistogram(@NotNull final HeapSnapshotStatistics.SharedClusterStatistics sharedClusterStatistics,
                                                   @NotNull String className,
                                                   long size,
                                                   boolean isMergePoint) {
    sharedClustersHistograms.putIfAbsent(sharedClusterStatistics.componentsMask,
                                         new ClusterHistogram(ClusterHistogram.ClusterType.SHARED_CLUSTER));
    sharedClustersHistograms.get(sharedClusterStatistics.componentsMask).addObjectClassName(className, size, isMergePoint);
  }

  public void logCategoryHistogram(@NotNull Consumer<String> writer, @NotNull final ComponentsSet.ComponentCategory componentCategory) {
    categoryHistograms.get(componentCategory.getId()).print(writer);
  }

  public void logComponentHistogram(@NotNull Consumer<String> writer, @NotNull final ComponentsSet.Component component) {
    componentHistograms.get(component.getId()).print(writer);
  }

  public void logSharedClusterHistogram(@NotNull Consumer<String> writer,
                                        @NotNull final HeapSnapshotStatistics.SharedClusterStatistics sharedClusterStatistics) {
    sharedClustersHistograms.get(sharedClusterStatistics.componentsMask).print(writer);
  }

  public void addDisposedButReferencedObject(long size, String className) {
    totalNumberOfDisposedButReferencedObjects++;
    disposedButReferencedObjectsClasses.putIfAbsent(className, new ObjectsStatistics());
    disposedButReferencedObjectsClasses.get(className).addObject(size);
  }

  public void setDisposerTreeSize(int size) {
    totalDisposerTreeSize = size;
  }

  public void logDisposerTreeReport(@NotNull Consumer<String> writer) {
    writer.accept("Disposer tree size: " + totalDisposerTreeSize);
    writer.accept("Total number of disposed but strong referenced objects: " + totalNumberOfDisposedButReferencedObjects);
    for (String className : disposedButReferencedObjectsClasses.keySet()) {
      writer.accept(
        HeapTraverseUtil.getObjectsStatsPresentation(disposedButReferencedObjectsClasses.get(className), OPTIMAL_UNITS) + " " + className);
    }
  }

  public void addOwnedObjectSizeToCategoryRoots(int categoryId, int hashCode, long currentObjectSize) {
    if (!categoryRootOwnershipSets.containsKey(hashCode)) {
      return;
    }
    CategoryHistogram categoryHistogram = categoryHistograms.get(categoryId);

    for (int rootId : categoryRootOwnershipSets.get(hashCode)) {
      categoryHistogram.addObjectOwnedByRoot(rootId, currentObjectSize);
    }
  }

  public void addOwnedObjectSizeToComponentRoots(@NotNull final ComponentsSet.Component component,
                                                 int hashCode,
                                                 long currentObjectSize) {
    if (!categoryRootOwnershipSets.containsKey(hashCode)) {
      return;
    }
    List<Integer> rootIds = categoryRootOwnershipSets.get(hashCode);
    CategoryHistogram categoryHistogram = categoryHistograms.get(component.getComponentCategory().getId());

    for (int rootId : rootIds) {
      Pair<Integer, Integer> componentAndRootId = categoryHistogram.categoryRootIdToComponentRootId.get(rootId);
      if (componentAndRootId == null || componentAndRootId.first != component.getId()) {
        continue;
      }
      componentHistograms.get(componentAndRootId.first).addObjectOwnedByRoot(componentAndRootId.second, currentObjectSize);
    }
  }

  private static int getHashCode(@NotNull final List<Integer> elements) {
    int result = 1;
    for (int element : elements) {
      result = 239017 * result + element;
    }

    return result;
  }

  public int getOwningRootsSetHashcode(int rootId) {
    List<Integer> rootIdList = List.of(rootId);
    int hashCode = getHashCode(rootIdList);
    categoryRootOwnershipSets.putIfAbsent(hashCode, rootIdList);
    return hashCode;
  }

  public int joinOwningRootsAndReturnHashcode(int hashcode1, int hashcode2) {
    List<Integer> l1 = categoryRootOwnershipSets.get(hashcode1);
    List<Integer> l2 = categoryRootOwnershipSets.get(hashcode2);
    HashSet<Integer> join = new HashSet<>(l1);
    join.addAll(l2);
    List<Integer> joinList = join.stream().toList();
    int hashCode = getHashCode(joinList);

    categoryRootOwnershipSets.putIfAbsent(hashCode, joinList);
    return hashCode;
  }

  static class CategoryHistogram extends ClusterHistogram {
    @NotNull final Int2ObjectMap<Pair<Integer, Integer>> categoryRootIdToComponentRootId = new Int2ObjectOpenHashMap<>();

    public CategoryHistogram() {
      super(ClusterType.CATEGORY);
    }
  }

  static class ClusterHistogram {

    enum ClusterType {
      COMPONENT,
      CATEGORY,
      SHARED_CLUSTER
    }

    private final static int HISTOGRAM_PRINT_LIMIT = 50;

    @NotNull final Map<String, ObjectsStatistics> histogram =
      new HashMap<>();
    @NotNull final List<ClusterRootStatistics> rootHistograms = new ArrayList<>();
    @NotNull final Map<String, Integer> rootClassNameToId = new HashMap<>();

    @NotNull
    private final ClusterType clusterType;

    public ClusterHistogram(@NotNull final ClusterType clusterType) {
      this.clusterType = clusterType;
    }

    void registerClusterRootIfNeeded(@NotNull final String className) {
      if (rootClassNameToId.containsKey(className)) {
        return;
      }
      rootHistograms.add(new ClusterRootStatistics(className));
      rootClassNameToId.put(className, rootClassNameToId.size());
    }

    public void addObjectClassName(@NotNull final String className, long size, boolean isRoot) {
      histogram.putIfAbsent(className, new ObjectsStatistics());
      histogram.get(className).addObject(size);
      if (isRoot) {
        registerClusterRootIfNeeded(className);
        rootHistograms.get(rootClassNameToId.get(className)).addObject(size);
      }
    }

    public void addObjectOwnedByRoot(int rootId, long currentObjectSize) {
      rootHistograms.get(rootId).addObjectToSubtree(currentObjectSize);
    }

    private boolean classNameIsStudioSource(@NotNull final String className) {
      return className.startsWith("com.android.") ||
             HeapSnapshotTraverse.isPlatformObject(className);
    }

    public void print(Consumer<String> writer) {
      writer.accept("      Histogram:");
      histogram.entrySet().stream()
        .sorted(Comparator.comparingLong((Map.Entry<String, ObjectsStatistics> a) -> a.getValue().getTotalSizeInBytes()).reversed())
        .limit(HISTOGRAM_PRINT_LIMIT)
        .forEach(e -> writer.accept(String.format(Locale.US, "        %s: %s",
                                                  HeapTraverseUtil.getObjectsStatsPresentation(e.getValue(), OPTIMAL_UNITS),
                                                  e.getKey())));

      writer.accept("      Studio objects histogram:");
      histogram.entrySet().stream().filter(e -> classNameIsStudioSource(e.getKey()))
        .sorted(Comparator.comparingLong((Map.Entry<String, ObjectsStatistics> a) -> a.getValue().getTotalSizeInBytes()).reversed())
        .limit(HISTOGRAM_PRINT_LIMIT)
        .forEach(e -> writer.accept(String.format(Locale.US, "        %s: %s",
                                                  HeapTraverseUtil.getObjectsStatsPresentation(e.getValue(), OPTIMAL_UNITS),
                                                  e.getKey())));

      switch (clusterType) {
        case CATEGORY -> writer.accept("      Category roots histogram:");
        case COMPONENT -> writer.accept("      Component roots histogram:");
        case SHARED_CLUSTER -> writer.accept("      Shared cluster merge-points histogram:");
      }
      rootHistograms.stream()
        .sorted(
          Comparator.comparingLong(
            (ClusterRootStatistics a) -> a.getObjectsStatistics().getObjectsCount()).reversed())
        .limit(HISTOGRAM_PRINT_LIMIT)
        .forEach(e -> writer.accept(String.format(Locale.US, "        %s[%s]: %s",
                                                  HeapTraverseUtil.getObjectsStatsPresentation(e.getObjectsStatistics(), OPTIMAL_UNITS),
                                                  HeapTraverseUtil.getObjectsStatsPresentation(e.getSubtree(), OPTIMAL_UNITS),
                                                  e.getRootObjectClassName())));
    }
  }
}
