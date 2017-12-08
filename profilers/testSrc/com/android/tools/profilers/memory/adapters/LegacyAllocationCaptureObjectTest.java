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
import com.android.tools.profiler.proto.MemoryProfiler.AllocationStack;
import com.android.tools.profiler.proto.MemoryProfiler.AllocationsInfo;
import com.android.tools.profiler.proto.MemoryProfiler.LegacyAllocationEvent;
import com.android.tools.profiler.proto.MemoryProfiler.LegacyAllocationEventsResponse;
import com.android.tools.profilers.FakeGrpcChannel;
import com.android.tools.profilers.FakeIdeProfilerServices;
import com.android.tools.profilers.ProfilersTestData;
import com.android.tools.profilers.RelativeTimeConverter;
import com.android.tools.profilers.memory.FakeMemoryService;
import com.android.tools.profilers.memory.MemoryProfilerTestUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class LegacyAllocationCaptureObjectTest {

  @NotNull private final FakeMemoryService myService = new FakeMemoryService();

  @NotNull private final FakeIdeProfilerServices myIdeProfilerServices = new FakeIdeProfilerServices();

  @NotNull private final RelativeTimeConverter myRelativeTimeConverter = new RelativeTimeConverter(0);

  @Rule
  public FakeGrpcChannel myGrpcChannel = new FakeGrpcChannel("LegacyAllocationCaptureObjectTest", myService);

  /**
   * This is a high-level test that validates the generation of allocation tracking MemoryObjects hierarchy based on fake allocation events.
   * We want to ensure not only the LegacyAllocationCaptureObject holds the correct HeapSet(s) representing the allocated classes, but
   * children MemoryObject nodes (e.g. ClassSet, InstanceObject) hold correct information as well.
   */
  @Test
  public void testAllocationsObjectGeneration() throws Exception {
    long startTimeNs = TimeUnit.MILLISECONDS.toNanos(3);
    long endTimeNs = TimeUnit.MILLISECONDS.toNanos(8);

    AllocationsInfo testInfo = AllocationsInfo.newBuilder().setStartTime(startTimeNs).setEndTime(endTimeNs).build();
    LegacyAllocationCaptureObject capture =
      new LegacyAllocationCaptureObject(myGrpcChannel.getClient().getMemoryClient(), ProfilersTestData.SESSION_DATA, testInfo,
                                        myRelativeTimeConverter, myIdeProfilerServices.getFeatureTracker());

    // Verify values associated with the AllocationsInfo object.
    assertEquals(startTimeNs, capture.getStartTimeNs());
    assertEquals(endTimeNs, capture.getEndTimeNs());
    assertEquals("Allocation Range: 00:00:00.003 - 00:00:00.008", capture.getName());

    final CountDownLatch loadLatch = new CountDownLatch(1);
    final CountDownLatch doneLatch = new CountDownLatch(1);
    myService.setExplicitAllocationEvents(MemoryProfiler.LegacyAllocationEventsResponse.Status.NOT_READY, Collections.emptyList());
    new Thread(() -> {
      loadLatch.countDown();
      capture.load(null, null);
      doneLatch.countDown();
    }).start();

    loadLatch.await();

    int stackId1 = 1;
    int stackId2 = 2;
    myService.addExplicitAllocationClass(0, "test.klass0");
    myService.addExplicitAllocationClass(1, "test.klass1");
    myService.addExplicitAllocationStack("test.klass0", "testMethod0", 3, stackId1);
    myService.addExplicitAllocationStack("test.klass1", "testMethod1", 7, stackId2);
    LegacyAllocationEvent event1 =
      LegacyAllocationEvent.newBuilder().setCaptureTime(startTimeNs).setClassId(0).setStackId(stackId2).build();
    LegacyAllocationEvent event2 =
      LegacyAllocationEvent.newBuilder().setCaptureTime(startTimeNs).setClassId(1).setStackId(stackId1).build();
    myService.setExplicitAllocationEvents(LegacyAllocationEventsResponse.Status.SUCCESS, Arrays.asList(event1, event2));
    doneLatch.await();

    assertTrue(capture.isDoneLoading());
    assertFalse(capture.isError());

    // Allocation Tracking only shows "default" heap
    Collection<HeapSet> heaps = capture.getHeapSets();
    assertEquals(1, heaps.size());

    HeapSet defaultHeap = heaps.stream().filter(heap -> "default".equals(heap.getName())).findFirst().orElse(null);
    assertNotNull(defaultHeap);
    defaultHeap.getChildrenClassifierSets(); // expand the children

    ClassSet class0 = MemoryProfilerTestUtils.findChildClassSetWithName(defaultHeap, "test.klass0");
    assertEquals(1, class0.getInstancesCount());
    ClassSet class1 = MemoryProfilerTestUtils.findChildClassSetWithName(defaultHeap, "test.klass1");
    assertEquals(1, class1.getInstancesCount());

    InstanceObject instance0 = class0.getInstancesStream().findFirst().orElse(null);
    InstanceObject instance1 = class1.getInstancesStream().findFirst().orElse(null);
    // Note: allocation records contains no depth/fields/references information
    verifyInstance(instance0, "test.klass0", Integer.MAX_VALUE, 0, 0, 1);
    verifyInstance(instance1, "test.klass1", Integer.MAX_VALUE, 0, 0, 1);

    assertNotNull(instance0.getCallStack());
    AllocationStack.StackFrame frame0 = instance0.getCallStack().getFullStack().getFramesList().get(0);
    assertNotNull(instance1.getCallStack());
    AllocationStack.StackFrame frame1 = instance1.getCallStack().getFullStack().getFramesList().get(0);
    verifyStackFrame(frame0, "test.klass1", "testMethod1", 7);
    verifyStackFrame(frame1, "test.klass0", "testMethod0", 3);
  }

  @Test
  public void testLoadingFailure() throws Exception {
    long startTimeNs = TimeUnit.MILLISECONDS.toNanos(3);
    long endTimeNs = TimeUnit.MILLISECONDS.toNanos(8);

    AllocationsInfo testInfo1 = AllocationsInfo.newBuilder().setStartTime(startTimeNs).setEndTime(endTimeNs).build();
    LegacyAllocationCaptureObject capture =
      new LegacyAllocationCaptureObject(myGrpcChannel.getClient().getMemoryClient(), ProfilersTestData.SESSION_DATA, testInfo1,
                                        myRelativeTimeConverter, myIdeProfilerServices.getFeatureTracker());

    assertFalse(capture.isDoneLoading());
    assertFalse(capture.isError());

    myService.setExplicitAllocationEvents(LegacyAllocationEventsResponse.Status.FAILURE_UNKNOWN, Collections.emptyList());
    capture.load(null, null);

    assertTrue(capture.isDoneLoading());
    assertTrue(capture.isError());
    capture.getHeapSets().forEach(heapSet -> assertEquals(0, heapSet.getInstancesCount()));
  }

  private static void verifyInstance(InstanceObject instance,
                                     String className,
                                     int depth,
                                     int fieldSize,
                                     int referenceSize,
                                     int frameCount) {
    assertEquals(className, instance.getClassEntry().getClassName());
    assertEquals(depth, instance.getDepth());
    assertEquals(fieldSize, instance.getFields().size());
    assertEquals(referenceSize, instance.getReferences().size());
    assertNotNull(instance.getCallStack());
    assertEquals(frameCount, instance.getCallStack().getFullStack().getFramesCount());
  }

  private static void verifyStackFrame(MemoryProfiler.AllocationStack.StackFrame frame, String klass, String method, int line) {
    assertEquals(klass, frame.getClassName());
    assertEquals(method, frame.getMethodName());
    assertEquals(line, frame.getLineNumber());
  }
}