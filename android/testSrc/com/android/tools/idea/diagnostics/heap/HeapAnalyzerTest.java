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
import com.android.tools.adtui.workbench.PropertiesComponentMock;
import com.android.tools.analytics.crash.CrashReport;
import com.android.tools.idea.diagnostics.TruncatingStringBuilder;
import com.android.tools.idea.diagnostics.crash.StudioCrashReporter;
import com.android.tools.idea.flags.StudioFlags;
import com.google.common.collect.Lists;
import com.google.common.io.ByteStreams;
import com.google.wireless.android.sdk.stats.MemoryUsageReportEvent;
import com.intellij.ide.PowerSaveMode;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.LowMemoryWatcher;
import com.intellij.testFramework.PlatformLiteFixture;
import com.intellij.util.containers.WeakList;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.assertj.core.util.Sets;
import org.hamcrest.core.SubstringMatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Assert;
import org.junit.AssumptionViolatedException;
import org.junit.Before;
import org.junit.Test;

public class HeapAnalyzerTest extends PlatformLiteFixture {

  private static final int MAX_DEPTH = 100;

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
      Assert.assertEquals(0, HeapSnapshotTraverse.getObjectTag(object));
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
                        new HeapSnapshotTraverse(stats).walkObjects(MAX_DEPTH, List.of(a)));

    List<HeapSnapshotStatistics.ComponentClusterObjectsStatistics> componentStats = stats.getComponentStats();
    Assert.assertEquals(3, componentStats.size());
    Assert.assertEquals(UNCATEGORIZED_CATEGORY_LABEL,
                        componentStats.get(0).getComponent().getComponentCategory().getComponentCategoryLabel());
    Assert.assertEquals("A", componentStats.get(1).getComponent().getComponentLabel());
    // instance of A, boxed int
    Assert.assertEquals(2, componentStats.get(1).getOwnedClusterStat().getObjectsStatistics().getObjectsCount());

    Assert.assertEquals(40, componentStats.get(1).getOwnedClusterStat().getObjectsStatistics().getTotalSizeInBytes());
    Assert.assertEquals("B", componentStats.get(2).getComponent().getComponentLabel());
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
                        new HeapSnapshotTraverse(stats).walkObjects(MAX_DEPTH, List.of(a, C.class)));

    List<HeapSnapshotStatistics.ComponentClusterObjectsStatistics> componentStats = stats.getComponentStats();
    Assert.assertEquals(2, componentStats.size());
    Assert.assertEquals(UNCATEGORIZED_CATEGORY_LABEL,
                        componentStats.get(0).getComponent().getComponentCategory().getComponentCategoryLabel());
    Assert.assertEquals("A", componentStats.get(1).getComponent().getComponentLabel());
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
    Assert.assertEquals(StatusCode.NO_ERROR,
                        new HeapSnapshotTraverse(new TestTraverseChildProcessor(stats), stats).walkObjects(MAX_DEPTH,
                                                                                                           List.of(new A(new B()),
                                                                                                                   c.getClass())));

    List<HeapSnapshotStatistics.ComponentClusterObjectsStatistics> componentStats = stats.getComponentStats();
    Assert.assertEquals(3, componentStats.size());
    Assert.assertEquals(UNCATEGORIZED_CATEGORY_LABEL,
                        componentStats.get(0).getComponent().getComponentCategory().getComponentCategoryLabel());
    Assert.assertEquals("A", componentStats.get(1).getComponent().getComponentLabel());
    // A instance and B instance
    Assert.assertEquals(2, componentStats.get(1).getOwnedClusterStat().getObjectsStatistics().getObjectsCount());
    Assert.assertEquals(40, componentStats.get(1).getOwnedClusterStat().getObjectsStatistics().getTotalSizeInBytes());

    Assert.assertEquals("C", componentStats.get(2).getComponent().getComponentLabel());
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
                        new HeapSnapshotTraverse(stats).walkObjects(MAX_DEPTH, List.of(d, a)));

    List<HeapSnapshotStatistics.ComponentClusterObjectsStatistics> componentStats = stats.getComponentStats();
    Assert.assertEquals(2, componentStats.size());
    Assert.assertEquals(UNCATEGORIZED_CATEGORY_LABEL,
                        componentStats.get(0).getComponent().getComponentCategory().getComponentCategoryLabel());

    Assert.assertEquals(2, componentStats.get(0).getOwnedClusterStat().getObjectsStatistics().getObjectsCount());
    Assert.assertEquals(40, componentStats.get(0).getOwnedClusterStat().getObjectsStatistics().getTotalSizeInBytes());
    Assert.assertEquals("D", componentStats.get(1).getComponent().getComponentLabel());
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
                        new HeapSnapshotTraverse(stats).walkObjects(MAX_DEPTH, List.of(d, a)));

    List<HeapSnapshotStatistics.ComponentClusterObjectsStatistics> componentStats = stats.getComponentStats();
    Assert.assertEquals(3, componentStats.size());
    Assert.assertEquals(UNCATEGORIZED_CATEGORY_LABEL,
                        componentStats.get(0).getComponent().getComponentCategory().getComponentCategoryLabel());

    Assert.assertEquals("A", componentStats.get(1).getComponent().getComponentLabel());
    Assert.assertEquals(3, componentStats.get(1).getOwnedClusterStat().getObjectsStatistics().getObjectsCount());
    Assert.assertEquals(56, componentStats.get(1).getOwnedClusterStat().getObjectsStatistics().getTotalSizeInBytes());
    Assert.assertEquals("D", componentStats.get(2).getComponent().getComponentLabel());
    Assert.assertEquals(2, componentStats.get(2).getOwnedClusterStat().getObjectsStatistics().getObjectsCount());
    Assert.assertEquals(40, componentStats.get(2).getOwnedClusterStat().getObjectsStatistics().getTotalSizeInBytes());
    checkObjectsUntagged(new Object[]{a, b, d, a.myB, a.myInt, d.myArray});
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
                        new HeapSnapshotTraverse(stats).walkObjects(MAX_DEPTH, List.of(f)));

    List<HeapSnapshotStatistics.ComponentClusterObjectsStatistics> componentStats = stats.getComponentStats();
    Assert.assertEquals(2, componentStats.size());
    Assert.assertEquals(UNCATEGORIZED_CATEGORY_LABEL,
                        componentStats.get(0).getComponent().getComponentCategory().getComponentCategoryLabel());

    Assert.assertEquals("F", componentStats.get(1).getComponent().getComponentLabel());
    // F, WeakReference, ReferenceQueue$Null and ReferenceQueue$Lock
    Assert.assertEquals(4, componentStats.get(1).getOwnedClusterStat().getObjectsStatistics().getObjectsCount());
    Assert.assertEquals(96, componentStats.get(1).getOwnedClusterStat().getObjectsStatistics().getTotalSizeInBytes());
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
                        new HeapSnapshotTraverse(stats).walkObjects(MAX_DEPTH, List.of(new D(new B()))));

    List<HeapSnapshotStatistics.ComponentClusterObjectsStatistics> componentStats = stats.getComponentStats();
    Assert.assertEquals(3, componentStats.size());
    Assert.assertEquals(UNCATEGORIZED_CATEGORY_LABEL,
                        componentStats.get(0).getComponent().getComponentCategory().getComponentCategoryLabel());

    Assert.assertEquals("B", componentStats.get(1).getComponent().getComponentLabel());
    Assert.assertEquals("D", componentStats.get(2).getComponent().getComponentLabel());

    Assert.assertEquals(2, componentStats.get(2).getOwnedClusterStat().getObjectsStatistics().getObjectsCount());
    Assert.assertEquals(40, componentStats.get(2).getOwnedClusterStat().getObjectsStatistics().getTotalSizeInBytes());
    Assert.assertEquals(3, componentStats.get(2).getRetainedClusterStat().getObjectsStatistics().getObjectsCount());
    Assert.assertEquals(56, componentStats.get(2).getRetainedClusterStat().getObjectsStatistics().getTotalSizeInBytes());
  }

  @Test
  public void testTraverseMetadata() {
    ComponentsSet componentsSet = new ComponentsSet();
    HeapSnapshotStatistics stats = new HeapSnapshotStatistics(componentsSet);
    HeapSnapshotTraverse traverse = new HeapSnapshotTraverse(stats);

    Assert.assertEquals(StatusCode.NO_ERROR,
                        traverse.walkObjects(MAX_DEPTH, List.of(new A())));
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
    HeapSnapshotTraverse traverse = new HeapSnapshotTraverse(stats);

    LowMemoryWatcher.onLowMemorySignalReceived(false);

    Assert.assertEquals(StatusCode.LOW_MEMORY,
                        traverse.walkObjects(MAX_DEPTH, List.of(new A())));
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
                        new HeapSnapshotTraverse(stats).walkObjects(MAX_DEPTH, List.of(new A(b), new D(b))));

    List<HeapSnapshotStatistics.ComponentClusterObjectsStatistics> componentStats = stats.getComponentStats();
    Assert.assertEquals(2, componentStats.size());
    Assert.assertEquals(UNCATEGORIZED_CATEGORY_LABEL,
                        componentStats.get(0).getComponent().getComponentCategory().getComponentCategoryLabel());

    // HeapAnalyzerTest$A and underlying Integer
    Assert.assertEquals(2, componentStats.get(0).getOwnedClusterStat().getObjectsStatistics().getObjectsCount());
    Assert.assertEquals("D", componentStats.get(1).getComponent().getComponentLabel());
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
    Assert.assertEquals(StatusCode.NO_ERROR, new HeapSnapshotTraverse(stats).walkObjects(MAX_DEPTH, List.of(new A())));

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
                             @NotNull final BiConsumer<Object, HeapTraverseNode.RefWeight> consumer,
                             @NotNull final FieldCache fieldCache) throws HeapSnapshotTraverseException {
      super.processChildObjects(obj, (Object value, HeapTraverseNode.RefWeight ownershipWeight) -> {
        if (value == null) {
          return;
        }
        if (!ALLOWED_CLASSES.contains(value.getClass())) {
          return;
        }
        consumer.accept(value, ownershipWeight);
      }, fieldCache);
    }
  }

  @Test
  public void testStudioStatsProtoCreation() {
    StudioFlags.DESIGN_TOOLS_POWER_SAVE_MODE_SUPPORT.override(true);
    PowerSaveMode.setEnabled(true);
    ComponentsSet componentsSet = new ComponentsSet();

    ComponentsSet.ComponentCategory defaultCategory = componentsSet.registerCategory("diagnostics");
    componentsSet.addComponentWithPackagesAndClassNames("diagnostics_main",
                                                        defaultCategory,
                                                        List.of(
                                                          "com.android.tools.idea.diagnostics"),
                                                        Collections.emptyList());
    Assert.assertEquals(StatusCode.NO_ERROR,
                        new HeapSnapshotTraverse(
                          new HeapSnapshotStatistics(componentsSet)).walkObjects(MAX_DEPTH, List.of(
                          new E(new TruncatingStringBuilder(0, "")))));

    HeapSnapshotStatistics stats = new HeapSnapshotStatistics(componentsSet);
    Assert.assertEquals(StatusCode.NO_ERROR,
                        new HeapSnapshotTraverse(stats).walkObjects(MAX_DEPTH, List.of(
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
    if (Arrays.stream(HeapSnapshotTraverse.getClasses())
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
    Assert.assertEquals(StatusCode.NO_ERROR, new HeapSnapshotTraverse(processor, statistics).walkObjects(MAX_DEPTH, List.of(gClass)));
    for (Class<?> aClass : HeapSnapshotTraverse.getClasses()) {
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
                        new HeapSnapshotTraverse(statistics).walkObjects(MAX_DEPTH, List.of(new D(new B(), new B(), new B()))));
    Assert.assertNotNull(statistics.getExtendedReportStatistics());
    ExtendedReportStatistics.ClassNameHistogram histogram0 = statistics.getExtendedReportStatistics().categoryHistograms.get(0);
    ExtendedReportStatistics.ClassNameHistogram histogram1 = statistics.getExtendedReportStatistics().categoryHistograms.get(1);
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
  public void testExtendedReportCollection() throws IOException {
    ComponentsSet componentsSet = new ComponentsSet();

    ComponentsSet.ComponentCategory defaultCategory = componentsSet.registerCategory("diagnostics");
    componentsSet.addComponentWithPackagesAndClassNames("A",
                                                        1,
                                                        defaultCategory,
                                                        Collections.emptyList(),
                                                        List.of("com.android.tools.idea.diagnostics.heap.HeapAnalyzerTest$A"));
    componentsSet.addComponentWithPackagesAndClassNames("B",
                                                        2,
                                                        defaultCategory,
                                                        Collections.emptyList(),
                                                        List.of("com.android.tools.idea.diagnostics.heap.HeapAnalyzerTest$B"));
    HeapSnapshotStatistics statistics = new HeapSnapshotStatistics(new HeapTraverseConfig(componentsSet,
      /*collectHistograms=*/false, /*collectDisposerTreeInfo=*/false));
    FakeCrushReporter crushReporter = new FakeCrushReporter();
    getApplication().registerService(StudioCrashReporter.class, crushReporter);

    WeakList<Object> roots = new WeakList<>();
    A a = new A(new B());
    roots.add(a);
    Assert.assertEquals(StatusCode.NO_ERROR,
                        HeapSnapshotTraverse.collectMemoryReport(statistics, () -> roots));
    assertSize(1, crushReporter.crashReports);
    CrashReport report = crushReporter.crashReports.get(0);
    MultipartEntityBuilder builder = MultipartEntityBuilder.create();
    report.serialize(builder);
    String serializedExtendedReport = new String(ByteStreams.toByteArray(builder.build().getContent()), Charset.defaultCharset());
    assertRequestContainsField(serializedExtendedReport, "Clusters that exceeded the threshold", "A,B");
    assertRequestContainsField(serializedExtendedReport, "Total used memory", "56/3 objects");
    assertRequestContainsField(serializedExtendedReport, "Category android:uncategorized", "Owned: 0/0 objects\n" +
                                                                                           "      Histogram:\n" +
                                                                                           "      Studio objects histogram:");
    assertRequestContainsField(serializedExtendedReport, "Category diagnostics", "Owned: 56/3 objects\n" +
                                                                                 "      Histogram:\n" +
                                                                                 "        24/1 objects: com.android.tools.idea.diagnostics.heap.HeapAnalyzerTest$A\n" +
                                                                                 "        16/1 objects: com.android.tools.idea.diagnostics.heap.HeapAnalyzerTest$B\n" +
                                                                                 "        16/1 objects: java.lang.Integer\n" +
                                                                                 "      Studio objects histogram:\n" +
                                                                                 "        24/1 objects: com.android.tools.idea.diagnostics.heap.HeapAnalyzerTest$A\n" +
                                                                                 "        16/1 objects: com.android.tools.idea.diagnostics.heap.HeapAnalyzerTest$B");
    assertRequestContainsField(serializedExtendedReport, "Component uncategorized_main", "Owned: 0/0 objects\n" +
                                                                                         "      Histogram:\n" +
                                                                                         "      Studio objects histogram:");
    assertRequestContainsField(serializedExtendedReport, "Component A", "Owned: 40/2 objects\n" +
                                                                        "      Histogram:\n" +
                                                                        "        24/1 objects: com.android.tools.idea.diagnostics.heap.HeapAnalyzerTest$A\n" +
                                                                        "        16/1 objects: java.lang.Integer\n" +
                                                                        "      Studio objects histogram:\n" +
                                                                        "        24/1 objects: com.android.tools.idea.diagnostics.heap.HeapAnalyzerTest$A");
    assertRequestContainsField(serializedExtendedReport, "Component B", "Owned: 16/1 objects\n" +
                                                                        "      Histogram:\n" +
                                                                        "        16/1 objects: com.android.tools.idea.diagnostics.heap.HeapAnalyzerTest$B\n" +
                                                                        "      Studio objects histogram:\n" +
                                                                        "        16/1 objects: com.android.tools.idea.diagnostics.heap.HeapAnalyzerTest$B");
    assertRequestContainsFieldWithPattern(serializedExtendedReport, "Disposer tree information", "Disposer tree size: \\d+\n" +
                                                                                      "Total number of disposed but strong referenced objects: 0");
  }

  private static void assertRequestContainsField(final String requestBody, final String name, final String value) {
    assertRequestContainsFieldWithPattern(requestBody, name, Pattern.quote(value));
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

    private Pattern myPattern;

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
                             @NotNull final BiConsumer<Object, HeapTraverseNode.RefWeight> consumer,
                             @NotNull final FieldCache fieldCache) throws HeapSnapshotTraverseException {
      if (obj instanceof Class) {
        visitedClassesNames.add(((Class<?>)obj).getName());
      }

      super.processChildObjects(obj, consumer, fieldCache);
    }
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

  private static class D {
    private final B[] myArray;

    private D(B... b) {
      myArray = b;
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
    private static Integer ourInt = 0;
    private H myH;
  }

  private static class H {
    private int myInt = 1;
  }
}
