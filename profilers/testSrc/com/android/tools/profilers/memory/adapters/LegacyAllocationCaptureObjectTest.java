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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.ddmlib.allocations.AllocationsParserTest;
import com.android.tools.adtui.model.FakeTimer;
import com.android.tools.idea.protobuf.ByteString;
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel;
import com.android.tools.idea.transport.faketransport.FakeTransportService;
import com.android.tools.profiler.proto.Memory;
import com.android.tools.profiler.proto.Memory.AllocationStack;
import com.android.tools.profiler.proto.Memory.AllocationsInfo;
import com.android.tools.profilers.FakeIdeProfilerServices;
import com.android.tools.profilers.ProfilerClient;
import com.android.tools.profilers.ProfilersTestData;
import com.android.tools.profilers.memory.MemoryProfilerTestUtils;
import com.android.tools.profilers.memory.adapters.classifiers.ClassSet;
import com.android.tools.profilers.memory.adapters.classifiers.HeapSet;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;

public class LegacyAllocationCaptureObjectTest {

  @NotNull private final FakeTimer myTimer = new FakeTimer();
  @NotNull private final FakeTransportService myTransportService = new FakeTransportService(myTimer);
  @NotNull private final FakeIdeProfilerServices myIdeProfilerServices = new FakeIdeProfilerServices();

  @Rule
  public FakeGrpcChannel myGrpcChannel = new FakeGrpcChannel("LegacyAllocationCaptureObjectTest", myTransportService);

  @Test
  public void testFailedAllocationsInfo() {
    AllocationsInfo testInfo = AllocationsInfo.newBuilder().setSuccess(false).build();
    LegacyAllocationCaptureObject capture =
      new LegacyAllocationCaptureObject(new ProfilerClient(myGrpcChannel.getChannel()),
                                        ProfilersTestData.SESSION_DATA,
                                        testInfo,
                                        myIdeProfilerServices.getFeatureTracker());

    capture.load(null, null);
    assertTrue(capture.isDoneLoading());
    assertTrue(capture.isError());
  }

  /**
   * This is a high-level test that validates the generation of allocation tracking MemoryObjects hierarchy based on fake allocation events.
   * We want to ensure not only the LegacyAllocationCaptureObject holds the correct HeapSet(s) representing the allocated classes, but
   * children MemoryObject nodes (e.g. ClassSet, InstanceObject) hold correct information as well.
   */
  @Test
  public void testAllocationsObjectGeneration() throws Exception {
    long startTimeNs = TimeUnit.MILLISECONDS.toNanos(3);
    long endTimeNs = TimeUnit.MILLISECONDS.toNanos(8);

    AllocationsInfo testInfo = AllocationsInfo.newBuilder().setStartTime(startTimeNs).setEndTime(endTimeNs).setSuccess(true).build();
    LegacyAllocationCaptureObject capture =
      new LegacyAllocationCaptureObject(new ProfilerClient(myGrpcChannel.getChannel()),
                                        ProfilersTestData.SESSION_DATA,
                                        testInfo,
                                        myIdeProfilerServices.getFeatureTracker());

    // Verify values associated with the AllocationsInfo object.
    assertEquals(startTimeNs, capture.getStartTimeNs());
    assertEquals(endTimeNs, capture.getEndTimeNs());

    final CountDownLatch loadLatch = new CountDownLatch(1);
    final CountDownLatch doneLatch = new CountDownLatch(1);
    new Thread(() -> {
      loadLatch.countDown();
      capture.load(null, null);
      doneLatch.countDown();
    }).start();

    loadLatch.await();

    ByteBuffer buffer = AllocationsParserTest.putAllocationInfo(new String[]{"test.klass0", "test.klass1"}, // class names
                                                                new String[]{"testMethod0", "testMethod1"}, // method names
                                                                new String[]{"test1.java", "test2.java"}, // file names
                                                                new int[][]{  // allocation events (size, thread id, class id, stack depth
                                                                  {0, 0, 0, 1},
                                                                  {0, 0, 1, 1}
                                                                },
                                                                new short[][][]{ // stack info (class id, method id, file id, line number)
                                                                  {
                                                                    {1, 1, 0, 7},
                                                                  },
                                                                  {
                                                                    {0, 0, 1, 3},
                                                                  }
                                                                });
    myTransportService.addFile(Long.toString(startTimeNs), ByteString.copyFrom(buffer));
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

    assertNotNull(instance0.getAllocationCallStack());
    AllocationStack.StackFrame frame0 = instance0.getAllocationCallStack().getFullStack().getFramesList().get(0);
    assertNotNull(instance1.getAllocationCallStack());
    AllocationStack.StackFrame frame1 = instance1.getAllocationCallStack().getFullStack().getFramesList().get(0);
    verifyStackFrame(frame0, "test.klass1", "testMethod1", 7);
    verifyStackFrame(frame1, "test.klass0", "testMethod0", 3);
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
    assertNotNull(instance.getAllocationCallStack());
    assertEquals(frameCount, instance.getAllocationCallStack().getFullStack().getFramesCount());
  }

  private static void verifyStackFrame(Memory.AllocationStack.StackFrame frame, String klass, String method, int line) {
    assertEquals(klass, frame.getClassName());
    assertEquals(method, frame.getMethodName());
    assertEquals(line, frame.getLineNumber());
  }
}