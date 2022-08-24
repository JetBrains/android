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
    componentsSet.addComponentWithPackagesAndClassNames("A", Collections.emptyList(),
                                                        "com.android.tools.idea.diagnostics.heap.HeapAnalyzerTest$A");
    componentsSet.addComponentWithPackagesAndClassNames("B", Collections.emptyList(),
                                                        "com.android.tools.idea.diagnostics.heap.HeapAnalyzerTest$B");

    HeapSnapshotStatistics stats = new HeapSnapshotStatistics(componentsSet);
    Assert.assertEquals(HeapSnapshotTraverse.ErrorCode.OK, new HeapSnapshotTraverse().walkObjects(MAX_DEPTH, List.of(new A()), stats));

    List<HeapSnapshotStatistics.HeapObjectsStatistics> componentStats = stats.getComponentStats();
    Assert.assertEquals(2, componentStats.size());
    Assert.assertEquals("A", componentStats.get(0).getComponentName());
    // instance of A, boxed int
    Assert.assertEquals(2, componentStats.get(0).getOwnedObjectsNumber());
    Assert.assertEquals(40, componentStats.get(0).getOwnedTotalSizeOfObjects());
    Assert.assertEquals("B", componentStats.get(1).getComponentName());
    // instance of B
    Assert.assertEquals(1, componentStats.get(1).getOwnedObjectsNumber());
    Assert.assertEquals(16, componentStats.get(1).getOwnedTotalSizeOfObjects());
  }

  @Test
  public void testNonComponentObjectLowOwnershipPriority() {
    ComponentsSet componentsSet = new ComponentsSet();
    componentsSet.addComponentWithPackagesAndClassNames("A", Collections.emptyList(),
                                                        "com.android.tools.idea.diagnostics.heap.HeapAnalyzerTest$A");

    HeapSnapshotStatistics stats = new HeapSnapshotStatistics(componentsSet);
    Assert.assertEquals(HeapSnapshotTraverse.ErrorCode.OK,
                        new HeapSnapshotTraverse().walkObjects(MAX_DEPTH, List.of(new A(), C.class), stats));

    List<HeapSnapshotStatistics.HeapObjectsStatistics> componentStats = stats.getComponentStats();
    Assert.assertEquals(1, componentStats.size());
    Assert.assertEquals("A", componentStats.get(0).getComponentName());
    // A, B, Integer
    Assert.assertEquals(3, componentStats.get(0).getOwnedObjectsNumber());
    Assert.assertEquals(56, componentStats.get(0).getOwnedTotalSizeOfObjects());
  }

  private static class TestTraverseChildProcessor extends HeapTraverseChildProcessor {
    private static final Set<Class<?>> ALLOWED_CLASSES = Set.of(A.class, B.class, C.class, D.class, F.class, Class.class, Integer.class);

    @Override
    void processChildObjects(@Nullable final Object obj,
                             @NotNull final BiConsumer<Object, HeapTraverseNode.RefWeight> consumer,
                             @NotNull final FieldCache fieldCache) {
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
  public void testStaticFieldHigherOwnershipPriorityThanInstanceField() {
    ComponentsSet componentsSet = new ComponentsSet();
    componentsSet.addComponentWithPackagesAndClassNames("A", Collections.emptyList(),
                                                        "com.android.tools.idea.diagnostics.heap.HeapAnalyzerTest$A");
    componentsSet.addComponentWithPackagesAndClassNames("C", Collections.emptyList(),
                                                        "com.android.tools.idea.diagnostics.heap.HeapAnalyzerTest$C");

    // to initialize C class.
    C c = new C();
    HeapSnapshotStatistics stats = new HeapSnapshotStatistics(componentsSet);
    Assert.assertEquals(HeapSnapshotTraverse.ErrorCode.OK,
                        new HeapSnapshotTraverse(new TestTraverseChildProcessor()).walkObjects(MAX_DEPTH,
                                                                                               List.of(new A(new B()), c.getClass()),
                                                                                               stats));

    List<HeapSnapshotStatistics.HeapObjectsStatistics> componentStats = stats.getComponentStats();
    Assert.assertEquals(2, componentStats.size());
    Assert.assertEquals("A", componentStats.get(0).getComponentName());
    // A instance and B instance
    Assert.assertEquals(2, componentStats.get(0).getOwnedObjectsNumber());
    Assert.assertEquals(40, componentStats.get(0).getOwnedTotalSizeOfObjects());

    Assert.assertEquals("C", componentStats.get(1).getComponentName());
    // C class object and boxed 0 static field
    Assert.assertEquals(2, componentStats.get(1).getOwnedObjectsNumber());
  }

  @Test
  public void testArrayElementsHigherOwnershipPriorityThanNonComponent() {
    ComponentsSet componentsSet = new ComponentsSet();
    componentsSet.addComponentWithPackagesAndClassNames("D", Collections.emptyList(),
                                                        "com.android.tools.idea.diagnostics.heap.HeapAnalyzerTest$D");

    B b = new B();
    HeapSnapshotStatistics stats = new HeapSnapshotStatistics(componentsSet);
    Assert.assertEquals(HeapSnapshotTraverse.ErrorCode.OK,
                        new HeapSnapshotTraverse().walkObjects(MAX_DEPTH, List.of(new D(b), new A(b)), stats));

    List<HeapSnapshotStatistics.HeapObjectsStatistics> componentStats = stats.getComponentStats();
    Assert.assertEquals(1, componentStats.size());
    Assert.assertEquals("D", componentStats.get(0).getComponentName());
    Assert.assertEquals(3, componentStats.get(0).getOwnedObjectsNumber());
    Assert.assertEquals(68, componentStats.get(0).getOwnedTotalSizeOfObjects());
  }

  @Test
  public void testInstanceFieldHigherOwnershipPriorityThanArrayElements() {
    ComponentsSet componentsSet = new ComponentsSet();
    componentsSet.addComponentWithPackagesAndClassNames("A", Collections.emptyList(),
                                                        "com.android.tools.idea.diagnostics.heap.HeapAnalyzerTest$A");
    componentsSet.addComponentWithPackagesAndClassNames("D", Collections.emptyList(),
                                                        "com.android.tools.idea.diagnostics.heap.HeapAnalyzerTest$D");

    B b = new B();
    HeapSnapshotStatistics stats = new HeapSnapshotStatistics(componentsSet);
    Assert.assertEquals(HeapSnapshotTraverse.ErrorCode.OK,
                        new HeapSnapshotTraverse().walkObjects(MAX_DEPTH, List.of(new D(b), new A(b)), stats));

    List<HeapSnapshotStatistics.HeapObjectsStatistics> componentStats = stats.getComponentStats();
    Assert.assertEquals(2, componentStats.size());
    Assert.assertEquals("A", componentStats.get(0).getComponentName());
    Assert.assertEquals(3, componentStats.get(0).getOwnedObjectsNumber());
    Assert.assertEquals(56, componentStats.get(0).getOwnedTotalSizeOfObjects());
    Assert.assertEquals("D", componentStats.get(1).getComponentName());
    Assert.assertEquals(2, componentStats.get(1).getOwnedObjectsNumber());
    Assert.assertEquals(52, componentStats.get(1).getOwnedTotalSizeOfObjects());
  }

  @Test
  public void testWeakSoftReferencesIgnored() {
    ComponentsSet componentsSet = new ComponentsSet();
    componentsSet.addComponentWithPackagesAndClassNames("F", Collections.emptyList(),
                                                        "com.android.tools.idea.diagnostics.heap.HeapAnalyzerTest$F");

    HeapSnapshotStatistics stats = new HeapSnapshotStatistics(componentsSet);
    Assert.assertEquals(HeapSnapshotTraverse.ErrorCode.OK, new HeapSnapshotTraverse().walkObjects(MAX_DEPTH, List.of(new F()), stats));

    List<HeapSnapshotStatistics.HeapObjectsStatistics> componentStats = stats.getComponentStats();
    Assert.assertEquals(1, componentStats.size());
    Assert.assertEquals("F", componentStats.get(0).getComponentName());
    // F, WeakReference, ReferenceQueue$Null and ReferenceQueue$Lock
    Assert.assertEquals(4, componentStats.get(0).getOwnedObjectsNumber());
    Assert.assertEquals(92, componentStats.get(0).getOwnedTotalSizeOfObjects());
  }

  @Test
  public void testComponentRetainedSize() {
    ComponentsSet componentsSet = new ComponentsSet();
    componentsSet.addComponentWithPackagesAndClassNames("B", Collections.emptyList(),
                                                        "com.android.tools.idea.diagnostics.heap.HeapAnalyzerTest$B");
    componentsSet.addComponentWithPackagesAndClassNames("D", Collections.emptyList(),
                                                        "com.android.tools.idea.diagnostics.heap.HeapAnalyzerTest$D");

    HeapSnapshotStatistics stats = new HeapSnapshotStatistics(componentsSet);
    Assert.assertEquals(HeapSnapshotTraverse.ErrorCode.OK,
                        new HeapSnapshotTraverse().walkObjects(MAX_DEPTH, List.of(new D(new B())), stats));

    List<HeapSnapshotStatistics.HeapObjectsStatistics> componentStats = stats.getComponentStats();
    Assert.assertEquals(2, componentStats.size());
    Assert.assertEquals("B", componentStats.get(0).getComponentName());
    Assert.assertEquals("D", componentStats.get(1).getComponentName());

    Assert.assertEquals(2, componentStats.get(1).getOwnedObjectsNumber());
    Assert.assertEquals(52, componentStats.get(1).getOwnedTotalSizeOfObjects());
    Assert.assertEquals(3, componentStats.get(1).getRetainedObjectsNumber());
    Assert.assertEquals(68, componentStats.get(1).getRetainedTotalSizeOfObjects());
  }

  @org.junit.Ignore("b/243081723")
  @Test
  public void testDisposerTreeReferences() {
    ComponentsSet componentsSet = new ComponentsSet();
    componentsSet.addComponentWithPackagesAndClassNames("A", Collections.emptyList(),
                                                        "com.android.tools.idea.diagnostics.heap.HeapAnalyzerTest$A");
    componentsSet.addComponentWithPackagesAndClassNames("C", Collections.emptyList(),
                                                        "com.android.tools.idea.diagnostics.heap.HeapAnalyzerTest$C");
    B b = new B();
    C c = new C();
    Disposer.register(c, b);

    HeapSnapshotStatistics stats = new HeapSnapshotStatistics(componentsSet);
    Assert.assertEquals(HeapSnapshotTraverse.ErrorCode.OK,
                        new HeapSnapshotTraverse().walkObjects(MAX_DEPTH, List.of(new A(b), c), stats));
    Disposer.dispose(c);

    List<HeapSnapshotStatistics.HeapObjectsStatistics> componentStats = stats.getComponentStats();
    Assert.assertEquals(2, componentStats.size());
    Assert.assertEquals("A", componentStats.get(0).getComponentName());
    Assert.assertEquals("C", componentStats.get(1).getComponentName());

    // a and it's static int
    Assert.assertEquals(2, componentStats.get(0).getOwnedObjectsNumber());
    Assert.assertEquals(40, componentStats.get(0).getOwnedTotalSizeOfObjects());
    // c and b
    Assert.assertEquals(2, componentStats.get(1).getOwnedObjectsNumber());
    Assert.assertEquals(32, componentStats.get(1).getOwnedTotalSizeOfObjects());
  }

  @Test
  public void testTraverseReturnLowMemoryError() {
    ComponentsSet componentsSet = new ComponentsSet();
    HeapSnapshotStatistics stats = new HeapSnapshotStatistics(componentsSet);
    HeapSnapshotTraverse traverse = new HeapSnapshotTraverse();

    LowMemoryWatcher.onLowMemorySignalReceived(false);

    Assert.assertEquals(HeapSnapshotTraverse.ErrorCode.LOW_MEMORY,
                        traverse.walkObjects(MAX_DEPTH, List.of(new A()), stats));
  }

  private static class A {
    private B myB = new B();
    private final Integer myInt = 0;

    private A(B b) {
      myB = b;
    }

    private A() {}
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

    private D(B b) {
      myArray = new B[]{b};
    }
  }

  private static class F {
    private final WeakReference<String> myWeakString = new WeakReference<>("test");
  }
}
