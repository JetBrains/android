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
import com.android.tools.adtui.model.FakeTimer;
import com.android.tools.adtui.model.RangedContinuousSeries;
import com.android.tools.adtui.model.legend.SeriesLegend;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.MemoryProfiler;
import com.android.tools.profiler.proto.MemoryProfiler.TrackAllocationsResponse;
import com.android.tools.profiler.proto.Profiler;
import com.android.tools.profilers.FakeGrpcChannel;
import com.android.tools.profilers.FakeProfilerService;
import com.android.tools.profilers.ProfilerMode;
import com.android.tools.profilers.memory.adapters.*;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;

import static com.android.tools.profilers.memory.MemoryProfilerConfiguration.ClassGrouping.ARRANGE_BY_CLASS;
import static com.android.tools.profilers.memory.MemoryProfilerConfiguration.ClassGrouping.ARRANGE_BY_PACKAGE;
import static org.junit.Assert.*;

public class MemoryProfilerStageTest extends MemoryProfilerTestBase {
  @NotNull private final FakeMemoryService myService = new FakeMemoryService();
  @NotNull private final FakeProfilerService myProfilerService = new FakeProfilerService();

  @Rule
  public FakeGrpcChannel myGrpcChannel = new FakeGrpcChannel("MemoryProfilerStageTestChannel", myService, myProfilerService);

  @Override
  protected FakeGrpcChannel getGrpcChannel() {
    return myGrpcChannel;
  }

  @Test
  public void testToggleLegacyCapture() throws Exception {
    assertEquals(false, myStage.isTrackingAllocations());
    assertNull(myStage.getSelectedCapture());
    assertEquals(ProfilerMode.NORMAL, myStage.getProfilerMode());

    // Test the no-action cases
    myStage.trackAllocations(false, null);
    assertEquals(false, myStage.isTrackingAllocations());
    assertEquals(ProfilerMode.NORMAL, myStage.getProfilerMode());
    myAspectObserver.assertAndResetCounts(1, 0, 0, 0, 0, 0, 0);
    myService.setExplicitAllocationsStatus(TrackAllocationsResponse.Status.NOT_ENABLED);
    myStage.trackAllocations(false, null);
    assertEquals(false, myStage.isTrackingAllocations());
    assertEquals(ProfilerMode.NORMAL, myStage.getProfilerMode());
    myAspectObserver.assertAndResetCounts(1, 0, 0, 0, 0, 0, 0);
    myService.setExplicitAllocationsStatus(TrackAllocationsResponse.Status.FAILURE_UNKNOWN);
    myStage.trackAllocations(false, null);
    assertEquals(false, myStage.isTrackingAllocations());
    assertEquals(ProfilerMode.NORMAL, myStage.getProfilerMode());
    myAspectObserver.assertAndResetCounts(1, 0, 0, 0, 0, 0, 0);

    // Starting a tracking session
    int infoStart = 5;
    int infoEnd = 10;
    myService.advanceTime(1);
    myService.setExplicitAllocationsStatus(TrackAllocationsResponse.Status.SUCCESS);
    myService.setExplicitAllocationsInfo(MemoryProfiler.AllocationsInfo.Status.IN_PROGRESS,
                                         infoStart, DurationData.UNSPECIFIED_DURATION, true);
    myStage.trackAllocations(true, null);
    assertEquals(true, myStage.isTrackingAllocations());
    assertEquals(null, myStage.getSelectedCapture());
    assertEquals(ProfilerMode.NORMAL, myStage.getProfilerMode());
    myAspectObserver.assertAndResetCounts(1, 0, 0, 0, 0, 0, 0);

    // Attempting to start a in-progress session
    myService.setExplicitAllocationsStatus(TrackAllocationsResponse.Status.IN_PROGRESS);
    myStage.trackAllocations(true, null);
    assertEquals(true, myStage.isTrackingAllocations());
    assertEquals(ProfilerMode.NORMAL, myStage.getProfilerMode());
    myAspectObserver.assertAndResetCounts(1, 0, 0, 0, 0, 0, 0);

    // Spawn a different thread to stop a tracking session
    // This will start loading the CaptureObject but it will loop until the AllocationEventsResponse returns a SUCCESS status.
    final CountDownLatch waitLatch = new CountDownLatch(1);
    new Thread(() -> {
      myService.setExplicitAllocationsStatus(TrackAllocationsResponse.Status.SUCCESS);
      myService.setExplicitAllocationsInfo(MemoryProfiler.AllocationsInfo.Status.COMPLETED,
                                           infoStart, infoEnd, true);
      myService.setExplicitAllocationEvents(MemoryProfiler.AllocationEventsResponse.Status.NOT_READY, Collections.emptyList());
      myStage.trackAllocations(false, null);
      assertEquals(false, myStage.isTrackingAllocations());
      assertTrue(myStage.getSelectedCapture() instanceof AllocationsCaptureObject);
      AllocationsCaptureObject capture = (AllocationsCaptureObject)myStage.getSelectedCapture();
      assertEquals(infoStart, capture.getStartTimeNs());
      assertEquals(infoEnd, capture.getEndTimeNs());
      assertFalse(capture.isDoneLoading());
      assertFalse(capture.isError());
      assertEquals(ProfilerMode.EXPANDED, myStage.getProfilerMode());
      myAspectObserver.assertAndResetCounts(1, 1, 0, 0, 0, 0, 0);
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
    myService.setExplicitAllocationEvents(MemoryProfiler.AllocationEventsResponse.Status.SUCCESS, Collections.emptyList());
    // Run the CaptureObject.load task
    myMockLoader.runTask();
    assertTrue(capture.isDoneLoading());
    assertFalse(capture.isError());
    myAspectObserver.assertAndResetCounts(0, 0, 1, 0, 0, 0, 0);
    assertEquals(ProfilerMode.EXPANDED, myStage.getProfilerMode());
  }

  @Test
  public void testRequestHeapDump() throws Exception {
    // Bypass the load mechanism in HeapDumpCaptureObject.
    myMockLoader.setReturnImmediateFuture(true);

    // Test the no-action cases
    myService.setExplicitHeapDumpStatus(MemoryProfiler.TriggerHeapDumpResponse.Status.FAILURE_UNKNOWN);
    myStage.requestHeapDump(null);
    assertNull(myStage.getSelectedCapture());
    assertEquals(ProfilerMode.NORMAL, myStage.getProfilerMode());
    myService.setExplicitHeapDumpStatus(MemoryProfiler.TriggerHeapDumpResponse.Status.IN_PROGRESS);
    myStage.requestHeapDump(null);
    assertNull(myStage.getSelectedCapture());
    assertEquals(ProfilerMode.NORMAL, myStage.getProfilerMode());
    myService.setExplicitHeapDumpStatus(MemoryProfiler.TriggerHeapDumpResponse.Status.UNSPECIFIED);
    myStage.requestHeapDump(null);
    assertNull(myStage.getSelectedCapture());
    assertEquals(ProfilerMode.NORMAL, myStage.getProfilerMode());

    myService.setExplicitHeapDumpStatus(MemoryProfiler.TriggerHeapDumpResponse.Status.SUCCESS);
    myService.setExplicitHeapDumpInfo(5, 10);
    myStage.requestHeapDump(null);
    assertNotNull(myStage.getSelectedCapture());
    assertTrue(myStage.getSelectedCapture() instanceof HeapDumpCaptureObject);
    HeapDumpCaptureObject capture = (HeapDumpCaptureObject)myStage.getSelectedCapture();
    assertEquals(5, capture.getStartTimeNs());
    assertEquals(10, capture.getEndTimeNs());
    assertEquals(ProfilerMode.EXPANDED, myStage.getProfilerMode());
  }

  @Test
  public void testMemoryObjectSelection() {
    final String dummyClassName = "DUMMY_CLASS1";
    InstanceObject mockInstance = mockInstanceObject(dummyClassName, "DUMMY_INSTANCE", null, null, null, 0, 1, 2, 3);
    ClassObject mockKlass = mockClassObject(dummyClassName, 1, 2, 3, Collections.singletonList(mockInstance));
    HeapObject mockHeap = mockHeapObject("DUMMY_HEAP1", Arrays.asList(mockKlass));
    CaptureObject mockCapture = mockCaptureObject("DUMMY_CAPTURE1", 5, 10, Collections.singletonList(mockHeap), true);

    myStage.selectCapture(mockCapture, null);
    assertEquals(mockCapture, myStage.getSelectedCapture());
    assertNull(myStage.getSelectedHeap());
    assertEquals(ARRANGE_BY_CLASS, myStage.getConfiguration().getClassGrouping());
    assertNull(myStage.getSelectedClass());
    assertNull(myStage.getSelectedInstance());
    assertEquals(ProfilerMode.EXPANDED, myStage.getProfilerMode());
    myAspectObserver.assertAndResetCounts(0, 1, 0, 0, 0, 0, 0);
    myMockLoader.runTask();
    assertEquals(ProfilerMode.EXPANDED, myStage.getProfilerMode());
    myAspectObserver.assertAndResetCounts(0, 0, 1, 0, 0, 0, 0);

    // Make sure the same capture selected shouldn't result in aspects getting raised again.
    myStage.selectCapture(mockCapture, null);
    assertEquals(mockCapture, myStage.getSelectedCapture());
    assertNull(myStage.getSelectedHeap());
    assertEquals(ARRANGE_BY_CLASS, myStage.getConfiguration().getClassGrouping());
    assertNull(myStage.getSelectedClass());
    assertNull(myStage.getSelectedInstance());
    myAspectObserver.assertAndResetCounts(0, 0, 0, 0, 0, 0, 0);

    myStage.selectHeap(mockHeap);
    assertEquals(mockCapture, myStage.getSelectedCapture());
    assertEquals(mockHeap, myStage.getSelectedHeap());
    assertEquals(ARRANGE_BY_CLASS, myStage.getConfiguration().getClassGrouping());
    assertNull(myStage.getSelectedClass());
    assertNull(myStage.getSelectedInstance());
    myAspectObserver.assertAndResetCounts(0, 0, 0, 0, 1, 0, 0);

    myStage.selectHeap(mockHeap);
    assertEquals(mockCapture, myStage.getSelectedCapture());
    assertEquals(mockHeap, myStage.getSelectedHeap());
    assertEquals(ARRANGE_BY_CLASS, myStage.getConfiguration().getClassGrouping());
    assertNull(myStage.getSelectedClass());
    assertNull(myStage.getSelectedInstance());
    myAspectObserver.assertAndResetCounts(0, 0, 0, 0, 0, 0, 0);

    assertEquals(ARRANGE_BY_CLASS, myStage.getConfiguration().getClassGrouping());
    myStage.getConfiguration().setClassGrouping(ARRANGE_BY_PACKAGE);
    assertEquals(ARRANGE_BY_PACKAGE, myStage.getConfiguration().getClassGrouping());
    myAspectObserver.assertAndResetCounts(0, 0, 0, 1, 0, 0, 0);

    myStage.getConfiguration().setClassGrouping(ARRANGE_BY_CLASS);
    assertEquals(ARRANGE_BY_CLASS, myStage.getConfiguration().getClassGrouping());
    myAspectObserver.assertAndResetCounts(0, 0, 0, 1, 0, 0, 0);

    myStage.selectClass(mockKlass);
    assertEquals(mockCapture, myStage.getSelectedCapture());
    assertEquals(mockHeap, myStage.getSelectedHeap());
    assertEquals(ARRANGE_BY_CLASS, myStage.getConfiguration().getClassGrouping());
    assertEquals(mockKlass, myStage.getSelectedClass());
    assertNull(myStage.getSelectedInstance());
    myAspectObserver.assertAndResetCounts(0, 0, 0, 0, 0, 1, 0);

    myStage.selectClass(mockKlass);
    assertEquals(mockCapture, myStage.getSelectedCapture());
    assertEquals(mockHeap, myStage.getSelectedHeap());
    assertEquals(ARRANGE_BY_CLASS, myStage.getConfiguration().getClassGrouping());
    assertEquals(mockKlass, myStage.getSelectedClass());
    assertNull(myStage.getSelectedInstance());
    myAspectObserver.assertAndResetCounts(0, 0, 0, 0, 0, 0, 0);

    myStage.selectInstance(mockInstance);
    assertEquals(mockCapture, myStage.getSelectedCapture());
    assertEquals(mockHeap, myStage.getSelectedHeap());
    assertEquals(ARRANGE_BY_CLASS, myStage.getConfiguration().getClassGrouping());
    assertEquals(mockKlass, myStage.getSelectedClass());
    assertEquals(mockInstance, myStage.getSelectedInstance());
    myAspectObserver.assertAndResetCounts(0, 0, 0, 0, 0, 0, 1);

    myStage.selectInstance(mockInstance);
    assertEquals(mockCapture, myStage.getSelectedCapture());
    assertEquals(mockHeap, myStage.getSelectedHeap());
    assertEquals(ARRANGE_BY_CLASS, myStage.getConfiguration().getClassGrouping());
    assertEquals(mockKlass, myStage.getSelectedClass());
    assertEquals(mockInstance, myStage.getSelectedInstance());
    myAspectObserver.assertAndResetCounts(0, 0, 0, 0, 0, 0, 0);

    // Test the reverse direction, to make sure children MemoryObjects are nullified in the selection.
    myStage.selectClass(null);
    assertEquals(mockCapture, myStage.getSelectedCapture());
    assertEquals(mockHeap, myStage.getSelectedHeap());
    assertEquals(ARRANGE_BY_CLASS, myStage.getConfiguration().getClassGrouping());
    assertNull(myStage.getSelectedClass());
    assertNull(myStage.getSelectedInstance());
    myAspectObserver.assertAndResetCounts(0, 0, 0, 0, 0, 1, 1);

    // However, if a selection didn't change (e.g. null => null), it shouldn't trigger an aspect change either.
    myStage.selectHeap(null);
    assertEquals(mockCapture, myStage.getSelectedCapture());
    assertNull(myStage.getSelectedHeap());
    assertEquals(ARRANGE_BY_CLASS, myStage.getConfiguration().getClassGrouping());
    assertNull(myStage.getSelectedClass());
    assertNull(myStage.getSelectedInstance());
    myAspectObserver.assertAndResetCounts(0, 0, 0, 0, 1, 0, 0);
  }

  @Test
  public void testSelectNewCaptureWhileLoading() {
    CaptureObject mockCapture1 = mockCaptureObject("DUMMY_CAPTURE1", 5, 10, Collections.EMPTY_LIST, true);
    CaptureObject mockCapture2 = mockCaptureObject("DUMMY_CAPTURE2", 10, 15, Collections.EMPTY_LIST, true);

    myStage.selectCapture(mockCapture1, null);
    assertEquals(mockCapture1, myStage.getSelectedCapture());
    assertEquals(ProfilerMode.EXPANDED, myStage.getProfilerMode());
    myAspectObserver.assertAndResetCounts(0, 1, 0, 0, 0, 0, 0);

    // Make sure selecting a new capture while the first one is loading will select the new one
    myStage.selectCapture(mockCapture2, null);
    assertEquals(mockCapture2, myStage.getSelectedCapture());
    assertEquals(ProfilerMode.EXPANDED, myStage.getProfilerMode());
    myAspectObserver.assertAndResetCounts(0, 1, 0, 0, 0, 0, 0);

    myMockLoader.runTask();
    assertEquals(mockCapture2, myStage.getSelectedCapture());
    assertEquals(ProfilerMode.EXPANDED, myStage.getProfilerMode());
    myAspectObserver.assertAndResetCounts(0, 0, 1, 0, 0, 0, 0);
  }

  @Test
  public void testCaptureLoadingFailure() {
    CaptureObject mockCapture1 = mockCaptureObject("DUMMY_CAPTURE1", 5, 10, Collections.EMPTY_LIST, false);

    myStage.selectCapture(mockCapture1, null);
    assertEquals(mockCapture1, myStage.getSelectedCapture());
    assertEquals(ProfilerMode.EXPANDED, myStage.getProfilerMode());
    myAspectObserver.assertAndResetCounts(0, 1, 0, 0, 0, 0, 0);

    myMockLoader.runTask();
    assertEquals(null, myStage.getSelectedCapture());
    assertEquals(ProfilerMode.NORMAL, myStage.getProfilerMode());
    myAspectObserver.assertAndResetCounts(0, 1, 0, 0, 0, 0, 0);
  }

  @Test
  public void testAgentStatusUpdatesObjectSeries() {
    // Test that agent status change fires after a process is selected.
    Profiler.Device device = Profiler.Device.newBuilder().setSerial("FakeDevice").setState(Profiler.Device.State.ONLINE).build();
    Profiler.Process process = Profiler.Process.newBuilder()
      .setPid(20)
      .setState(Profiler.Process.State.ALIVE)
      .setName("FakeProcess")
      .build();
    myProfilerService.addDevice(device);
    Common.Session session = Common.Session.newBuilder()
      .setBootId(device.getBootId())
      .setDeviceSerial(device.getSerial())
      .build();
    myProfilerService.addProcess(session, process);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);

    MemoryProfilerStage.MemoryStageLegends legends = myStage.getLegends();
    DetailedMemoryUsage usage = myStage.getDetailedMemoryUsage();
    SeriesLegend objectLegend = legends.getObjectsLegend();
    RangedContinuousSeries objectSeries = usage.getObjectsSeries();
    assertTrue(legends.getLegends().stream().noneMatch(legend -> legend == objectLegend));
    assertTrue(usage.getSeries().stream().noneMatch(series -> series == objectSeries));

    myProfilerService.setAgentStatus(Profiler.AgentStatusResponse.Status.ATTACHED);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertTrue(legends.getLegends().stream().anyMatch(legend -> legend == objectLegend));
    assertTrue(usage.getSeries().stream().anyMatch(series -> series == objectSeries));
  }
}