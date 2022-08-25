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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.LowMemoryWatcher;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;

public class HeapAnalyzerTest {

  private static final int MAX_DEPTH = 100;

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
    Assert.assertEquals(StatusCode.NO_ERROR,
                        new HeapSnapshotTraverse(stats).walkObjects(MAX_DEPTH, List.of(new A())));

    List<HeapSnapshotStatistics.ComponentClusterObjectsStatistics> componentStats = stats.getComponentStats();
    Assert.assertEquals(2, componentStats.size());
    Assert.assertEquals("A", componentStats.get(0).getComponent().getComponentLabel());
    // instance of A, boxed int
    Assert.assertEquals(2, componentStats.get(0).getOwnedClusterStat().getObjectsNumber());
    Assert.assertEquals(40, componentStats.get(0).getOwnedClusterStat().getTotalSizeOfObjects());
    Assert.assertEquals("B", componentStats.get(1).getComponent().getComponentLabel());
    // instance of B
    Assert.assertEquals(1, componentStats.get(1).getOwnedClusterStat().getObjectsNumber());
    Assert.assertEquals(16, componentStats.get(1).getOwnedClusterStat().getTotalSizeOfObjects());
  }

  @Test
  public void testNonComponentObjectLowOwnershipPriority() {
    ComponentsSet componentsSet = new ComponentsSet();
    ComponentsSet.ComponentCategory defaultCategory = componentsSet.registerCategory("DEFAULT");
    componentsSet.addComponentWithPackagesAndClassNames("A", defaultCategory,
                                                        Collections.emptyList(),
                                                        List.of("com.android.tools.idea.diagnostics.heap.HeapAnalyzerTest$A"));

    HeapSnapshotStatistics stats = new HeapSnapshotStatistics(componentsSet);
    Assert.assertEquals(StatusCode.NO_ERROR,
                        new HeapSnapshotTraverse(stats).walkObjects(MAX_DEPTH, List.of(new A(), C.class)));

    List<HeapSnapshotStatistics.ComponentClusterObjectsStatistics> componentStats = stats.getComponentStats();
    Assert.assertEquals(1, componentStats.size());
    Assert.assertEquals("A", componentStats.get(0).getComponent().getComponentLabel());
    // A, B, Integer
    Assert.assertEquals(3, componentStats.get(0).getOwnedClusterStat().getObjectsNumber());
    Assert.assertEquals(56, componentStats.get(0).getOwnedClusterStat().getTotalSizeOfObjects());
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
    Assert.assertEquals(2, componentStats.size());
    Assert.assertEquals("A", componentStats.get(0).getComponent().getComponentLabel());
    // A instance and B instance
    Assert.assertEquals(2, componentStats.get(0).getOwnedClusterStat().getObjectsNumber());
    Assert.assertEquals(40, componentStats.get(0).getOwnedClusterStat().getTotalSizeOfObjects());

    Assert.assertEquals("C", componentStats.get(1).getComponent().getComponentLabel());
    // C class object and boxed 0 static field
    Assert.assertEquals(2, componentStats.get(1).getOwnedClusterStat().getObjectsNumber());
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
    Assert.assertEquals(StatusCode.NO_ERROR,
                        new HeapSnapshotTraverse(stats).walkObjects(MAX_DEPTH, List.of(new D(b), new A(b))));

    List<HeapSnapshotStatistics.ComponentClusterObjectsStatistics> componentStats = stats.getComponentStats();
    Assert.assertEquals(1, componentStats.size());
    Assert.assertEquals("D", componentStats.get(0).getComponent().getComponentLabel());
    Assert.assertEquals(3, componentStats.get(0).getOwnedClusterStat().getObjectsNumber());
    Assert.assertEquals(56, componentStats.get(0).getOwnedClusterStat().getTotalSizeOfObjects());
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
    HeapSnapshotStatistics stats = new HeapSnapshotStatistics(componentsSet);
    Assert.assertEquals(StatusCode.NO_ERROR,
                        new HeapSnapshotTraverse(stats).walkObjects(MAX_DEPTH, List.of(new D(b), new A(b))));

    List<HeapSnapshotStatistics.ComponentClusterObjectsStatistics> componentStats = stats.getComponentStats();
    Assert.assertEquals(2, componentStats.size());
    Assert.assertEquals("A", componentStats.get(0).getComponent().getComponentLabel());
    Assert.assertEquals(3, componentStats.get(0).getOwnedClusterStat().getObjectsNumber());
    Assert.assertEquals(56, componentStats.get(0).getOwnedClusterStat().getTotalSizeOfObjects());
    Assert.assertEquals("D", componentStats.get(1).getComponent().getComponentLabel());
    Assert.assertEquals(2, componentStats.get(1).getOwnedClusterStat().getObjectsNumber());
    Assert.assertEquals(40, componentStats.get(1).getOwnedClusterStat().getTotalSizeOfObjects());
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
    Assert.assertEquals(StatusCode.NO_ERROR,
                        new HeapSnapshotTraverse(stats).walkObjects(MAX_DEPTH, List.of(new F())));

    List<HeapSnapshotStatistics.ComponentClusterObjectsStatistics> componentStats = stats.getComponentStats();
    Assert.assertEquals(1, componentStats.size());
    Assert.assertEquals("F", componentStats.get(0).getComponent().getComponentLabel());
    // F, WeakReference, ReferenceQueue$Null and ReferenceQueue$Lock
    Assert.assertEquals(4, componentStats.get(0).getOwnedClusterStat().getObjectsNumber());
    Assert.assertEquals(96, componentStats.get(0).getOwnedClusterStat().getTotalSizeOfObjects());
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
    Assert.assertEquals(2, componentStats.size());
    Assert.assertEquals("B", componentStats.get(0).getComponent().getComponentLabel());
    Assert.assertEquals("D", componentStats.get(1).getComponent().getComponentLabel());

    Assert.assertEquals(2, componentStats.get(1).getOwnedClusterStat().getObjectsNumber());
    Assert.assertEquals(40, componentStats.get(1).getOwnedClusterStat().getTotalSizeOfObjects());
    Assert.assertEquals(3, componentStats.get(1).getRetainedClusterStat().getObjectsNumber());
    Assert.assertEquals(56, componentStats.get(1).getRetainedClusterStat().getTotalSizeOfObjects());
  }

  @org.junit.Ignore("b/243081723")
  @Test
  public void testDisposerTreeReferences() {
    C c = new C();
    try {
      ComponentsSet componentsSet = new ComponentsSet();
      ComponentsSet.ComponentCategory defaultCategory = componentsSet.registerCategory("DEFAULT");
      componentsSet.addComponentWithPackagesAndClassNames("A", defaultCategory,
                                                          Collections.emptyList(),
                                                          List.of("com.android.tools.idea.diagnostics.heap.HeapAnalyzerTest$A"));
      componentsSet.addComponentWithPackagesAndClassNames("C", defaultCategory,
                                                          Collections.emptyList(),
                                                          List.of("com.android.tools.idea.diagnostics.heap.HeapAnalyzerTest$C"));
      B b = new B();

      Disposer.register(c, b);

      HeapSnapshotStatistics stats = new HeapSnapshotStatistics(componentsSet);
      Assert.assertEquals(StatusCode.NO_ERROR,
                          new HeapSnapshotTraverse(stats).walkObjects(MAX_DEPTH, List.of(new A(b), c)));

      List<HeapSnapshotStatistics.ComponentClusterObjectsStatistics> componentStats = stats.getComponentStats();
      Assert.assertEquals(2, componentStats.size());
      Assert.assertEquals("A", componentStats.get(0).getComponent().getComponentLabel());
      Assert.assertEquals("C", componentStats.get(1).getComponent().getComponentLabel());

      // a and it's static int
      Assert.assertEquals(2, componentStats.get(0).getOwnedClusterStat().getObjectsNumber());
      Assert.assertEquals(40, componentStats.get(0).getOwnedClusterStat().getTotalSizeOfObjects());
      // c and b
      Assert.assertEquals(2, componentStats.get(1).getOwnedClusterStat().getObjectsNumber());
      Assert.assertEquals(32, componentStats.get(1).getOwnedClusterStat().getTotalSizeOfObjects());
    }
    finally {
      Disposer.dispose(c);
    }
  }

  @Test
  public void testTraverseMetadata() {
    ComponentsSet componentsSet = new ComponentsSet();
    HeapSnapshotStatistics stats = new HeapSnapshotStatistics(componentsSet);
    HeapSnapshotTraverse traverse = new HeapSnapshotTraverse(stats);

    Assert.assertEquals(StatusCode.NO_ERROR,
                        traverse.walkObjects(MAX_DEPTH, List.of(new A())));
    Assert.assertEquals(stats.myMaxFieldsCacheSize, 7);
    Assert.assertEquals(stats.myMaxObjectsQueueSize, 2);
    Assert.assertEquals(stats.myEnumeratedGarbageCollectedObjects, 0);
    Assert.assertEquals(stats.myUnsuccessfulFieldAccessCounter, 0);
    Assert.assertEquals(stats.myHeapObjectCount, 3);
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
    Assert.assertEquals(3, categoryComponentStats.getOwnedClusterStat().getObjectsNumber());
    Assert.assertEquals(56, categoryComponentStats.getOwnedClusterStat().getTotalSizeOfObjects());
    Assert.assertEquals(3, categoryComponentStats.getRetainedClusterStat().getObjectsNumber());
    Assert.assertEquals(56, categoryComponentStats.getRetainedClusterStat().getTotalSizeOfObjects());
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

  private static class F {
    private final WeakReference<String> myWeakString = new WeakReference<>("test");
  }
}
