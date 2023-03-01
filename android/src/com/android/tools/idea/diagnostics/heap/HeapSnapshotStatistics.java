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
import static com.android.tools.idea.diagnostics.heap.HeapTraverseUtil.processMask;
import static com.google.wireless.android.sdk.stats.MemoryUsageReportEvent.MemoryUsageCollectionMetadata.StatusCode;

import com.android.annotations.NonNull;
import com.android.tools.analytics.crash.CrashReport;
import com.android.tools.analytics.crash.GoogleCrashReporter;
import com.android.tools.idea.diagnostics.report.DiagnosticCrashReport;
import com.android.tools.idea.diagnostics.report.DiagnosticReportProperties;
import com.google.wireless.android.sdk.stats.MemoryUsageReportEvent;
import com.intellij.ide.PowerSaveMode;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class HeapSnapshotStatistics {

  @NotNull
  private final ClusterObjectsStatistics.MemoryTrafficStatistics
    totalStats = new ClusterObjectsStatistics.MemoryTrafficStatistics();
  @NotNull
  private final List<ComponentClusterObjectsStatistics> componentStats = new ArrayList<>();
  @NotNull
  private final List<CategoryClusterObjectsStatistics> categoryComponentStats =
    new ArrayList<>();
  @NotNull final Long2ObjectMap<SharedClusterStatistics> maskToSharedComponentStats =
    new Long2ObjectOpenHashMap<>();

  int maxFieldsCacheSize = 0;
  int maxObjectsQueueSize = 0;
  // number of objects that were enumerated during the first traverse, but GCed after that and were
  // not reached during the second pass
  int enumeratedGarbageCollectedObjects = 0;
  int unsuccessfulFieldAccessCounter = 0;
  int heapObjectCount = 0;
  private short traverseSessionId;
  @NotNull
  private final HeapTraverseConfig config;

  @Nullable
  private final ExtendedReportStatistics extendedReportStatistics;

  public HeapSnapshotStatistics(@NotNull final ComponentsSet componentsSet) {
    this(new HeapTraverseConfig(componentsSet, /*collectHistograms=*/false, /*collectDisposerTreeInfo=*/false));
  }

  public HeapSnapshotStatistics(@NotNull final HeapTraverseConfig config) {
    this.config = config;
    for (ComponentsSet.Component component : config.getComponentsSet().getComponents()) {
      componentStats.add(new ComponentClusterObjectsStatistics(component));
    }

    for (ComponentsSet.ComponentCategory category : config.getComponentsSet().getComponentsCategories()) {
      categoryComponentStats.add(new CategoryClusterObjectsStatistics(category));
    }

    if (config.collectHistograms) {
      extendedReportStatistics = new ExtendedReportStatistics(config);
    }
    else {
      extendedReportStatistics = null;
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

  public void addObjectSizeToSharedComponent(long sharedMask, long size, String objectClassName, boolean isMergePoint) {
    if (!maskToSharedComponentStats.containsKey(sharedMask)) {
      maskToSharedComponentStats.put(sharedMask, new SharedClusterStatistics(sharedMask));
    }
    SharedClusterStatistics stats = maskToSharedComponentStats.get(sharedMask);
    stats.getStatistics().addObject(size);

    if (config.collectHistograms && extendedReportStatistics != null) {
      extendedReportStatistics.addClassNameToSharedClusterHistogram(stats, objectClassName, size, isMergePoint);
    }
  }

  public void addOwnedObjectSizeToComponent(int componentId, long size, String objectClassName, boolean isRoot) {
    ComponentClusterObjectsStatistics stats = componentStats.get(componentId);
    stats.addOwnedObject(size);
    if (config.collectHistograms && extendedReportStatistics != null) {
      extendedReportStatistics.addClassNameToComponentOwnedHistogram(stats.getComponent(), objectClassName, size, isRoot);
    }
  }

  public void addObjectToTotal(long size) {
    totalStats.addObject(size);
  }

  public void addRetainedObjectSizeToCategoryComponent(int categoryId, long size) {
    categoryComponentStats.get(categoryId).addRetainedObject(size);
  }

  public void addOwnedObjectSizeToCategoryComponent(int categoryId, long size, String objectClassName, boolean isRoot) {
    CategoryClusterObjectsStatistics stats = categoryComponentStats.get(categoryId);
    stats.addOwnedObject(size);
    if (config.collectHistograms && extendedReportStatistics != null) {
      extendedReportStatistics.addClassNameToCategoryOwnedHistogram(stats.getComponentCategory(), objectClassName, size, isRoot);
    }
  }

  public void addRetainedObjectSizeToComponent(int componentID, long size) {
    componentStats.get(componentID).addRetainedObject(size);
  }


  public void addDisposedButReferencedObject(long size, String objectClassName) {
    if (config.collectDisposerTreeInfo && extendedReportStatistics != null) {
      extendedReportStatistics.addDisposedButReferencedObject(size, objectClassName);
    }
  }

  public void addDisposerTreeInfo(@NotNull Object disposerTree) {
    if (config.collectDisposerTreeInfo && extendedReportStatistics != null) {
      Object objToNodeMap = getFieldValue(disposerTree, "myObject2ParentNode");
      if (objToNodeMap instanceof Map) {
        extendedReportStatistics.setDisposerTreeSize(((Map<?, ?>)objToNodeMap).size());
      }
    }
  }

  private static String getOptimalUnitsStatisticsPresentation(@NotNull final ObjectsStatistics statistics) {
    return HeapTraverseUtil.getObjectsStatsPresentation(statistics,
                                                        HeapSnapshotTraverse.HeapSnapshotPresentationConfig.SizePresentationStyle.OPTIMAL_UNITS);
  }

  @NotNull
  public CrashReport asCrashReport(@NotNull final List<String> exceededClusters) {
    if (extendedReportStatistics == null) {
      throw new IllegalStateException("Extended memory report required for sending a Crash report was not calculated.");
    }
    return new DiagnosticCrashReport("Extended Memory Report", new DiagnosticReportProperties()) {
      @Override
      public void serialize(@NonNull final MultipartEntityBuilder builder) {
        super.serialize(builder);
        GoogleCrashReporter.addBodyToBuilder(builder, "Total used memory", getOptimalUnitsStatisticsPresentation(totalStats.objectsStat));
        GoogleCrashReporter.addBodyToBuilder(builder, "Clusters that exceeded the threshold", String.join(",", exceededClusters));
        for (CategoryClusterObjectsStatistics stat : categoryComponentStats) {
          StringBuilder categoryReportBuilder = new StringBuilder();
          categoryReportBuilder.append(
            String.format(Locale.US, "Owned: %s\n",
                          getOptimalUnitsStatisticsPresentation(stat.getOwnedClusterStat().getObjectsStatistics())));
          extendedReportStatistics.logCategoryHistogram((String s) -> categoryReportBuilder.append(s).append("\n"),
                                                         stat.getComponentCategory());
          GoogleCrashReporter.addBodyToBuilder(builder, "Category " + stat.getComponentCategory().getComponentCategoryLabel(),
                                               categoryReportBuilder.toString());
        }

        for (ComponentClusterObjectsStatistics stat : componentStats) {
          StringBuilder componentReportBuilder = new StringBuilder();
          componentReportBuilder.append(
            String.format(Locale.US, "Owned: %s\n",
                          getOptimalUnitsStatisticsPresentation(stat.getOwnedClusterStat().getObjectsStatistics())));
          extendedReportStatistics.logComponentHistogram((String s) -> componentReportBuilder.append(s).append("\n"), stat.getComponent());
          GoogleCrashReporter.addBodyToBuilder(builder, "Component " + stat.getComponent().getComponentLabel(),
                                               componentReportBuilder.toString());
        }

        maskToSharedComponentStats.values().stream()
          .sorted(Comparator.comparingLong((SharedClusterStatistics a) -> a.getStatistics().getObjectsStatistics().getTotalSizeInBytes())
                    .reversed()).limit(10)
          .forEach((SharedClusterStatistics stat) -> {
            StringBuilder sharedClusterReportBuilder = new StringBuilder();
            sharedClusterReportBuilder.append(
              String.format(Locale.US, "Owned: %s\n", getOptimalUnitsStatisticsPresentation(stat.getStatistics().getObjectsStatistics())));
            extendedReportStatistics.logSharedClusterHistogram((String s) -> sharedClusterReportBuilder.append(s).append("\n"), stat);

            GoogleCrashReporter.addBodyToBuilder(builder,
                                                 "Shared cluster " + getSharedClusterPresentationLabel(stat, HeapSnapshotStatistics.this),
                                                 sharedClusterReportBuilder.toString());
          });

        StringBuilder disposerTreeInfoBuilder = new StringBuilder();
        extendedReportStatistics.logDisposerTreeReport((String s) -> disposerTreeInfoBuilder.append(s).append("\n"));
        GoogleCrashReporter.addBodyToBuilder(builder, "Disposer tree information", disposerTreeInfoBuilder.toString());
      }
    };
  }

  void print(@NotNull final Consumer<String> writer, @NotNull final Function<ObjectsStatistics, String> objectsStatsPresentation,
             @NotNull final HeapSnapshotTraverse.HeapSnapshotPresentationConfig presentationConfig, long collectionTimeMs) {
    writer.accept(
      String.format(Locale.US, "Total used memory: %s",
                    objectsStatsPresentation.apply(totalStats.objectsStat)));
    ObjectsStatistics sharedObjectsStatistics = new ObjectsStatistics();
    maskToSharedComponentStats.values().forEach(e -> sharedObjectsStatistics.addStats(e.getStatistics().getObjectsStatistics()));

    writer.accept(
      String.format(Locale.US, "Total shared memory: %s", objectsStatsPresentation.apply(sharedObjectsStatistics)));
    writer.accept(String.format(Locale.US, "Report collection time: %d ms", collectionTimeMs));

    writer.accept(String.format(Locale.US, "%d Categories:", categoryComponentStats.size()));
    for (CategoryClusterObjectsStatistics stat : categoryComponentStats) {
      writer.accept(String.format(Locale.US, "  Category %s:", stat.getComponentCategory().getComponentCategoryLabel()));
      writer.accept(String.format(Locale.US, "    Owned: %s",
                                  objectsStatsPresentation.apply(stat.getOwnedClusterStat().getObjectsStatistics())));
      if (config.collectHistograms && extendedReportStatistics != null) {
        extendedReportStatistics.logCategoryHistogram(writer, stat.getComponentCategory());
      }
      if (presentationConfig.shouldLogRetainedSizes) {
        writer.accept(String.format(Locale.US, "    Retained: %s",
                                    objectsStatsPresentation.apply(
                                      stat.getRetainedClusterStat().getObjectsStatistics())));
      }
    }

    writer.accept(String.format(Locale.US, "%d Components:", componentStats.size()));
    for (ComponentClusterObjectsStatistics stat : componentStats) {
      writer.accept(String.format(Locale.US, "  Component %s:", stat.getComponent().getComponentLabel()));
      writer.accept(String.format(Locale.US, "    Owned: %s",
                                  objectsStatsPresentation.apply(stat.getOwnedClusterStat().getObjectsStatistics())));
      if (config.collectHistograms && extendedReportStatistics != null) {
        extendedReportStatistics.logComponentHistogram(writer, stat.getComponent());
      }
      if (presentationConfig.shouldLogRetainedSizes) {
        writer.accept(String.format(Locale.US, "    Retained: %s",
                                    objectsStatsPresentation.apply(
                                      stat.getRetainedClusterStat().getObjectsStatistics())));
      }
    }

    if (presentationConfig.shouldLogSharedClusters) {
      writer.accept("Shared clusters:");
      maskToSharedComponentStats.values().stream()
        .sorted(Comparator.comparingLong(a -> -a.getStatistics().getObjectsStatistics().getTotalSizeInBytes())).limit(10)
        .forEach((SharedClusterStatistics s) -> {
          writer.accept(String.format(Locale.US, "  %s: %s",
                                      getSharedClusterPresentationLabel(s, this),
                                      objectsStatsPresentation.apply(s.getStatistics().getObjectsStatistics())));

          if (config.collectHistograms && extendedReportStatistics != null) {
            extendedReportStatistics.logSharedClusterHistogram(writer, s);
          }

          if (extendedReportStatistics != null) {
            extendedReportStatistics.logDisposerTreeReport(writer);
          }
        });
    }
  }

  static String getSharedClusterPresentationLabel(@NotNull final SharedClusterStatistics clusterStats,
                                                  @NotNull final HeapSnapshotStatistics stats) {
    return clusterStats.getComponentIds(stats.getConfig()).stream()
      .map(id -> stats.getComponentStats().get(id).getComponent().getComponentLabel())
      .toList().toString();
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
  private MemoryUsageReportEvent.ObjectsStatistics buildObjectStatistics(@NotNull final ObjectsStatistics objectsStatistics) {
    return MemoryUsageReportEvent.ObjectsStatistics.newBuilder()
      .setObjectsCount(objectsStatistics.getObjectsCount())
      .setTotalSizeBytes(objectsStatistics.getTotalSizeInBytes()).build();
  }

  @NotNull
  private MemoryUsageReportEvent.MemoryTrafficStatistics buildMemoryTrafficStatistics(@NotNull final ClusterObjectsStatistics.MemoryTrafficStatistics memoryTrafficStatistics) {
    return MemoryUsageReportEvent.MemoryTrafficStatistics.newBuilder()
      .setTotalStats(buildObjectStatistics(memoryTrafficStatistics.getObjectsStatistics()))
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
                                                            long executionStartMs,
                                                            int sharedComponentsLimit) {
    MemoryUsageReportEvent.Builder builder = MemoryUsageReportEvent.newBuilder();

    for (ComponentClusterObjectsStatistics componentStat : componentStats) {
      builder.addComponentStats(
        MemoryUsageReportEvent.ClusterMemoryUsage.newBuilder()
          .setLabel(componentStat.getComponent().getComponentLabel())
          .setStats(buildClusterObjectsStatistics(componentStat)));
    }

    maskToSharedComponentStats.values().stream()
      .sorted(
        Comparator.comparingLong(s -> -s.getStatistics().getObjectsStatistics().getTotalSizeInBytes()))
      .limit(sharedComponentsLimit).forEach(s -> builder.addSharedComponentStats(
        MemoryUsageReportEvent.SharedClusterMemoryUsage.newBuilder().addAllIds(s.getComponentIds(config))
          .setStats(buildMemoryTrafficStatistics(s.getStatistics()))));

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

  @NotNull
  public HeapTraverseConfig getConfig() {
    return config;
  }

  @Nullable
  public ExtendedReportStatistics getExtendedReportStatistics() {
    return extendedReportStatistics;
  }

  static class SharedClusterStatistics {
    @NotNull
    private final ClusterObjectsStatistics.MemoryTrafficStatistics statistics;
    final long componentsMask;

    private SharedClusterStatistics(long componentsMask) {
      this.componentsMask = componentsMask;
      statistics = new ClusterObjectsStatistics.MemoryTrafficStatistics();
    }

    @NotNull
    ClusterObjectsStatistics.MemoryTrafficStatistics getStatistics() {
      return statistics;
    }

    @NotNull
    Collection<Integer> getComponentIds(@NotNull final HeapTraverseConfig config) {
      List<Integer> components = new ArrayList<>();
      processMask(componentsMask,
                  (index) -> components.add(config.getComponentsSet().getComponents().get(index).getId()));
      return components;
    }
  }

  static class ComponentClusterObjectsStatistics extends ClusterObjectsStatistics {
    @NotNull
    private final ComponentsSet.Component component;

    private ComponentClusterObjectsStatistics(@NotNull final ComponentsSet.Component component) {
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
    @NotNull
    private final MemoryTrafficStatistics retainedClusterStat = new MemoryTrafficStatistics();
    @NotNull
    private final MemoryTrafficStatistics ownedClusterStat = new MemoryTrafficStatistics();

    public void addOwnedObject(long size) {
      ownedClusterStat.addObject(size);
    }

    public void addRetainedObject(long size) {
      retainedClusterStat.addObject(size);
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

      public void addObject(long size) {
        objectsStat.addObject(size);
      }

      public ObjectsStatistics getObjectsStatistics() {
        return objectsStat;
      }
    }
  }
}
