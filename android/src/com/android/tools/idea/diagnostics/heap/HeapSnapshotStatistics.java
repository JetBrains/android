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

import com.android.annotations.NonNull;
import com.android.tools.analytics.crash.CrashReport;
import com.android.tools.analytics.crash.GoogleCrashReporter;
import com.android.tools.idea.diagnostics.report.DiagnosticCrashReport;
import com.android.tools.idea.diagnostics.report.DiagnosticReportProperties;
import com.google.common.base.Ascii;
import com.google.wireless.android.sdk.stats.MemoryUsageReportEvent;
import com.intellij.diagnostic.hprof.util.HeapReportUtils;
import com.intellij.ide.PowerSaveMode;
import com.intellij.util.containers.WeakList;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class HeapSnapshotStatistics {

  // 1.2mb is a Crash file size limit. If the size of the crash report field value exceeds 250kb it will be wrapped into a file. This file
  // should not be more than 1.2mb to be processes by Crash.
  private static final int MAX_BYTES_FOR_REPORT_ATTACHMENT_FILE = 1_200_000;
  private static final int MAX_BYTES_FOR_HEAP_SUMMARY_FIELD = 250_000;

  @NotNull final ClusterObjectsStatistics.ObjectsStatisticsWithPlatformTracking totalStats =
    new ClusterObjectsStatistics.ObjectsStatisticsWithPlatformTracking();

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

  HeapSnapshotStatistics(@NotNull final ComponentsSet componentsSet) {
    this(new HeapTraverseConfig(componentsSet, /*collectHistograms=*/false, /*collectDisposerTreeInfo=*/false));
  }

  HeapSnapshotStatistics(@NotNull final HeapTraverseConfig config) {
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

  void addObjectSizeToSharedComponent(long sharedMask, long size, String objectClassName, boolean isMergePoint,
                                      boolean isPlatformObject, boolean isRetainedByPlatform, boolean isDisposedButReferenced) {
    if (!maskToSharedComponentStats.containsKey(sharedMask)) {
      maskToSharedComponentStats.put(sharedMask, new SharedClusterStatistics(sharedMask));
    }
    SharedClusterStatistics stats = maskToSharedComponentStats.get(sharedMask);
    stats.getStatistics().addObject(size, isPlatformObject, isRetainedByPlatform);

    if (config.collectHistograms && extendedReportStatistics != null) {
      extendedReportStatistics.addClassNameToSharedClusterHistogram(stats, objectClassName, size, isMergePoint, isDisposedButReferenced);
    }
  }

  void addOwnedObjectSizeToComponent(int componentId, long size, String objectClassName, boolean isRoot, boolean isPlatformObject,
                                     boolean isRetainedByPlatform, boolean isDisposedButReferenced) {
    ComponentClusterObjectsStatistics stats = componentStats.get(componentId);
    stats.addOwnedObject(size, isPlatformObject, isRetainedByPlatform);

    if (config.collectHistograms && extendedReportStatistics != null) {
      extendedReportStatistics.addClassNameToComponentOwnedHistogram(stats.getCluster(), objectClassName, size, isRoot,
                                                                     isDisposedButReferenced);
    }
  }

  void addObjectToTotal(long size, boolean isPlatformObject, boolean isRetainedByPlatform) {
    totalStats.addObject(size, isPlatformObject, isRetainedByPlatform);
  }

  void addRetainedObjectSizeToCategoryComponent(int categoryId, long size, boolean isPlatformObject, boolean isRetainedByPlatform) {
    categoryComponentStats.get(categoryId).addRetainedObject(size, isPlatformObject, isRetainedByPlatform);
  }

  void addOwnedObjectSizeToCategoryComponent(int categoryId, long size, String objectClassName, boolean isRoot,
                                             boolean isPlatformObject,
                                             boolean isRetainedByPlatform,
                                             boolean isDisposedButReferenced) {
    CategoryClusterObjectsStatistics stats = categoryComponentStats.get(categoryId);
    stats.addOwnedObject(size, isPlatformObject, isRetainedByPlatform);
    if (config.collectHistograms && extendedReportStatistics != null) {
      extendedReportStatistics.addClassNameToCategoryOwnedHistogram(stats.getCluster(), objectClassName, size, isRoot,
                                                                    isDisposedButReferenced);
    }
  }

  void addRetainedObjectSizeToComponent(int componentID, long size, boolean isPlatformObject, boolean isRetainedByPlatform) {
    componentStats.get(componentID).addRetainedObject(size, isPlatformObject, isRetainedByPlatform);
  }


  void addDisposedButReferencedObject(long size, String objectClassName) {
    if (config.collectDisposerTreeInfo && extendedReportStatistics != null) {
      extendedReportStatistics.addDisposedButReferencedObject(size, objectClassName);
    }
  }

  public void calculateExtendedReportDataIfNeeded(@NotNull final FieldCache fieldCache,
                                                  @NotNull final MemoryReportCollector collector,
                                                  @NotNull final WeakList<Object> startRoots,
                                                  @NotNull final Map<String, ExtendedReportStatistics.ClassObjectsStatistics> nameToClassObjectsStatistics)
    throws HeapSnapshotTraverseException {
    if (extendedReportStatistics == null || !config.collectObjectTreesData) {
      return;
    }
    extendedReportStatistics.calculateExtendedReportData(config, fieldCache, collector, startRoots, nameToClassObjectsStatistics);
  }

  public void checkClassIsTrackedAndAdd(@NotNull final String className) {
    ComponentsSet componentsSet = config.getComponentsSet();

    for (ComponentsSet.ComponentCategory category : componentsSet.getCategoriesTrackingClassName().get(className)) {
      categoryComponentStats.get(category.getId()).addTrackedFQNInstance(className);
    }
    for (ComponentsSet.Component component : componentsSet.getComponentsTrackingClassName().get(className)) {
      componentStats.get(component.getId()).addTrackedFQNInstance(className);
    }
  }

  private static String getOptimalUnitsStatisticsPresentation(@NotNull final ObjectsStatistics statistics) {
    return HeapTraverseUtil.getObjectsStatsPresentation(statistics,
                                                        MemoryReportCollector.HeapSnapshotPresentationConfig.PresentationStyle.OPTIMAL_UNITS);
  }

  @NotNull
  CrashReport asCrashReport(@NotNull final List<ComponentsSet.Component> exceededComponents,
                            @NotNull final ComponentsSet.Component targetExceededComponent,
                            @NotNull final StatusCode statusCode) {
    if (extendedReportStatistics == null) {
      throw new IllegalStateException("Extended memory report required for sending a Crash report was not calculated.");
    }
    return new DiagnosticCrashReport("Extended Memory Report", new DiagnosticReportProperties()) {
      @Override
      public void serialize(@NonNull final MultipartEntityBuilder builder) {
        super.serialize(builder);
        String exceededComponentsPresentation = exceededComponents.stream().map(
          ComponentsSet.Component::getLabel).collect(Collectors.joining(","));
        GoogleCrashReporter.addBodyToBuilder(builder, "signature",
                                             "Cluster that exceeded the memory usage threshold:" +
                                             targetExceededComponent.getLabel());

        GoogleCrashReporter.addBodyToBuilder(builder, "Total used memory",
                                             getOptimalUnitsStatisticsPresentation(totalStats.getObjectsStatistics()));
        String totalPlatformObjectsPresentation =
          String.format("%s[%s]", getOptimalUnitsStatisticsPresentation(totalStats.platformObjectsSelfStats),
                        getOptimalUnitsStatisticsPresentation(
                          totalStats.platformRetainedObjectsStats));
        GoogleCrashReporter.addBodyToBuilder(builder, "Total platform objects memory", totalPlatformObjectsPresentation);

        GoogleCrashReporter.addBodyToBuilder(builder, "Target exceeded cluster",
                                             targetExceededComponent.getLabel());
        GoogleCrashReporter.addBodyToBuilder(builder, "Target exceeded cluster owned memory", getOptimalUnitsStatisticsPresentation(
          componentStats.get(targetExceededComponent.getId()).getOwnedClusterStat().objectsStat));
        GoogleCrashReporter.addBodyToBuilder(builder, "All clusters that exceeded the memory usage threshold",
                                             exceededComponentsPresentation);
        GoogleCrashReporter.addBodyToBuilder(builder, "Number of nodes in GC root paths trees",
                                             String.valueOf(extendedReportStatistics.rootPathTree.getNumberOfRootPathTreeNodes()));
        GoogleCrashReporter.addBodyToBuilder(builder, "Status code", statusCode.name());

        GoogleCrashReporter.addBodyToBuilder(builder, "heapSummary", collectHeapSummary(targetExceededComponent));

        builder.addBinaryBody("extendedMemoryReport", collectExtendedReport(targetExceededComponent).getBytes(), ContentType.TEXT_PLAIN,
                              "extendedMemoryReport.txt");
      }
    };
  }

  @NotNull
  private String collectHeapSummary(@NotNull final ComponentsSet.Component targetExceededComponent) {
    StringBuilder builder = new StringBuilder();
    assert extendedReportStatistics != null;
    new RootPathTreePrinter.RootPathTreeSummaryPrinter(extendedReportStatistics,
                                                       extendedReportStatistics.componentToExceededClustersStatistics.get(
                                                         targetExceededComponent), config.summaryRequiredSubtreeSize).print(
      (String s) -> appendLine(builder, s));
    return Ascii.truncate(builder.toString(), MAX_BYTES_FOR_HEAP_SUMMARY_FIELD, "[truncated]");
  }

  private static void appendLine(@NotNull final StringBuilder builder, @NotNull final String text) {
    builder.append(text).append("\n");
  }

  private void addClusterHistograms(@NotNull final StringBuilder builder,
                                    @NotNull final String clusterLabel,
                                    @NotNull final ClusterObjectsStatistics<?> stat,
                                    @NotNull final ExtendedReportStatistics.ClusterHistogram.ClusterType clusterType) {
    appendLine(builder, HeapReportUtils.INSTANCE.sectionHeader(clusterLabel));

    appendLine(builder,
               String.format(Locale.US, "Owned: %s",
                             getOptimalUnitsStatisticsPresentation(stat.getOwnedClusterStat().getObjectsStatistics())));

    if (extendedReportStatistics != null) {
      extendedReportStatistics.logClusterHistogram((String s) -> appendLine(builder, s), stat.getCluster(), clusterType);
    }

    if (!stat.getTrackedFQNInstanceCounter().isEmpty()) {
      appendLine(builder, "Number of instances of tracked classes:");
      for (String s : stat.getTrackedFQNInstanceCounter().keySet()) {
        appendLine(builder, String.format(Locale.US, "      %s:%d", s, stat.getTrackedFQNInstanceCounter().getInt(s)));
      }
    }
    appendLine(builder, String.format(Locale.US, "Platform object: %s[%s]",
                                      getOptimalUnitsStatisticsPresentation(
                                        stat.getOwnedClusterStat().platformObjectsSelfStats),
                                      getOptimalUnitsStatisticsPresentation(
                                        stat.getOwnedClusterStat().platformRetainedObjectsStats)));
  }

  @NotNull
  private String collectExtendedReport(@NotNull final ComponentsSet.Component targetExceededComponent) {
    StringBuilder extendedReportBuilder = new StringBuilder();

    for (CategoryClusterObjectsStatistics stat : categoryComponentStats) {
      addClusterHistograms(extendedReportBuilder, "Category " + stat.getCluster().getLabel(), stat,
                           ExtendedReportStatistics.ClusterHistogram.ClusterType.CATEGORY);
    }

    for (ComponentClusterObjectsStatistics stat : componentStats) {
      addClusterHistograms(extendedReportBuilder, "Component " + stat.getCluster().getLabel(), stat,
                           ExtendedReportStatistics.ClusterHistogram.ClusterType.COMPONENT);
    }

    maskToSharedComponentStats.values().stream()
      .sorted(Comparator.comparingLong((SharedClusterStatistics a) -> a.getStatistics().getObjectsStatistics().getTotalSizeInBytes())
                .reversed()).limit(10)
      .forEach((SharedClusterStatistics stat) -> {
        appendLine(extendedReportBuilder,
                   HeapReportUtils.INSTANCE.sectionHeader(
                     "Shared cluster " + getSharedClusterPresentationLabel(stat, HeapSnapshotStatistics.this)));
        appendLine(extendedReportBuilder,
                   String.format(Locale.US, "Owned: %s\n",
                                 getOptimalUnitsStatisticsPresentation(stat.getStatistics().getObjectsStatistics())));
        if (extendedReportStatistics != null) {
          extendedReportStatistics.logSharedClusterHistogram((String s) -> appendLine(extendedReportBuilder, s), stat);
        }
      });

    if (extendedReportStatistics != null) {
      appendLine(extendedReportBuilder,
                 HeapReportUtils.INSTANCE.sectionHeader("Exceeded cluster " + targetExceededComponent.getLabel()));
      extendedReportStatistics.printExceededClusterStatisticsIfNeeded((String s) -> appendLine(extendedReportBuilder, s),
                                                                      targetExceededComponent);

      appendLine(extendedReportBuilder,
                 HeapReportUtils.INSTANCE.sectionHeader("Disposer tree report"));
      extendedReportStatistics.logDisposerTreeReport((String s) -> appendLine(extendedReportBuilder, s));
    }

    return Ascii.truncate(extendedReportBuilder.toString(), MAX_BYTES_FOR_REPORT_ATTACHMENT_FILE, "[truncated]");
  }

  void print(@NotNull final Consumer<String> writer, @NotNull final Function<ObjectsStatistics, String> objectsStatsPresentation,
             @NotNull final MemoryReportCollector.HeapSnapshotPresentationConfig presentationConfig, long collectionTimeMs) {
    writer.accept(
      String.format(Locale.US, "Total used memory: %s",
                    objectsStatsPresentation.apply(totalStats.getObjectsStatistics())));
    writer.accept(String.format(Locale.US, "Total platform objects memory: %s[%s]",
                                objectsStatsPresentation.apply(
                                  totalStats.platformObjectsSelfStats), objectsStatsPresentation.apply(
        totalStats.platformRetainedObjectsStats)));

    ObjectsStatistics sharedObjectsStatistics = new ObjectsStatistics();
    maskToSharedComponentStats.values().forEach(e -> sharedObjectsStatistics.addStats(e.getStatistics().objectsStat));

    writer.accept(
      String.format(Locale.US, "Total shared memory: %s", objectsStatsPresentation.apply(sharedObjectsStatistics)));
    writer.accept(String.format(Locale.US, "Report collection time: %d ms", collectionTimeMs));

    writer.accept(String.format(Locale.US, "%d Categories:", categoryComponentStats.size()));
    for (CategoryClusterObjectsStatistics stat : categoryComponentStats) {
      writer.accept(String.format(Locale.US, "  Category %s:", stat.getCluster().getLabel()));
      writer.accept(String.format(Locale.US, "    Owned: %s",
                                  objectsStatsPresentation.apply(stat.getOwnedClusterStat().getObjectsStatistics())));
      if (config.collectHistograms && extendedReportStatistics != null) {
        extendedReportStatistics.logCategoryHistogram(writer, stat.getCluster());
      }
      if (presentationConfig.shouldLogRetainedSizes) {
        writer.accept(String.format(Locale.US, "    Retained: %s",
                                    objectsStatsPresentation.apply(
                                      stat.getRetainedClusterStat().objectsStat)));
      }
      if (!stat.getTrackedFQNInstanceCounter().isEmpty()) {
        writer.accept("    Number of instances of tracked classes:");
        for (String s : stat.getTrackedFQNInstanceCounter().keySet()) {
          writer.accept(String.format(Locale.US, "      %s:%d", s, stat.getTrackedFQNInstanceCounter().getInt(s)));
        }
      }
      writer.accept(String.format(Locale.US, "    Platform object: %s[%s]",
                                  objectsStatsPresentation.apply(
                                    stat.getOwnedClusterStat().platformObjectsSelfStats), objectsStatsPresentation.apply(
          stat.getOwnedClusterStat().platformRetainedObjectsStats)));
    }

    writer.accept(String.format(Locale.US, "%d Components:", componentStats.size()));
    for (ComponentClusterObjectsStatistics stat : componentStats) {
      writer.accept(String.format(Locale.US, "  Component %s:", stat.getCluster().getLabel()));
      writer.accept(String.format(Locale.US, "    Owned: %s",
                                  objectsStatsPresentation.apply(stat.getOwnedClusterStat().getObjectsStatistics())));
      if (config.collectHistograms && extendedReportStatistics != null) {
        extendedReportStatistics.logComponentHistogram(writer, stat.getCluster());
        if (extendedReportStatistics.componentToExceededClustersStatistics.containsKey(stat.getCluster())) {
          extendedReportStatistics.printExceededClusterStatisticsIfNeeded(writer, stat.getCluster());
        }
      }
      if (presentationConfig.shouldLogRetainedSizes) {
        writer.accept(String.format(Locale.US, "    Retained: %s",
                                    objectsStatsPresentation.apply(
                                      stat.getRetainedClusterStat().objectsStat)));
      }
      writer.accept(String.format(Locale.US, "    Platform object: %s[%s]",
                                  objectsStatsPresentation.apply(
                                    stat.getOwnedClusterStat().platformObjectsSelfStats), objectsStatsPresentation.apply(
          stat.getOwnedClusterStat().platformRetainedObjectsStats)));
    }

    if (presentationConfig.shouldLogSharedClusters) {
      writer.accept("Shared clusters:");
      maskToSharedComponentStats.values().stream()
        .sorted(Comparator.comparingLong(a -> -a.getStatistics().objectsStat.getTotalSizeInBytes())).limit(10)
        .forEach((SharedClusterStatistics s) -> {
          writer.accept(String.format(Locale.US, "  %s: %s",
                                      getSharedClusterPresentationLabel(s, this),
                                      objectsStatsPresentation.apply(s.getStatistics().objectsStat)));

          if (config.collectHistograms && extendedReportStatistics != null) {
            extendedReportStatistics.logSharedClusterHistogram(writer, s);
          }
        });
    }
    if (extendedReportStatistics != null) {
      extendedReportStatistics.logDisposerTreeReport(writer);
    }
  }

  static String getSharedClusterPresentationLabel(@NotNull final SharedClusterStatistics clusterStats,
                                                  @NotNull final HeapSnapshotStatistics stats) {
    return clusterStats.getComponentIds(stats.getConfig()).stream()
      .map(id -> stats.getComponentStats().get(id).getCluster().getLabel())
      .toList().toString();
  }

  void updateMaxFieldsCacheSize(int currentFieldSize) {
    maxFieldsCacheSize = Math.max(maxFieldsCacheSize, currentFieldSize);
  }

  void updateMaxObjectsQueueSize(int currentObjectsQueueSize) {
    maxObjectsQueueSize = Math.max(maxObjectsQueueSize, currentObjectsQueueSize);
  }

  void incrementGarbageCollectedObjectsCounter() {
    enumeratedGarbageCollectedObjects++;
  }

  void incrementUnsuccessfulFieldAccessCounter() {
    unsuccessfulFieldAccessCounter++;
  }

  void setHeapObjectCount(int heapObjectCount) {
    this.heapObjectCount = heapObjectCount;
  }

  @NotNull
  private MemoryUsageReportEvent.ObjectsStatistics buildObjectStatistics(@NotNull final ObjectsStatistics objectsStatistics) {
    return MemoryUsageReportEvent.ObjectsStatistics.newBuilder()
      .setObjectsCount(objectsStatistics.getObjectsCount())
      .setTotalSizeBytes(objectsStatistics.getTotalSizeInBytes()).build();
  }

  @NotNull
  private MemoryUsageReportEvent.MemoryTrafficStatistics buildMemoryTrafficStatistics(@NotNull final ClusterObjectsStatistics.ObjectsStatisticsWithPlatformTracking statistics) {
    return MemoryUsageReportEvent.MemoryTrafficStatistics.newBuilder()
      .setTotalStats(buildObjectStatistics(statistics.getObjectsStatistics()))
      .setPlatformObjectsStats(buildObjectStatistics(statistics.getPlatformObjectsSelfStats()))
      .setPlatformRetainedStats(buildObjectStatistics(statistics.getPlatformRetainedObjectsStats()))
      .build();
  }

  @NotNull
  private MemoryUsageReportEvent.ClusterObjectsStatistics buildClusterObjectsStatistics(@NotNull final ClusterObjectsStatistics<?> componentStatistics) {
    return MemoryUsageReportEvent.ClusterObjectsStatistics.newBuilder()
      .setOwnedClusterStats(buildMemoryTrafficStatistics(componentStatistics.getOwnedClusterStat()))
      .setRetainedClusterStats(buildMemoryTrafficStatistics(
        componentStatistics.getRetainedClusterStat())).build();
  }

  @NotNull
  MemoryUsageReportEvent buildMemoryUsageReportEvent(StatusCode statusCode,
                                                     long executionTimeMs,
                                                     long executionStartMs,
                                                     int sharedComponentsLimit) {
    MemoryUsageReportEvent.Builder builder = MemoryUsageReportEvent.newBuilder();

    for (ComponentClusterObjectsStatistics componentStat : componentStats) {
      builder.addComponentStats(
        MemoryUsageReportEvent.ClusterMemoryUsage.newBuilder()
          .setLabel(componentStat.getCluster().getLabel())
          .setStats(buildClusterObjectsStatistics(componentStat))
          .putAllInstanceCountPerClassName(componentStat.getTrackedFQNInstanceCounter()));
    }

    maskToSharedComponentStats.values().stream()
      .sorted(
        Comparator.comparingLong(s -> -s.getStatistics().objectsStat.getTotalSizeInBytes()))
      .limit(sharedComponentsLimit).forEach(s -> builder.addSharedComponentStats(
        MemoryUsageReportEvent.SharedClusterMemoryUsage.newBuilder().addAllIds(s.getComponentIds(config))
          .setStats(buildMemoryTrafficStatistics(s.statistics))));

    for (CategoryClusterObjectsStatistics categoryStat : categoryComponentStats) {
      builder.addComponentCategoryStats(
        MemoryUsageReportEvent.ClusterMemoryUsage.newBuilder()
          .setLabel(categoryStat.getCluster().getLabel())
          .setStats(buildClusterObjectsStatistics(categoryStat))
          .putAllInstanceCountPerClassName(categoryStat.getTrackedFQNInstanceCounter()));
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

  void setTraverseSessionId(short traverseSessionId) {
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
    private final ClusterObjectsStatistics.ObjectsStatisticsWithPlatformTracking statistics;
    final long componentsMask;

    private SharedClusterStatistics(long componentsMask) {
      this.componentsMask = componentsMask;
      statistics = new ClusterObjectsStatistics.ObjectsStatisticsWithPlatformTracking();
    }

    @NotNull
    ClusterObjectsStatistics.ObjectsStatisticsWithPlatformTracking getStatistics() {
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

  public static class ComponentClusterObjectsStatistics extends ClusterObjectsStatistics<ComponentsSet.Component> {
    private ComponentClusterObjectsStatistics(@NotNull final ComponentsSet.Component component) {
      super(component);
    }
  }

  public static class CategoryClusterObjectsStatistics extends ClusterObjectsStatistics<ComponentsSet.ComponentCategory> {
    private CategoryClusterObjectsStatistics(@NotNull final ComponentsSet.ComponentCategory category) {
      super(category);
    }
  }

  public static abstract class ClusterObjectsStatistics<T extends ComponentsSet.Cluster> {
    @NotNull
    private final ObjectsStatisticsWithPlatformTracking retainedClusterStat = new ObjectsStatisticsWithPlatformTracking();
    @NotNull
    private final ObjectsStatisticsWithPlatformTracking ownedClusterStat = new ObjectsStatisticsWithPlatformTracking();
    @NotNull
    private final Object2IntMap<String> trackedFQNInstanceCounter = new Object2IntOpenHashMap<>();
    @NotNull
    private final T cluster;

    public ClusterObjectsStatistics(@NotNull final T cluster) {
      this.cluster = cluster;
    }

    void addOwnedObject(long size, boolean isPlatformObject, boolean isRetainedByPlatform) {
      ownedClusterStat.addObject(size, isPlatformObject, isRetainedByPlatform);
    }

    void addRetainedObject(long size, boolean isPlatformObject, boolean isRetainedByPlatform) {
      retainedClusterStat.addObject(size, isPlatformObject, isRetainedByPlatform);
    }

    @NotNull
    public ObjectsStatisticsWithPlatformTracking getOwnedClusterStat() {
      return ownedClusterStat;
    }

    @NotNull
    public ObjectsStatisticsWithPlatformTracking getRetainedClusterStat() {
      return retainedClusterStat;
    }

    @NotNull
    public Object2IntMap<String> getTrackedFQNInstanceCounter() {
      return trackedFQNInstanceCounter;
    }

    void addTrackedFQNInstance(String name) {
      trackedFQNInstanceCounter.put(name, trackedFQNInstanceCounter.getOrDefault(name, 0) + 1);
    }

    @NotNull
    public T getCluster() {
      return cluster;
    }

    public static class ObjectsStatisticsWithPlatformTracking {
      @NotNull
      private final ObjectsStatistics objectsStat = new ObjectsStatistics();
      @NotNull
      private final ObjectsStatistics platformObjectsSelfStats = new ObjectsStatistics();
      @NotNull
      private final ObjectsStatistics platformRetainedObjectsStats = new ObjectsStatistics();


      void addObject(long size, boolean isPlatformObject, boolean isRetainedByPlatform) {
        objectsStat.addObject(size);

        if (isPlatformObject) {
          platformObjectsSelfStats.addObject(size);
        }

        if (isRetainedByPlatform) {
          platformRetainedObjectsStats.addObject(size);
        }
      }

      public ObjectsStatistics getObjectsStatistics() {
        return objectsStat;
      }

      @NotNull
      public ObjectsStatistics getPlatformObjectsSelfStats() {
        return platformObjectsSelfStats;
      }

      @NotNull
      public ObjectsStatistics getPlatformRetainedObjectsStats() {
        return platformRetainedObjectsStats;
      }
    }
  }
}
