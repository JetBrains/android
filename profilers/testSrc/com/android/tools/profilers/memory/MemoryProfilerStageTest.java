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

import com.android.tools.adtui.model.FakeTimer;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangedContinuousSeries;
import com.android.tools.adtui.model.legend.SeriesLegend;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.MemoryProfiler.*;
import com.android.tools.profiler.proto.Profiler.AgentStatusResponse;
import com.android.tools.profilers.FakeGrpcChannel;
import com.android.tools.profilers.FakeProfilerService;
import com.android.tools.profilers.ProfilerMode;
import com.android.tools.profilers.cpu.FakeCpuService;
import com.android.tools.profilers.event.FakeEventService;
import com.android.tools.profilers.memory.adapters.*;
import com.android.tools.profilers.network.FakeNetworkService;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.MoreExecutors;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.android.tools.profilers.FakeProfilerService.FAKE_DEVICE_ID;
import static com.android.tools.profilers.memory.MemoryProfilerConfiguration.ClassGrouping.ARRANGE_BY_CLASS;
import static com.android.tools.profilers.memory.MemoryProfilerConfiguration.ClassGrouping.ARRANGE_BY_PACKAGE;
import static com.google.common.truth.Truth.assertThat;

public class MemoryProfilerStageTest extends MemoryProfilerTestBase {
  @NotNull private final FakeMemoryService myService = new FakeMemoryService();
  @NotNull private final FakeProfilerService myProfilerService = new FakeProfilerService();

  @Rule
  public FakeGrpcChannel myGrpcChannel =
    new FakeGrpcChannel("MemoryProfilerStageTestChannel", myService, myProfilerService,
                        new FakeCpuService(), new FakeEventService(), FakeNetworkService.newBuilder().build());

  @Override
  protected FakeGrpcChannel getGrpcChannel() {
    return myGrpcChannel;
  }

  @Test
  public void testToggleAllocationTrackingFailedStatuses() throws Exception {
    myStage.trackAllocations(false);
    assertThat(myStage.isTrackingAllocations()).isEqualTo(false);
    assertThat(myStage.getProfilerMode()).isEqualTo(ProfilerMode.NORMAL);
    myAspectObserver.assertAndResetCounts(1, 0, 0, 0, 0, 0, 0, 0);

    myService.setExplicitAllocationsStatus(TrackAllocationsResponse.Status.NOT_ENABLED);
    myStage.trackAllocations(false);
    assertThat(myStage.isTrackingAllocations()).isEqualTo(false);
    assertThat(myStage.getProfilerMode()).isEqualTo(ProfilerMode.NORMAL);
    myAspectObserver.assertAndResetCounts(1, 0, 0, 0, 0, 0, 0, 0);

    myService.setExplicitAllocationsStatus(TrackAllocationsResponse.Status.FAILURE_UNKNOWN);
    myStage.trackAllocations(false);
    assertThat(myStage.isTrackingAllocations()).isEqualTo(false);
    assertThat(myStage.getProfilerMode()).isEqualTo(ProfilerMode.NORMAL);
    myAspectObserver.assertAndResetCounts(1, 0, 0, 0, 0, 0, 0, 0);
  }

  @Test
  public void testToggleAllocationTracking() throws Exception {
    // Enable the auto capture selection mechanism.
    myStage.enableSelectLatestCapture(true, MoreExecutors.directExecutor());

    // Starting a tracking session
    long infoStart = TimeUnit.MICROSECONDS.toNanos(5);
    long infoEnd = TimeUnit.MICROSECONDS.toNanos(10);
    myService.setExplicitAllocationsStatus(TrackAllocationsResponse.Status.SUCCESS);
    myService.setExplicitAllocationsInfo(AllocationsInfo.Status.IN_PROGRESS, infoStart, Long.MAX_VALUE, true);
    myStage.trackAllocations(true);
    assertThat(myStage.isTrackingAllocations()).isEqualTo(true);
    assertThat(myStage.getSelectedCapture()).isEqualTo(null);
    assertThat(myStage.getProfilerMode()).isEqualTo(ProfilerMode.NORMAL);
    myAspectObserver.assertAndResetCounts(1, 0, 0, 0, 0, 0, 0, 0);

    // Attempting to start a in-progress session
    myService.setExplicitAllocationsStatus(TrackAllocationsResponse.Status.IN_PROGRESS);
    myStage.trackAllocations(true);
    assertThat(myStage.isTrackingAllocations()).isEqualTo(true);
    assertThat(myStage.getSelectedCapture()).isEqualTo(null);
    assertThat(myStage.getProfilerMode()).isEqualTo(ProfilerMode.NORMAL);
    myAspectObserver.assertAndResetCounts(1, 0, 0, 0, 0, 0, 0, 0);

    // Stops the tracking session.
    myService.setExplicitAllocationsStatus(TrackAllocationsResponse.Status.SUCCESS);
    myService.setExplicitAllocationsInfo(AllocationsInfo.Status.COMPLETED,
                                         infoStart, infoEnd, true);
    myStage.trackAllocations(false);
    assertThat(myStage.isTrackingAllocations()).isEqualTo(false);
    assertThat(myStage.getSelectedCapture()).isEqualTo(null);
    assertThat(myStage.getProfilerMode()).isEqualTo(ProfilerMode.NORMAL);
    myAspectObserver.assertAndResetCounts(1, 0, 0, 0, 0, 0, 0, 0);

    // Prepares the AllocationsInfo with the correct start time in the FakeMemoryService.
    myService.setMemoryData(MemoryData.newBuilder().addAllocationsInfo(
      AllocationsInfo.newBuilder().setStartTime(infoStart).setEndTime(infoEnd).setLegacy(true).setStatus(
        AllocationsInfo.Status.COMPLETED).build()).build());
    myService.setExplicitAllocationEvents(LegacyAllocationEventsResponse.Status.SUCCESS, Collections.emptyList());

    // The timeline has reset at this point so we need to advance the current time so data range is advanced during the next update.
    myTimer.setCurrentTimeNs(FakeTimer.ONE_SECOND_IN_NS);
    // Advancing time (data range) should trigger MemoryProfilerStage to select the capture.
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(myStage.getSelectedCapture()).isInstanceOf(LegacyAllocationCaptureObject.class);
    LegacyAllocationCaptureObject capture = (LegacyAllocationCaptureObject)myStage.getSelectedCapture();
    assertThat(capture.isDoneLoading()).isFalse();
    assertThat(capture.isError()).isFalse();
    myAspectObserver.assertAndResetCounts(0, 1, 0, 0, 0, 0, 0, 0);
    assertThat(myStage.getProfilerMode()).isEqualTo(ProfilerMode.EXPANDED);

    // Finish the load task.
    myMockLoader.runTask();
    assertThat(myStage.getSelectedCapture()).isEqualTo(capture);
    assertThat(capture.isDoneLoading()).isTrue();
    assertThat(capture.isError()).isFalse();
    myAspectObserver.assertAndResetCounts(0, 0, 1, 0, 1, 0, 0, 0);
    assertThat(myStage.getProfilerMode()).isEqualTo(ProfilerMode.EXPANDED);
  }

  @Test
  public void testAllocationTrackingSetStreaming() throws Exception {
    myProfilers.getTimeline().setStreaming(false);
    assertThat(myProfilers.getTimeline().isStreaming()).isFalse();

    // Stopping tracking should not cause streaming.
    myService.setExplicitAllocationsStatus(TrackAllocationsResponse.Status.NOT_ENABLED);
    myStage.trackAllocations(false);
    assertThat(myProfilers.getTimeline().isStreaming()).isFalse();

    long infoStart = TimeUnit.MICROSECONDS.toNanos(5);
    myService.setExplicitAllocationsStatus(TrackAllocationsResponse.Status.SUCCESS);
    myService.setExplicitAllocationsInfo(AllocationsInfo.Status.IN_PROGRESS, infoStart, Long.MAX_VALUE, true);
    myStage.trackAllocations(true);
    assertThat(myProfilers.getTimeline().isStreaming()).isTrue();
  }

  @Test
  public void testAllocationTrackingStateOnTransition() throws Exception {
    myStage.enter();
    assertThat(myStage.isTrackingAllocations()).isFalse();

    long infoStart = TimeUnit.MICROSECONDS.toNanos(5);
    long infoEnd = TimeUnit.MICROSECONDS.toNanos(10);
    AllocationsInfo startInfo = AllocationsInfo.newBuilder().setStartTime(infoStart).setEndTime(Long.MAX_VALUE).setLegacy(true).setStatus(
      AllocationsInfo.Status.COMPLETED).build();
    AllocationsInfo endInfo = AllocationsInfo.newBuilder().setStartTime(infoStart).setEndTime(infoEnd).setLegacy(true).setStatus(
      AllocationsInfo.Status.COMPLETED).build();
    myService.setExplicitAllocationsStatus(TrackAllocationsResponse.Status.SUCCESS);
    myService.setExplicitAllocationsInfo(AllocationsInfo.Status.IN_PROGRESS, infoStart, Long.MAX_VALUE, true);
    myService.setMemoryData(MemoryData.newBuilder().addAllocationsInfo(startInfo).build());
    myStage.trackAllocations(true);

    assertThat(myStage.isTrackingAllocations()).isTrue();
    myStage.exit();
    myStage.enter();
    assertThat(myStage.isTrackingAllocations()).isTrue();

    myService.setExplicitAllocationsStatus(TrackAllocationsResponse.Status.SUCCESS);
    myService.setExplicitAllocationsInfo(AllocationsInfo.Status.COMPLETED, infoStart, infoEnd, true);
    myService.setMemoryData(MemoryData.newBuilder().addAllocationsInfo(startInfo).addAllocationsInfo(endInfo).build());
    myStage.trackAllocations(false);

    assertThat(myStage.isTrackingAllocations()).isFalse();
    myStage.exit();
    myStage.enter();
    assertThat(myStage.isTrackingAllocations()).isFalse();
  }

  @Test
  public void testRequestHeapDump() throws Exception {
    // Bypass the load mechanism in HeapDumpCaptureObject.
    myMockLoader.setReturnImmediateFuture(true);
    // Test the no-action cases
    myService.setExplicitHeapDumpStatus(TriggerHeapDumpResponse.Status.FAILURE_UNKNOWN);
    myStage.requestHeapDump();
    assertThat(myStage.getSelectedCapture()).isNull();
    assertThat(myStage.getProfilerMode()).isEqualTo(ProfilerMode.NORMAL);
    myService.setExplicitHeapDumpStatus(TriggerHeapDumpResponse.Status.IN_PROGRESS);
    myStage.requestHeapDump();
    assertThat(myStage.getSelectedCapture()).isNull();
    assertThat(myStage.getProfilerMode()).isEqualTo(ProfilerMode.NORMAL);
    myService.setExplicitHeapDumpStatus(TriggerHeapDumpResponse.Status.UNSPECIFIED);
    myStage.requestHeapDump();
    assertThat(myStage.getSelectedCapture()).isNull();
    assertThat(myStage.getProfilerMode()).isEqualTo(ProfilerMode.NORMAL);

    myService.setExplicitHeapDumpStatus(TriggerHeapDumpResponse.Status.SUCCESS);
    myService.setExplicitHeapDumpInfo(5, 10);
    myStage.requestHeapDump();

    // TODO need to add a mock heap dump here to test the success path
  }

  @Test
  public void testHeapDumpSetStreaming() throws Exception {
    myProfilers.getTimeline().setStreaming(false);
    assertThat(myProfilers.getTimeline().isStreaming()).isFalse();
    myMockLoader.setReturnImmediateFuture(true);
    myStage.requestHeapDump();
    assertThat(myProfilers.getTimeline().isStreaming()).isTrue();
  }

  @Test
  public void defaultHeapSetTest() throws ExecutionException, InterruptedException {
    myMockLoader.setReturnImmediateFuture(true);

    FakeCaptureObject capture0 = new FakeCaptureObject.Builder().setHeapIdToNameMap(ImmutableMap.of(0, "default", 1, "app")).build();
    FakeInstanceObject instanceObject = new FakeInstanceObject.Builder(capture0, "DUMMY_CLASS1").setHeapId(0).build();
    capture0.addInstanceObjects(ImmutableSet.of(instanceObject));

    myStage.selectCaptureDuration(new CaptureDurationData<>(1, false, false, new CaptureEntry<CaptureObject>(new Object(), () -> capture0)),
                                  null);
    assertThat(myStage.getSelectedCapture()).isEqualTo(capture0);
    assertThat(myStage.getSelectedHeapSet()).isNotNull();
    assertThat(myStage.getSelectedHeapSet().getName()).isEqualTo("default");

    FakeCaptureObject capture1 = new FakeCaptureObject.Builder().setHeapIdToNameMap(ImmutableMap.of(0, "default", 1, "app")).build();
    instanceObject = new FakeInstanceObject.Builder(capture1, "DUMMY_CLASS1").setHeapId(1).build();
    capture1.addInstanceObjects(ImmutableSet.of(instanceObject));

    myStage.selectCaptureDuration(new CaptureDurationData<>(1, false, false, new CaptureEntry<CaptureObject>(new Object(), () -> capture1)),
                                  null);
    assertThat(myStage.getSelectedCapture()).isEqualTo(capture1);
    assertThat(myStage.getSelectedHeapSet()).isNotNull();
    assertThat(myStage.getSelectedHeapSet().getName()).isEqualTo("app");

    FakeCaptureObject capture2 = new FakeCaptureObject.Builder().setHeapIdToNameMap(ImmutableMap.of(0, "default", 1, "app")).build();
    instanceObject = new FakeInstanceObject.Builder(capture2, "DUMMY_CLASS1").setHeapId(0).build();
    FakeInstanceObject otherInstanceObject = new FakeInstanceObject.Builder(capture2, "DUMMY_CLASS2").setHeapId(1).build();
    capture2.addInstanceObjects(ImmutableSet.of(instanceObject, otherInstanceObject));

    myStage.selectCaptureDuration(new CaptureDurationData<>(1, false, false, new CaptureEntry<CaptureObject>(new Object(), () -> capture2)),
                                  null);
    assertThat(myStage.getSelectedCapture()).isEqualTo(capture2);
    assertThat(myStage.getSelectedHeapSet()).isNotNull();
    assertThat(myStage.getSelectedHeapSet().getName()).isEqualTo("app");
  }

  @Test
  public void testSelectionRangeUpdateOnCaptureSelection() throws Exception {
    long startTimeUs = 5;
    long endTimeUs = 10;
    FakeCaptureObject captureObject = new FakeCaptureObject.Builder().setStartTime(TimeUnit.MICROSECONDS.toNanos(startTimeUs))
      .setEndTime(TimeUnit.MICROSECONDS.toNanos(endTimeUs)).build();

    Range selectionRange = myStage.getStudioProfilers().getTimeline().getSelectionRange();
    Object captureKey = new Object();
    myStage
      .selectCaptureDuration(new CaptureDurationData<>(1, false, false, new CaptureEntry<CaptureObject>(captureKey, () -> captureObject)),
                             null);
    assertThat(myStage.getSelectedCapture()).isEqualTo(captureObject);
    assertThat((long)selectionRange.getMin()).isEqualTo(startTimeUs);
    assertThat((long)selectionRange.getMax()).isEqualTo(endTimeUs);
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
    myStage
      .selectCaptureDuration(new CaptureDurationData<>(1, false, false, new CaptureEntry<CaptureObject>(captureKey, () -> captureObject)),
                             null);
    assertThat(myStage.getSelectedCapture()).isEqualTo(captureObject);
    assertThat(myStage.getSelectedHeapSet()).isNull();
    assertThat(myStage.getConfiguration().getClassGrouping()).isEqualTo(ARRANGE_BY_CLASS);
    assertThat(myStage.getSelectedClassSet()).isNull();
    assertThat(myStage.getSelectedInstanceObject()).isNull();
    assertThat(myStage.getProfilerMode()).isEqualTo(ProfilerMode.EXPANDED);
    myAspectObserver.assertAndResetCounts(0, 1, 0, 0, 0, 0, 0, 0);
    myMockLoader.runTask();
    assertThat(myStage.getProfilerMode()).isEqualTo(ProfilerMode.EXPANDED);
    myAspectObserver.assertAndResetCounts(0, 0, 1, 0, 1, 0, 0, 0);

    // Make sure the same capture selected shouldn't result in aspects getting raised again.
    myStage
      .selectCaptureDuration(new CaptureDurationData<>(1, false, false, new CaptureEntry<CaptureObject>(captureKey, () -> captureObject)),
                             null);
    myMockLoader.runTask();
    assertThat(myStage.getSelectedCapture()).isEqualTo(captureObject);
    assertThat(myStage.getSelectedHeapSet()).isNotNull();
    assertThat(myStage.getConfiguration().getClassGrouping()).isEqualTo(ARRANGE_BY_CLASS);
    assertThat(myStage.getSelectedClassSet()).isNull();
    assertThat(myStage.getSelectedInstanceObject()).isNull();
    myAspectObserver.assertAndResetCounts(0, 0, 0, 0, 0, 0, 0, 0);

    HeapSet heapSet = captureObject.getHeapSet(FakeCaptureObject.DEFAULT_HEAP_ID);
    assertThat(heapSet).isNotNull();
    myStage.selectHeapSet(heapSet);
    assertThat(myStage.getSelectedCapture()).isEqualTo(captureObject);
    assertThat(myStage.getSelectedHeapSet()).isEqualTo(heapSet);
    assertThat(myStage.getConfiguration().getClassGrouping()).isEqualTo(ARRANGE_BY_CLASS);
    assertThat(myStage.getSelectedClassSet()).isNull();
    assertThat(myStage.getSelectedInstanceObject()).isNull();
    myAspectObserver.assertAndResetCounts(0, 0, 0, 0, 0, 0, 0, 0);

    myStage.selectHeapSet(heapSet);
    assertThat(myStage.getSelectedCapture()).isEqualTo(captureObject);
    assertThat(myStage.getSelectedHeapSet()).isEqualTo(heapSet);
    assertThat(myStage.getConfiguration().getClassGrouping()).isEqualTo(ARRANGE_BY_CLASS);
    assertThat(myStage.getSelectedClassSet()).isNull();
    assertThat(myStage.getSelectedInstanceObject()).isNull();
    myAspectObserver.assertAndResetCounts(0, 0, 0, 0, 0, 0, 0, 0);

    Pattern pattern = Pattern.compile("Filter");
    myStage.selectCaptureFilter(pattern);
    assertThat(myStage.getSelectedCapture()).isEqualTo(captureObject);
    assertThat(myStage.getSelectedHeapSet()).isEqualTo(heapSet);
    assertThat(myStage.getConfiguration().getClassGrouping()).isEqualTo(ARRANGE_BY_CLASS);
    assertThat(myStage.getSelectedClassSet()).isNull();
    assertThat(myStage.getSelectedInstanceObject()).isNull();
    assertThat(myStage.getCaptureFilter()).isEqualTo(pattern);
    myAspectObserver.assertAndResetCounts(0, 0, 0, 0, 0, 0, 0, 0);

    assertThat(myStage.getConfiguration().getClassGrouping()).isEqualTo(ARRANGE_BY_CLASS);
    myStage.getConfiguration().setClassGrouping(ARRANGE_BY_PACKAGE);
    // Retain Filter after Grouping change
    assertThat(myStage.getCaptureFilter()).isEqualTo(pattern);
    // Reset Filter
    myStage.selectCaptureFilter(null);
    assertThat(myStage.getConfiguration().getClassGrouping()).isEqualTo(ARRANGE_BY_PACKAGE);
    myAspectObserver.assertAndResetCounts(0, 0, 0, 1, 0, 0, 0, 0);

    myStage.getConfiguration().setClassGrouping(ARRANGE_BY_CLASS);
    assertThat(myStage.getConfiguration().getClassGrouping()).isEqualTo(ARRANGE_BY_CLASS);
    myAspectObserver.assertAndResetCounts(0, 0, 0, 1, 0, 0, 0, 0);

    ClassifierSet classifierSet = heapSet.findContainingClassifierSet(mockInstance);
    assertThat(classifierSet).isInstanceOf(ClassSet.class);
    ClassSet classSet = (ClassSet)classifierSet;
    myStage.selectClassSet(classSet);
    assertThat(myStage.getSelectedCapture()).isEqualTo(captureObject);
    assertThat(myStage.getSelectedHeapSet()).isEqualTo(heapSet);
    assertThat(myStage.getConfiguration().getClassGrouping()).isEqualTo(ARRANGE_BY_CLASS);
    assertThat(myStage.getSelectedClassSet()).isEqualTo(classSet);
    assertThat(myStage.getSelectedInstanceObject()).isNull();
    myAspectObserver.assertAndResetCounts(0, 0, 0, 0, 0, 1, 0, 0);

    myStage.selectClassSet(classSet);
    assertThat(myStage.getSelectedCapture()).isEqualTo(captureObject);
    assertThat(myStage.getSelectedHeapSet()).isEqualTo(heapSet);
    assertThat(myStage.getConfiguration().getClassGrouping()).isEqualTo(ARRANGE_BY_CLASS);
    assertThat(myStage.getSelectedClassSet()).isEqualTo(classSet);
    assertThat(myStage.getSelectedInstanceObject()).isNull();
    myAspectObserver.assertAndResetCounts(0, 0, 0, 0, 0, 0, 0, 0);

    myStage.selectInstanceObject(mockInstance);
    assertThat(myStage.getSelectedCapture()).isEqualTo(captureObject);
    assertThat(myStage.getSelectedHeapSet()).isEqualTo(heapSet);
    assertThat(myStage.getConfiguration().getClassGrouping()).isEqualTo(ARRANGE_BY_CLASS);
    assertThat(myStage.getSelectedClassSet()).isEqualTo(classSet);
    assertThat(myStage.getSelectedInstanceObject()).isEqualTo(mockInstance);
    myAspectObserver.assertAndResetCounts(0, 0, 0, 0, 0, 0, 1, 0);

    myStage.selectInstanceObject(mockInstance);
    assertThat(myStage.getSelectedCapture()).isEqualTo(captureObject);
    assertThat(myStage.getSelectedHeapSet()).isEqualTo(heapSet);
    assertThat(myStage.getConfiguration().getClassGrouping()).isEqualTo(ARRANGE_BY_CLASS);
    assertThat(myStage.getSelectedClassSet()).isEqualTo(classSet);
    assertThat(myStage.getSelectedInstanceObject()).isEqualTo(mockInstance);
    myAspectObserver.assertAndResetCounts(0, 0, 0, 0, 0, 0, 0, 0);

    // Test the reverse direction, to make sure children MemoryObjects are nullified in the selection.
    myStage.selectClassSet(null);
    assertThat(myStage.getSelectedCapture()).isEqualTo(captureObject);
    assertThat(myStage.getSelectedHeapSet()).isEqualTo(heapSet);
    assertThat(myStage.getConfiguration().getClassGrouping()).isEqualTo(ARRANGE_BY_CLASS);
    assertThat(myStage.getSelectedClassSet()).isNull();
    assertThat(myStage.getSelectedInstanceObject()).isNull();
    myAspectObserver.assertAndResetCounts(0, 0, 0, 0, 0, 1, 1, 0);

    // However, if a selection didn't change (e.g. null => null), it shouldn't trigger an aspect change either.
    myStage.selectHeapSet(null);
    assertThat(myStage.getSelectedCapture()).isEqualTo(captureObject);
    assertThat(myStage.getSelectedHeapSet()).isNull();
    assertThat(myStage.getConfiguration().getClassGrouping()).isEqualTo(ARRANGE_BY_CLASS);
    assertThat(myStage.getSelectedClassSet()).isNull();
    assertThat(myStage.getSelectedInstanceObject()).isNull();
    myAspectObserver.assertAndResetCounts(0, 0, 0, 0, 1, 0, 0, 0);
  }

  @Test
  public void testSelectNewCaptureWhileLoading() throws ExecutionException, InterruptedException {
    CaptureObject mockCapture1 = new FakeCaptureObject.Builder().setCaptureName("DUMMY_CAPTURE1").setStartTime(5).setEndTime(10).build();
    CaptureObject mockCapture2 = new FakeCaptureObject.Builder().setCaptureName("DUMMY_CAPTURE2").setStartTime(10).setEndTime(15).build();

    myStage
      .selectCaptureDuration(new CaptureDurationData<>(1, false, false, new CaptureEntry<CaptureObject>(new Object(), () -> mockCapture1)),
                             null);
    assertThat(myStage.getSelectedCapture()).isEqualTo(mockCapture1);
    assertThat(myStage.getProfilerMode()).isEqualTo(ProfilerMode.EXPANDED);
    myAspectObserver.assertAndResetCounts(0, 1, 0, 0, 0, 0, 0, 0);

    // Make sure selecting a new capture while the first one is loading will select the new one
    myStage
      .selectCaptureDuration(new CaptureDurationData<>(1, false, false, new CaptureEntry<CaptureObject>(new Object(), () -> mockCapture2)),
                             null);
    assertThat(myStage.getSelectedCapture()).isEqualTo(mockCapture2);
    assertThat(myStage.getProfilerMode()).isEqualTo(ProfilerMode.EXPANDED);
    myAspectObserver.assertAndResetCounts(0, 1, 0, 0, 0, 0, 0, 0);

    myMockLoader.runTask();
    assertThat(myStage.getSelectedCapture()).isEqualTo(mockCapture2);
    assertThat(myStage.getProfilerMode()).isEqualTo(ProfilerMode.EXPANDED);
    myAspectObserver.assertAndResetCounts(0, 0, 1, 0, 0, 0, 0, 0);
  }

  @Test
  public void testCaptureLoadingFailure() throws ExecutionException, InterruptedException {
    long startTimeUs = 5;
    long endTimeUs = 10;
    CaptureObject mockCapture1 = new FakeCaptureObject.Builder()
      .setCaptureName("DUMMY_CAPTURE1")
      .setStartTime(TimeUnit.MICROSECONDS.toNanos(startTimeUs))
      .setEndTime(TimeUnit.MICROSECONDS.toNanos(endTimeUs))
      .setError(true)
      .build();
    Range selectionRange = myStage.getStudioProfilers().getTimeline().getSelectionRange();

    myStage
      .selectCaptureDuration(new CaptureDurationData<>(1, false, false, new CaptureEntry<CaptureObject>(new Object(), () -> mockCapture1)),
                             null);
    assertThat(myStage.getSelectedCapture()).isEqualTo(mockCapture1);

    assertThat((long)selectionRange.getMin()).isEqualTo(startTimeUs);
    assertThat((long)selectionRange.getMax()).isEqualTo(endTimeUs);
    assertThat(myStage.getProfilerMode()).isEqualTo(ProfilerMode.EXPANDED);
    myAspectObserver.assertAndResetCounts(0, 1, 0, 0, 0, 0, 0, 0);

    myMockLoader.runTask();
    assertThat(myStage.getSelectedCapture()).isEqualTo(null);
    assertThat(selectionRange.isEmpty()).isTrue();
    assertThat(myStage.getProfilerMode()).isEqualTo(ProfilerMode.NORMAL);
    myAspectObserver.assertAndResetCounts(0, 1, 1, 0, 0, 0, 0, 0);
  }

  @Test
  public void testAgentStatusUpdatesObjectSeries() {
    // Test that agent status change fires after a process is selected.
    Common.Device device = Common.Device.newBuilder()
      .setDeviceId(FAKE_DEVICE_ID)
      .setSerial("FakeDevice")
      .setState(Common.Device.State.ONLINE)
      .build();
    Common.Process process = Common.Process.newBuilder()
      .setPid(20)
      .setDeviceId(FAKE_DEVICE_ID)
      .setState(Common.Process.State.ALIVE)
      .setName("FakeProcess")
      .build();
    myProfilerService.addDevice(device);
    myProfilerService.addProcess(device, process);

    MemoryData memoryData = MemoryData.newBuilder()
      .setEndTimestamp(FakeTimer.ONE_SECOND_IN_NS)
      .addAllocStatsSamples(
        MemoryData.AllocStatsSample.newBuilder().setTimestamp(FakeTimer.ONE_SECOND_IN_NS).setJavaAllocationCount(5).build()
      ).build();
    myService.setMemoryData(memoryData);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);

    MemoryProfilerStage.MemoryStageLegends legends = myStage.getLegends();
    DetailedMemoryUsage usage = myStage.getDetailedMemoryUsage();
    SeriesLegend objectLegend = legends.getObjectsLegend();
    RangedContinuousSeries objectSeries = usage.getObjectsSeries();
    assertThat(legends.getLegends().stream().filter(legend -> legend == objectLegend).findFirst().get().getValue()).isNull();
    assertThat(usage.getSeries().stream().anyMatch(series -> series == objectSeries)).isTrue();
    assertThat(objectSeries.getDataSeries().getDataForXRange(new Range(TimeUnit.SECONDS.toMicros(1), TimeUnit.SECONDS.toMicros(1))))
      .isEmpty();

    myProfilerService.setAgentStatus(AgentStatusResponse.Status.ATTACHED);
    memoryData = MemoryData.newBuilder()
      .setEndTimestamp(2 * FakeTimer.ONE_SECOND_IN_NS)
      .addAllocStatsSamples(
        MemoryData.AllocStatsSample.newBuilder().setTimestamp(2 * FakeTimer.ONE_SECOND_IN_NS).setJavaAllocationCount(10).build()
      ).build();
    myService.setMemoryData(memoryData);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(legends.getLegends().stream().filter(legend -> legend == objectLegend).findFirst().get().getValue()).isNotNull();
    assertThat(usage.getSeries().stream().anyMatch(series -> series == objectSeries)).isTrue();
    assertThat(objectSeries.getDataSeries().getDataForXRange(new Range(TimeUnit.SECONDS.toMicros(2), TimeUnit.SECONDS.toMicros(2))))
      .isNotEmpty();
  }

  @Test
  public void testTooltipLegends() {
    long time = TimeUnit.MICROSECONDS.toNanos(2);
    MemoryData memoryData = MemoryData.newBuilder()
      .setEndTimestamp(time)
      .addMemSamples(MemoryData.MemorySample.newBuilder()
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
    assertThat(legends.getJavaLegend().getName()).isEqualTo("Java");
    assertThat(legends.getJavaLegend().getValue()).isEqualTo("10 KB");

    assertThat(legends.getNativeLegend().getName()).isEqualTo("Native");
    assertThat(legends.getNativeLegend().getValue()).isEqualTo("20 KB");

    assertThat(legends.getGraphicsLegend().getName()).isEqualTo("Graphics");
    assertThat(legends.getGraphicsLegend().getValue()).isEqualTo("30 KB");

    assertThat(legends.getStackLegend().getName()).isEqualTo("Stack");
    assertThat(legends.getStackLegend().getValue()).isEqualTo("40 KB");

    assertThat(legends.getCodeLegend().getName()).isEqualTo("Code");
    assertThat(legends.getCodeLegend().getValue()).isEqualTo("50 KB");

    assertThat(legends.getOtherLegend().getName()).isEqualTo("Others");
    assertThat(legends.getOtherLegend().getValue()).isEqualTo("60 KB");
  }

  @Test
  public void testLegendsOrder() {
    MemoryProfilerStage.MemoryStageLegends legends = myStage.getLegends();
    List<String> legendNames = legends.getLegends().stream()
      .map(legend -> legend.getName())
      .collect(Collectors.toList());
    assertThat(legendNames).containsExactly("Total", "Java", "Native", "Graphics", "Stack", "Code", "Others", "Allocated")
      .inOrder();
  }

  @Test
  public void testTooltipLegendsOrder() {
    MemoryProfilerStage.MemoryStageLegends legends = myStage.getTooltipLegends();
    List<String> legendNames = legends.getLegends().stream()
      .map(legend -> legend.getName())
      .collect(Collectors.toList());
    assertThat(legendNames).containsExactly("Total", "Others", "Code", "Stack", "Graphics", "Native", "Java", "Allocated", "GC Duration")
      .inOrder();
  }

  @Test
  public void testSelectLatestCaptureDisabled() throws Exception {
    myStage.enableSelectLatestCapture(false, null);
    myMockLoader.setReturnImmediateFuture(true);
    assertThat(myStage.getSelectedCapture()).isNull();

    // Start+Stop a capture session (allocation tracking)
    long infoStart = TimeUnit.MICROSECONDS.toNanos(5);
    long infoEnd = TimeUnit.MICROSECONDS.toNanos(10);
    myService.setExplicitAllocationsStatus(TrackAllocationsResponse.Status.SUCCESS);
    myService.setExplicitAllocationsInfo(AllocationsInfo.Status.IN_PROGRESS, infoStart, Long.MAX_VALUE, true);
    myStage.trackAllocations(true);
    myService.setExplicitAllocationsInfo(AllocationsInfo.Status.COMPLETED,
                                         infoStart, infoEnd, true);
    myStage.trackAllocations(false);
    assertThat(myStage.isTrackingAllocations()).isEqualTo(false);
    assertThat(myStage.getSelectedCapture()).isEqualTo(null);
    myAspectObserver.assertAndResetCounts(2, 0, 0, 0, 0, 0, 0, 0);

    // Prepares the AllocationsInfo with the correct start time in the FakeMemoryService.
    myService.setMemoryData(MemoryData.newBuilder().addAllocationsInfo(
      AllocationsInfo.newBuilder().setStartTime(infoStart).setEndTime(infoEnd).setLegacy(true).setStatus(
        AllocationsInfo.Status.COMPLETED).build()).build());
    myService.setExplicitAllocationEvents(LegacyAllocationEventsResponse.Status.SUCCESS, Collections.emptyList());

    // Advancing time (data range) - MemoryProfilerStage should not select the capture since the feature is disabled.
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(myStage.getSelectedCapture()).isNull();
    myAspectObserver.assertAndResetCounts(0, 0, 0, 0, 0, 0, 0, 0);
    assertThat(myStage.getProfilerMode()).isEqualTo(ProfilerMode.NORMAL);
  }

  @Test
  public void testSelectLatestCaptureEnabled() throws Exception {
    myStage.enableSelectLatestCapture(true, MoreExecutors.directExecutor());
    myMockLoader.setReturnImmediateFuture(true);
    assertThat(myStage.getSelectedCapture()).isNull();

    // Start+Stop a capture session (allocation tracking)
    long infoStart = TimeUnit.MICROSECONDS.toNanos(5);
    long infoEnd = TimeUnit.MICROSECONDS.toNanos(10);
    myService.setExplicitAllocationsStatus(TrackAllocationsResponse.Status.SUCCESS);
    myService.setExplicitAllocationsInfo(AllocationsInfo.Status.IN_PROGRESS, infoStart, Long.MAX_VALUE, true);
    myStage.trackAllocations(true);
    myService.setExplicitAllocationsInfo(AllocationsInfo.Status.COMPLETED,
                                         infoStart, infoEnd, true);
    myStage.trackAllocations(false);
    assertThat(myStage.isTrackingAllocations()).isEqualTo(false);
    assertThat(myStage.getSelectedCapture()).isEqualTo(null);
    myAspectObserver.assertAndResetCounts(2, 0, 0, 0, 0, 0, 0, 0);

    // Prepares an unfinished AllocationsInfo with the correct start time in the FakeMemoryService.
    myService.setMemoryData(MemoryData.newBuilder().addAllocationsInfo(
      AllocationsInfo.newBuilder().setStartTime(infoStart).setEndTime(Long.MAX_VALUE).setLegacy(true).setStatus(
        AllocationsInfo.Status.COMPLETED).build()).build());
    myService.setExplicitAllocationEvents(LegacyAllocationEventsResponse.Status.SUCCESS, Collections.emptyList());

    // Advancing time (data range) - stage should not select it yet since the tracking session has not finished
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(myStage.getSelectedCapture()).isNull();
    myAspectObserver.assertAndResetCounts(0, 0, 0, 0, 0, 0, 0, 0);
    assertThat(myStage.getProfilerMode()).isEqualTo(ProfilerMode.NORMAL);

    // Prepares a finished AllocationsInfo with the correct start time in the FakeMemoryService.
    myService.setMemoryData(MemoryData.newBuilder().addAllocationsInfo(
      AllocationsInfo.newBuilder().setStartTime(infoStart).setEndTime(infoEnd).setLegacy(true).setStatus(
        AllocationsInfo.Status.COMPLETED).build()).build());

    // Advancing time (data range) - stage should select it since the tracking session is now done.
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(myStage.getSelectedCapture()).isInstanceOf(LegacyAllocationCaptureObject.class);
    myAspectObserver.assertAndResetCounts(0, 1, 1, 0, 1, 0, 0, 0);
    assertThat(myStage.getProfilerMode()).isEqualTo(ProfilerMode.EXPANDED);
  }

  @Test
  public void testHasUserUsedCaptureViaHeapDump() {
    assertThat(myStage.getInstructionsEaseOutModel().getPercentageComplete()).isWithin(0).of(0);
    assertThat(myStage.hasUserUsedMemoryCapture()).isFalse();
    myStage.requestHeapDump();
    assertThat(myStage.getInstructionsEaseOutModel().getPercentageComplete()).isWithin(0).of(1);
    assertThat(myStage.hasUserUsedMemoryCapture()).isTrue();
  }

  @Test
  public void testHasUserUsedCaptureViaLegacyTracking() {
    assertThat(myStage.getInstructionsEaseOutModel().getPercentageComplete()).isWithin(0).of(0);
    assertThat(myStage.hasUserUsedMemoryCapture()).isFalse();
    long infoStart = TimeUnit.MICROSECONDS.toNanos(5);
    myService.setExplicitAllocationsStatus(TrackAllocationsResponse.Status.SUCCESS);
    myService.setExplicitAllocationsInfo(AllocationsInfo.Status.IN_PROGRESS, infoStart, Long.MAX_VALUE, true);
    myStage.trackAllocations(true);
    assertThat(myStage.getInstructionsEaseOutModel().getPercentageComplete()).isWithin(0).of(1);
    assertThat(myStage.hasUserUsedMemoryCapture()).isTrue();
  }

  @Test
  public void testHasUserUsedCaptureViaSelection() {
    assertThat(myStage.getInstructionsEaseOutModel().getPercentageComplete()).isWithin(0).of(0);
    assertThat(myStage.hasUserUsedMemoryCapture()).isFalse();
    long infoStart = TimeUnit.MICROSECONDS.toNanos(5);
    long infoEnd = TimeUnit.MICROSECONDS.toNanos(10);
    myService.setMemoryData(MemoryData.newBuilder().addAllocationsInfo(
      AllocationsInfo.newBuilder().setStartTime(infoStart).setEndTime(infoEnd).setLegacy(true).setStatus(
        AllocationsInfo.Status.COMPLETED).build()).build());
    myStage.getSelectionModel().set(5, 10);
    assertThat(myStage.getInstructionsEaseOutModel().getPercentageComplete()).isWithin(0).of(1);
    assertThat(myStage.hasUserUsedMemoryCapture()).isTrue();
  }
}
