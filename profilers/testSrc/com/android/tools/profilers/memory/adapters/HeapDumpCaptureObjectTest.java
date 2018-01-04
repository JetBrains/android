/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.profilers.memory.adapters;

import com.android.tools.perflib.heap.SnapshotBuilder;
import com.android.tools.profiler.proto.MemoryProfiler;
import com.android.tools.profilers.FakeGrpcChannel;
import com.android.tools.profilers.FakeIdeProfilerServices;
import com.android.tools.profilers.ProfilerTimeline;
import com.android.tools.profilers.ProfilersTestData;
import com.android.tools.profilers.memory.FakeMemoryService;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static com.android.tools.profilers.memory.MemoryProfilerTestUtils.findChildClassSetWithName;
import static org.junit.Assert.*;

public class HeapDumpCaptureObjectTest {

  @NotNull private final FakeMemoryService myService = new FakeMemoryService();

  @NotNull private final FakeIdeProfilerServices myIdeProfilerServices = new FakeIdeProfilerServices();

  @Rule
  public FakeGrpcChannel myGrpcChannel = new FakeGrpcChannel("HeapDumpCaptureObjectTest", myService);

  /**
   * This is a high-level test that validates the generation of the hprof MemoryObject hierarchy based on a Snapshot buffer.
   * We want to ensure not only the HeapDumpCaptureObject holds the correct HeapSet(s) representing the Snapshot, but
   * children MemoryObject nodes (e.g. ClassSet, InstanceObject) hold correct information as well.
   */
  @Test
  public void testHeapDumpObjectsGeneration() throws Exception {
    long startTimeNs = 3;
    long endTimeNs = 8;
    MemoryProfiler.HeapDumpInfo dumpInfo =
      MemoryProfiler.HeapDumpInfo.newBuilder().setStartTime(startTimeNs).setEndTime(endTimeNs).build();
    HeapDumpCaptureObject capture =
      new HeapDumpCaptureObject(myGrpcChannel.getClient().getMemoryClient(), ProfilersTestData.SESSION_DATA,
                                dumpInfo, null, myIdeProfilerServices.getFeatureTracker());

    // Verify values associated with the HeapDumpInfo object.
    assertEquals(startTimeNs, capture.getStartTimeNs());
    assertEquals(endTimeNs, capture.getEndTimeNs());
    assertFalse(capture.isDoneLoading());
    assertFalse(capture.isError());

    final CountDownLatch loadLatch = new CountDownLatch(1);
    final CountDownLatch doneLatch = new CountDownLatch(1);
    myService.setExplicitDumpDataStatus(MemoryProfiler.DumpDataResponse.Status.NOT_READY);
    new Thread(() -> {
      loadLatch.countDown();
      capture.load(null, null);
      doneLatch.countDown();
    }).start();

    loadLatch.await();
    // Load in a simple Snapshot and verify the MemoryObject hierarchy:
    // - 1 holds reference to 2
    // - single root object in default heap
    SnapshotBuilder snapshotBuilder = new SnapshotBuilder(2, 0, 0)
      .addReferences(1, 2)
      .addRoot(1);
    byte[] buffer = snapshotBuilder.getByteBuffer();
    myService.setExplicitSnapshotBuffer(buffer);
    myService.setExplicitDumpDataStatus(MemoryProfiler.DumpDataResponse.Status.SUCCESS);
    doneLatch.await();

    assertTrue(capture.isDoneLoading());
    assertFalse(capture.isError());

    Collection<HeapSet> heaps = capture.getHeapSets();
    assertEquals(2, heaps.size());

    // "default" heap only contains roots, no ClassObjects
    HeapSet defaultHeap = heaps.stream().filter(heap -> "default".equals(heap.getName())).findFirst().orElse(null);
    assertNotNull(defaultHeap);
    assertEquals(0, defaultHeap.getInstancesCount());

    // "testHeap" contains the reference, softreference classes, plus a unique class for each instance we created (2).
    HeapSet testHeap = heaps.stream().filter(heap -> "testHeap".equals(heap.getName())).findFirst().orElse(null);
    assertEquals(testHeap.getName(), "testHeap");
    assertEquals(6, testHeap.getInstancesCount());

    ClassifierSet.Classifier classClassifier = ClassSet.createDefaultClassifier();
    classClassifier.partition(
      Collections.emptyList(), testHeap.getInstancesStream().collect(HashSet::new, HashSet::add, HashSet::addAll));
    List<ClassifierSet> classSets = classClassifier.getFilteredClassifierSets();
    assertEquals(3, classSets.size());
    assertTrue(classSets.stream().allMatch(classifier -> classifier instanceof ClassSet));
    assertTrue(classSets.stream().anyMatch(classifier -> "java.lang.Class".equals(((ClassSet)classifier).getClassEntry().getClassName())));
    assertTrue(classSets.stream().anyMatch(classifier -> "Class0".equals(((ClassSet)classifier).getClassEntry().getClassName())));
    assertTrue(classSets.stream().anyMatch(classifier -> "Class1".equals(((ClassSet)classifier).getClassEntry().getClassName())));

    InstanceObject instance0 = findChildClassSetWithName(classClassifier, "Class0").getInstancesStream().findFirst().orElse(null);
    InstanceObject instance1 = findChildClassSetWithName(classClassifier, "Class1").getInstancesStream().findFirst().orElse(null);
    verifyInstance(instance0, "Class0@1 (0x1)", 0, 1, 0);
    verifyInstance(instance1, "Class1@2 (0x2)", 1, 0, 1);

    FieldObject field0 = instance0.getFields().get(0);
    assertEquals(field0.getAsInstance(), instance1);
    ReferenceObject reference1 = instance1.getReferences().get(0);
    assertEquals(reference1.getReferenceInstance(), instance0);
  }

  @Test
  public void testLoadingFailure() throws Exception {
    MemoryProfiler.HeapDumpInfo dumpInfo = MemoryProfiler.HeapDumpInfo.newBuilder().setStartTime(3).setEndTime(8).build();
    HeapDumpCaptureObject capture =
      new HeapDumpCaptureObject(myGrpcChannel.getClient().getMemoryClient(), ProfilersTestData.SESSION_DATA, dumpInfo, null,
                                myIdeProfilerServices.getFeatureTracker());

    assertFalse(capture.isDoneLoading());
    assertFalse(capture.isError());

    myService.setExplicitDumpDataStatus(MemoryProfiler.DumpDataResponse.Status.FAILURE_UNKNOWN);
    capture.load(null, null);

    assertTrue(capture.isDoneLoading());
    assertTrue(capture.isError());
    assertEquals(0, capture.getHeapSets().size());
  }

  @Test
  public void testSaveToFile() throws Exception {
    long startTimeNs = 3;
    long endTimeNs = 8;
    MemoryProfiler.HeapDumpInfo dumpInfo =
      MemoryProfiler.HeapDumpInfo.newBuilder().setStartTime(startTimeNs).setEndTime(endTimeNs).build();
    HeapDumpCaptureObject capture =
      new HeapDumpCaptureObject(myGrpcChannel.getClient().getMemoryClient(), ProfilersTestData.SESSION_DATA,
                                dumpInfo, null, myIdeProfilerServices.getFeatureTracker());

    final CountDownLatch loadLatch = new CountDownLatch(1);
    final CountDownLatch doneLatch = new CountDownLatch(1);
    myService.setExplicitDumpDataStatus(MemoryProfiler.DumpDataResponse.Status.NOT_READY);
    new Thread(() -> {
      loadLatch.countDown();
      capture.load(null, null);
      doneLatch.countDown();
    }).start();

    loadLatch.await();
    // Load in a simple Snapshot and verify the MemoryObject hierarchy:
    // - 1 holds reference to 2
    // - single root object in default heap
    SnapshotBuilder snapshotBuilder = new SnapshotBuilder(2, 0, 0)
      .addReferences(1, 2)
      .addRoot(1);
    byte[] buffer = snapshotBuilder.getByteBuffer();
    myService.setExplicitSnapshotBuffer(buffer);
    myService.setExplicitDumpDataStatus(MemoryProfiler.DumpDataResponse.Status.SUCCESS);
    doneLatch.await();

    assertTrue(capture.isDoneLoading());
    assertFalse(capture.isError());

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    capture.saveToFile(baos);
    assertArrayEquals(buffer, baos.toByteArray());
  }

  private static void verifyInstance(@NotNull InstanceObject instance,
                                     @NotNull String valueText,
                                     int depth,
                                     int fieldsCount,
                                     int referencesCount) {
    assertEquals(valueText, instance.getValueText());
    assertEquals(depth, instance.getDepth());
    assertEquals(fieldsCount, instance.getFields().size());
    assertEquals(referencesCount, instance.getReferences().size());
  }
}