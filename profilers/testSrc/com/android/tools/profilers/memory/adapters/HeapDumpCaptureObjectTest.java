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
import com.android.tools.profilers.ProfilersTestData;
import com.android.tools.profilers.RelativeTimeConverter;
import com.android.tools.profilers.memory.FakeMemoryService;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

public class HeapDumpCaptureObjectTest {

  @NotNull private final FakeMemoryService myService = new FakeMemoryService();

  @NotNull private final FakeIdeProfilerServices myIdeProfilerServices = new FakeIdeProfilerServices();

  @NotNull private final RelativeTimeConverter myRelativeTimeConverter = new RelativeTimeConverter(0);

  @Rule
  public FakeGrpcChannel myGrpcChannel = new FakeGrpcChannel("HeapDumpCaptureObjectTest", myService);

  /**
   * This is a high-level test that validates the generation of the hprof MemoryObject hierarchy based on a Snapshot buffer.
   * We want to ensure not only the HeapDumpCaptureObject holds the correct HeapObject(s) representing the Snapshot, but
   * children MemoryObject nodes (e.g. ClassObject, InstanceObject) hold correct information as well.
   */
  @Test
  public void testHeapDumpObjectsGeneration() throws Exception {
    int appId = -1;
    long startTimeNs = 3;
    long endTimeNs = 8;
    MemoryProfiler.HeapDumpInfo dumpInfo =
      MemoryProfiler.HeapDumpInfo.newBuilder().setStartTime(startTimeNs).setEndTime(endTimeNs).build();
    HeapDumpCaptureObject capture =
      new HeapDumpCaptureObject(myGrpcChannel.getClient().getMemoryClient(), ProfilersTestData.SESSION_DATA, appId,
                                dumpInfo, null, myRelativeTimeConverter, myIdeProfilerServices.getFeatureTracker());

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
      capture.load();
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

    List<HeapObject> heaps = capture.getHeaps();
    assertEquals(2, heaps.size());

    // "default" heap only contains roots, no ClassObjects
    HeapObject defaultHeap = heaps.get(0);
    verifyHeap(defaultHeap, "default", 0);

    // "testHeap" contains the reference, softreference classes, plus a unique class for each instance we created (2).
    HeapObject testHeap = heaps.get(1);
    verifyHeap(testHeap, "testHeap", 4);
    List<ClassObject> testHeapKlasses = testHeap.getClasses();
    verifyClass(testHeapKlasses.get(0), "java.lang.ref.Reference", 0);
    verifyClass(testHeapKlasses.get(1), "SoftAndHardReference", 0);
    verifyClass(testHeapKlasses.get(2), "Class0", 1);
    verifyClass(testHeapKlasses.get(3), "Class1", 1);

    InstanceObject instance0 = testHeapKlasses.get(2).getInstances().get(0);
    InstanceObject instance1 = testHeapKlasses.get(3).getInstances().get(0);
    verifyInstance(instance0, "Class0@1 (0x1)", 0, 1, 0);
    verifyInstance(instance1, "Class1@2 (0x2)", 1, 0, 1);

    FieldObject field0 = instance0.getFields().get(0);
    verifyField(field0, "field0", "Class1@2 (0x2)");
    // Ensure that the various info in the field match those of instance1
    verifyInstance(field0, String.format(FieldObject.FIELD_DISPLAY_FORMAT, "field0", "Class1@2 (0x2)"), 1, 0, 1);

    ReferenceObject reference1 = instance1.getReferences().get(0);
    verifyReference(reference1, "Class0@1 (0x1)", Arrays.asList("field0"));
  }

  @Test
  public void testLoadingFailure() throws Exception {
    MemoryProfiler.HeapDumpInfo dumpInfo = MemoryProfiler.HeapDumpInfo.newBuilder().setStartTime(3).setEndTime(8).build();
    HeapDumpCaptureObject capture =
      new HeapDumpCaptureObject(myGrpcChannel.getClient().getMemoryClient(), ProfilersTestData.SESSION_DATA, -1, dumpInfo, null,
                                myRelativeTimeConverter, myIdeProfilerServices.getFeatureTracker());

    assertFalse(capture.isDoneLoading());
    assertFalse(capture.isError());

    myService.setExplicitDumpDataStatus(MemoryProfiler.DumpDataResponse.Status.FAILURE_UNKNOWN);
    capture.load();

    assertTrue(capture.isDoneLoading());
    assertTrue(capture.isError());
    assertEquals(0, capture.getHeaps().size());
  }

  @Test
  public void testEquality() throws Exception {
    MemoryProfiler.HeapDumpInfo dumpInfo1 = MemoryProfiler.HeapDumpInfo.newBuilder().setStartTime(3).setEndTime(8).build();
    MemoryProfiler.HeapDumpInfo dumpInfo2 = MemoryProfiler.HeapDumpInfo.newBuilder().setStartTime(9).setEndTime(13).build();

    HeapDumpCaptureObject capture =
      new HeapDumpCaptureObject(myGrpcChannel.getClient().getMemoryClient(), ProfilersTestData.SESSION_DATA, -1, dumpInfo1, null,
                                myRelativeTimeConverter, myIdeProfilerServices.getFeatureTracker());
    // Test inequality with different object type
    assertNotEquals(mock(CaptureObject.class), capture);

    HeapDumpCaptureObject captureWithDifferentAppId =
      new HeapDumpCaptureObject(myGrpcChannel.getClient().getMemoryClient(), ProfilersTestData.SESSION_DATA, -2, dumpInfo1, null,
                                myRelativeTimeConverter, myIdeProfilerServices.getFeatureTracker());
    // Test inequality with different app id
    assertNotEquals(captureWithDifferentAppId, capture);

    HeapDumpCaptureObject captureWithDifferentDump =
      new HeapDumpCaptureObject(myGrpcChannel.getClient().getMemoryClient(), ProfilersTestData.SESSION_DATA, -1, dumpInfo2, null,
                                myRelativeTimeConverter, myIdeProfilerServices.getFeatureTracker());
    // Test inequality with different HeapDumpInfo
    assertNotEquals(captureWithDifferentDump, capture);

    HeapDumpCaptureObject captureWithDifferentLoadStatus =
      new HeapDumpCaptureObject(myGrpcChannel.getClient().getMemoryClient(), ProfilersTestData.SESSION_DATA, -1, dumpInfo1, null,
                                myRelativeTimeConverter, myIdeProfilerServices.getFeatureTracker());
    // Test equality with same HeapDumpInfo and status
    assertEquals(captureWithDifferentLoadStatus, capture);

    myService.setExplicitDumpDataStatus(MemoryProfiler.DumpDataResponse.Status.FAILURE_UNKNOWN);
    captureWithDifferentLoadStatus.load();
    // Test inequality with different status
    assertNotEquals(captureWithDifferentLoadStatus, capture);

    // Ensure equality again after statuses have re-aligned.
    capture.load();
    assertEquals(captureWithDifferentLoadStatus, capture);
  }

  @Test
  public void testSaveToFile() throws Exception {
    int appId = -1;
    long startTimeNs = 3;
    long endTimeNs = 8;
    MemoryProfiler.HeapDumpInfo dumpInfo =
      MemoryProfiler.HeapDumpInfo.newBuilder().setStartTime(startTimeNs).setEndTime(endTimeNs).build();
    HeapDumpCaptureObject capture =
      new HeapDumpCaptureObject(myGrpcChannel.getClient().getMemoryClient(), ProfilersTestData.SESSION_DATA, appId,
                                dumpInfo, null, myRelativeTimeConverter, myIdeProfilerServices.getFeatureTracker());

    final CountDownLatch loadLatch = new CountDownLatch(1);
    final CountDownLatch doneLatch = new CountDownLatch(1);
    myService.setExplicitDumpDataStatus(MemoryProfiler.DumpDataResponse.Status.NOT_READY);
    new Thread(() -> {
      loadLatch.countDown();
      capture.load();
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

  private void verifyHeap(HeapObject heap, String name, int klassSize) {
    assertEquals(name, heap.getName());
    assertEquals(klassSize, heap.getClasses().size());
  }

  private void verifyClass(ClassObject klass, String name, int instanceSize) {
    assertEquals(name, klass.getName());
    assertEquals(instanceSize, klass.getInstances().size());
  }

  private void verifyInstance(InstanceObject instance, String name, int depth, int fieldSize, int referenceSize) {
    assertEquals(name, instance.getName());
    assertEquals(depth, instance.getDepth());
    assertEquals(fieldSize, instance.getFields().size());
    assertEquals(referenceSize, instance.getReferences().size());
  }

  private void verifyField(FieldObject field, String fieldName, String valueName) {
    assertEquals(String.format(FieldObject.FIELD_DISPLAY_FORMAT, fieldName, valueName), field.getName());
  }

  private void verifyReference(ReferenceObject reference, String referrerName, List<String> referrerFieldNames) {
    assertEquals(referrerName, reference.getName());
    assertEquals(referrerFieldNames, reference.getReferenceFieldNames());
  }
}