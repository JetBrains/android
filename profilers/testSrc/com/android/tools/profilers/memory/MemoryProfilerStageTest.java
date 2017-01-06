/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.profilers.memory;

import com.android.tools.adtui.model.DurationData;
import com.android.tools.profiler.proto.MemoryProfiler;
import com.android.tools.profiler.proto.MemoryProfiler.TrackAllocationsResponse;
import com.android.tools.profilers.FakeGrpcChannel;
import com.android.tools.profilers.memory.adapters.*;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.*;

public class MemoryProfilerStageTest extends MemoryProfilerTestBase {
  @NotNull private final FakeMemoryService myService = new FakeMemoryService();
  @NotNull private CaptureObject myMockCapture;
  @NotNull private HeapObject myMockHeap;
  @NotNull private ClassObject myMockKlass;
  @NotNull private InstanceObject myMockInstance;

  @Rule
  public FakeGrpcChannel myGrpcChannel = new FakeGrpcChannel("MemoryProfilerStageTestChannel", myService);

  @Override
  protected FakeGrpcChannel getGrpcChannel() {
    return myGrpcChannel;
  };

  @Before
  public void setup() {
    myMockInstance = mockInstanceObject("DUMMY_INSTANCE");
    myMockKlass = mockClassObject("DUMMY_CLASS1", Collections.singletonList(myMockInstance));
    myMockHeap = mockHeapObject("DUMMY_HEAP1", Arrays.asList(myMockKlass));
    myMockCapture = mockCaptureObject("DUMMY_CAPTURE1", 5, 10, Arrays.asList(myMockHeap));
  }

  @Test
  public void testToggleLegacyCapture() throws Exception {
    assertEquals(false, myStage.isTrackingAllocations());
    assertNull(myStage.getSelectedCapture());

    // Test the no-action cases
    myStage.trackAllocations(false, null);
    assertEquals(false, myStage.isTrackingAllocations());
    assertAndResetCounts(1, 0, 0, 0, 0, 0);
    myService.setExplicitAllocationsStatus(TrackAllocationsResponse.Status.NOT_ENABLED);
    myStage.trackAllocations(false, null);
    assertEquals(false, myStage.isTrackingAllocations());
    assertAndResetCounts(1, 0, 0, 0, 0, 0);
    myService.setExplicitAllocationsStatus(TrackAllocationsResponse.Status.FAILURE_UNKNOWN);
    myStage.trackAllocations(false, null);
    assertEquals(false, myStage.isTrackingAllocations());
    assertAndResetCounts(1, 0, 0, 0, 0, 0);

    // Starting a tracking session
    int infoId = 1;
    int infoStart = 5;
    int infoEnd = 10;
    myService.advanceTime(1);
    myService.setExplicitAllocationsStatus(TrackAllocationsResponse.Status.SUCCESS);
    myService.setExplicitAllocationsInfo(infoId, MemoryProfiler.AllocationsInfo.Status.IN_PROGRESS,
                                         infoStart, DurationData.UNSPECIFIED_DURATION, true);
    myStage.trackAllocations(true, null);
    assertEquals(true, myStage.isTrackingAllocations());
    assertEquals(null, myStage.getSelectedCapture());
    assertAndResetCounts(1, 0, 0, 0, 0, 0);

    // Attempting to start a in-progress session
    myService.setExplicitAllocationsStatus(TrackAllocationsResponse.Status.IN_PROGRESS);
    myStage.trackAllocations(true, null);
    assertEquals(true, myStage.isTrackingAllocations());
    assertAndResetCounts(1, 0, 0, 0, 0, 0);

    // Spawn a different thread to stopping a tracking session
    // This will start loading the CaptureObject but it will loop until the AllocationsInfo returns a COMPLETED status.
    final CountDownLatch waitLatch = new CountDownLatch(1);
    new Thread(() -> {
      myService.setExplicitAllocationsStatus(TrackAllocationsResponse.Status.SUCCESS);
      myService.setExplicitAllocationsInfo(infoId, MemoryProfiler.AllocationsInfo.Status.POST_PROCESS,
                                           infoStart, infoEnd, true);
      myStage.trackAllocations(false, null);
      assertEquals(false, myStage.isTrackingAllocations());
      assertTrue(myStage.getSelectedCapture() instanceof AllocationsCaptureObject);
      AllocationsCaptureObject capture = (AllocationsCaptureObject)myStage.getSelectedCapture();
      assertEquals(infoId, capture.getInfoId());
      assertEquals(infoStart, capture.getStartTimeNs());
      assertEquals(infoEnd, capture.getEndTimeNs());
      assertFalse(capture.isDoneLoading());
      assertFalse(capture.isError());
      assertAndResetCounts(1, 1, 0, 0, 0, 0);
      waitLatch.countDown();
    }).run();

    try {
      waitLatch.await();
    }
    catch (InterruptedException ignored) {
    }

    // Manually mark the current allocation session as complete, which will trigger the CaptureObject to finish loading
    assertTrue(myStage.getSelectedCapture() instanceof AllocationsCaptureObject);
    AllocationsCaptureObject capture = (AllocationsCaptureObject)myStage.getSelectedCapture();
    myService.setExplicitAllocationsInfo(infoId, MemoryProfiler.AllocationsInfo.Status.COMPLETED,
                                         infoStart, infoEnd, true);
    // Run the CaptureObject.load task
    myMockLoader.runTask();
    assertTrue(capture.isDoneLoading());
    assertFalse(capture.isError());
    assertAndResetCounts(0, 0, 1, 0, 0, 0);
  }

  @Test
  public void testRequestHeapDump() throws Exception {
    // Bypass the load mechanism in HeapDumpCaptureObject.
    myMockLoader.setReturnImmediateFuture(true);

    // Test the no-action cases
    myService.setExplicitHeapDumpStatus(MemoryProfiler.TriggerHeapDumpResponse.Status.FAILURE_UNKNOWN);
    myStage.requestHeapDump(null);
    assertNull(myStage.getSelectedCapture());
    myService.setExplicitHeapDumpStatus(MemoryProfiler.TriggerHeapDumpResponse.Status.IN_PROGRESS);
    myStage.requestHeapDump(null);
    assertNull(myStage.getSelectedCapture());
    myService.setExplicitHeapDumpStatus(MemoryProfiler.TriggerHeapDumpResponse.Status.UNSPECIFIED);
    myStage.requestHeapDump(null);
    assertNull(myStage.getSelectedCapture());

    myService.setExplicitHeapDumpStatus(MemoryProfiler.TriggerHeapDumpResponse.Status.SUCCESS);
    myService.setExplicitHeapDumpInfo(1, 5, 10);
    myStage.requestHeapDump(null);
    assertNotNull(myStage.getSelectedCapture());
    assertTrue(myStage.getSelectedCapture() instanceof HeapDumpCaptureObject);
    HeapDumpCaptureObject capture = (HeapDumpCaptureObject)myStage.getSelectedCapture();
    assertEquals(1, capture.getDumpId());
    assertEquals(5, capture.getStartTimeNs());
    assertEquals(10, capture.getEndTimeNs());
  }

  @Test
  public void testMemoryObjectSelection() {
    myStage.selectCapture(myMockCapture, null);
    assertEquals(myMockCapture, myStage.getSelectedCapture());
    assertNull(myStage.getSelectedHeap());
    assertNull(myStage.getSelectedClass());
    assertNull(myStage.getSelectedInstance());
    assertAndResetCounts(0, 1, 0, 0, 0, 0);
    myMockLoader.runTask();
    assertAndResetCounts(0, 0, 1, 0, 0, 0);

    // Make sure the same capture selected shouldn't result in aspects getting raised again.
    myStage.selectCapture(myMockCapture, null);
    assertEquals(myMockCapture, myStage.getSelectedCapture());
    assertNull(myStage.getSelectedHeap());
    assertNull(myStage.getSelectedClass());
    assertNull(myStage.getSelectedInstance());
    assertAndResetCounts(0, 0, 0, 0, 0, 0);

    myStage.selectHeap(myMockHeap);
    assertEquals(myMockCapture, myStage.getSelectedCapture());
    assertEquals(myMockHeap, myStage.getSelectedHeap());
    assertNull(myStage.getSelectedClass());
    assertNull(myStage.getSelectedInstance());
    assertAndResetCounts(0, 0, 0, 1, 0, 0);

    myStage.selectHeap(myMockHeap);
    assertEquals(myMockCapture, myStage.getSelectedCapture());
    assertEquals(myMockHeap, myStage.getSelectedHeap());
    assertNull(myStage.getSelectedClass());
    assertNull(myStage.getSelectedInstance());
    assertAndResetCounts(0, 0, 0, 0, 0, 0);

    myStage.selectClass(myMockKlass);
    assertEquals(myMockCapture, myStage.getSelectedCapture());
    assertEquals(myMockHeap, myStage.getSelectedHeap());
    assertEquals(myMockKlass, myStage.getSelectedClass());
    assertNull(myStage.getSelectedInstance());
    assertAndResetCounts(0, 0, 0, 0, 1, 0);

    myStage.selectClass(myMockKlass);
    assertEquals(myMockCapture, myStage.getSelectedCapture());
    assertEquals(myMockHeap, myStage.getSelectedHeap());
    assertEquals(myMockKlass, myStage.getSelectedClass());
    assertNull(myStage.getSelectedInstance());
    assertAndResetCounts(0, 0, 0, 0, 0, 0);

    myStage.selectInstance(myMockInstance);
    assertEquals(myMockCapture, myStage.getSelectedCapture());
    assertEquals(myMockHeap, myStage.getSelectedHeap());
    assertEquals(myMockKlass, myStage.getSelectedClass());
    assertEquals(myMockInstance, myStage.getSelectedInstance());
    assertAndResetCounts(0, 0, 0, 0, 0, 1);

    myStage.selectInstance(myMockInstance);
    assertEquals(myMockCapture, myStage.getSelectedCapture());
    assertEquals(myMockHeap, myStage.getSelectedHeap());
    assertEquals(myMockKlass, myStage.getSelectedClass());
    assertEquals(myMockInstance, myStage.getSelectedInstance());
    assertAndResetCounts(0, 0, 0, 0, 0, 0);

    // Test the reverse direction, to make sure children MemoryObjects are nullified in the selection.
    myStage.selectClass(null);
    assertEquals(myMockCapture, myStage.getSelectedCapture());
    assertEquals(myMockHeap, myStage.getSelectedHeap());
    assertNull(myStage.getSelectedClass());
    assertNull(myStage.getSelectedInstance());
    assertAndResetCounts(0, 0, 0, 0, 1, 1);

    // However, if a selection didn't change (e.g. null => null), it shouldn't trigger an aspect change either.
    myStage.selectHeap(null);
    assertEquals(myMockCapture, myStage.getSelectedCapture());
    assertNull(myStage.getSelectedHeap());
    assertNull(myStage.getSelectedClass());
    assertNull(myStage.getSelectedInstance());
    assertAndResetCounts(0, 0, 0, 1, 0, 0);
  }
}