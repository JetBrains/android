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

import com.android.tools.adtui.model.Range;
import com.android.tools.profiler.proto.MemoryProfiler;
import com.android.tools.profiler.proto.MemoryProfiler.AllocationEvent;
import com.android.tools.profilers.FakeGrpcChannel;
import com.android.tools.profilers.FakeIdeProfilerServices;
import com.android.tools.profilers.ProfilersTestData;
import com.android.tools.profilers.memory.FakeMemoryService;
import com.google.common.util.concurrent.MoreExecutors;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static com.android.tools.profilers.memory.adapters.LiveAllocationCaptureObject.DEFAULT_HEAP_NAME;
import static org.junit.Assert.*;

public class LiveAllocationCaptureObjectTest {
  @NotNull private final FakeMemoryService myService = new FakeMemoryService();
  @NotNull private final FakeIdeProfilerServices myIdeProfilerServices = new FakeIdeProfilerServices();

  @Rule
  public FakeGrpcChannel myGrpcChannel = new FakeGrpcChannel("LiveAllocationCaptureObjectTest", myService);


  static final int STACK1 = 1;
  static final int STACK2 = 2;
  static final long CLASS1 = 1000;
  static final long CLASS2 = 1001;
  static final long CLASS3 = 1002;
  static final long CLASS1_INSTANCE1 = 10000;
  static final long CLASS2_INSTANCE1 = 10001;
  static final long CLASS3_INSTANCE1 = 10002;
  static final String METHOD1_NAME = "Method1";
  static final String METHOD2_NAME = "Method2";
  static final String CLASS1_NAME = "java.lang.Klass1";
  static final String CLASS2_NAME = "java.lang.Klass2[][]";
  static final String CLASS3_NAME = "java.lang.Klass3[][][]";;
  static final String CLASS1_NAME_SIMPLE = "Klass1";
  static final String CLASS2_NAME_SIMPLE = "Klass2[][]";
  static final String CLASS3_NAME_SIMPLE = "Klass3[][][]";

  // Simple test to check that we get the correct event+data on load and selection changes.
  // Note that the fake data does not include timestamps as we are hardcoding what the FakeMemoryService returns.
  @Test
  public void testBasicLiveAllocationWorkflow() throws Exception {
    final int APP_ID = 1;
    final long SELECTION_START_US = TimeUnit.MILLISECONDS.toMicros(1);
    final long SELECTION_END_US = TimeUnit.MILLISECONDS.toMicros(10);
    final Range SELECTION_RANGE = new Range(SELECTION_START_US, SELECTION_START_US);

    LiveAllocationCaptureObject capture = new LiveAllocationCaptureObject(myGrpcChannel.getClient().getMemoryClient(),
                                                                          ProfilersTestData.SESSION_DATA, APP_ID,
                                                                          SELECTION_START_US,
                                                                          myIdeProfilerServices.getFeatureTracker());

    // Populate FakeMemoryService with fake allocation contexts and events
    myService.addExplicitAllocationClass(CLASS1, CLASS1_NAME);
    myService.addExplicitAllocationClass(CLASS2, CLASS2_NAME);
    myService.addExplicitAllocationStack(CLASS1_NAME, METHOD1_NAME, -1, STACK1);
    myService.addExplicitAllocationStack(CLASS2_NAME, METHOD2_NAME, -1, STACK2);

    AllocationEvent alloc1 = AllocationEvent.newBuilder()
      .setAllocData(AllocationEvent.Allocation.newBuilder().setTag(CLASS1_INSTANCE1).setClassTag(CLASS1).setStackId(STACK1)).build();
    AllocationEvent dealloc1 = AllocationEvent.newBuilder()
      .setFreeData(AllocationEvent.Deallocation.newBuilder().setTag(CLASS1_INSTANCE1).setClassTag(CLASS1)).build();
    AllocationEvent alloc2 = AllocationEvent.newBuilder()
      .setAllocData(AllocationEvent.Allocation.newBuilder().setTag(CLASS2_INSTANCE1).setClassTag(CLASS2).setStackId(STACK2)).build();
    MemoryProfiler.BatchAllocationSample.Builder allocDataBuilder = MemoryProfiler.BatchAllocationSample.newBuilder();
    allocDataBuilder.addAllEvents(Arrays.asList(alloc1, dealloc1, alloc2));
    myService.setExplicitBatchAllocationSample(allocDataBuilder.setTimestamp(Long.MAX_VALUE / 2).build());

    // Heap set should start out empty.
    HeapSet heapSet = capture.getHeapSet(LiveAllocationCaptureObject.DEFAULT_HEAP_ID);
    assertEquals(0, heapSet.getChildrenClassifierSets().size());

    // Listens to the event that gets fired when load is called, then check the content of the changedNode parameter
    capture.addCaptureChangedListener(new CaptureObject.CaptureChangedListener() {
      //int eventCount = 0;

      // Assertions running in other threads do not work properly
      // We commented out the entire block and will resume it after test refactoring
      @Override
      public void heapChanged(@NotNull ChangedNode changedNode, boolean clear) {
        //if (eventCount == 0) {
        //  // The first event triggered by load.
        //  assertEquals(heapSet, changedNode.getClassifierSet());
        //  verifyClassifierSet(changedNode.getClassifierSet(), DEFAULT_HEAP_NAME, 2, 1, 2, 2, true);
        //  List<ClassifierSet> childrenClassifierSet = new ArrayList<>(changedNode.getChildClassifierSets());
        //  verifyClassifierSet(childrenClassifierSet.get(0), CLASS1_NAME_SIMPLE, 1, 1, 1, 0, true);
        //  verifyClassifierSet(childrenClassifierSet.get(1), CLASS2_NAME_SIMPLE, 1, 0, 1, 0, true);
        //}
        //else if (eventCount == 1) {
        //  // The second event triggered by selection range changes
        //  assertEquals(heapSet, changedNode.getClassifierSet());
        //  verifyClassifierSet(changedNode.getClassifierSet(), DEFAULT_HEAP_NAME, 3, 2, 3, 3, true);
        //  List<ClassifierSet> childrenClassifierSet = new ArrayList<>(changedNode.getChildClassifierSets());
        //  // Only CLASS2 and CLASS3 nodes have changed
        //  verifyClassifierSet(childrenClassifierSet.get(0), CLASS2_NAME_SIMPLE, 1, 1, 1, 0, true);
        //  verifyClassifierSet(childrenClassifierSet.get(1), CLASS3_NAME_SIMPLE, 1, 0, 1, 0, false);
        //}
        //
        //eventCount++;
      }
    });

    SELECTION_RANGE.set(SELECTION_START_US, SELECTION_END_US);
    capture.load(SELECTION_RANGE, MoreExecutors.directExecutor());
    waitForLoadComplete(capture);
    verifyClassifierSet(heapSet, DEFAULT_HEAP_NAME, 2, 1, 2, 2, true);
    List<ClassifierSet> childrenClassifierSet = heapSet.getChildrenClassifierSets();
    verifyClassifierSet(childrenClassifierSet.get(0), CLASS1_NAME_SIMPLE, 1, 1, 1, 0, true);
    verifyClassifierSet(childrenClassifierSet.get(1), CLASS2_NAME_SIMPLE, 1, 0, 1, 0, true);

    // Simulates a subsequent deallocation event for the previous alloc2 + a new allocation event (without callstack)
    myService.addExplicitAllocationClass(CLASS3, CLASS3_NAME);
    allocDataBuilder = MemoryProfiler.BatchAllocationSample.newBuilder();
    AllocationEvent dealloc2 = AllocationEvent.newBuilder()
      .setFreeData(AllocationEvent.Deallocation.newBuilder().setTag(CLASS2_INSTANCE1).setClassTag(CLASS2)).build();
    AllocationEvent alloc3 = AllocationEvent.newBuilder()
      .setAllocData(AllocationEvent.Allocation.newBuilder().setTag(CLASS3_INSTANCE1).setClassTag(CLASS3)).build();
    allocDataBuilder.addAllEvents(Arrays.asList(dealloc2, alloc3));
    myService.setExplicitBatchAllocationSample(allocDataBuilder.setTimestamp(Long.MAX_VALUE / 2).build());

    // Fake a selection range change that only changes the max value (e.g. the heap set should not clear)
    SELECTION_RANGE.set(SELECTION_START_US, SELECTION_END_US + 1);
    waitForLoadComplete(capture);
    verifyClassifierSet(heapSet, DEFAULT_HEAP_NAME, 3, 2, 3, 3, true);
    childrenClassifierSet = heapSet.getChildrenClassifierSets();
    verifyClassifierSet(childrenClassifierSet.get(0), CLASS1_NAME_SIMPLE, 1, 1, 1, 0, true);
    verifyClassifierSet(childrenClassifierSet.get(1), CLASS2_NAME_SIMPLE, 1, 1, 1, 0, true);
    verifyClassifierSet(childrenClassifierSet.get(2), CLASS3_NAME_SIMPLE, 1, 0, 1, 0, false);
  }

  // This test checks that optimization by canceling outstanding queries works properly
  // 1. Load Initial Range
  // 2. Block myService
  // 3. Fake 5 selection changes
  // 4. Unblock myService
  // 5. Only the last range change should have been running
  @Test
  public void testOptimizedLiveAllocationWorkflow() throws Exception {
    final int APP_ID = 1;
    final long SELECTION_START_US = TimeUnit.MILLISECONDS.toMicros(1);
    final Range SELECTION_RANGE = new Range(SELECTION_START_US, SELECTION_START_US);

    long selectionEndUs = SELECTION_START_US;
    LiveAllocationCaptureObject capture = new LiveAllocationCaptureObject(myGrpcChannel.getClient().getMemoryClient(),
                                                                          ProfilersTestData.SESSION_DATA, APP_ID,
                                                                          SELECTION_START_US,
                                                                          myIdeProfilerServices.getFeatureTracker());

    // Populate FakeMemoryService with fake allocation contexts and events
    myService.addExplicitAllocationClass(CLASS1, CLASS1_NAME);
    myService.addExplicitAllocationClass(CLASS2, CLASS2_NAME);
    myService.addExplicitAllocationStack(CLASS1_NAME, METHOD1_NAME, -1, STACK1);
    myService.addExplicitAllocationStack(CLASS2_NAME, METHOD2_NAME, -1, STACK2);

    AllocationEvent alloc1 = AllocationEvent.newBuilder()
      .setAllocData(AllocationEvent.Allocation.newBuilder().setTag(CLASS1_INSTANCE1).setClassTag(CLASS1).setStackId(STACK1)).build();
    AllocationEvent dealloc1 = AllocationEvent.newBuilder()
      .setFreeData(AllocationEvent.Deallocation.newBuilder().setTag(CLASS1_INSTANCE1).setClassTag(CLASS1)).build();
    AllocationEvent alloc2 = AllocationEvent.newBuilder()
      .setAllocData(AllocationEvent.Allocation.newBuilder().setTag(CLASS2_INSTANCE1).setClassTag(CLASS2).setStackId(STACK2)).build();
    MemoryProfiler.BatchAllocationSample.Builder allocDataBuilder = MemoryProfiler.BatchAllocationSample.newBuilder();
    allocDataBuilder.addAllEvents(Arrays.asList(alloc1, dealloc1, alloc2));
    // If allocationSample's timeStamp is not large enough, LiveAllocationCaptureObject's myEventsEndTimeNs may affect query range
    myService.setExplicitBatchAllocationSample(allocDataBuilder.setTimestamp(Long.MAX_VALUE / 2).build());

    // Heap set should start out empty.
    HeapSet heapSet = capture.getHeapSet(LiveAllocationCaptureObject.DEFAULT_HEAP_ID);
    assertEquals(0, heapSet.getChildrenClassifierSets().size());

    // Listens to the event that gets fired when load is called, then check the content of the changedNode parameter
    capture.addCaptureChangedListener(new CaptureObject.CaptureChangedListener() {
      //int eventCount = 0;

      // Assertions running in other threads do not work properly
      // We commented out the entire block and will resume it after test refactoring
      @Override
      public void heapChanged(@NotNull ChangedNode changedNode, boolean clear) {
        //if (eventCount == 0) {
        //  // The first event triggered by load.
        //  assertTrue(clear);
        //  assertEquals(heapSet, changedNode.getClassifierSet());
        //  verifyClassifierSet(changedNode.getClassifierSet(), DEFAULT_HEAP_NAME, 2, 1, 2, 2, true);
        //  List<ClassifierSet> childrenClassifierSet = new ArrayList<>(changedNode.getChildClassifierSets());
        //  verifyClassifierSet(childrenClassifierSet.get(0), CLASS1_NAME_SIMPLE, 1, 1, 1, 0, true);
        //  verifyClassifierSet(childrenClassifierSet.get(1), CLASS2_NAME_SIMPLE, 1, 0, 1, 0, true);
        //}
        //else if (eventCount == 1) {
        //  // The second event triggered by selection range changes
        //  assertFalse(clear);
        //  assertEquals(heapSet, changedNode.getClassifierSet());
        //  verifyClassifierSet(changedNode.getClassifierSet(), DEFAULT_HEAP_NAME, 3, 2, 3, 3, true);
        //  List<ClassifierSet> childrenClassifierSet = new ArrayList<>(changedNode.getChildClassifierSets());
        //  // Only CLASS2 and CLASS3 nodes have changed
        //  verifyClassifierSet(childrenClassifierSet.get(0), CLASS2_NAME_SIMPLE, 1, 1, 1, 0, true);
        //  verifyClassifierSet(childrenClassifierSet.get(1), CLASS3_NAME_SIMPLE, 1, 0, 1, 0, false);
        //}
        //else {
        //  // Other event should be canceled
        //  assertEquals(0, 1);
        //}
        //eventCount++;
      }
    });

    SELECTION_RANGE.set(SELECTION_START_US,  ++selectionEndUs);
    capture.load(SELECTION_RANGE, MoreExecutors.directExecutor());
    waitForLoadComplete(capture);
    verifyClassifierSet(heapSet, DEFAULT_HEAP_NAME, 2, 1, 2, 2, true);
    List<ClassifierSet> childrenClassifierSet = heapSet.getChildrenClassifierSets();
    verifyClassifierSet(childrenClassifierSet.get(0), CLASS1_NAME_SIMPLE, 1, 1, 1, 0, true);
    verifyClassifierSet(childrenClassifierSet.get(1), CLASS2_NAME_SIMPLE, 1, 0, 1, 0, true);


    myService.blockGetAllocationContexts();
    // Simulates a subsequent deallocation event for the previous alloc2 + a new allocation event (without callstack)
    myService.addExplicitAllocationClass(CLASS3, CLASS3_NAME);
    allocDataBuilder = MemoryProfiler.BatchAllocationSample.newBuilder();
    AllocationEvent dealloc2 = AllocationEvent.newBuilder()
      .setFreeData(AllocationEvent.Deallocation.newBuilder().setTag(CLASS2_INSTANCE1).setClassTag(CLASS2)).build();
    AllocationEvent alloc3 = AllocationEvent.newBuilder()
      .setAllocData(AllocationEvent.Allocation.newBuilder().setTag(CLASS3_INSTANCE1).setClassTag(CLASS3)).build();
    allocDataBuilder.addAllEvents(Arrays.asList(dealloc2, alloc3));
    myService.setExplicitBatchAllocationSample(allocDataBuilder.setTimestamp(Long.MAX_VALUE / 2).build());

    // Fake 5 selection range changes that would be canceled
    for (int k = 0; k < 5; ++k) {
      SELECTION_RANGE.set(SELECTION_START_US, ++selectionEndUs);
      Thread.sleep(10);
    }
    myService.unblockGetAllocatinoContexts();
    waitForLoadComplete(capture);
    verifyClassifierSet(heapSet, DEFAULT_HEAP_NAME, 4, 3, 3, 3, true);
    childrenClassifierSet = heapSet.getChildrenClassifierSets();
    verifyClassifierSet(childrenClassifierSet.get(0), CLASS1_NAME_SIMPLE, 1, 1, 1, 0, true);
    verifyClassifierSet(childrenClassifierSet.get(1), CLASS2_NAME_SIMPLE, 1, 2, 1, 0, true);
    verifyClassifierSet(childrenClassifierSet.get(2), CLASS3_NAME_SIMPLE, 2, 0, 1, 0, false);
  }

  private static void verifyClassifierSet(@NotNull ClassifierSet classifierSet,
                                          @NotNull String name,
                                          int allocCount,
                                          int deallocCount,
                                          int totalInstanceCount,
                                          int childrenCount,
                                          boolean hasStackInfo) {
    assertEquals(name, classifierSet.getName());
    assertEquals(allocCount, classifierSet.getAllocatedCount());
    assertEquals(deallocCount, classifierSet.getDeallocatedCount());
    assertEquals(totalInstanceCount, classifierSet.getInstancesCount());
    assertEquals(childrenCount, classifierSet.getChildrenClassifierSets().size());
    assertEquals(hasStackInfo, classifierSet.hasStackInfo());
  }

  // Wait for the executor service to complete the task created in load(...)
  // NOTE - this works because myExecutorService is a single-threaded executor.
  private static void waitForLoadComplete(@NotNull LiveAllocationCaptureObject capture) throws InterruptedException, ExecutionException {
    CountDownLatch latch = new CountDownLatch(1);
    capture.myExecutorService.invokeAny(Collections.singleton((Callable<CaptureObject>)() -> {
      try {
        latch.countDown();
      }
      catch (Exception ignored) {
      }
      return capture;
    }));
    latch.await();
  }
}