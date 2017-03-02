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

import com.android.tools.profiler.proto.MemoryProfiler;
import com.android.tools.profiler.proto.MemoryProfiler.AllocationEvent;
import com.android.tools.profiler.proto.MemoryProfiler.AllocationEventsResponse;
import com.android.tools.profiler.proto.MemoryProfiler.AllocationStack;
import com.android.tools.profiler.proto.MemoryProfiler.AllocationsInfo;
import com.android.tools.profilers.FakeGrpcChannel;
import com.android.tools.profilers.ProfilersTestData;
import com.android.tools.profilers.RelativeTimeConverter;
import com.android.tools.profilers.memory.FakeMemoryService;
import com.google.protobuf3jarjar.ByteString;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

public class AllocationsCaptureObjectTest {

  @NotNull private final FakeMemoryService myService = new FakeMemoryService();

  @NotNull private final RelativeTimeConverter myRelativeTimeConverter = new RelativeTimeConverter(0);

  @Rule
  public FakeGrpcChannel myGrpcChannel = new FakeGrpcChannel("AllocationsCaptureObjectTest", myService);

  /**
   * This is a high-level test that validates the generation of allocation tracking MemoryObjects hierarchy based on fake allocation events.
   * We want to ensure not only the AllocationsCaptureObject holds the correct HeapObject(s) representing the allocated classes, but
   * children MemoryObject nodes (e.g. ClassObject, InstanceObject) hold correct information as well.
   */
  @Test
  public void testAllocationsObjectGeneration() throws Exception {
    int appId = -1;
    int infoId = 1;
    long startTimeNs = TimeUnit.MILLISECONDS.toNanos(3);
    long endTimeNs = TimeUnit.MILLISECONDS.toNanos(8);

    AllocationsInfo testInfo = AllocationsInfo.newBuilder().setStartTime(startTimeNs).setEndTime(endTimeNs).build();
    AllocationsCaptureObject capture =
      new AllocationsCaptureObject(myGrpcChannel.getClient().getMemoryClient(), appId, ProfilersTestData.SESSION_DATA, testInfo,
                                   myRelativeTimeConverter);

    // Verify values associated with the AllocationsInfo object.
    assertEquals(startTimeNs, capture.getStartTimeNs());
    assertEquals(endTimeNs, capture.getEndTimeNs());
    assertEquals("Allocations from 3ms to 8ms", capture.getName());

    final CountDownLatch loadLatch = new CountDownLatch(1);
    final CountDownLatch doneLatch = new CountDownLatch(1);
    myService.setExplicitAllocationEvents(MemoryProfiler.AllocationEventsResponse.Status.NOT_READY, Collections.emptyList());
    new Thread(() -> {
      loadLatch.countDown();
      capture.load();
      doneLatch.countDown();
    }).start();

    loadLatch.await();

    ByteString stackId1 = ByteString.copyFrom(new byte[]{'a'});
    ByteString stackId2 = ByteString.copyFrom(new byte[]{'b'});
    myService.addExplicitAllocationClass(0, "test.klass0");
    myService.addExplicitAllocationClass(1, "test.klass1");
    myService.addExplicitAllocationStack("test.klass0", "testMethod0", 3, stackId1);
    myService.addExplicitAllocationStack("test.klass1", "testMethod1", 7, stackId2);
    AllocationEvent event1 =
      AllocationEvent.newBuilder().setTrackingStartTime(startTimeNs).setAllocatedClassId(0).setAllocationStackId(stackId2).build();
    AllocationEvent event2 =
      AllocationEvent.newBuilder().setTrackingStartTime(startTimeNs).setAllocatedClassId(1).setAllocationStackId(stackId1).build();
    myService.setExplicitAllocationEvents(AllocationEventsResponse.Status.SUCCESS, Arrays.asList(event1, event2));
    doneLatch.await();

    assertTrue(capture.isDoneLoading());
    assertFalse(capture.isError());

    // Allocation Tracking only shows "default" heap
    List<HeapObject> heaps = capture.getHeaps();
    assertEquals(1, heaps.size());

    HeapObject defaultHeap = heaps.get(0);
    verifyHeap(defaultHeap, "default", 2);

    ClassObject klass0 = defaultHeap.getClasses().get(0);
    ClassObject klass1 = defaultHeap.getClasses().get(1);
    verifyClass(klass0, "test.klass0", 1);
    verifyClass(klass1, "test.klass1", 1);

    InstanceObject instance0 = klass0.getInstances().get(0);
    InstanceObject instance1 = klass1.getInstances().get(0);
    // Note: allocation records contains no depth/fields/references information
    verifyInstance(instance0, "test.klass0", Integer.MAX_VALUE, 0, 0, 1);
    verifyInstance(instance1, "test.klass1", Integer.MAX_VALUE, 0, 0, 1);

    AllocationStack.StackFrame frame0 = instance0.getCallStack().getStackFramesList().get(0);
    AllocationStack.StackFrame frame1 = instance1.getCallStack().getStackFramesList().get(0);
    verifyStackFrame(frame0, "test.klass1", "testMethod1", 7);
    verifyStackFrame(frame1, "test.klass0", "testMethod0", 3);
  }

  @Test
  public void testLoadingFailure() throws Exception {
    long startTimeNs = TimeUnit.MILLISECONDS.toNanos(3);
    long endTimeNs = TimeUnit.MILLISECONDS.toNanos(8);

    AllocationsInfo testInfo1 = AllocationsInfo.newBuilder().setStartTime(startTimeNs).setEndTime(endTimeNs).build();
    AllocationsCaptureObject capture =
      new AllocationsCaptureObject(myGrpcChannel.getClient().getMemoryClient(), -1, ProfilersTestData.SESSION_DATA, testInfo1,
                                   myRelativeTimeConverter);

    assertFalse(capture.isDoneLoading());
    assertFalse(capture.isError());

    myService.setExplicitAllocationEvents(AllocationEventsResponse.Status.FAILURE_UNKNOWN, Collections.emptyList());
    capture.load();

    assertTrue(capture.isDoneLoading());
    assertTrue(capture.isError());
    try {
      // If loading failed, we are not allowed to query HeapObjects
      capture.getHeaps();
    }
    catch (AssertionError ignored) {
      return;
    }

    // Should not reach here.
    assert false;
  }

  @Test
  public void testEquality() throws Exception {
    long startTimeNs = TimeUnit.MILLISECONDS.toNanos(3);
    long endTimeNs = TimeUnit.MILLISECONDS.toNanos(8);
    long endTimeNs2 = TimeUnit.MILLISECONDS.toNanos(13);
    MemoryProfiler.AllocationsInfo testInfo1 =
      MemoryProfiler.AllocationsInfo.newBuilder().setStartTime(startTimeNs).setEndTime(endTimeNs).build();
    MemoryProfiler.AllocationsInfo testInfo2 =
      MemoryProfiler.AllocationsInfo.newBuilder().setStartTime(endTimeNs).setEndTime(endTimeNs2).build();
    AllocationsCaptureObject capture =
      new AllocationsCaptureObject(myGrpcChannel.getClient().getMemoryClient(), -1, ProfilersTestData.SESSION_DATA, testInfo1,
                                   myRelativeTimeConverter);

    // Test inequality with different object type
    assertNotEquals(mock(CaptureObject.class), capture);

    AllocationsCaptureObject captureWithDifferentAppId =
      new AllocationsCaptureObject(myGrpcChannel.getClient().getMemoryClient(), -2, ProfilersTestData.SESSION_DATA, testInfo1,
                                   myRelativeTimeConverter);
    // Test inequality with different app id
    assertNotEquals(captureWithDifferentAppId, capture);

    AllocationsCaptureObject captureWithDifferentTimes =
      new AllocationsCaptureObject(myGrpcChannel.getClient().getMemoryClient(), -1, ProfilersTestData.SESSION_DATA, testInfo2,
                                   myRelativeTimeConverter);
    // Test inequality with different start/end times
    assertNotEquals(captureWithDifferentTimes, capture);

    AllocationsCaptureObject captureWithDifferentStatus =
      new AllocationsCaptureObject(myGrpcChannel.getClient().getMemoryClient(), -1, ProfilersTestData.SESSION_DATA, testInfo1,
                                   myRelativeTimeConverter);
    // Test equality as long as appId + times are equal
    assertEquals(captureWithDifferentStatus, capture);

    myService.setExplicitAllocationsInfo(MemoryProfiler.AllocationsInfo.Status.FAILURE_UNKNOWN, startTimeNs, endTimeNs, true);
    captureWithDifferentStatus.load();
    assertEquals(captureWithDifferentStatus, capture);
  }

  private void verifyHeap(HeapObject heap, String name, int klassSize) {
    assertEquals(name, heap.getName());
    assertEquals(klassSize, heap.getClasses().size());
  }

  private void verifyClass(ClassObject klass, String name, int instanceSize) {
    assertEquals(name, klass.getName());
    assertEquals(instanceSize, klass.getInstances().size());
  }

  private void verifyInstance(InstanceObject instance, String name, int depth, int fieldSize, int referenceSize, int frameCount) {
    assertEquals(name, instance.getName());
    assertEquals(depth, instance.getDepth());
    assertEquals(fieldSize, instance.getFields().size());
    assertEquals(referenceSize, instance.getReferences().size());
    assertEquals(frameCount, instance.getCallStack().getStackFramesCount());
  }

  private void verifyStackFrame(MemoryProfiler.AllocationStack.StackFrame frame, String klass, String method, int line) {
    assertEquals(klass, frame.getClassName());
    assertEquals(method, frame.getMethodName());
    assertEquals(line, frame.getLineNumber());
  }
}