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

import com.android.sdklib.AndroidVersion;
import com.android.tools.adtui.model.DurationData;
import com.android.tools.adtui.model.FakeTimer;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangedContinuousSeries;
import com.android.tools.adtui.model.legend.SeriesLegend;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.MemoryProfiler;
import com.android.tools.profiler.proto.MemoryProfiler.TrackAllocationsResponse;
import com.android.tools.profiler.proto.Profiler;
import com.android.tools.profilers.FakeGrpcChannel;
import com.android.tools.profilers.FakeProfilerService;
import com.android.tools.profilers.NullMonitorStage;
import com.android.tools.profilers.ProfilerMode;
import com.android.tools.profilers.memory.adapters.*;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;

import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

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
    myAspectObserver.assertAndResetCounts(1, 0, 0, 0, 0, 0, 0, 0);
    myService.setExplicitAllocationsStatus(TrackAllocationsResponse.Status.NOT_ENABLED);
    myStage.trackAllocations(false, null);
    assertEquals(false, myStage.isTrackingAllocations());
    assertEquals(ProfilerMode.NORMAL, myStage.getProfilerMode());
    myAspectObserver.assertAndResetCounts(1, 0, 0, 0, 0, 0, 0, 0);
    myService.setExplicitAllocationsStatus(TrackAllocationsResponse.Status.FAILURE_UNKNOWN);
    myStage.trackAllocations(false, null);
    assertEquals(false, myStage.isTrackingAllocations());
    assertEquals(ProfilerMode.NORMAL, myStage.getProfilerMode());
    myAspectObserver.assertAndResetCounts(1, 0, 0, 0, 0, 0, 0, 0);

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
    myAspectObserver.assertAndResetCounts(1, 0, 0, 0, 0, 0, 0, 0);

    // Attempting to start a in-progress session
    myService.setExplicitAllocationsStatus(TrackAllocationsResponse.Status.IN_PROGRESS);
    myStage.trackAllocations(true, null);
    assertEquals(true, myStage.isTrackingAllocations());
    assertEquals(ProfilerMode.NORMAL, myStage.getProfilerMode());
    myAspectObserver.assertAndResetCounts(1, 0, 0, 0, 0, 0, 0, 0);

    // Spawn a different thread to stop a tracking session
    // This will start loading the CaptureObject but it will loop until the AllocationEventsResponse returns a SUCCESS status.
    final CountDownLatch waitLatch = new CountDownLatch(1);
    new Thread(() -> {
      myService.setExplicitAllocationsStatus(TrackAllocationsResponse.Status.SUCCESS);
      myService.setExplicitAllocationsInfo(MemoryProfiler.AllocationsInfo.Status.COMPLETED,
                                           infoStart, infoEnd, true);
      myService.setExplicitAllocationEvents(MemoryProfiler.LegacyAllocationEventsResponse.Status.NOT_READY, Collections.emptyList());
      myStage.trackAllocations(false, null);
      assertEquals(false, myStage.isTrackingAllocations());
      assertTrue(myStage.getSelectedCapture() instanceof LegacyAllocationCaptureObject);
      LegacyAllocationCaptureObject capture = (LegacyAllocationCaptureObject)myStage.getSelectedCapture();
      assertEquals(infoStart, capture.getStartTimeNs());
      assertEquals(infoEnd, capture.getEndTimeNs());
      assertFalse(capture.isDoneLoading());
      assertFalse(capture.isError());
      assertEquals(ProfilerMode.EXPANDED, myStage.getProfilerMode());
      myAspectObserver.assertAndResetCounts(1, 1, 0, 0, 0, 0, 0, 0);
      waitLatch.countDown();
    }).start();

    try {
      waitLatch.await();
    }
    catch (InterruptedException ignored) {
    }

    // Manually mark the current allocation session as complete, which will trigger the CaptureObject to finish loading
    assertTrue(myStage.getSelectedCapture() instanceof LegacyAllocationCaptureObject);
    LegacyAllocationCaptureObject capture = (LegacyAllocationCaptureObject)myStage.getSelectedCapture();
    myService.setExplicitAllocationEvents(MemoryProfiler.LegacyAllocationEventsResponse.Status.SUCCESS, Collections.emptyList());
    // Run the CaptureObject.load task
    myMockLoader.runTask();
    assertTrue(capture.isDoneLoading());
    assertFalse(capture.isError());
    myAspectObserver.assertAndResetCounts(0, 0, 1, 0, 1, 0, 0, 0);
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
    // TODO need to add a mock heap dump here to test the success path
  }

  @Test
  public void defaultHeapSetTest() throws ExecutionException, InterruptedException {
    myMockLoader.setReturnImmediateFuture(true);

    FakeCaptureObject capture0 = new FakeCaptureObject.Builder().setHeapIdToNameMap(ImmutableMap.of(0, "default", 1, "app")).build();
    FakeInstanceObject instanceObject = new FakeInstanceObject.Builder(capture0, "DUMMY_CLASS1").setHeapId(0).build();
    capture0.addInstanceObjects(ImmutableSet.of(instanceObject));

    myStage.selectCaptureDuration(new CaptureDurationData<>(1, false, false, new CaptureEntry<CaptureObject>(new Object(), () -> capture0)),
                                  new Range(0, 1), null);
    assertEquals(capture0, myStage.getSelectedCapture());
    assertNotNull(myStage.getSelectedHeapSet());
    assertEquals("default", myStage.getSelectedHeapSet().getName());

    FakeCaptureObject capture1 = new FakeCaptureObject.Builder().setHeapIdToNameMap(ImmutableMap.of(0, "default", 1, "app")).build();
    instanceObject = new FakeInstanceObject.Builder(capture1, "DUMMY_CLASS1").setHeapId(1).build();
    capture1.addInstanceObjects(ImmutableSet.of(instanceObject));

    myStage.selectCaptureDuration(new CaptureDurationData<>(1, false, false, new CaptureEntry<CaptureObject>(new Object(), () -> capture1)),
                                  new Range(0, 1), null);
    assertEquals(capture1, myStage.getSelectedCapture());
    assertNotNull(myStage.getSelectedHeapSet());
    assertEquals("app", myStage.getSelectedHeapSet().getName());

    FakeCaptureObject capture2 = new FakeCaptureObject.Builder().setHeapIdToNameMap(ImmutableMap.of(0, "default", 1, "app")).build();
    instanceObject = new FakeInstanceObject.Builder(capture2, "DUMMY_CLASS1").setHeapId(0).build();
    FakeInstanceObject otherInstanceObject = new FakeInstanceObject.Builder(capture2, "DUMMY_CLASS2").setHeapId(1).build();
    capture2.addInstanceObjects(ImmutableSet.of(instanceObject, otherInstanceObject));

    myStage.selectCaptureDuration(new CaptureDurationData<>(1, false, false, new CaptureEntry<CaptureObject>(new Object(), () -> capture2)),
                                  new Range(0, 1), null);
    assertEquals(capture2, myStage.getSelectedCapture());
    assertNotNull(myStage.getSelectedHeapSet());
    assertEquals("app", myStage.getSelectedHeapSet().getName());
  }

  @Test
  public void testMemoryObjectSelection() throws ExecutionException, InterruptedException {
    final String dummyClassName = "DUMMY_CLASS1";
    FakeCaptureObject captureObject = new FakeCaptureObject.Builder().setStartTime(5).setEndTime(10).build();
    InstanceObject mockInstance =
      new FakeInstanceObject.Builder(captureObject, dummyClassName).setName("DUMMY_INSTANCE")
        .setDepth(1).setShallowSize(2).setRetainedSize(3).build();
    captureObject.addInstanceObjects(Collections.singleton(mockInstance));

    Object captureKey = new Object();
    myStage.selectCaptureDuration(new CaptureDurationData<>(1, false, false, new CaptureEntry<CaptureObject>(captureKey, () -> captureObject)),
                                  new Range(0, 1), null);
    assertEquals(captureObject, myStage.getSelectedCapture());
    assertNull(myStage.getSelectedHeapSet());
    assertEquals(ARRANGE_BY_CLASS, myStage.getConfiguration().getClassGrouping());
    assertNull(myStage.getSelectedClassSet());
    assertNull(myStage.getSelectedInstanceObject());
    assertEquals(ProfilerMode.EXPANDED, myStage.getProfilerMode());
    myAspectObserver.assertAndResetCounts(0, 1, 0, 0, 0, 0, 0, 0);
    myMockLoader.runTask();
    assertEquals(ProfilerMode.EXPANDED, myStage.getProfilerMode());
    myAspectObserver.assertAndResetCounts(0, 0, 1, 0, 1, 0, 0, 0);

    // Make sure the same capture selected shouldn't result in aspects getting raised again.
    myStage.selectCaptureDuration(new CaptureDurationData<>(1, false, false, new CaptureEntry<CaptureObject>(captureKey, () -> captureObject)),
                                  new Range(0, 1), null);
    myMockLoader.runTask();
    assertEquals(captureObject, myStage.getSelectedCapture());
    assertNotNull(myStage.getSelectedHeapSet());
    assertEquals(ARRANGE_BY_CLASS, myStage.getConfiguration().getClassGrouping());
    assertNull(myStage.getSelectedClassSet());
    assertNull(myStage.getSelectedInstanceObject());
    myAspectObserver.assertAndResetCounts(0, 0, 0, 0, 0, 0, 0, 0);

    HeapSet heapSet = captureObject.getHeapSet(FakeCaptureObject.DEFAULT_HEAP_ID);
    assertNotNull(heapSet);
    myStage.selectHeapSet(heapSet);
    assertEquals(captureObject, myStage.getSelectedCapture());
    assertEquals(heapSet, myStage.getSelectedHeapSet());
    assertEquals(ARRANGE_BY_CLASS, myStage.getConfiguration().getClassGrouping());
    assertNull(myStage.getSelectedClassSet());
    assertNull(myStage.getSelectedInstanceObject());
    myAspectObserver.assertAndResetCounts(0, 0, 0, 0, 0, 0, 0, 0);

    myStage.selectHeapSet(heapSet);
    assertEquals(captureObject, myStage.getSelectedCapture());
    assertEquals(heapSet, myStage.getSelectedHeapSet());
    assertEquals(ARRANGE_BY_CLASS, myStage.getConfiguration().getClassGrouping());
    assertNull(myStage.getSelectedClassSet());
    assertNull(myStage.getSelectedInstanceObject());
    myAspectObserver.assertAndResetCounts(0, 0, 0, 0, 0, 0, 0, 0);

    assertEquals(ARRANGE_BY_CLASS, myStage.getConfiguration().getClassGrouping());
    myStage.getConfiguration().setClassGrouping(ARRANGE_BY_PACKAGE);
    assertEquals(ARRANGE_BY_PACKAGE, myStage.getConfiguration().getClassGrouping());
    myAspectObserver.assertAndResetCounts(0, 0, 0, 1, 0, 0, 0, 0);

    myStage.getConfiguration().setClassGrouping(ARRANGE_BY_CLASS);
    assertEquals(ARRANGE_BY_CLASS, myStage.getConfiguration().getClassGrouping());
    myAspectObserver.assertAndResetCounts(0, 0, 0, 1, 0, 0, 0, 0);

    ClassifierSet classifierSet = heapSet.findContainingClassifierSet(mockInstance);
    assertTrue(classifierSet instanceof ClassSet);
    ClassSet classSet = (ClassSet)classifierSet;
    myStage.selectClassSet(classSet);
    assertEquals(captureObject, myStage.getSelectedCapture());
    assertEquals(heapSet, myStage.getSelectedHeapSet());
    assertEquals(ARRANGE_BY_CLASS, myStage.getConfiguration().getClassGrouping());
    assertEquals(classSet, myStage.getSelectedClassSet());
    assertNull(myStage.getSelectedInstanceObject());
    myAspectObserver.assertAndResetCounts(0, 0, 0, 0, 0, 1, 0, 0);

    myStage.selectClassSet(classSet);
    assertEquals(captureObject, myStage.getSelectedCapture());
    assertEquals(heapSet, myStage.getSelectedHeapSet());
    assertEquals(ARRANGE_BY_CLASS, myStage.getConfiguration().getClassGrouping());
    assertEquals(classSet, myStage.getSelectedClassSet());
    assertNull(myStage.getSelectedInstanceObject());
    myAspectObserver.assertAndResetCounts(0, 0, 0, 0, 0, 0, 0, 0);

    myStage.selectInstanceObject(mockInstance);
    assertEquals(captureObject, myStage.getSelectedCapture());
    assertEquals(heapSet, myStage.getSelectedHeapSet());
    assertEquals(ARRANGE_BY_CLASS, myStage.getConfiguration().getClassGrouping());
    assertEquals(classSet, myStage.getSelectedClassSet());
    assertEquals(mockInstance, myStage.getSelectedInstanceObject());
    myAspectObserver.assertAndResetCounts(0, 0, 0, 0, 0, 0, 1, 0);

    myStage.selectInstanceObject(mockInstance);
    assertEquals(captureObject, myStage.getSelectedCapture());
    assertEquals(heapSet, myStage.getSelectedHeapSet());
    assertEquals(ARRANGE_BY_CLASS, myStage.getConfiguration().getClassGrouping());
    assertEquals(classSet, myStage.getSelectedClassSet());
    assertEquals(mockInstance, myStage.getSelectedInstanceObject());
    myAspectObserver.assertAndResetCounts(0, 0, 0, 0, 0, 0, 0, 0);

    // Test the reverse direction, to make sure children MemoryObjects are nullified in the selection.
    myStage.selectClassSet(null);
    assertEquals(captureObject, myStage.getSelectedCapture());
    assertEquals(heapSet, myStage.getSelectedHeapSet());
    assertEquals(ARRANGE_BY_CLASS, myStage.getConfiguration().getClassGrouping());
    assertNull(myStage.getSelectedClassSet());
    assertNull(myStage.getSelectedInstanceObject());
    myAspectObserver.assertAndResetCounts(0, 0, 0, 0, 0, 1, 1, 0);

    // However, if a selection didn't change (e.g. null => null), it shouldn't trigger an aspect change either.
    myStage.selectHeapSet(null);
    assertEquals(captureObject, myStage.getSelectedCapture());
    assertNull(myStage.getSelectedHeapSet());
    assertEquals(ARRANGE_BY_CLASS, myStage.getConfiguration().getClassGrouping());
    assertNull(myStage.getSelectedClassSet());
    assertNull(myStage.getSelectedInstanceObject());
    myAspectObserver.assertAndResetCounts(0, 0, 0, 0, 1, 0, 0, 0);
  }

  @Test
  public void testSelectNewCaptureWhileLoading() throws ExecutionException, InterruptedException {
    CaptureObject mockCapture1 = new FakeCaptureObject.Builder().setCaptureName("DUMMY_CAPTURE1").setStartTime(5).setEndTime(10).build();
    CaptureObject mockCapture2 = new FakeCaptureObject.Builder().setCaptureName("DUMMY_CAPTURE2").setStartTime(10).setEndTime(15).build();

    myStage.selectCaptureDuration(new CaptureDurationData<>(1, false, false, new CaptureEntry<CaptureObject>(new Object(), () -> mockCapture1)),
                                  new Range(0, 1), null);
    assertEquals(mockCapture1, myStage.getSelectedCapture());
    assertEquals(ProfilerMode.EXPANDED, myStage.getProfilerMode());
    myAspectObserver.assertAndResetCounts(0, 1, 0, 0, 0, 0, 0, 0);

    // Make sure selecting a new capture while the first one is loading will select the new one
    myStage.selectCaptureDuration(new CaptureDurationData<>(1, false, false, new CaptureEntry<CaptureObject>(new Object(), () -> mockCapture2)),
                                  new Range(0, 1), null);
    assertEquals(mockCapture2, myStage.getSelectedCapture());
    assertEquals(ProfilerMode.EXPANDED, myStage.getProfilerMode());
    myAspectObserver.assertAndResetCounts(0, 1, 0, 0, 0, 0, 0, 0);

    myMockLoader.runTask();
    assertEquals(mockCapture2, myStage.getSelectedCapture());
    assertEquals(ProfilerMode.EXPANDED, myStage.getProfilerMode());
    myAspectObserver.assertAndResetCounts(0, 0, 1, 0, 0, 0, 0, 0);
  }

  @Test
  public void testCaptureLoadingFailure() throws ExecutionException, InterruptedException {
    CaptureObject mockCapture1 =
      new FakeCaptureObject.Builder().setCaptureName("DUMMY_CAPTURE1").setStartTime(5).setEndTime(10).setError(true).build();

    myStage.selectCaptureDuration(new CaptureDurationData<>(1, false, false, new CaptureEntry<CaptureObject>(new Object(), () -> mockCapture1)),
                                  new Range(0, 1), null);
    assertEquals(mockCapture1, myStage.getSelectedCapture());
    assertEquals(ProfilerMode.EXPANDED, myStage.getProfilerMode());
    myAspectObserver.assertAndResetCounts(0, 1, 0, 0, 0, 0, 0, 0);

    myMockLoader.runTask();
    assertEquals(null, myStage.getSelectedCapture());
    assertEquals(ProfilerMode.NORMAL, myStage.getProfilerMode());
    myAspectObserver.assertAndResetCounts(0, 1, 1, 0, 0, 0, 0, 0);
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

  @Test
  public void testTooltipLegends() {
    long time = TimeUnit.MICROSECONDS.toNanos(2);
    MemoryProfiler.MemoryData memoryData = MemoryProfiler.MemoryData.newBuilder()
      .setEndTimestamp(time)
      .addMemSamples(MemoryProfiler.MemoryData.MemorySample.newBuilder()
                       .setTimestamp(time)
                       .setJavaMem(10)
                       .setNativeMem(20)
                       .setGraphicsMem(30)
                       .setStackMem(40)
                       .setCodeMem(50)
                       .setOthersMem(60)).build();
    myService.setMemoryData(memoryData);
    MemoryProfilerStage.MemoryStageLegends legends = myStage.getTooltipLegends();
    myStage.getStudioProfilers().getTimeline().getTooltipRange().set(time, time);
    assertEquals("Java", legends.getJavaLegend().getName());
    assertEquals("10KB", legends.getJavaLegend().getValue());

    assertEquals("Native", legends.getNativeLegend().getName());
    assertEquals("20KB", legends.getNativeLegend().getValue());

    assertEquals("Graphics", legends.getGraphicsLegend().getName());
    assertEquals("30KB", legends.getGraphicsLegend().getValue());

    assertEquals("Stack", legends.getStackLegend().getName());
    assertEquals("40KB", legends.getStackLegend().getValue());

    assertEquals("Code", legends.getCodeLegend().getName());
    assertEquals("50KB", legends.getCodeLegend().getValue());

    assertEquals("Others", legends.getOtherLegend().getName());
    assertEquals("60KB", legends.getOtherLegend().getValue());
  }

  @Test
  public void testLiveAllocationTrackingOnEnterExit() {
    Profiler.Device device = Profiler.Device.newBuilder().setSerial("FakeDevice").setFeatureLevel(AndroidVersion.VersionCodes.O)
      .setState(Profiler.Device.State.ONLINE).build();
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

    // Test that if the the live allocation tracking feature is on and the device is O+, allocation tracking is started on enter stage.
    myIdeProfilerServices.enableLiveAllocationTracking(true);
    myService.setExplicitAllocationsStatus(TrackAllocationsResponse.Status.SUCCESS);
    myProfilers.setStage(myStage);
    assertTrue(myStage.isTrackingAllocations());

    // Test that stage exit would disable the tracking session
    myProfilers.setStage(new NullMonitorStage(myProfilers));
    assertFalse(myStage.isTrackingAllocations());

    // Test that if the device is < O, allocation tracking would not start.
    device = device.toBuilder().setFeatureLevel(AndroidVersion.VersionCodes.N).build();
    myProfilerService.addDevice(device);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    myProfilers.setStage(myStage);
    assertFalse(myStage.isTrackingAllocations());
  }
}