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

import static com.android.tools.idea.diagnostics.heap.ComponentsSet.UNCATEGORIZED_CATEGORY_LABEL;
import static com.android.tools.idea.diagnostics.heap.ComponentsSet.UNCATEGORIZED_COMPONENT_LABEL;
import static com.google.wireless.android.sdk.stats.MemoryUsageReportEvent.MemoryUsageCollectionMetadata.StatusCode;
import static org.junit.Assert.assertThat;

import com.android.annotations.NonNull;
import com.android.test.testutils.TestUtils;
import com.android.testutils.classloader.SingleClassLoader;
import com.android.tools.adtui.workbench.PropertiesComponentMock;
import com.android.tools.analytics.crash.CrashReport;
import com.android.tools.idea.diagnostics.TruncatingStringBuilder;
import com.android.tools.idea.diagnostics.crash.StudioCrashReporter;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.io.ByteStreams;
import com.google.wireless.android.sdk.stats.MemoryUsageReportEvent;
import com.intellij.ide.PowerSaveMode;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.LowMemoryWatcher;
import com.intellij.testFramework.PlatformLiteFixture;
import com.intellij.util.TriConsumer;
import com.intellij.util.containers.WeakList;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.hamcrest.core.SubstringMatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.AssumptionViolatedException;
import org.junit.Before;
import org.junit.Test;

public class HeapAnalyzerTest extends PlatformLiteFixture {

  @Before
  public void setUp() throws Exception {
    super.setUp();
    initApplication();
    getApplication().registerService(PropertiesComponent.class, new PropertiesComponentMock());
    getApplication().registerService(HeapSnapshotTraverseService.class, new HeapSnapshotTraverseService());
    HeapSnapshotTraverseService.getInstance().loadObjectTaggingAgent();
  }

  private void checkObjectsUntagged(Object[] objects) {
    for (Object object : objects) {
      Assert.assertEquals(0, MemoryReportJniHelper.getObjectTag(object));
    }
  }

  @Test
  public void testSimpleComponents() {
    ComponentsSet componentsSet = new ComponentsSet();
    ComponentsSet.ComponentCategory defaultCategory = componentsSet.registerCategory("DEFAULT");
    componentsSet.addComponentWithPackagesAndClassNames("A",
                                                        defaultCategory,
                                                        Collections.emptyList(),
                                                        List.of("com.android.tools.idea.diagnostics.heap.HeapAnalyzerTest$A"));
    componentsSet.addComponentWithPackagesAndClassNames("B",
                                                        defaultCategory,
                                                        Collections.emptyList(),
                                                        List.of("com.android.tools.idea.diagnostics.heap.HeapAnalyzerTest$B"));

    HeapSnapshotStatistics stats = new HeapSnapshotStatistics(componentsSet);
    A a = new A();
    Assert.assertEquals(StatusCode.NO_ERROR,
                        new MemoryReportCollector(stats).walkObjects(List.of(a)));

    List<HeapSnapshotStatistics.ComponentClusterObjectsStatistics> componentStats = stats.getComponentStats();
    Assert.assertEquals(3, componentStats.size());
    Assert.assertEquals(UNCATEGORIZED_CATEGORY_LABEL,
                        componentStats.get(0).getCluster().getComponentCategory().getLabel());
    Assert.assertEquals("A", componentStats.get(1).getCluster().getLabel());
    // instance of A, boxed int
    Assert.assertEquals(2, componentStats.get(1).getOwnedClusterStat().getObjectsStatistics().getObjectsCount());

    Assert.assertEquals(40, componentStats.get(1).getOwnedClusterStat().getObjectsStatistics().getTotalSizeInBytes());
    Assert.assertEquals("B", componentStats.get(2).getCluster().getLabel());
    // instance of B
    Assert.assertEquals(1, componentStats.get(2).getOwnedClusterStat().getObjectsStatistics().getObjectsCount());
    Assert.assertEquals(16, componentStats.get(2).getOwnedClusterStat().getObjectsStatistics().getTotalSizeInBytes());
    checkObjectsUntagged(new Object[]{a, a.myB, a.myInt});
  }

  @Test
  public void testNonComponentObjectLowOwnershipPriority() {
    ComponentsSet componentsSet = new ComponentsSet();
    ComponentsSet.ComponentCategory defaultCategory = componentsSet.registerCategory("DEFAULT");
    componentsSet.addComponentWithPackagesAndClassNames("A", defaultCategory,
                                                        Collections.emptyList(),
                                                        List.of("com.android.tools.idea.diagnostics.heap.HeapAnalyzerTest$A"));

    HeapSnapshotStatistics stats = new HeapSnapshotStatistics(componentsSet);
    A a = new A();
    Assert.assertEquals(StatusCode.NO_ERROR,
                        new MemoryReportCollector(stats).walkObjects(List.of(a, C.class)));

    List<HeapSnapshotStatistics.ComponentClusterObjectsStatistics> componentStats = stats.getComponentStats();
    Assert.assertEquals(2, componentStats.size());
    Assert.assertEquals(UNCATEGORIZED_CATEGORY_LABEL,
                        componentStats.get(0).getCluster().getComponentCategory().getLabel());
    Assert.assertEquals("A", componentStats.get(1).getCluster().getLabel());
    // A, B, Integer
    Assert.assertEquals(3, componentStats.get(1).getOwnedClusterStat().getObjectsStatistics().getObjectsCount());
    Assert.assertEquals(56, componentStats.get(1).getOwnedClusterStat().getObjectsStatistics().getTotalSizeInBytes());
    checkObjectsUntagged(new Object[]{a, C.class, a.myInt, a.myB});
  }

  @Test
  public void testStaticFieldHigherOwnershipPriorityThanInstanceField() {
    ComponentsSet componentsSet = new ComponentsSet();
    ComponentsSet.ComponentCategory defaultCategory = componentsSet.registerCategory("DEFAULT");
    componentsSet.addComponentWithPackagesAndClassNames("A", defaultCategory,
                                                        Collections.emptyList(),
                                                        List.of("com.android.tools.idea.diagnostics.heap.HeapAnalyzerTest$A"));
    componentsSet.addComponentWithPackagesAndClassNames("C", defaultCategory,
                                                        Collections.emptyList(),
                                                        List.of("com.android.tools.idea.diagnostics.heap.HeapAnalyzerTest$C"));

    // to initialize C class.
    C c = new C();
    HeapSnapshotStatistics stats = new HeapSnapshotStatistics(componentsSet);
    Assert.assertEquals(StatusCode.NO_ERROR, new MemoryReportCollector(new TestTraverseChildProcessor(stats), stats).walkObjects(
      List.of(new A(new B()), c.getClass())));

    List<HeapSnapshotStatistics.ComponentClusterObjectsStatistics> componentStats = stats.getComponentStats();
    Assert.assertEquals(3, componentStats.size());
    Assert.assertEquals(UNCATEGORIZED_CATEGORY_LABEL,
                        componentStats.get(0).getCluster().getComponentCategory().getLabel());
    Assert.assertEquals("A", componentStats.get(1).getCluster().getLabel());
    // A instance and B instance
    Assert.assertEquals(2, componentStats.get(1).getOwnedClusterStat().getObjectsStatistics().getObjectsCount());
    Assert.assertEquals(40, componentStats.get(1).getOwnedClusterStat().getObjectsStatistics().getTotalSizeInBytes());

    Assert.assertEquals("C", componentStats.get(2).getCluster().getLabel());
    // C class object and boxed 0 static field
    Assert.assertEquals(2, componentStats.get(2).getOwnedClusterStat().getObjectsStatistics().getObjectsCount());
    checkObjectsUntagged(new Object[]{c, C.STATIC_INT});
  }

  @Test
  public void testArrayElementsHigherOwnershipPriorityThanNonComponent() {
    ComponentsSet componentsSet = new ComponentsSet();
    ComponentsSet.ComponentCategory defaultCategory = componentsSet.registerCategory("DEFAULT");
    componentsSet.addComponentWithPackagesAndClassNames("D", defaultCategory,
                                                        Collections.emptyList(),
                                                        List.of("com.android.tools.idea.diagnostics.heap.HeapAnalyzerTest$D"));

    B b = new B();
    HeapSnapshotStatistics stats = new HeapSnapshotStatistics(componentsSet);
    D d = new D(b);
    A a = new A(b);
    Assert.assertEquals(StatusCode.NO_ERROR,
                        new MemoryReportCollector(stats).walkObjects(List.of(d, a)));

    List<HeapSnapshotStatistics.ComponentClusterObjectsStatistics> componentStats = stats.getComponentStats();
    Assert.assertEquals(2, componentStats.size());
    Assert.assertEquals(UNCATEGORIZED_CATEGORY_LABEL,
                        componentStats.get(0).getCluster().getComponentCategory().getLabel());

    Assert.assertEquals(2, componentStats.get(0).getOwnedClusterStat().getObjectsStatistics().getObjectsCount());
    Assert.assertEquals(40, componentStats.get(0).getOwnedClusterStat().getObjectsStatistics().getTotalSizeInBytes());
    Assert.assertEquals("D", componentStats.get(1).getCluster().getLabel());
    Assert.assertEquals(3, componentStats.get(1).getOwnedClusterStat().getObjectsStatistics().getObjectsCount());
    Assert.assertEquals(56, componentStats.get(1).getOwnedClusterStat().getObjectsStatistics().getTotalSizeInBytes());
    checkObjectsUntagged(new Object[]{a, b, d, a.myB, a.myInt, d.myArray});
  }

  @Test
  public void testInstanceFieldHigherOwnershipPriorityThanArrayElements() {
    ComponentsSet componentsSet = new ComponentsSet();
    ComponentsSet.ComponentCategory defaultCategory = componentsSet.registerCategory("DEFAULT");
    componentsSet.addComponentWithPackagesAndClassNames("A", defaultCategory,
                                                        Collections.emptyList(),
                                                        List.of("com.android.tools.idea.diagnostics.heap.HeapAnalyzerTest$A"));
    componentsSet.addComponentWithPackagesAndClassNames("D", defaultCategory,
                                                        Collections.emptyList(),
                                                        List.of("com.android.tools.idea.diagnostics.heap.HeapAnalyzerTest$D"));

    B b = new B();
    D d = new D(b);
    A a = new A(b);
    HeapSnapshotStatistics stats = new HeapSnapshotStatistics(componentsSet);
    Assert.assertEquals(StatusCode.NO_ERROR,
                        new MemoryReportCollector(stats).walkObjects(List.of(d, a)));

    List<HeapSnapshotStatistics.ComponentClusterObjectsStatistics> componentStats = stats.getComponentStats();
    Assert.assertEquals(3, componentStats.size());
    Assert.assertEquals(UNCATEGORIZED_CATEGORY_LABEL,
                        componentStats.get(0).getCluster().getComponentCategory().getLabel());

    Assert.assertEquals("A", componentStats.get(1).getCluster().getLabel());
    Assert.assertEquals(3, componentStats.get(1).getOwnedClusterStat().getObjectsStatistics().getObjectsCount());
    Assert.assertEquals(56, componentStats.get(1).getOwnedClusterStat().getObjectsStatistics().getTotalSizeInBytes());
    Assert.assertEquals("D", componentStats.get(2).getCluster().getLabel());
    Assert.assertEquals(2, componentStats.get(2).getOwnedClusterStat().getObjectsStatistics().getObjectsCount());
    Assert.assertEquals(40, componentStats.get(2).getOwnedClusterStat().getObjectsStatistics().getTotalSizeInBytes());
    checkObjectsUntagged(new Object[]{a, b, d, a.myB, a.myInt, d.myArray});
  }

  /**
   * Current JVM version: 8,11,17
   */
  private static int javaVersion() {
    String version = System.getProperty("java.specification.version");
    int dot = version.indexOf('.');
    if (dot >= 0) {
      return Integer.parseInt(version.substring(dot+1));
    } else {
      return Integer.parseInt(version);
    }
  }

  @Test
  public void testWeakSoftReferencesIgnored() {
    ComponentsSet componentsSet = new ComponentsSet();
    ComponentsSet.ComponentCategory defaultCategory = componentsSet.registerCategory("DEFAULT");
    componentsSet.addComponentWithPackagesAndClassNames("F",
                                                        defaultCategory,
                                                        Collections.emptyList(),
                                                        List.of("com.android.tools.idea.diagnostics.heap.HeapAnalyzerTest$F"));

    HeapSnapshotStatistics stats = new HeapSnapshotStatistics(componentsSet);
    F f = new F();
    Assert.assertEquals(StatusCode.NO_ERROR,
                        new MemoryReportCollector(stats).walkObjects(List.of(f)));

    List<HeapSnapshotStatistics.ComponentClusterObjectsStatistics> componentStats = stats.getComponentStats();
    Assert.assertEquals(2, componentStats.size());
    Assert.assertEquals(UNCATEGORIZED_CATEGORY_LABEL,
                        componentStats.get(0).getCluster().getComponentCategory().getLabel());

    Assert.assertEquals("F", componentStats.get(1).getCluster().getLabel());
    // JDK17 : F, WeakReference, ReferenceQueue$Null and ReferenceQueue$Lock
    // JDK21 : F, WeakReference, ReferenceQueue$Null
    Assert.assertEquals(javaVersion() > 17 ? 3 : 4, componentStats.get(1).getOwnedClusterStat().getObjectsStatistics().getObjectsCount());
    Assert.assertEquals(javaVersion() > 17 ? 80 : 96, componentStats.get(1).getOwnedClusterStat().getObjectsStatistics().getTotalSizeInBytes());
    checkObjectsUntagged(new Object[]{f, f.myWeakString});
  }

  @Test
  public void testComponentRetainedSize() {
    ComponentsSet componentsSet = new ComponentsSet();
    ComponentsSet.ComponentCategory defaultCategory = componentsSet.registerCategory("DEFAULT");
    componentsSet.addComponentWithPackagesAndClassNames("B",
                                                        defaultCategory,
                                                        Collections.emptyList(),
                                                        List.of("com.android.tools.idea.diagnostics.heap.HeapAnalyzerTest$B"));
    componentsSet.addComponentWithPackagesAndClassNames("D",
                                                        defaultCategory,
                                                        Collections.emptyList(),
                                                        List.of("com.android.tools.idea.diagnostics.heap.HeapAnalyzerTest$D"));

    HeapSnapshotStatistics stats = new HeapSnapshotStatistics(componentsSet);
    Assert.assertEquals(StatusCode.NO_ERROR,
                        new MemoryReportCollector(stats).walkObjects(List.of(new D(new B()))));

    List<HeapSnapshotStatistics.ComponentClusterObjectsStatistics> componentStats = stats.getComponentStats();
    Assert.assertEquals(3, componentStats.size());
    Assert.assertEquals(UNCATEGORIZED_CATEGORY_LABEL,
                        componentStats.get(0).getCluster().getComponentCategory().getLabel());

    Assert.assertEquals("B", componentStats.get(1).getCluster().getLabel());
    Assert.assertEquals("D", componentStats.get(2).getCluster().getLabel());

    Assert.assertEquals(2, componentStats.get(2).getOwnedClusterStat().getObjectsStatistics().getObjectsCount());
    Assert.assertEquals(40, componentStats.get(2).getOwnedClusterStat().getObjectsStatistics().getTotalSizeInBytes());
    Assert.assertEquals(3, componentStats.get(2).getRetainedClusterStat().getObjectsStatistics().getObjectsCount());
    Assert.assertEquals(56, componentStats.get(2).getRetainedClusterStat().getObjectsStatistics().getTotalSizeInBytes());
  }

  @Test
  public void testTraverseMetadata() {
    ComponentsSet componentsSet = new ComponentsSet();
    HeapSnapshotStatistics stats = new HeapSnapshotStatistics(componentsSet);
    MemoryReportCollector traverse = new MemoryReportCollector(stats);

    Assert.assertEquals(StatusCode.NO_ERROR,
                        traverse.walkObjects(List.of(new A())));
    Assert.assertEquals(stats.maxFieldsCacheSize, 2);
    Assert.assertEquals(stats.maxObjectsQueueSize, 2);
    Assert.assertEquals(stats.enumeratedGarbageCollectedObjects, 0);
    Assert.assertEquals(stats.unsuccessfulFieldAccessCounter, 0);
    Assert.assertEquals(stats.heapObjectCount, 3);
  }

  @Test
  public void testTraverseReturnLowMemoryError() {
    ComponentsSet componentsSet = new ComponentsSet();
    HeapSnapshotStatistics stats = new HeapSnapshotStatistics(componentsSet);
    MemoryReportCollector traverse = new MemoryReportCollector(stats);

    LowMemoryWatcher.onLowMemorySignalReceived(false);

    Assert.assertEquals(StatusCode.LOW_MEMORY,
                        traverse.walkObjects(List.of(new A())));
  }

  @Test
  public void testUncategorizedComponent() {
    ComponentsSet componentsSet = new ComponentsSet();
    ComponentsSet.ComponentCategory defaultCategory = componentsSet.registerCategory("DEFAULT");
    componentsSet.addComponentWithPackagesAndClassNames("D",
                                                        defaultCategory,
                                                        Collections.emptyList(),
                                                        List.of("com.android.tools.idea.diagnostics.heap.HeapAnalyzerTest$D"));

    HeapSnapshotStatistics stats = new HeapSnapshotStatistics(componentsSet);
    B b = new B();
    Assert.assertEquals(StatusCode.NO_ERROR,
                        new MemoryReportCollector(stats).walkObjects(List.of(new A(b), new D(b))));

    List<HeapSnapshotStatistics.ComponentClusterObjectsStatistics> componentStats = stats.getComponentStats();
    Assert.assertEquals(2, componentStats.size());
    Assert.assertEquals(UNCATEGORIZED_CATEGORY_LABEL,
                        componentStats.get(0).getCluster().getComponentCategory().getLabel());

    // HeapAnalyzerTest$A and underlying Integer
    Assert.assertEquals(2, componentStats.get(0).getOwnedClusterStat().getObjectsStatistics().getObjectsCount());
    Assert.assertEquals("D", componentStats.get(1).getCluster().getLabel());
    // HeapAnalyzerTest$D, underlying array and HeapAnalyzerTest$B
    Assert.assertEquals(3, componentStats.get(1).getOwnedClusterStat().getObjectsStatistics().getObjectsCount());
  }

  @Test
  public void testCategoryComponentData() {
    ComponentsSet componentsSet = new ComponentsSet();
    ComponentsSet.ComponentCategory defaultCategory = componentsSet.registerCategory("DEFAULT");
    componentsSet.addComponentWithPackagesAndClassNames("A",
                                                        defaultCategory,
                                                        Collections.emptyList(),
                                                        List.of("com.android.tools.idea.diagnostics.heap.HeapAnalyzerTest$A"));
    componentsSet.addComponentWithPackagesAndClassNames("B",
                                                        defaultCategory,
                                                        Collections.emptyList(),
                                                        List.of("com.android.tools.idea.diagnostics.heap.HeapAnalyzerTest$B"));

    HeapSnapshotStatistics stats = new HeapSnapshotStatistics(componentsSet);
    Assert.assertEquals(StatusCode.NO_ERROR, new MemoryReportCollector(stats).walkObjects(List.of(new A())));

    HeapSnapshotStatistics.ClusterObjectsStatistics categoryComponentStats =
      stats.getCategoryComponentStats().get(defaultCategory.getId());
    Assert.assertEquals(3, categoryComponentStats.getOwnedClusterStat().getObjectsStatistics().getObjectsCount());
    Assert.assertEquals(56, categoryComponentStats.getOwnedClusterStat().getObjectsStatistics().getTotalSizeInBytes());
    Assert.assertEquals(3, categoryComponentStats.getRetainedClusterStat().getObjectsStatistics().getObjectsCount());
    Assert.assertEquals(56, categoryComponentStats.getRetainedClusterStat().getObjectsStatistics().getTotalSizeInBytes());
  }

  private static class TestTraverseChildProcessor extends HeapTraverseChildProcessor {
    private static final Set<Class<?>> ALLOWED_CLASSES = Set.of(A.class, B.class, C.class, D.class, F.class, Class.class, Integer.class);

    public TestTraverseChildProcessor(@NotNull HeapSnapshotStatistics statistics) {
      super(statistics);
    }

    @Override
    void processChildObjects(@Nullable final Object obj,
                             @NotNull final TriConsumer<Object, HeapTraverseNode.RefWeight, String> consumer,
                             @NotNull final FieldCache fieldCache) throws HeapSnapshotTraverseException {
      super.processChildObjects(obj, (Object value, HeapTraverseNode.RefWeight ownershipWeight, String label) -> {
        if (value == null) {
          return;
        }
        if (!ALLOWED_CLASSES.contains(value.getClass())) {
          return;
        }
        consumer.accept(value, ownershipWeight, label);
      }, fieldCache);
    }
  }

  @Test
  public void testStudioStatsProtoCreation() {
    PowerSaveMode.setEnabled(true);
    ComponentsSet componentsSet = new ComponentsSet();

    ComponentsSet.ComponentCategory defaultCategory = componentsSet.registerCategory("diagnostics");
    componentsSet.addComponentWithPackagesAndClassNames("diagnostics_main",
                                                        defaultCategory,
                                                        List.of(
                                                          "com.android.tools.idea.diagnostics"),
                                                        Collections.emptyList());
    Assert.assertEquals(StatusCode.NO_ERROR,
                        new MemoryReportCollector(
                          new HeapSnapshotStatistics(componentsSet)).walkObjects(List.of(
                          new E(new TruncatingStringBuilder(0, "")))));

    HeapSnapshotStatistics stats = new HeapSnapshotStatistics(componentsSet);
    Assert.assertEquals(StatusCode.NO_ERROR,
                        new MemoryReportCollector(stats).walkObjects(List.of(
                          new E(new TruncatingStringBuilder(0, "")))));
    MemoryUsageReportEvent event =
      stats.buildMemoryUsageReportEvent(StatusCode.NO_ERROR, 1500, 1000, 200);

    // ANDROID_REST, diagnostics
    Assert.assertEquals(2, event.getComponentStatsCount());

    Assert.assertEquals(UNCATEGORIZED_COMPONENT_LABEL, event.getComponentStats(0).getLabel());
    Assert.assertEquals(0,
                        event.getComponentStats(0).getStats().getOwnedClusterStats().getTotalStats()
                          .getObjectsCount());
    Assert.assertEquals(0,
                        event.getComponentStats(0).getStats().getOwnedClusterStats().getTotalStats()
                          .getTotalSizeBytes());

    Assert.assertEquals("diagnostics_main", event.getComponentStats(1).getLabel());
    Assert.assertEquals(8,
                        event.getComponentStats(1).getStats().getOwnedClusterStats().getTotalStats()
                          .getObjectsCount());
    Assert.assertEquals(192, event.getComponentStats(1).getStats().getOwnedClusterStats().getTotalStats().getTotalSizeBytes());

    Assert.assertEquals(0, event.getSharedComponentStatsCount());

    // ANDROID_REST and diagnostics
    Assert.assertEquals(2, event.getComponentCategoryStatsCount());
    Assert.assertEquals(UNCATEGORIZED_CATEGORY_LABEL, event.getComponentCategoryStats(0).getLabel());
    Assert.assertEquals("diagnostics", event.getComponentCategoryStats(1).getLabel());
    Assert.assertEquals(8, event.getComponentCategoryStats(1).getStats().getOwnedClusterStats().getTotalStats().getObjectsCount());
    Assert.assertEquals(192, event.getComponentCategoryStats(1).getStats().getOwnedClusterStats().getTotalStats().getTotalSizeBytes());

    Assert.assertEquals(8, event.getMetadata().getTotalHeapObjectsStats().getTotalStats().getObjectsCount());
    Assert.assertEquals(192, event.getMetadata().getTotalHeapObjectsStats().getTotalStats().getTotalSizeBytes());
    Assert.assertEquals(StatusCode.NO_ERROR, event.getMetadata().getStatusCode());
    Assert.assertEquals(1.5, event.getMetadata().getCollectionTimeSeconds(), 0);
    Assert.assertEquals(1, event.getMetadata().getCollectionStartTimestampSeconds(), 0);
  }

  @Test
  public void testGetStaticFieldsNoSideEffect() throws
                                                ClassNotFoundException {
    if (Arrays.stream(MemoryReportJniHelper.getClasses())
      .anyMatch(c -> "com.android.tools.idea.diagnostics.heap.HeapAnalyzerTest$H".equals(c.getName()))) {
      throw new AssumptionViolatedException("One of the tests loaded HeapAnalyzerTest$H class");
    }
    ComponentsSet componentsSet = new ComponentsSet();

    ComponentsSet.ComponentCategory defaultCategory = componentsSet.registerCategory("diagnostics");
    componentsSet.addComponentWithPackagesAndClassNames("diagnostics_main",
                                                        defaultCategory,
                                                        List.of(
                                                          "com.android.tools.idea.diagnostics"),
                                                        Collections.emptyList());
    Class<?> gClass = Class.forName(G.class.getName());
    HeapSnapshotStatistics statistics = new HeapSnapshotStatistics(componentsSet);
    ClassNameRecordingChildProcessor processor = new ClassNameRecordingChildProcessor(statistics);
    Assert.assertEquals(StatusCode.NO_ERROR, new MemoryReportCollector(processor, statistics).walkObjects(List.of(gClass)));
    for (Class<?> aClass : MemoryReportJniHelper.getClasses()) {
      Assert.assertNotEquals("com.android.tools.idea.diagnostics.heap.HeapAnalyzerTest$H", aClass.getName());
    }

    Assert.assertTrue(processor.visitedClassesNames.contains("com.android.tools.idea.diagnostics.heap.HeapAnalyzerTest$G"));
  }

  @Test
  public void testHistogramCollection() {
    ComponentsSet componentsSet = new ComponentsSet();

    ComponentsSet.ComponentCategory defaultCategory = componentsSet.registerCategory("diagnostics");
    componentsSet.addComponentWithPackagesAndClassNames("diagnostics_main",
                                                        defaultCategory,
                                                        List.of(
                                                          "com.android.tools.idea.diagnostics"),
                                                        Collections.emptyList());
    HeapSnapshotStatistics statistics = new HeapSnapshotStatistics(new HeapTraverseConfig(componentsSet,
      /*collectHistograms=*/true, /*collectDisposerTreeInfo=*/false));
    Assert.assertEquals(StatusCode.NO_ERROR,
                        new MemoryReportCollector(statistics).walkObjects(List.of(new D(new B(), new B(), new B()))));
    Assert.assertNotNull(statistics.getExtendedReportStatistics());
    ExtendedReportStatistics.ClusterHistogram histogram0 = statistics.getExtendedReportStatistics().categoryHistograms.get(0);
    ExtendedReportStatistics.ClusterHistogram histogram1 = statistics.getExtendedReportStatistics().categoryHistograms.get(1);
    Assert.assertTrue(histogram0.histogram.isEmpty());
    Assert.assertEquals(48, histogram1.histogram.get("com.android.tools.idea.diagnostics.heap.HeapAnalyzerTest$B").getTotalSizeInBytes());
    Assert.assertEquals(3, histogram1.histogram.get("com.android.tools.idea.diagnostics.heap.HeapAnalyzerTest$B").getObjectsCount());
    Assert.assertEquals(16, histogram1.histogram.get("com.android.tools.idea.diagnostics.heap.HeapAnalyzerTest$D").getTotalSizeInBytes());
    Assert.assertEquals(1, histogram1.histogram.get("com.android.tools.idea.diagnostics.heap.HeapAnalyzerTest$D").getObjectsCount());
  }

  @Test
  public void testPopFromEmptyStackThrows() {
    try {
      assertThrows(
        NoSuchElementException.class,
        StackNode::popElementFromDepthFirstSearchStack
      );
    }
    finally {
      StackNode.clearDepthFirstSearchStack();
    }
  }

  private static class FakeCrushReporter extends StudioCrashReporter {

    @NotNull
    List<CrashReport> crashReports = Lists.newArrayList();

    @Override
    @NonNull
    public CompletableFuture<String> submit(@NonNull CrashReport report, boolean userReported) {
      crashReports.add(report);
      return super.submit(report, userReported);
    }
  }

  @Test
  public void testTrackedFQNs() {
    ComponentsSet componentsSet = new ComponentsSet();

    ComponentsSet.ComponentCategory defaultCategory =
      componentsSet.registerCategory("diagnostics", Long.MAX_VALUE, List.of("com.android.tools.idea.diagnostics.heap.HeapAnalyzerTest$B",
                                                                            "com.android.tools.idea.diagnostics.heap.HeapAnalyzerTest$D"));
    componentsSet.addComponentWithPackagesAndClassNames("B",
                                                        Long.MAX_VALUE,
                                                        defaultCategory,
                                                        Collections.emptyList(),
                                                        List.of(
                                                          "com.android.tools.idea.diagnostics$B"),
                                                        List.of("com.android.tools.idea.diagnostics.heap.HeapAnalyzerTest$B",
                                                                "com.android.tools.idea.diagnostics.heap.HeapAnalyzerTest$D"),
                                                        Collections.emptyList());
    componentsSet.addComponentWithPackagesAndClassNames("D",
                                                        defaultCategory,
                                                        Collections.emptyList(),
                                                        List.of(
                                                          "com.android.tools.idea.diagnostics$D"));

    HeapSnapshotStatistics statistics = new HeapSnapshotStatistics(new HeapTraverseConfig(componentsSet,
      /*collectHistograms=*/true, /*collectDisposerTreeInfo=*/false));
    Assert.assertEquals(StatusCode.NO_ERROR,
                        new MemoryReportCollector(statistics).walkObjects(List.of(new D(new B(), new B(), new B()))));
    Assert.assertEquals(3, statistics.getComponentStats().get(1).getTrackedFQNInstanceCounter()
      .getInt("com.android.tools.idea.diagnostics.heap.HeapAnalyzerTest$B"));
    Assert.assertEquals(1, statistics.getComponentStats().get(1).getTrackedFQNInstanceCounter()
      .getInt("com.android.tools.idea.diagnostics.heap.HeapAnalyzerTest$D"));
    MemoryUsageReportEvent event =
      statistics.buildMemoryUsageReportEvent(StatusCode.NO_ERROR, 1500, 1000, 200);
    Assert.assertEquals(3, event.getComponentStats(1)
      .getInstanceCountPerClassNameOrThrow("com.android.tools.idea.diagnostics.heap.HeapAnalyzerTest$B"));
    Assert.assertEquals(1, event.getComponentStats(1)
      .getInstanceCountPerClassNameOrThrow("com.android.tools.idea.diagnostics.heap.HeapAnalyzerTest$D"));
    Assert.assertTrue(event.getComponentStats(0).getInstanceCountPerClassNameMap().isEmpty());
    Assert.assertTrue(event.getComponentCategoryStats(0).getInstanceCountPerClassNameMap().isEmpty());
    Assert.assertEquals(3, event.getComponentCategoryStats(1)
      .getInstanceCountPerClassNameOrThrow("com.android.tools.idea.diagnostics.heap.HeapAnalyzerTest$B"));
    Assert.assertEquals(1, event.getComponentCategoryStats(1)
      .getInstanceCountPerClassNameOrThrow("com.android.tools.idea.diagnostics.heap.HeapAnalyzerTest$D"));
  }

  @Test
  public void testExtendedReportNodesOrdering() throws IOException {
    ComponentsSet componentsSet = new ComponentsSet();

    ComponentsSet.ComponentCategory defaultCategory = componentsSet.registerCategory("diagnostics");
    componentsSet.addComponentWithPackagesAndClassNames("TestComponent",
                                                        defaultCategory,
                                                        Collections.emptyList(),
                                                        List.of(
                                                          "com.android.tools.idea.diagnostics.heap.HeapAnalyzerTest$ReferenceToObjectArray",
                                                          "com.android.tools.idea.diagnostics.heap.HeapAnalyzerTest$ReferenceToObjectArray$ReferenceToObjectArray2"));
    FakeCrushReporter crushReporter = new FakeCrushReporter();
    getApplication().registerService(StudioCrashReporter.class, crushReporter);

    WeakList<Object> roots = new WeakList<>();
    roots.add(new ReferenceToObjectArray(new int[]{1}, new int[]{2}, new int[]{3}));
    roots.add(new ReferenceToObjectArray.ReferenceToObjectArray2(new int[]{1, 2, 3, 4, 5, 6}, new int[]{7, 8, 9, 10, 11, 12}));

    MemoryReportCollector.collectAndSendExtendedMemoryReport(componentsSet, List.of(componentsSet.getComponents().get(1)), () -> roots, 0);
    assertSize(1, crushReporter.crashReports);
    CrashReport report = crushReporter.crashReports.get(0);
    MultipartEntityBuilder builder = MultipartEntityBuilder.create();
    report.serialize(builder);
    String serializedExtendedReport = new String(ByteStreams.toByteArray(builder.build().getContent()), Charset.defaultCharset());
    serializedExtendedReport = replaceNewlines(serializedExtendedReport);
    assertExtendedMemoryReport("testExtendedReportNodesOrdering", serializedExtendedReport);
    assertExtendedMemoryReportSummary("testExtendedReportNodesOrdering", serializedExtendedReport);
  }

  @Test
  public void testExtendedReportRepeatedNodes() throws IOException {
    ComponentsSet componentsSet = new ComponentsSet();

    ComponentsSet.ComponentCategory defaultCategory = componentsSet.registerCategory("diagnostics");
    componentsSet.addComponentWithPackagesAndClassNames("D",
                                                        1,
                                                        defaultCategory,
                                                        Collections.emptyList(),
                                                        List.of("java.util.LinkedList"),
                                                        Collections.emptyList(),
                                                        Collections.emptyList());
    FakeCrushReporter crushReporter = new FakeCrushReporter();
    getApplication().registerService(StudioCrashReporter.class, crushReporter);

    List<String> strings = new LinkedList<>();
    strings.add("hello1");
    strings.add("hello2");
    strings.add("hello3");
    strings.add(String.join("", Collections.nCopies(100000, "hello4")));
    strings.add("hello5");
    WeakList<Object> roots = new WeakList<>();
    roots.add(strings);

    MemoryReportCollector.collectAndSendExtendedMemoryReport(componentsSet, List.of(componentsSet.getComponents().get(1)), () -> roots, 0);
    assertSize(1, crushReporter.crashReports);
    CrashReport report = crushReporter.crashReports.get(0);
    MultipartEntityBuilder builder = MultipartEntityBuilder.create();
    report.serialize(builder);
    String serializedExtendedReport = new String(ByteStreams.toByteArray(builder.build().getContent()), Charset.defaultCharset());
    serializedExtendedReport = replaceNewlines(serializedExtendedReport);
    assertExtendedMemoryReport("testExtendedReportRepeatedNodes", serializedExtendedReport);
    assertExtendedMemoryReportSummary("testExtendedReportRepeatedNodes", serializedExtendedReport);
  }

  @Test
  public void testExtendedReportNominatedClassesTree() throws IOException {
    ComponentsSet componentsSet = new ComponentsSet();

    ComponentsSet.ComponentCategory defaultCategory = componentsSet.registerCategory("diagnostics");
    componentsSet.addComponentWithPackagesAndClassNames("D",
                                                        1,
                                                        defaultCategory,
                                                        Collections.emptyList(),
                                                        List.of("com.android.tools.idea.diagnostics.heap.HeapAnalyzerTest$D"),
                                                        List.of("com.android.tools.idea.diagnostics.heap.HeapAnalyzerTest$D"),
                                                        Collections.emptyList());
    FakeCrushReporter crushReporter = new FakeCrushReporter();
    getApplication().registerService(StudioCrashReporter.class, crushReporter);

    WeakList<Object> roots = new WeakList<>();
    B b = new B();
    B b2 = new B();
    roots.add(new D(b));
    roots.add(new D(b));
    roots.add(new ReferenceToB(b));
    roots.add(new D(b2, new B()));
    roots.add(new D(b2));
    Disposer.register(b, b2);
    Disposer.dispose(b);

    MemoryReportCollector.collectAndSendExtendedMemoryReport(componentsSet, List.of(componentsSet.getComponents().get(1)), () -> roots, 0);
    assertSize(1, crushReporter.crashReports);
    CrashReport report = crushReporter.crashReports.get(0);
    MultipartEntityBuilder builder = MultipartEntityBuilder.create();
    report.serialize(builder);
    String serializedExtendedReport = new String(ByteStreams.toByteArray(builder.build().getContent()), Charset.defaultCharset());
    serializedExtendedReport = replaceNewlines(serializedExtendedReport);
    assertExtendedMemoryReport("testExtendedReportNominatedClassesTree", serializedExtendedReport);
    assertExtendedMemoryReportSummary("testExtendedReportNominatedClassesTree", serializedExtendedReport);
  }

  @Test
  public void testDisposedObjectsInExtendedReport() throws IOException {
    ComponentsSet componentsSet = new ComponentsSet();

    ComponentsSet.ComponentCategory defaultCategory = componentsSet.registerCategory("diagnostics");
    componentsSet.addComponentWithPackagesAndClassNames("D",
                                                        1,
                                                        defaultCategory,
                                                        Collections.emptyList(),
                                                        List.of("com.android.tools.idea.diagnostics.heap.HeapAnalyzerTest$D"),
                                                        List.of("com.android.tools.idea.diagnostics.heap.HeapAnalyzerTest$D"),
                                                        Collections.emptyList());
    FakeCrushReporter crushReporter = new FakeCrushReporter();
    getApplication().registerService(StudioCrashReporter.class, crushReporter);

    WeakList<Object> roots = new WeakList<>();
    B b = new B();
    B b2 = new B();
    D d1 = new D(b);
    D d2 = new D(b);
    D d3 = new D(b2);

    roots.add(d1);
    roots.add(List.of(d2));
    roots.add(d3);

    Disposer.register(d1, b);
    Disposer.register(d1, d3);
    Disposer.register(d1, b2);
    Disposer.dispose(d1);

    MemoryReportCollector.collectAndSendExtendedMemoryReport(componentsSet, List.of(componentsSet.getComponents().get(1)), () -> roots, 0);
    assertSize(1, crushReporter.crashReports);
    CrashReport report = crushReporter.crashReports.get(0);
    MultipartEntityBuilder builder = MultipartEntityBuilder.create();
    report.serialize(builder);
    String serializedExtendedReport = new String(ByteStreams.toByteArray(builder.build().getContent()), Charset.defaultCharset());
    serializedExtendedReport = replaceNewlines(serializedExtendedReport);
    assertExtendedMemoryReport("testDisposedObjectsInExtendedReport", serializedExtendedReport);
    assertExtendedMemoryReportSummary("testDisposedObjectsInExtendedReport", serializedExtendedReport);
  }

  @Test
  public void testExtendedReportCollection() throws IOException {
    ComponentsSet componentsSet = new ComponentsSet();

    ComponentsSet.ComponentCategory defaultCategory = componentsSet.registerCategory("diagnostics");
    componentsSet.addComponentWithPackagesAndClassNames("A",
                                                        1,
                                                        defaultCategory,
                                                        Collections.emptyList(),
                                                        List.of("com.android.tools.idea.diagnostics.heap.HeapAnalyzerTest$A"),
                                                        List.of("com.android.tools.idea.diagnostics.heap.HeapAnalyzerTest$A"),
                                                        Collections.emptyList());
    componentsSet.addComponentWithPackagesAndClassNames("B",
                                                        2,
                                                        defaultCategory,
                                                        Collections.emptyList(),
                                                        List.of("com.android.tools.idea.diagnostics.heap.HeapAnalyzerTest$B"),
                                                        List.of("com.android.tools.idea.diagnostics.heap.HeapAnalyzerTest$B"),
                                                        Collections.emptyList());
    FakeCrushReporter crushReporter = new FakeCrushReporter();
    getApplication().registerService(StudioCrashReporter.class, crushReporter);

    WeakList<Object> roots = new WeakList<>();
    A a = new A(new B());
    roots.add(a);
    MemoryReportCollector.collectAndSendExtendedMemoryReport(componentsSet, List.of(componentsSet.getComponents().get(1),
                                                                                    componentsSet.getComponents().get(2)), () -> roots, 0);
    assertSize(2, crushReporter.crashReports);
    CrashReport report = crushReporter.crashReports.get(0);
    MultipartEntityBuilder builder = MultipartEntityBuilder.create();
    report.serialize(builder);
    String serializedExtendedReport = new String(ByteStreams.toByteArray(builder.build().getContent()), Charset.defaultCharset());
    serializedExtendedReport = replaceNewlines(serializedExtendedReport);
    assertRequestContainsField(serializedExtendedReport, "All clusters that exceeded the memory usage threshold", "A,B");
    assertRequestContainsField(serializedExtendedReport, "Target exceeded cluster", "A");
    assertRequestContainsField(serializedExtendedReport, "Total used memory", "56B/3 objects");

    assertExtendedMemoryReport("testExtendedReportCollectionA", serializedExtendedReport);
    assertExtendedMemoryReportSummary("testExtendedReportCollectionA", serializedExtendedReport);

    report = crushReporter.crashReports.get(1);
    builder = MultipartEntityBuilder.create();
    report.serialize(builder);
    serializedExtendedReport = new String(ByteStreams.toByteArray(builder.build().getContent()), Charset.defaultCharset());
    serializedExtendedReport = replaceNewlines(serializedExtendedReport);
    assertRequestContainsField(serializedExtendedReport, "All clusters that exceeded the memory usage threshold", "A,B");
    assertRequestContainsField(serializedExtendedReport, "Target exceeded cluster", "B");
    assertRequestContainsField(serializedExtendedReport, "Total used memory", "56B/3 objects");

    assertExtendedMemoryReport("testExtendedReportCollectionB", serializedExtendedReport);
    assertExtendedMemoryReportSummary("testExtendedReportCollectionB", serializedExtendedReport);
  }

  @Test
  public void testEssentialNominatedTypesInSummary() throws IOException {
    ComponentsSet componentsSet = new ComponentsSet();

    ComponentsSet.ComponentCategory defaultCategory = componentsSet.registerCategory("diagnostics");
    componentsSet.addComponentWithPackagesAndClassNames("D",
                                                        1,
                                                        defaultCategory,
                                                        Collections.emptyList(),
                                                        List.of("com.android.tools.idea.diagnostics.heap.HeapAnalyzerTest$D"),
                                                        List.of("com.android.tools.idea.diagnostics.heap.HeapAnalyzerTest$D"),
                                                        Collections.emptyList());
    FakeCrushReporter crushReporter = new FakeCrushReporter();
    getApplication().registerService(StudioCrashReporter.class, crushReporter);

    WeakList<Object> roots = new WeakList<>();
    B b = new B();
    D d = new D(b, new B());

    roots.add(List.of(d, "test"));

    Disposer.dispose(b);

    MemoryReportCollector.collectAndSendExtendedMemoryReport(componentsSet, List.of(componentsSet.getComponents().get(1)), () -> roots,
                                                             50);
    assertSize(1, crushReporter.crashReports);
    CrashReport report = crushReporter.crashReports.get(0);
    MultipartEntityBuilder builder = MultipartEntityBuilder.create();
    report.serialize(builder);
    String serializedExtendedReport = new String(ByteStreams.toByteArray(builder.build().getContent()), Charset.defaultCharset());
    serializedExtendedReport = replaceNewlines(serializedExtendedReport);
    assertExtendedMemoryReport("testEssentialNominatedTypesInSummary", serializedExtendedReport);
    assertExtendedMemoryReportSummary("testEssentialNominatedTypesInSummary", serializedExtendedReport);
  }

  @Test
  public void testCustomClassLoaderInNonExceedingComponent() throws
                                                             IOException,
                                                             ClassNotFoundException,
                                                             NoSuchMethodException,
                                                             InvocationTargetException,
                                                             InstantiationException,
                                                             IllegalAccessException {
    ComponentsSet componentsSet = new ComponentsSet();

    ComponentsSet.ComponentCategory defaultCategory = componentsSet.registerCategory("diagnostics");
    componentsSet.addComponentWithPackagesAndClassNames("B",
                                                        2,
                                                        defaultCategory,
                                                        Collections.emptyList(),
                                                        List.of("com.android.tools.idea.diagnostics.heap.SampleClass"),
                                                        List.of("com.android.tools.idea.diagnostics.heap.SampleClass"),
                                                        List.of("com.android.testutils.classloader.MultiClassLoader"));
    HeapSnapshotStatistics statistics = new HeapSnapshotStatistics(new HeapTraverseConfig(componentsSet,
      /*collectHistograms=*/false, /*collectDisposerTreeInfo=*/false));
    FakeCrushReporter crushReporter = new FakeCrushReporter();
    getApplication().registerService(StudioCrashReporter.class, crushReporter);

    WeakList<Object> roots = new WeakList<>();
    SingleClassLoader sampleClassLoader = new SingleClassLoader(SampleClass.class.getName());
    Class<?> sampleClass = sampleClassLoader.load();

    Constructor<?> sampleClassConstructor = sampleClass.getDeclaredConstructor();
    sampleClassConstructor.setAccessible(true);
    Object sampleClassObject = sampleClassConstructor.newInstance();
    Object[] objects = new Object[]{sampleClassObject, "test"};
    roots.add(objects);


    MemoryReportCollector.collectAndSendExtendedMemoryReport(componentsSet, List.of(componentsSet.getComponents().get(0)), () -> roots, 0);
    assertSize(1, crushReporter.crashReports);
    CrashReport report = crushReporter.crashReports.get(0);
    MultipartEntityBuilder builder = MultipartEntityBuilder.create();
    report.serialize(builder);
    String serializedExtendedReport = new String(ByteStreams.toByteArray(builder.build().getContent()), Charset.defaultCharset());
    serializedExtendedReport = replaceNewlines(serializedExtendedReport);
    assertRequestContainsField(serializedExtendedReport, "Target exceeded cluster", "uncategorized_main");
    assertRequestContainsField(serializedExtendedReport, "Total used memory", "88B/4 objects");
  }

  @Test
  public void testExtendedReportCustomClassLoaders() throws
                                                     IOException,
                                                     ClassNotFoundException,
                                                     NoSuchMethodException,
                                                     InvocationTargetException,
                                                     InstantiationException,
                                                     IllegalAccessException {
    ComponentsSet componentsSet = new ComponentsSet();

    ComponentsSet.ComponentCategory defaultCategory = componentsSet.registerCategory("diagnostics");
    componentsSet.addComponentWithPackagesAndClassNames("B",
                                                        2,
                                                        defaultCategory,
                                                        Collections.emptyList(),
                                                        List.of("com.android.tools.idea.diagnostics.heap.SampleClass"),
                                                        List.of("com.android.tools.idea.diagnostics.heap.SampleClass"),
                                                        Collections.emptyList());
    FakeCrushReporter crushReporter = new FakeCrushReporter();
    getApplication().registerService(StudioCrashReporter.class, crushReporter);

    WeakList<Object> roots = new WeakList<>();
    Object[] objects = new Object[22];

    for (int i = 0; i < 20; i++) {
      SingleClassLoader sampleClassLoader = new SingleClassLoader(SampleClass.class.getName());
      Class<?> sampleClass = sampleClassLoader.load();

      Constructor<?> sampleClassConstructor = sampleClass.getDeclaredConstructor();
      sampleClassConstructor.setAccessible(true);
      objects[i] = sampleClassConstructor.newInstance();
    }
    objects[20] = "test";

    roots.add(objects);

    MemoryReportCollector.collectAndSendExtendedMemoryReport(componentsSet, List.of(componentsSet.getComponents().get(1)), () -> roots, 0);
    assertSize(1, crushReporter.crashReports);
    CrashReport report = crushReporter.crashReports.get(0);
    MultipartEntityBuilder builder = MultipartEntityBuilder.create();
    report.serialize(builder);
    String serializedExtendedReport = new String(ByteStreams.toByteArray(builder.build().getContent()), Charset.defaultCharset());
    serializedExtendedReport = replaceNewlines(serializedExtendedReport);
    assertRequestContainsField(serializedExtendedReport, "Target exceeded cluster", "B");
    assertRequestContainsField(serializedExtendedReport, "Total used memory", "472B/23 objects");
    assertRequestContainsField(serializedExtendedReport, "Number of duplicated class loaders", "20");
    assertRequestContainsField(serializedExtendedReport, "Number of nominated class loaders", "0");

    assertExtendedMemoryReport("testExtendedReportCustomClassLoaders", serializedExtendedReport);
    assertExtendedMemoryReportSummary("testExtendedReportCustomClassLoaders", serializedExtendedReport);
  }

  private static void assertRequestContainsField(final String requestBody, final String name, final String value) {
    assertRequestContainsFieldWithPattern(requestBody, name, Pattern.quote(value));
  }

  private static void assertExtendedMemoryReportSummary(@NotNull final String fileName,
                                                        @NotNull final String requestBody) throws IOException {
    assertRequestContainsField(requestBody, "heapSummary", getBaselineContents(getBaselinePath(fileName + "_summary")));
  }

  private static void assertExtendedMemoryReport(@NotNull final String fileName, @NotNull final String requestBody) throws IOException {
    assertExtendedMemoryReportFile(requestBody, "extendedMemoryReport", getBaselineContents(getBaselinePath(fileName)));
  }

  /**
   * Returns path to a baseline file. Baselines may be different for different runtime versions.
   */
  @NotNull
  private static Path getBaselinePath(@NotNull final String fileName) {
    return TestUtils.resolveWorkspacePath("tools/adt/idea/android/testData/profiling/memory-usage-reports-baseline/" + fileName + ".txt");
  }

  /**
   * Get the contents of the baseline file, with system-dependent line endings
   */
  @NotNull
  private static String getBaselineContents(@NotNull final Path path) throws IOException {
    return replaceNewlines(Files.readString(path));
  }

  @NotNull
  private static String replaceNewlines(@NotNull final String s) {
    return s.replaceAll("(\r\n|\n)", System.lineSeparator());
  }

  private static void assertExtendedMemoryReportFile(final String requestBody, final String name, final String value) {
    assertExtendedMemoryReportFileWithPattern(requestBody, name, Pattern.quote(value));
  }

  private static void assertExtendedMemoryReportFileWithPattern(final String requestBody, final String name, final String pattern) {
    assertThat(requestBody, new RegexMatcher(
      "(?s).*\r?\nContent-Disposition: form-data; name=\"" + Pattern.quote(name) + "\"; filename=\"extendedMemoryReport.txt\"\r?\n" +
      "Content-Type: [^\r\n]*?\r?\n" +
      "Content-Transfer-Encoding: binary\r?\n" +
      "\r?\n" +
      pattern + "\r?\n.*"
    ));
  }

  private static void assertRequestContainsFieldWithPattern(final String requestBody, final String name, final String pattern) {
    assertThat(requestBody, new RegexMatcher(
      "(?s).*\r?\nContent-Disposition: form-data; name=\"" + Pattern.quote(name) + "\"\r?\n" +
      "Content-Type: [^\r\n]*?\r?\n" +
      "Content-Transfer-Encoding: 8bit\r?\n" +
      "\r?\n" +
      pattern + "\r?\n.*"
    ));
  }

  private static class RegexMatcher extends SubstringMatcher {

    private final Pattern myPattern;

    public RegexMatcher(@NonNull String patternString) {
      super(patternString);
      myPattern = Pattern.compile(patternString);
    }

    @Override
    protected boolean evalSubstringOf(String string) {
      return myPattern.matcher(string).matches();
    }

    @Override
    protected String relationship() {
      return "matches regular expression";
    }
  }

  private static class ClassNameRecordingChildProcessor extends HeapTraverseChildProcessor {

    @NotNull final Set<String> visitedClassesNames;

    public ClassNameRecordingChildProcessor(@NotNull HeapSnapshotStatistics statistics) {
      super(statistics);
      visitedClassesNames = Sets.newHashSet();
    }

    @Override
    void processChildObjects(@Nullable final Object obj,
                             @NotNull final TriConsumer<Object, HeapTraverseNode.RefWeight, String> consumer,
                             @NotNull final FieldCache fieldCache) throws HeapSnapshotTraverseException {
      if (obj instanceof Class) {
        visitedClassesNames.add(((Class<?>)obj).getName());
      }

      super.processChildObjects(obj, consumer, fieldCache);
    }
  }

  @Test
  public void testMergePoints() {
    ComponentsSet componentsSet = new ComponentsSet();

    ComponentsSet.ComponentCategory categoryA = componentsSet.registerCategory("A");
    componentsSet.addComponentWithPackagesAndClassNames("A",
                                                        categoryA,
                                                        Collections.emptyList(),
                                                        List.of("com.android.tools.idea.diagnostics.heap.HeapAnalyzerTest$A"));
    ComponentsSet.ComponentCategory categoryReferenceToB = componentsSet.registerCategory("ReferenceToB");
    componentsSet.addComponentWithPackagesAndClassNames("B",
                                                        categoryReferenceToB,
                                                        Collections.emptyList(),
                                                        List.of("com.android.tools.idea.diagnostics.heap.HeapAnalyzerTest$ReferenceToB"));

    HeapSnapshotStatistics statistics = new HeapSnapshotStatistics(new HeapTraverseConfig(componentsSet,
      /*collectHistograms=*/true, /*collectDisposerTreeInfo=*/false));

    B b1 = new B();
    B b2 = new B();

    Assert.assertEquals(StatusCode.NO_ERROR,
                        new MemoryReportCollector(statistics).walkObjects(List.of(new A(b1), new A(b2), new ReferenceToB(b1),
                                                                                  new ReferenceToB(b2))));
    Assert.assertNotNull(statistics.getExtendedReportStatistics());

    Assert.assertEquals(1, statistics.getExtendedReportStatistics().sharedClustersHistograms.size());
    ExtendedReportStatistics.ClusterHistogram sharedHistogram =
      statistics.getExtendedReportStatistics().sharedClustersHistograms.values().iterator().next();
    Assert.assertEquals(1, sharedHistogram.rootHistogram.size());
    Assert.assertEquals(2,
                        sharedHistogram.rootHistogram.get("com.android.tools.idea.diagnostics.heap.HeapAnalyzerTest$B").getObjectsCount());

    Assert.assertEquals(3, statistics.getExtendedReportStatistics().componentHistograms.size());
    ExtendedReportStatistics.ClusterHistogram histogram1 = statistics.getExtendedReportStatistics().componentHistograms.get(1);
    Assert.assertEquals(2,
                        histogram1.rootHistogram.get("com.android.tools.idea.diagnostics.heap.HeapAnalyzerTest$A")
                          .getObjectsCount());
    ExtendedReportStatistics.ClusterHistogram histogram2 = statistics.getExtendedReportStatistics().componentHistograms.get(2);
    Assert.assertEquals(2,
                        histogram2.rootHistogram.get("com.android.tools.idea.diagnostics.heap.HeapAnalyzerTest$ReferenceToB")
                          .getObjectsCount());
  }

  public void testPlatformObjectsTracking() {
    ComponentsSet componentsSet = new ComponentsSet();

    ComponentsSet.ComponentCategory category = componentsSet.registerCategory("Main");
    componentsSet.addComponentWithPackagesAndClassNames("HeapAnalyzerTest$ReferenceToPlatformObject",
                                                        category,
                                                        Collections.emptyList(),
                                                        List.of(
                                                          "com.android.tools.idea.diagnostics.heap.HeapAnalyzerTest$ReferenceToPlatformObject"));
    componentsSet.addComponentWithPackagesAndClassNames("A",
                                                        category,
                                                        Collections.emptyList(),
                                                        List.of("com.android.tools.idea.diagnostics.heap.HeapAnalyzerTest$A"));
    HeapSnapshotStatistics statistics = new HeapSnapshotStatistics(componentsSet);

    ReferenceToPlatformObject ref = new ReferenceToPlatformObject(new WeakList<>());
    Assert.assertEquals(StatusCode.NO_ERROR,
                        new MemoryReportCollector(statistics).walkObjects(List.of(ref, new A(new B()))));

    Assert.assertEquals(1, statistics.totalStats.getPlatformObjectsSelfStats().getObjectsCount());
    Assert.assertEquals(32, statistics.totalStats.getPlatformObjectsSelfStats().getTotalSizeInBytes());

    Assert.assertEquals(javaVersion() >= 21 ? 7 : 5, statistics.totalStats.getPlatformRetainedObjectsStats().getObjectsCount());
    Assert.assertEquals(javaVersion() >= 21 ? 176 : 120, statistics.totalStats.getPlatformRetainedObjectsStats().getTotalSizeInBytes());

    Assert.assertEquals(1, statistics.getComponentStats().get(1).getOwnedClusterStat().getPlatformObjectsSelfStats().getObjectsCount());
    Assert.assertEquals(32, statistics.getComponentStats().get(1).getOwnedClusterStat().getPlatformObjectsSelfStats()
      .getTotalSizeInBytes());
    Assert.assertEquals(javaVersion() >= 21 ? 7 : 5, statistics.getComponentStats().get(1).getOwnedClusterStat().getPlatformRetainedObjectsStats().getObjectsCount());
    Assert.assertEquals(javaVersion() >= 21 ? 176 : 120, statistics.getComponentStats().get(1).getOwnedClusterStat().getPlatformRetainedObjectsStats()
      .getTotalSizeInBytes());

    Assert.assertEquals(0, statistics.getComponentStats().get(2).getOwnedClusterStat().getPlatformObjectsSelfStats().getObjectsCount());
    Assert.assertEquals(0, statistics.getComponentStats().get(2).getOwnedClusterStat().getPlatformObjectsSelfStats()
      .getTotalSizeInBytes());
    Assert.assertEquals(0, statistics.getComponentStats().get(2).getOwnedClusterStat().getPlatformRetainedObjectsStats().getObjectsCount());
    Assert.assertEquals(0, statistics.getComponentStats().get(2).getOwnedClusterStat().getPlatformRetainedObjectsStats()
      .getTotalSizeInBytes());

    Assert.assertEquals(1, statistics.getCategoryComponentStats().get(1).getOwnedClusterStat().getPlatformObjectsSelfStats()
      .getObjectsCount());
    Assert.assertEquals(32, statistics.getCategoryComponentStats().get(1).getOwnedClusterStat().getPlatformObjectsSelfStats()
      .getTotalSizeInBytes());
    Assert.assertEquals(javaVersion() >= 21 ? 7 : 5, statistics.getCategoryComponentStats().get(1).getOwnedClusterStat().getPlatformRetainedObjectsStats()
      .getObjectsCount());
    Assert.assertEquals(javaVersion() >= 21 ? 176 : 120, statistics.getCategoryComponentStats().get(1).getOwnedClusterStat().getPlatformRetainedObjectsStats()
      .getTotalSizeInBytes());
  }

  private static class A {
    private B myB = new B();
    private final Integer myInt = 0;

    private A(B b) {
      myB = b;
    }

    private A() { }
  }

  private static class B implements Disposable {

    @Override
    public void dispose() {

    }
  }

  private static class C implements Disposable {
    private static final Integer STATIC_INT = 0;

    @Override
    public void dispose() {

    }
  }

  private static class D implements Disposable {
    private final B[] myArray;

    private D(B... b) {
      myArray = b;
    }

    @Override
    public void dispose() {

    }
  }

  private static class ReferenceToObjectArray {
    private final Object[] myArray;

    private ReferenceToObjectArray(Object... b) {
      myArray = b;
    }

    private static class ReferenceToObjectArray2 extends ReferenceToObjectArray {
      private ReferenceToObjectArray2(Object... b) {
        super(b);
      }
    }
  }

  private static class E {
    private final TruncatingStringBuilder[] myArray;
    private final Integer myInt = 1;

    private E(TruncatingStringBuilder... b) {
      myArray = b;
    }
  }

  private static class F {
    private final WeakReference<String> myWeakString = new WeakReference<>("test");
  }

  private static class G {
    private static final Integer ourInt = 0;
    private H myH;
  }

  private static class H {
    private final int myInt = 1;
  }

  private static class ReferenceToB {
    private final B myB;

    private ReferenceToB(B b) {
      myB = b;
    }
  }

  private static class ReferenceToPlatformObject {
    @NotNull
    private final WeakList myWeakList;

    private ReferenceToPlatformObject(@NotNull final WeakList l) {
      myWeakList = l;
    }
  }
}
