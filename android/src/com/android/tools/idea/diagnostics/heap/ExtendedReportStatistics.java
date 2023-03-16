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

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;

public class ExtendedReportStatistics {

  @NotNull final List<ClassNameHistogram> componentHistograms;

  @NotNull final List<ClassNameHistogram> categoryHistograms;

  @NotNull final Long2ObjectMap<ClassNameHistogram> sharedClustersHistograms;

  private int totalNumberOfDisposedButReferencedObjects = 0;
  private int totalDisposerTreeSize = 0;

  @NotNull
  private final Map<String, ObjectsStatistics> disposedButReferencedObjectsClasses = Maps.newHashMap();

  public ExtendedReportStatistics(@NotNull final HeapTraverseConfig config) {
    this.componentHistograms = Lists.newArrayList();
    this.categoryHistograms = Lists.newArrayList();
    this.sharedClustersHistograms = new Long2ObjectOpenHashMap<>();

    int componentIndex = 0;
    for (ComponentsSet.Component component : config.getComponentsSet().getComponents()) {
      componentHistograms.add(new ClassNameHistogram(ClassNameHistogram.ClusterType.COMPONENT));
      assert component.getId() == componentIndex;
      componentIndex++;
    }
    int componentCategoryIndex = 0;
    for (ComponentsSet.ComponentCategory componentCategory : config.getComponentsSet().getComponentsCategories()) {
      categoryHistograms.add(new ClassNameHistogram(ClassNameHistogram.ClusterType.CATEGORY));
      assert componentCategory.getId() == componentCategoryIndex;
      componentCategoryIndex++;
    }
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
                                         new ClassNameHistogram(ClassNameHistogram.ClusterType.SHARED_CLUSTER));
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

  static class ClassNameHistogram {

    enum ClusterType {
      COMPONENT,
      CATEGORY,
      SHARED_CLUSTER
    }

    private final static int HISTOGRAM_PRINT_LIMIT = 50;

    @NotNull final Map<String, ObjectsStatistics> histogram =
      Maps.newHashMap();
    @NotNull final Map<String, ObjectsStatistics> rootsHistogram =
      Maps.newHashMap();
    @NotNull
    private final ClusterType clusterType;

    public ClassNameHistogram(@NotNull final ClusterType clusterType) {
      this.clusterType = clusterType;
    }

    public void addObjectClassName(@NotNull final String className, long size, boolean isRoot) {
      histogram.putIfAbsent(className, new ObjectsStatistics());
      histogram.get(className).addObject(size);
      if (isRoot) {
        rootsHistogram.putIfAbsent(className, new ObjectsStatistics());
        rootsHistogram.get(className).addObject(size);
      }
    }

    private boolean classNameIsStudioSource(@NotNull final String className) {
      return className.startsWith("com.android.") ||
             className.startsWith("org.jetbrains") ||
             className.startsWith("com.intellij") ||
             className.startsWith("com.jetbrains");
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
      rootsHistogram.entrySet().stream()
        .sorted(
          Comparator.comparingLong(
            (Map.Entry<String, ObjectsStatistics> a) -> a.getValue().getTotalSizeInBytes()).reversed())
        .limit(HISTOGRAM_PRINT_LIMIT)
        .forEach(e -> writer.accept(String.format(Locale.US, "        %s: %s",
                                                  HeapTraverseUtil.getObjectsStatsPresentation(e.getValue(), OPTIMAL_UNITS),
                                                  e.getKey())));
    }
  }
}
