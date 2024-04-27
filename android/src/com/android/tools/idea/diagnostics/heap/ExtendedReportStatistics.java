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

import static com.android.tools.idea.diagnostics.heap.HeapTraverseUtil.getFieldValue;
import static com.android.tools.idea.diagnostics.heap.MemoryReportCollector.HeapSnapshotPresentationConfig.PresentationStyle.OPTIMAL_UNITS;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.WeakList;
import it.unimi.dsi.fastutil.ints.Int2ByteMap;
import it.unimi.dsi.fastutil.ints.Int2ByteOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.ToLongFunction;
import org.jetbrains.annotations.NotNull;

public class ExtendedReportStatistics {

  // In the current configuration maximum value for NOMINATED_CLASSES_NUMBER_IN_SECTION is 4. If it's not enough please also change the
  // type of RootPathElement#rootPathTreeNodeWasPropagated mask.
  static final int NOMINATED_CLASSES_NUMBER_IN_SECTION = 3;
  static final int NUMBER_OF_LOADERS_FOR_CLASS_NAME_THRESHOLD = 10;
  private static final String CLASS_FQN = "java.lang.Class";

  @NotNull final List<ClusterHistogram> componentHistograms;

  @NotNull final List<CategoryHistogram> categoryHistograms;

  @NotNull final Long2ObjectMap<ClusterHistogram> sharedClustersHistograms;
  @NotNull final Int2IntMap objectIdToMinDepth = new Int2IntOpenHashMap();
  @NotNull final Int2ByteMap objectIdToMinDepthKind = new Int2ByteOpenHashMap();

  @NotNull final Map<ComponentsSet.Component, ExceededClusterStatistics> componentToExceededClustersStatistics = Maps.newHashMap();
  @NotNull final Int2ObjectMap<ExceededClusterStatistics> exceededClustersEnumeration = new Int2ObjectOpenHashMap<>();
  @NotNull final Set<ClassLoader> globalNominatedClassLoaders = ContainerUtil.createWeakSet();
  @NotNull final List<Pair<String, Integer>> duplicatedClassNamesAndNumberOfInstances = Lists.newArrayList();

  private int totalNumberOfDisposedButReferencedObjects = 0;
  private int totalDisposerTreeSize = 0;

  @NotNull
  private final Map<String, ObjectsStatistics> disposedButReferencedObjectsClasses = Maps.newHashMap();
  @NotNull
  final RootPathTree rootPathTree = new RootPathTree(this);
  @NotNull
  final HeapTraverseConfig myConfig;

  public ExtendedReportStatistics(@NotNull final HeapTraverseConfig config) {
    myConfig = config;
    this.componentHistograms = Lists.newArrayList();
    this.categoryHistograms = Lists.newArrayList();
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

    int exceededComponentIndex = 0;
    for (ComponentsSet.Component component : config.exceededComponents) {
      ExceededClusterStatistics exceededClusterStatistics = new ExceededClusterStatistics(exceededComponentIndex++);
      exceededClustersEnumeration.put(exceededClusterStatistics.exceededClusterIndex, exceededClusterStatistics);
      componentToExceededClustersStatistics.put(component, exceededClusterStatistics);
    }
  }

  void addClassNameToComponentOwnedHistogram(@NotNull final ComponentsSet.Component component,
                                             @NotNull final String className,
                                             long size,
                                             boolean isRoot,
                                             boolean isDisposedButReferenced) {
    componentHistograms.get(component.getId()).addObjectClassName(className, size, isRoot, isDisposedButReferenced);
  }

  void addClassNameToCategoryOwnedHistogram(@NotNull final ComponentsSet.ComponentCategory componentCategory,
                                            @NotNull final String className,
                                            long size,
                                            boolean isRoot,
                                            boolean isDisposedButReferenced) {
    categoryHistograms.get(componentCategory.getId()).addObjectClassName(className, size, isRoot, isDisposedButReferenced);
  }

  public void addClassNameToSharedClusterHistogram(@NotNull final HeapSnapshotStatistics.SharedClusterStatistics sharedClusterStatistics,
                                                   @NotNull String className,
                                                   long size,
                                                   boolean isMergePoint,
                                                   boolean isDisposedButReferenced) {
    sharedClustersHistograms.putIfAbsent(sharedClusterStatistics.componentsMask,
                                         new ClusterHistogram(ClusterHistogram.ClusterType.SHARED_CLUSTER));
    sharedClustersHistograms.get(sharedClusterStatistics.componentsMask)
      .addObjectClassName(className, size, isMergePoint, isDisposedButReferenced);
  }

  public void logClusterHistogram(@NotNull Consumer<String> writer,
                                  @NotNull final ComponentsSet.Cluster cluster,
                                  @NotNull final ExtendedReportStatistics.ClusterHistogram.ClusterType clusterType) {
    switch (clusterType) {
      case COMPONENT -> logComponentHistogram(writer, (ComponentsSet.Component)cluster);
      case CATEGORY -> logCategoryHistogram(writer, (ComponentsSet.ComponentCategory)cluster);
    }
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

  public void calculateExtendedReportData(@NotNull final HeapTraverseConfig config,
                                          @NotNull final FieldCache fieldCache,
                                          @NotNull final MemoryReportCollector collector,
                                          @NotNull final WeakList<Object> startRoots,
                                          @NotNull final Map<String, ExtendedReportStatistics.ClassObjectsStatistics> nameToClassObjectsStatistics)
    throws HeapSnapshotTraverseException {
    if (config.collectDisposerTreeInfo) {
      //noinspection UnstableApiUsage
      Object objToNodeMap = getFieldValue(Disposer.getTree(), "myObject2ParentNode");
      if (objToNodeMap instanceof Map) {
        setDisposerTreeSize(((Map<?, ?>)objToNodeMap).size());
      }
    }

    for (Map.Entry<String, ClassObjectsStatistics> entry : nameToClassObjectsStatistics.entrySet()) {
      if (entry.getValue().classLoaders.size() > NUMBER_OF_LOADERS_FOR_CLASS_NAME_THRESHOLD) {
        globalNominatedClassLoaders.addAll(entry.getValue().classLoaders);
        duplicatedClassNamesAndNumberOfInstances.add(Pair.create(entry.getKey(), entry.getValue().classObjects.size()));
      }
    }

    for (ComponentsSet.Component component : config.exceededComponents) {
      ClusterHistogram histogram = componentHistograms.get(component.getId());
      ExceededClusterStatistics exceededClusterStatistics = componentToExceededClustersStatistics.get(component);

      // Classes with the highest objects count owned by the exceeded component
      addNominatedClassesFromHistogram(exceededClusterStatistics, histogram.histogram,
                                       (Map.Entry<String, ObjectsStatistics> e) -> e.getValue().getObjectsCount());
      // Classes with the highest total size owned by the exceeded component
      addNominatedClassesFromHistogram(exceededClusterStatistics, histogram.histogram,
                                       (Map.Entry<String, ObjectsStatistics> e) -> e.getValue().getTotalSizeInBytes());

      // Exceeded component root classes with the highest number of instances
      addNominatedClassesFromHistogram(exceededClusterStatistics, histogram.rootHistogram,
                                       (Map.Entry<String, ObjectsStatistics> e) -> e.getValue().getObjectsCount());
    }

    DepthFirstSearchTraverse.ExtendedReportCollectionTraverse traverse =
      new DepthFirstSearchTraverse.ExtendedReportCollectionTraverse(fieldCache, collector, this);
    WeakList<Object> firstTraverseRoots = new WeakList<>();
    WeakList<Object> nominatedLoadersRoots = new WeakList<>();

    for (Object root : startRoots) {
      if (root == null) {
        continue;
      }
      ClassLoader cl;
      if(root instanceof Class) {
        cl = ((Class<?>)root).getClassLoader();
      } else if (root instanceof ClassLoader) {
        cl = (ClassLoader)root;
      }
      else {
        firstTraverseRoots.add(root);
        continue;
      }

      if (cl != null && (globalNominatedClassLoaders.contains(cl) ||
                         componentToExceededClustersStatistics.values().stream().anyMatch(a -> a.isClassLoaderNominated(cl)))) {
        nominatedLoadersRoots.add(root);
      }
      else {
        firstTraverseRoots.add(root);
      }
    }

    traverse.start(firstTraverseRoots);
    traverse.disableClassLoaderTracking();
    traverse.start(nominatedLoadersRoots);
  }

  /**
   *  Sort classes from the histogram using the specified {@param extractorForComparator} and register top
   *  {@code NOMINATED_CLASSES_NUMBER_IN_SECTION} of them as nominated classes.
   */
  private void addNominatedClassesFromHistogram(@NotNull final ExceededClusterStatistics exceededClusterStatistics,
                                                @NotNull final Map<String, ObjectsStatistics> histogram,
                                                @NotNull final ToLongFunction<Map.Entry<String, ObjectsStatistics>> extractorForComparator) {
    histogram.entrySet().stream().filter(e -> !CLASS_FQN.equals(e.getKey())).sorted(
        Comparator.comparingLong(extractorForComparator).reversed())
      .limit(NOMINATED_CLASSES_NUMBER_IN_SECTION).forEach(e -> exceededClusterStatistics.addNominatedClass(e.getKey(), e.getValue()));
  }

  public void printExceededClusterStatisticsIfNeeded(@NotNull final Consumer<String> writer,
                                                     @NotNull final ComponentsSet.Component component) {
    ExceededClusterStatistics statistics = componentToExceededClustersStatistics.get(component);
    List<Pair<String, ObjectsStatistics>> nominatedClassesInOrder = statistics.nominatedClassesTotalStatistics.entrySet().stream()
      .sorted(Comparator.comparingInt((Map.Entry<String, ObjectsStatistics> e) -> e.getValue().getObjectsCount()).reversed())
      .map(e -> new Pair<>(e.getKey(), e.getValue())).toList();

    ObjectsStatistics totalDisposedButReferencedObjectsStatistics = new ObjectsStatistics();
    for (ObjectsStatistics value : componentHistograms.get(component.getId()).disposedButReferencedObjects.values()) {
      totalDisposedButReferencedObjectsStatistics.addStats(value);
    }
    new RootPathTreePrinter.RootPathTreeDisposedObjectsPrinter(totalDisposedButReferencedObjectsStatistics, this,
                                                               statistics).print(writer);

    writer.accept("======== INSTANCES OF EACH NOMINATED CLASS ========");
    writer.accept("Nominated classes:");
    for (Pair<String, ObjectsStatistics> pair : nominatedClassesInOrder) {
      writer.accept(
        String.format(Locale.US, " --> [%s] %s", HeapTraverseUtil.getObjectsStatsPresentation(pair.second, OPTIMAL_UNITS), pair.first));
    }
    writer.accept("");

    for (Pair<String, ObjectsStatistics> pair : nominatedClassesInOrder) {
      writer.accept(String.format(Locale.US, "CLASS: %s (%d objects)", pair.first, pair.second.getObjectsCount()));
      new RootPathTreePrinter.RootPathTreeNominatedTypePrinter(pair.second, this, statistics,
                                                               statistics.nominatedClassesEnumeration.getInt(pair.first)).print(writer);
    }

    new RootPathTreePrinter.RootPathTreeNominatedLoadersPrinter(rootPathTree.totalNominatedLoadersReferringObjectsStatistics, this,
                                                                statistics).print(writer);
  }

  class CategoryHistogram extends ClusterHistogram {
    public CategoryHistogram() {
      super(ClusterType.CATEGORY);
    }
  }

  class ClusterHistogram {

    enum ClusterType {
      COMPONENT,
      CATEGORY,
      SHARED_CLUSTER
    }

    @NotNull final Map<String, ObjectsStatistics> disposedButReferencedObjects = Maps.newHashMap();

    @NotNull final Map<String, ObjectsStatistics> histogram =
      Maps.newHashMap();
    @NotNull final Map<String, ObjectsStatistics> rootHistogram = Maps.newHashMap();

    @NotNull
    private final ClusterType clusterType;

    public ClusterHistogram(@NotNull final ClusterType clusterType) {
      this.clusterType = clusterType;
    }

    public void addObjectClassName(@NotNull final String className, long size, boolean isRoot, boolean isDisposedButReferenced) {
      histogram.putIfAbsent(className, new ObjectsStatistics());
      histogram.get(className).addObject(size);
      if (isRoot) {
        rootHistogram.putIfAbsent(className, new ObjectsStatistics());
        rootHistogram.get(className).addObject(size);
      }

      if (isDisposedButReferenced) {
        disposedButReferencedObjects.putIfAbsent(className, new ObjectsStatistics());
        disposedButReferencedObjects.get(className).addObject(size);
      }
    }

    private boolean classNameIsStudioSource(@NotNull final String className) {
      return className.startsWith("com.android.") ||
             MemoryReportCollector.isPlatformObject(className);
    }

    public void print(Consumer<String> writer) {
      writer.accept("      Histogram:");
      histogram.entrySet().stream()
        .sorted(Comparator.comparingLong((Map.Entry<String, ObjectsStatistics> a) -> a.getValue().getTotalSizeInBytes()).reversed())
        .limit(myConfig.histogramPrintLimit)
        .forEach(e -> writer.accept(String.format(Locale.US, "        %s: %s",
                                                  HeapTraverseUtil.getObjectsStatsPresentation(e.getValue(), OPTIMAL_UNITS),
                                                  e.getKey())));

      writer.accept("      Studio objects histogram:");
      histogram.entrySet().stream().filter(e -> classNameIsStudioSource(e.getKey()))
        .sorted(Comparator.comparingLong((Map.Entry<String, ObjectsStatistics> a) -> a.getValue().getTotalSizeInBytes()).reversed())
        .limit(myConfig.histogramPrintLimit)
        .forEach(e -> writer.accept(String.format(Locale.US, "        %s: %s",
                                                  HeapTraverseUtil.getObjectsStatsPresentation(e.getValue(), OPTIMAL_UNITS),
                                                  e.getKey())));

      switch (clusterType) {
        case CATEGORY -> writer.accept("      Category roots histogram:");
        case COMPONENT -> writer.accept("      Component roots histogram:");
        case SHARED_CLUSTER -> writer.accept("      Shared cluster merge-points histogram:");
      }
      rootHistogram.entrySet().stream()
        .sorted(
          Comparator.comparingInt(
            (Map.Entry<String, ObjectsStatistics> a) -> a.getValue().getObjectsCount()).reversed())
        .limit(myConfig.histogramPrintLimit)
        .forEach(e -> writer.accept(String.format(Locale.US, "        %s: %s",
                                                  HeapTraverseUtil.getObjectsStatsPresentation(e.getValue(), OPTIMAL_UNITS),
                                                  e.getKey())));

      if (!disposedButReferencedObjects.isEmpty()) {
        writer.accept("      Disposed but strong referenced objects:");
        disposedButReferencedObjects.entrySet().stream().sorted(
            Comparator.comparingInt(
              (Map.Entry<String, ObjectsStatistics> a) -> a.getValue().getObjectsCount()).reversed())
          .forEach(e -> writer.accept(String.format(Locale.US, "        %s: %s",
                                                    HeapTraverseUtil.getObjectsStatsPresentation(e.getValue(), OPTIMAL_UNITS),
                                                    e.getKey())));
      }
    }
  }

  static class ClassObjectsStatistics {
    Set<ClassLoader> classLoaders;
    Set<Class<?>> classObjects;

    ClassObjectsStatistics() {
      classLoaders = ContainerUtil.createWeakSet();
      classObjects = ContainerUtil.createWeakSet();
    }
  }
}
