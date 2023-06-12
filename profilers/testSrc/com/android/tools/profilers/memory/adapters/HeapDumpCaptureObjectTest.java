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

import static com.android.testutils.TestUtils.resolveWorkspacePath;
import static com.android.tools.profilers.memory.MemoryProfilerTestUtils.findChildClassSetWithName;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.adtui.model.FakeTimer;
import com.android.tools.idea.protobuf.ByteString;
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel;
import com.android.tools.idea.transport.faketransport.FakeTransportService;
import com.android.tools.perflib.heap.SnapshotBuilder;
import com.android.tools.profiler.proto.Memory.HeapDumpInfo;
import com.android.tools.profilers.FakeIdeProfilerServices;
import com.android.tools.profilers.ProfilerClient;
import com.android.tools.profilers.ProfilersTestData;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.memory.FakeCaptureObjectLoader;
import com.android.tools.profilers.memory.MainMemoryProfilerStage;
import com.android.tools.profilers.memory.adapters.classifiers.ClassSet;
import com.android.tools.profilers.memory.adapters.classifiers.Classifier;
import com.android.tools.profilers.memory.adapters.classifiers.ClassifierSet;
import com.android.tools.profilers.memory.adapters.classifiers.HeapSet;
import com.android.tools.profilers.memory.adapters.instancefilters.ActivityFragmentLeakInstanceFilter;
import com.android.tools.profilers.memory.adapters.instancefilters.CaptureObjectInstanceFilter;
import com.google.common.truth.Truth;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class HeapDumpCaptureObjectTest {

  @NotNull private final FakeTimer myTimer = new FakeTimer();
  @NotNull private final FakeTransportService myTransportService = new FakeTransportService(myTimer);

  @NotNull private final FakeIdeProfilerServices myIdeProfilerServices = new FakeIdeProfilerServices();

  @Rule
  public FakeGrpcChannel myGrpcChannel = new FakeGrpcChannel("HeapDumpCaptureObjectTest", myTransportService);

  private MainMemoryProfilerStage myStage;

  @Before
  public void setUp() {
    myStage = new MainMemoryProfilerStage(new StudioProfilers(new ProfilerClient(myGrpcChannel.getChannel()), myIdeProfilerServices, myTimer),
                                          new FakeCaptureObjectLoader());
  }

  /**
   * This is a high-level test that validates the generation of the hprof MemoryObject hierarchy based on a Snapshot buffer.
   * We want to ensure not only the HeapDumpCaptureObject holds the correct HeapSet(s) representing the Snapshot, but
   * children MemoryObject nodes (e.g. ClassSet, InstanceObject) hold correct information as well.
   */
  @Test
  public void testHeapDumpObjectsGeneration() throws Exception {
    long startTimeNs = 3;
    long endTimeNs = 8;
    HeapDumpInfo dumpInfo =
      HeapDumpInfo.newBuilder().setStartTime(startTimeNs).setEndTime(endTimeNs).build();
    HeapDumpCaptureObject capture =
      new HeapDumpCaptureObject(new ProfilerClient(myGrpcChannel.getChannel()), ProfilersTestData.SESSION_DATA,
                                dumpInfo, null, myIdeProfilerServices.getFeatureTracker(),
                                myStage.getStudioProfilers().getIdeServices());

    // Verify values associated with the HeapDumpInfo object.
    assertEquals(startTimeNs, capture.getStartTimeNs());
    assertEquals(endTimeNs, capture.getEndTimeNs());
    assertFalse(capture.isDoneLoading());
    assertFalse(capture.isError());

    // Load in a simple Snapshot and verify the MemoryObject hierarchy:
    // - 1 holds reference to 2
    // - single root object in default heap
    SnapshotBuilder snapshotBuilder = new SnapshotBuilder(2, 0, 0)
      .addReferences(1, 2)
      .addRoot(1);
    byte[] buffer = snapshotBuilder.getByteBuffer();
    myTransportService.addFile(Long.toString(startTimeNs), ByteString.copyFrom(buffer));
    capture.load(null, null);
    assertTrue(capture.isDoneLoading());
    assertFalse(capture.isError());

    Collection<HeapSet> heaps = capture.getHeapSets();
    assertEquals(2, heaps.size()); // default heap should not show up if it doesn't contain anything

    // "default" heap only contains roots, no ClassObjects
    HeapSet defaultHeap = heaps.stream().filter(heap -> "default".equals(heap.getName())).findFirst().orElse(null);
    assertNull(defaultHeap);

    // "testHeap" contains the reference, softreference classes, plus a unique class for each instance we created (2).
    HeapSet testHeap = heaps.stream().filter(heap -> "testHeap".equals(heap.getName())).findFirst().orElse(null);
    assertEquals(testHeap.getName(), "testHeap");
    assertEquals(6, testHeap.getInstancesCount());

    Classifier classClassifier = ClassSet.createDefaultClassifier();
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
  public void testDefaultHeapShowsUpWhenItIsNonEmpty() throws Exception {
    long startTimeNs = 3;
    long endTimeNs = 8;
    HeapDumpInfo dumpInfo =
      HeapDumpInfo.newBuilder().setStartTime(startTimeNs).setEndTime(endTimeNs).build();
    HeapDumpCaptureObject capture =
      new HeapDumpCaptureObject(new ProfilerClient(myGrpcChannel.getChannel()), ProfilersTestData.SESSION_DATA, dumpInfo,
                                null,
                                myIdeProfilerServices.getFeatureTracker(),
                                myStage.getStudioProfilers().getIdeServices());

    // Verify values associated with the HeapDumpInfo object.
    assertEquals(startTimeNs, capture.getStartTimeNs());
    assertEquals(endTimeNs, capture.getEndTimeNs());
    assertFalse(capture.isDoneLoading());
    assertFalse(capture.isError());

    // Load in a simple Snapshot and verify the MemoryObject hierarchy:
    // - 1 holds reference to 2
    // - single root object in default heap
    SnapshotBuilder snapshotBuilder = new SnapshotBuilder(2, 0, 0)
      .addReferences(1, 2).setDefaultHeapInstanceCount(1)
      .addRoot(1);
    byte[] buffer = snapshotBuilder.getByteBuffer();
    myTransportService.addFile(Long.toString(startTimeNs), ByteString.copyFrom(buffer));
    capture.load(null, null);

    assertTrue(capture.isDoneLoading());
    assertFalse(capture.isError());

    Collection<HeapSet> heaps = capture.getHeapSets();
    assertEquals(3, heaps.size());

    HeapSet defaultHeap = heaps.stream().filter(heap -> "default".equals(heap.getName())).findFirst().orElse(null);
    assertNotNull(defaultHeap);
  }

  @Test
  public void testLoadingFailure() throws Exception {
    HeapDumpInfo dumpInfo = HeapDumpInfo.newBuilder().setStartTime(3).setEndTime(8).build();
    HeapDumpCaptureObject capture =
      new HeapDumpCaptureObject(new ProfilerClient(myGrpcChannel.getChannel()), ProfilersTestData.SESSION_DATA, dumpInfo,
                                null,
                                myIdeProfilerServices.getFeatureTracker(),
                                myStage.getStudioProfilers().getIdeServices());

    assertFalse(capture.isDoneLoading());
    assertFalse(capture.isError());
    capture.load(null, null);

    assertTrue(capture.isDoneLoading());
    assertTrue(capture.isError());
    assertEquals(0, capture.getHeapSets().size());
  }

  @Test
  public void testHeapDumpActivityLeak() throws Exception {
    HeapDumpInfo dumpInfo = HeapDumpInfo.newBuilder().setStartTime(0).setEndTime(1).build();
    HeapDumpCaptureObject capture =
      new HeapDumpCaptureObject(new ProfilerClient(myGrpcChannel.getChannel()), ProfilersTestData.SESSION_DATA,
                                dumpInfo, null, myIdeProfilerServices.getFeatureTracker(),
                                myStage.getStudioProfilers().getIdeServices());

    Path hprof = resolveWorkspacePath("tools/adt/idea/profilers/testData/hprofs/displayingbitmaps_leakedActivity.hprof");
    FileChannel fileChannel = FileChannel.open(hprof, StandardOpenOption.READ);
    MappedByteBuffer buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileChannel.size());
    buffer.load();

    myTransportService.addFile(Long.toString(0), ByteString.copyFrom(buffer));
    capture.load(null, null);
    assertTrue(capture.isDoneLoading());
    assertFalse(capture.isError());

    long allInstanceCount = capture.getInstances().count();
    Truth.assertThat(allInstanceCount).isGreaterThan(7L);
    Set<CaptureObjectInstanceFilter> filters = capture.getSupportedInstanceFilters();
    Optional<CaptureObjectInstanceFilter> leakFilter =
      filters.stream().filter(filter -> filter instanceof ActivityFragmentLeakInstanceFilter).findAny();
    Truth.assertThat(leakFilter.isPresent()).isTrue();

    CountDownLatch addFilterLatch = new CountDownLatch(1);
    capture.addInstanceFilter(leakFilter.get(), Runnable::run);
    // Wait for the filter to finish running on the off-main-thread executor.
    capture.getInstanceFilterExecutor().execute(addFilterLatch::countDown);
    addFilterLatch.await();
    List<InstanceObject> filtredInstances = capture.getInstances().collect(Collectors.toList());
    Truth.assertThat(filtredInstances).hasSize(7);
    Truth.assertThat(filtredInstances.stream().filter(
      instance -> instance.getClassEntry().getSimpleClassName().equals("ImageDetailActivity")).count()).isEqualTo(1);
    Truth.assertThat(filtredInstances.stream().filter(
      instance -> instance.getClassEntry().getSimpleClassName().equals("ImageCache$RetainFragment")).count()).isEqualTo(1);
    Truth.assertThat(filtredInstances.stream().filter(
      instance -> instance.getClassEntry().getSimpleClassName().equals("ImageDetailFragment")).count()).isEqualTo(5);

    CountDownLatch removeFilterLatch = new CountDownLatch(1);
    capture.removeInstanceFilter(leakFilter.get(), Runnable::run);
    // Wait for the filter to finish running on the off-main-thread executor.
    capture.getInstanceFilterExecutor().execute(removeFilterLatch::countDown);
    removeFilterLatch.await();
    Truth.assertThat(capture.getInstances().count()).isEqualTo(allInstanceCount);
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