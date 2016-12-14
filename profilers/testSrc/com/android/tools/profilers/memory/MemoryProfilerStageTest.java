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

import com.android.tools.profiler.proto.MemoryProfiler.TrackAllocationsResponse;
import com.android.tools.profilers.TestGrpcChannel;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class MemoryProfilerStageTest extends MemoryProfilerTestBase {
  @Rule
  public TestGrpcChannel<MemoryServiceMock> myGrpcChannel = new TestGrpcChannel<>("MEMORY_TEST_CHANNEL", new MemoryServiceMock());

  @Override
  @Before
  public void setup() {
    myProfilers = myGrpcChannel.getProfilers();
    myMockService = myGrpcChannel.getService();
    myStage = new MemoryProfilerStage(myProfilers, DUMMY_LOADER);
    myProfilers.setStage(myStage);

    super.setup();
  }

  @Test
  public void testToggleLegacyCapture() throws Exception {
    assertEquals(false, myStage.isTrackingAllocations());
    assertNull(myStage.getSelectedCapture());

    // Test the no-action cases
    myStage.trackAllocations(false);
    assertEquals(false, myStage.isTrackingAllocations());
    assertAndResetCounts(1, 0, 0, 0, 0, 0);
    myMockService.setExplicitStatus(TrackAllocationsResponse.Status.FAILURE_UNKNOWN);
    myStage.trackAllocations(false);
    assertEquals(false, myStage.isTrackingAllocations());
    assertAndResetCounts(1, 0, 0, 0, 0, 0);

    // Starting a tracking session
    myMockService.setExplicitStatus(null);
    myMockService.advanceTime(1);
    myStage.trackAllocations(true);
    assertEquals(true, myStage.isTrackingAllocations());
    assertEquals(null, myStage.getSelectedCapture());
    assertAndResetCounts(1, 0, 0, 0, 0, 0);

    // Attempting to start a in-progress session
    myStage.trackAllocations(true);
    assertEquals(true, myStage.isTrackingAllocations());
    assertAndResetCounts(1, 0, 0, 0, 0, 0);

    // Stopping a tracking session;
    myStage.trackAllocations(false);
    assertEquals(false, myStage.isTrackingAllocations());
    assertAndResetCounts(1, 0, 0, 0, 0, 0);  // Stopping a tracking session should NOT fire a CURRENT_CAPTURE change event.
  }

  @Test
  public void testMemoryObjectSelection() {
    myStage.selectCapture(DUMMY_CAPTURE, null);
    assertEquals(DUMMY_CAPTURE, myStage.getSelectedCapture());
    assertNull(myStage.getSelectedHeap());
    assertNull(myStage.getSelectedClass());
    assertNull(myStage.getSelectedInstance());
    assertAndResetCounts(0, 1, 1, 0, 0, 0);

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