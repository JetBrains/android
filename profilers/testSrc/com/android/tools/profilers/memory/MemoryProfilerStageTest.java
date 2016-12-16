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
import com.android.tools.profilers.memory.adapters.AllocationsCaptureObject;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.*;

public class MemoryProfilerStageTest extends MemoryProfilerTestBase {
  private final FakeMemoryService myService = new FakeMemoryService();

  @Rule
  public FakeGrpcChannel myGrpcChannel = new FakeGrpcChannel("MemoryProfilerStageTestChannel", myService);

  @Override
  protected FakeGrpcChannel getGrpcChannel() {
    return myGrpcChannel;
  }

  @Test
  public void testToggleLegacyCapture() throws Exception {
    assertEquals(false, myStage.isTrackingAllocations());
    assertNull(myStage.getSelectedCapture());

    // Test the no-action cases
    myStage.trackAllocations(false, null);
    assertEquals(false, myStage.isTrackingAllocations());
    assertAndResetCounts(1, 0, 0, 0, 0, 0);
    myService.setExplicitStatus(TrackAllocationsResponse.Status.NOT_ENABLED);
    myStage.trackAllocations(false, null);
    assertEquals(false, myStage.isTrackingAllocations());
    assertAndResetCounts(1, 0, 0, 0, 0, 0);
    myService.setExplicitStatus(TrackAllocationsResponse.Status.FAILURE_UNKNOWN);
    myStage.trackAllocations(false, null);
    assertEquals(false, myStage.isTrackingAllocations());
    assertAndResetCounts(1, 0, 0, 0, 0, 0);

    // Starting a tracking session
    int infoId = 1;
    int infoStart = 5;
    int infoEnd = 10;
    myService.advanceTime(1);
    myService.setExplicitStatus(TrackAllocationsResponse.Status.SUCCESS);
    myService.setExplicitAllocationsInfo(infoId, MemoryProfiler.AllocationsInfo.Status.IN_PROGRESS,
                                         infoStart, DurationData.UNSPECIFIED_DURATION, true);
    myStage.trackAllocations(true, null);
    assertEquals(true, myStage.isTrackingAllocations());
    assertEquals(null, myStage.getSelectedCapture());
    assertAndResetCounts(1, 0, 0, 0, 0, 0);

    // Attempting to start a in-progress session
    myService.setExplicitStatus(TrackAllocationsResponse.Status.IN_PROGRESS);
    myStage.trackAllocations(true, null);
    assertEquals(true, myStage.isTrackingAllocations());
    assertAndResetCounts(1, 0, 0, 0, 0, 0);

    // Spawn a different thread to stopping a tracking session
    // This will start loading the CaptureObject but it will loop until the AllocationsInfo returns a COMPLETED status.
    final CountDownLatch waitLatch = new CountDownLatch(1);
    new Thread(() -> {
      myService.setExplicitStatus(TrackAllocationsResponse.Status.SUCCESS);
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
  public void testMemoryObjectSelection() {
    myStage.selectCapture(DUMMY_CAPTURE, null);
    assertEquals(DUMMY_CAPTURE, myStage.getSelectedCapture());
    assertNull(myStage.getSelectedHeap());
    assertNull(myStage.getSelectedClass());
    assertNull(myStage.getSelectedInstance());
    assertAndResetCounts(0, 1, 0, 0, 0, 0);
    myMockLoader.runTask();
    assertAndResetCounts(0, 0, 1, 0, 0, 0);

    // Make sure the same capture selected shouldn't result in aspects getting raised again.
    myStage.selectCapture(DUMMY_CAPTURE, null);
    assertEquals(DUMMY_CAPTURE, myStage.getSelectedCapture());
    assertNull(myStage.getSelectedHeap());
    assertNull(myStage.getSelectedClass());
    assertNull(myStage.getSelectedInstance());
    assertAndResetCounts(0, 0, 0, 0, 0, 0);

    myStage.selectHeap(DUMMY_HEAP_1);
    assertEquals(DUMMY_CAPTURE, myStage.getSelectedCapture());
    assertEquals(DUMMY_HEAP_1, myStage.getSelectedHeap());
    assertNull(myStage.getSelectedClass());
    assertNull(myStage.getSelectedInstance());
    assertAndResetCounts(0, 0, 0, 1, 0, 0);

    myStage.selectHeap(DUMMY_HEAP_1);
    assertEquals(DUMMY_CAPTURE, myStage.getSelectedCapture());
    assertEquals(DUMMY_HEAP_1, myStage.getSelectedHeap());
    assertNull(myStage.getSelectedClass());
    assertNull(myStage.getSelectedInstance());
    assertAndResetCounts(0, 0, 0, 0, 0, 0);

    myStage.selectClass(DUMMY_CLASS_1);
    assertEquals(DUMMY_CAPTURE, myStage.getSelectedCapture());
    assertEquals(DUMMY_HEAP_1, myStage.getSelectedHeap());
    assertEquals(DUMMY_CLASS_1, myStage.getSelectedClass());
    assertNull(myStage.getSelectedInstance());
    assertAndResetCounts(0, 0, 0, 0, 1, 0);

    myStage.selectClass(DUMMY_CLASS_1);
    assertEquals(DUMMY_CAPTURE, myStage.getSelectedCapture());
    assertEquals(DUMMY_HEAP_1, myStage.getSelectedHeap());
    assertEquals(DUMMY_CLASS_1, myStage.getSelectedClass());
    assertNull(myStage.getSelectedInstance());
    assertAndResetCounts(0, 0, 0, 0, 0, 0);

    myStage.selectInstance(DUMMY_INSTANCE);
    assertEquals(DUMMY_CAPTURE, myStage.getSelectedCapture());
    assertEquals(DUMMY_HEAP_1, myStage.getSelectedHeap());
    assertEquals(DUMMY_CLASS_1, myStage.getSelectedClass());
    assertEquals(DUMMY_INSTANCE, myStage.getSelectedInstance());
    assertAndResetCounts(0, 0, 0, 0, 0, 1);

    myStage.selectInstance(DUMMY_INSTANCE);
    assertEquals(DUMMY_CAPTURE, myStage.getSelectedCapture());
    assertEquals(DUMMY_HEAP_1, myStage.getSelectedHeap());
    assertEquals(DUMMY_CLASS_1, myStage.getSelectedClass());
    assertEquals(DUMMY_INSTANCE, myStage.getSelectedInstance());
    assertAndResetCounts(0, 0, 0, 0, 0, 0);

    // Test the reverse direction, to make sure children MemoryObjects are nullified in the selection.
    myStage.selectClass(null);
    assertEquals(DUMMY_CAPTURE, myStage.getSelectedCapture());
    assertEquals(DUMMY_HEAP_1, myStage.getSelectedHeap());
    assertNull(myStage.getSelectedClass());
    assertNull(myStage.getSelectedInstance());
    assertAndResetCounts(0, 0, 0, 0, 1, 1);

    // However, if a selection didn't change (e.g. null => null), it shouldn't trigger an aspect change either.
    myStage.selectHeap(null);
    assertEquals(DUMMY_CAPTURE, myStage.getSelectedCapture());
    assertNull(myStage.getSelectedHeap());
    assertNull(myStage.getSelectedClass());
    assertNull(myStage.getSelectedInstance());
    assertAndResetCounts(0, 0, 0, 1, 0, 0);
  }
}