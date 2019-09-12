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

import static com.android.tools.idea.transport.faketransport.FakeTransportService.FAKE_DEVICE_ID;
import static com.android.tools.profilers.memory.MemoryProfilerConfiguration.ClassGrouping.ARRANGE_BY_CLASS;
import static com.android.tools.profilers.memory.MemoryProfilerConfiguration.ClassGrouping.ARRANGE_BY_PACKAGE;
import static com.google.common.truth.Truth.assertThat;

import com.android.ddmlib.allocations.AllocationsParserTest;
import com.android.tools.adtui.model.FakeTimer;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangedContinuousSeries;
import com.android.tools.adtui.model.filter.Filter;
import com.android.tools.adtui.model.legend.SeriesLegend;
import com.android.tools.idea.protobuf.ByteString;
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel;
import com.android.tools.idea.transport.faketransport.FakeTransportService;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Common.AgentData;
import com.android.tools.profiler.proto.Memory;
import com.android.tools.profiler.proto.Memory.AllocationsInfo;
import com.android.tools.profiler.proto.Memory.MemoryAllocSamplingData;
import com.android.tools.profiler.proto.Memory.TrackStatus;
import com.android.tools.profiler.proto.Memory.TrackStatus.Status;
import com.android.tools.profiler.proto.MemoryProfiler.AllocationSamplingRateEvent;
import com.android.tools.profiler.proto.MemoryProfiler.MemoryData;
import com.android.tools.profilers.FakeProfilerService;
import com.android.tools.profilers.ProfilerMode;
import com.android.tools.profilers.cpu.FakeCpuService;
import com.android.tools.profilers.event.FakeEventService;
import com.android.tools.profilers.memory.adapters.CaptureObject;
import com.android.tools.profilers.memory.adapters.ClassSet;
import com.android.tools.profilers.memory.adapters.ClassifierSet;
import com.android.tools.profilers.memory.adapters.FakeCaptureObject;
import com.android.tools.profilers.memory.adapters.FakeInstanceObject;
import com.android.tools.profilers.memory.adapters.HeapSet;
import com.android.tools.profilers.memory.adapters.InstanceObject;
import com.android.tools.profilers.memory.adapters.LegacyAllocationCaptureObject;
import com.android.tools.profilers.network.FakeNetworkService;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.MoreExecutors;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;

public class MemoryProfilerStageTest extends MemoryProfilerTestBase {

  @NotNull private final ByteBuffer FAKE_ALLOC_BUFFER = AllocationsParserTest.putAllocationInfo(
    new String[0], new String[0], new String[0], new int[0][], new short[0][][]
  );

  @NotNull private final FakeMemoryService myService = new FakeMemoryService();
  @NotNull private final FakeTransportService myTransportService = new FakeTransportService(myTimer);

  @Rule
  public FakeGrpcChannel myGrpcChannel =
    new FakeGrpcChannel("MemoryProfilerStageTestChannel", myService, myTransportService, new FakeProfilerService(myTimer),
                        new FakeCpuService(), new FakeEventService(), FakeNetworkService.newBuilder().build());

  @Override
  protected FakeGrpcChannel getGrpcChannel() {
    return myGrpcChannel;
  }

  @Test
  public void testToggleAllocationTrackingFailedStatuses() {
    myStage.trackAllocations(false);
    assertThat(myStage.isTrackingAllocations()).isEqualTo(false);
    assertThat(myStage.getProfilerMode()).isEqualTo(ProfilerMode.NORMAL);
    myAspectObserver.assertAndResetCounts(1, 0, 0, 0, 0, 0, 0, 0);

    myService.setExplicitAllocationsStatus(TrackStatus.newBuilder().setStatus(Status.NOT_ENABLED).build());
    myStage.trackAllocations(false);
    assertThat(myStage.isTrackingAllocations()).isEqualTo(false);
    assertThat(myStage.getProfilerMode()).isEqualTo(ProfilerMode.NORMAL);
    myAspectObserver.assertAndResetCounts(1, 0, 0, 0, 0, 0, 0, 0);

    myService.setExplicitAllocationsStatus(TrackStatus.newBuilder().setStatus(Status.FAILURE_UNKNOWN).build());
    myStage.trackAllocations(false);
    assertThat(myStage.isTrackingAllocations()).isEqualTo(false);
    assertThat(myStage.getProfilerMode()).isEqualTo(ProfilerMode.NORMAL);
    myAspectObserver.assertAndResetCounts(1, 0, 0, 0, 0, 0, 0, 0);
  }

  @Test
  public void testToggleAllocationTracking() {
    // Enable the auto capture selection mechanism.
    myStage.enableSelectLatestCapture(true, MoreExecutors.directExecutor());

    // Starting a tracking session
    long infoStart = TimeUnit.MICROSECONDS.toNanos(5);
    long infoEnd = TimeUnit.MICROSECONDS.toNanos(10);
    myService.setExplicitAllocationsStatus(TrackStatus.newBuilder().setStartTime(infoStart).setStatus(Status.SUCCESS).build());
    myService.setExplicitAllocationsInfo(infoStart, Long.MAX_VALUE, true);
    myStage.trackAllocations(true);
    assertThat(myStage.isTrackingAllocations()).isEqualTo(true);
    assertThat(myStage.getSelectedCapture()).isEqualTo(null);
    assertThat(myStage.getProfilerMode()).isEqualTo(ProfilerMode.NORMAL);
    myAspectObserver.assertAndResetCounts(1, 0, 0, 0, 0, 0, 0, 0);

    // Attempting to start a in-progress session
    myService.setExplicitAllocationsStatus(TrackStatus.newBuilder().setStatus(Status.IN_PROGRESS).build());
    myStage.trackAllocations(true);
    assertThat(myStage.isTrackingAllocations()).isEqualTo(true);
    assertThat(myStage.getSelectedCapture()).isEqualTo(null);
    assertThat(myStage.getProfilerMode()).isEqualTo(ProfilerMode.NORMAL);
    myAspectObserver.assertAndResetCounts(1, 0, 0, 0, 0, 0, 0, 0);

    // Stops the tracking session.
    myService.setExplicitAllocationsStatus(TrackStatus.newBuilder().setStartTime(infoStart).setStatus(Status.SUCCESS).build());
    myService.setExplicitAllocationsInfo(infoStart, infoEnd, true);
    myStage.trackAllocations(false);
    assertThat(myStage.isTrackingAllocations()).isEqualTo(false);
    assertThat(myStage.getSelectedCapture()).isEqualTo(null);
    assertThat(myStage.getProfilerMode()).isEqualTo(ProfilerMode.NORMAL);
    myAspectObserver.assertAndResetCounts(1, 0, 0, 0, 0, 0, 0, 0);

    // Prepares the AllocationsInfo with the correct start time in the FakeMemoryService.
    myService.setMemoryData(MemoryData.newBuilder().addAllocationsInfo(
      AllocationsInfo.newBuilder().setStartTime(infoStart).setEndTime(infoEnd).setLegacy(true).setSuccess(true)).build());
    myTransportService.addFile(Long.toString(infoStart), ByteString.copyFrom(FAKE_ALLOC_BUFFER));

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
  public void testAllocationTrackingSetStreaming() {
    myProfilers.getTimeline().setStreaming(false);
    assertThat(myProfilers.getTimeline().isStreaming()).isFalse();

    // Stopping tracking should not cause streaming.
    myService.setExplicitAllocationsStatus(TrackStatus.newBuilder().setStatus(Status.NOT_ENABLED).build());
    myStage.trackAllocations(false);
    assertThat(myProfilers.getTimeline().isStreaming()).isFalse();

    long infoStart = TimeUnit.MICROSECONDS.toNanos(5);
    myService.setExplicitAllocationsStatus(TrackStatus.newBuilder().setStartTime(infoStart).setStatus(Status.SUCCESS).build());
    myService.setExplicitAllocationsInfo(infoStart, Long.MAX_VALUE, true);
    myStage.trackAllocations(true);
    assertThat(myProfilers.getTimeline().isStreaming()).isTrue();
  }

  @Test
  public void testAllocationTrackingStateOnTransition() {
    myStage.enter();
    assertThat(myStage.isTrackingAllocations()).isFalse();

    long infoStart = TimeUnit.MICROSECONDS.toNanos(5);
    long infoEnd = TimeUnit.MICROSECONDS.toNanos(10);
    AllocationsInfo startInfo = AllocationsInfo.newBuilder().setStartTime(infoStart).setEndTime(Long.MAX_VALUE).setLegacy(true).build();
    AllocationsInfo endInfo = AllocationsInfo.newBuilder().setStartTime(infoStart).setEndTime(infoEnd).setLegacy(true).build();
    myService.setExplicitAllocationsStatus(TrackStatus.newBuilder().setStartTime(infoStart).setStatus(Status.SUCCESS).build());
    myService.setExplicitAllocationsInfo(infoStart, Long.MAX_VALUE, true);
    myService.setMemoryData(MemoryData.newBuilder().addAllocationsInfo(startInfo).build());
    myStage.trackAllocations(true);

    assertThat(myStage.isTrackingAllocations()).isTrue();
    myStage.exit();
    myStage.enter();
    assertThat(myStage.isTrackingAllocations()).isTrue();

    myService.setExplicitAllocationsInfo(infoStart, infoEnd, true);
    myService.setMemoryData(MemoryData.newBuilder().addAllocationsInfo(startInfo).addAllocationsInfo(endInfo).build());
    myStage.trackAllocations(false);

    assertThat(myStage.isTrackingAllocations()).isFalse();
    myStage.exit();
    myStage.enter();
    assertThat(myStage.isTrackingAllocations()).isFalse();
  }

  @Test
  public void testRequestHeapDump() {
    // Bypass the load mechanism in HeapDumpCaptureObject.
    myMockLoader.setReturnImmediateFuture(true);
    // Test the no-action cases
    myService.setExplicitHeapDumpStatus(Memory.HeapDumpStatus.Status.FAILURE_UNKNOWN);
    myStage.requestHeapDump();
    assertThat(myStage.getSelectedCapture()).isNull();
    assertThat(myStage.getProfilerMode()).isEqualTo(ProfilerMode.NORMAL);
    myService.setExplicitHeapDumpStatus(Memory.HeapDumpStatus.Status.IN_PROGRESS);
    myStage.requestHeapDump();
    assertThat(myStage.getSelectedCapture()).isNull();
    assertThat(myStage.getProfilerMode()).isEqualTo(ProfilerMode.NORMAL);
    myService.setExplicitHeapDumpStatus(Memory.HeapDumpStatus.Status.UNSPECIFIED);
    myStage.requestHeapDump();
    assertThat(myStage.getSelectedCapture()).isNull();
    assertThat(myStage.getProfilerMode()).isEqualTo(ProfilerMode.NORMAL);

    myService.setExplicitHeapDumpStatus(Memory.HeapDumpStatus.Status.SUCCESS);
    myService.setExplicitHeapDumpInfo(5, 10);
    myStage.requestHeapDump();

    // TODO need to add a mock heap dump here to test the success path
  }

  @Test
  public void testHeapDumpSetStreaming() {
    myProfilers.getTimeline().setStreaming(false);
    assertThat(myProfilers.getTimeline().isStreaming()).isFalse();
    myMockLoader.setReturnImmediateFuture(true);
    myStage.requestHeapDump();
    assertThat(myProfilers.getTimeline().isStreaming()).isTrue();
  }

  @Test
  public void defaultHeapSetTest() {
    String fakeClassName1 = "DUMMY_CLASS1", fakeClassName2 = "DUMMY_CLASS2";

    myMockLoader.setReturnImmediateFuture(true);

    FakeCaptureObject capture0 = new FakeCaptureObject.Builder().setHeapIdToNameMap(ImmutableMap.of(0, "default", 1, "app")).build();
    FakeInstanceObject instanceObject = new FakeInstanceObject.Builder(capture0, 1, fakeClassName1).setHeapId(0).build();
    capture0.addInstanceObjects(ImmutableSet.of(instanceObject));

    myStage.selectCaptureDuration(new CaptureDurationData<>(1, false, false, new CaptureEntry<CaptureObject>(new Object(), () -> capture0)),
                                  null);
    assertThat(myStage.getSelectedCapture()).isEqualTo(capture0);
    assertThat(myStage.getSelectedHeapSet()).isNotNull();
    assertThat(myStage.getSelectedHeapSet().getName()).isEqualTo("default");

    FakeCaptureObject capture1 = new FakeCaptureObject.Builder().setHeapIdToNameMap(ImmutableMap.of(0, "default", 1, "app")).build();
    instanceObject = new FakeInstanceObject.Builder(capture1, 1, fakeClassName1).setHeapId(1).build();
    capture1.addInstanceObjects(ImmutableSet.of(instanceObject));

    myStage.selectCaptureDuration(new CaptureDurationData<>(1, false, false, new CaptureEntry<CaptureObject>(new Object(), () -> capture1)),
                                  null);
    assertThat(myStage.getSelectedCapture()).isEqualTo(capture1);
    assertThat(myStage.getSelectedHeapSet()).isNotNull();
    assertThat(myStage.getSelectedHeapSet().getName()).isEqualTo("app");

    FakeCaptureObject capture2 = new FakeCaptureObject.Builder().setHeapIdToNameMap(ImmutableMap.of(0, "default", 1, "app")).build();
    instanceObject = new FakeInstanceObject.Builder(capture2, 1, fakeClassName1).setHeapId(0).build();
    FakeInstanceObject otherInstanceObject = new FakeInstanceObject.Builder(capture2, 2, fakeClassName2).setHeapId(1).build();
    capture2.addInstanceObjects(ImmutableSet.of(instanceObject, otherInstanceObject));

    myStage.selectCaptureDuration(new CaptureDurationData<>(1, false, false, new CaptureEntry<CaptureObject>(new Object(), () -> capture2)),
                                  null);
    assertThat(myStage.getSelectedCapture()).isEqualTo(capture2);
    assertThat(myStage.getSelectedHeapSet()).isNotNull();
    assertThat(myStage.getSelectedHeapSet().getName()).isEqualTo("app");
  }

  @Test
  public void testSelectionRangeUpdateOnCaptureSelection() {
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
  public void testMemoryObjectSelection() {
    final String dummyClassName = "DUMMY_CLASS1";
    FakeCaptureObject captureObject = new FakeCaptureObject.Builder().setStartTime(5).setEndTime(10).build();
    InstanceObject mockInstance =
      new FakeInstanceObject.Builder(captureObject, 1, dummyClassName).setName("DUMMY_INSTANCE")
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

    HeapSet heapSet = captureObject.getHeapSet(CaptureObject.DEFAULT_HEAP_ID);
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

    String filterString = "Filter";
    myStage.getFilterHandler().setFilter(new Filter(filterString));
    assertThat(myStage.getSelectedCapture()).isEqualTo(captureObject);
    assertThat(myStage.getSelectedHeapSet()).isEqualTo(heapSet);
    assertThat(myStage.getConfiguration().getClassGrouping()).isEqualTo(ARRANGE_BY_CLASS);
    assertThat(myStage.getSelectedClassSet()).isNull();
    assertThat(myStage.getSelectedInstanceObject()).isNull();
    myAspectObserver.assertAndResetCounts(0, 0, 0, 0, 0, 0, 0, 0);

    assertThat(myStage.getConfiguration().getClassGrouping()).isEqualTo(ARRANGE_BY_CLASS);
    myStage.getConfiguration().setClassGrouping(ARRANGE_BY_PACKAGE);
    // Retain Filter after Grouping change
    assertThat(myStage.getFilterHandler().getFilter().getFilterString()).isEqualTo(filterString);
    // Reset Filter
    myStage.getFilterHandler().setFilter(Filter.EMPTY_FILTER);
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
  public void testSelectNewCaptureWhileLoading() {
    CaptureObject mockCapture1 = new FakeCaptureObject.Builder().setCaptureName("DUMMY_CAPTURE1").setStartTime(5).setEndTime(10).build();
    CaptureObject mockCapture2 = new FakeCaptureObject.Builder().setCaptureName("DUMMY_CAPTURE2").setStartTime(10).setEndTime(15).build();

    myStage
      .selectCaptureDuration(new CaptureDurationData<>(1, false, false, new CaptureEntry<>(new Object(), () -> mockCapture1)),
                             null);
    assertThat(myStage.getSelectedCapture()).isEqualTo(mockCapture1);
    assertThat(myStage.getProfilerMode()).isEqualTo(ProfilerMode.EXPANDED);
    myAspectObserver.assertAndResetCounts(0, 1, 0, 0, 0, 0, 0, 0);

    // Make sure selecting a new capture while the first one is loading will select the new one
    myStage
      .selectCaptureDuration(new CaptureDurationData<>(1, false, false, new CaptureEntry<>(new Object(), () -> mockCapture2)),
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
  public void testCaptureLoadingFailure() {
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
      .selectCaptureDuration(new CaptureDurationData<>(1, false, false, new CaptureEntry<>(new Object(), () -> mockCapture1)),
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
    MemoryData memoryData = MemoryData.newBuilder()
      .setEndTimestamp(FakeTimer.ONE_SECOND_IN_NS)
      .addAllocStatsSamples(
        MemoryData.AllocStatsSample.newBuilder().setTimestamp(FakeTimer.ONE_SECOND_IN_NS)
          .setAllocStats(Memory.MemoryAllocStatsData.newBuilder().setJavaAllocationCount(5)))
      .build();
    myService.setMemoryData(memoryData);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);

    MemoryProfilerStage.MemoryStageLegends legends = myStage.getLegends();
    DetailedMemoryUsage usage = myStage.getDetailedMemoryUsage();
    SeriesLegend objectLegend = legends.getObjectsLegend();
    RangedContinuousSeries objectSeries = usage.getObjectsSeries();
    assertThat(legends.getLegends().stream().filter(legend -> legend == objectLegend).findFirst().get().getValue()).isEqualTo("N/A");
    assertThat(usage.getSeries().stream().anyMatch(series -> series == objectSeries)).isTrue();
    assertThat(objectSeries.getSeriesForRange(new Range(TimeUnit.SECONDS.toMicros(1), TimeUnit.SECONDS.toMicros(1))))
      .isEmpty();

    myTransportService.setAgentStatus(AgentData.newBuilder().setStatus(AgentData.Status.ATTACHED).build());
    memoryData = MemoryData.newBuilder()
      .setEndTimestamp(2 * FakeTimer.ONE_SECOND_IN_NS)
      .addAllocStatsSamples(
        MemoryData.AllocStatsSample.newBuilder().setTimestamp(2 * FakeTimer.ONE_SECOND_IN_NS)
          .setAllocStats(Memory.MemoryAllocStatsData.newBuilder().setJavaAllocationCount(10)))
      .build();
    myService.setMemoryData(memoryData);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(legends.getLegends().stream().filter(legend -> legend == objectLegend).findFirst().get().getValue()).isNotNull();
    assertThat(usage.getSeries().stream().anyMatch(series -> series == objectSeries)).isTrue();
    assertThat(objectSeries.getSeriesForRange(new Range(TimeUnit.SECONDS.toMicros(2), TimeUnit.SECONDS.toMicros(2))))
      .isNotEmpty();
  }

  @Test
  public void testTooltipLegends() {
    long time = TimeUnit.MICROSECONDS.toNanos(2);
    MemoryData memoryData = MemoryData.newBuilder()
      .setEndTimestamp(time)
      .addMemSamples(MemoryData.MemorySample.newBuilder()
                       .setTimestamp(time)
                       .setMemoryUsage(Memory.MemoryUsageData.newBuilder()
                                         .setJavaMem(10)
                                         .setNativeMem(20)
                                         .setGraphicsMem(30)
                                         .setStackMem(40)
                                         .setCodeMem(50)
                                         .setOthersMem(60)))
      .build();
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
  public void testAllocatedLegendChangesBasedOnSamplingMode() {
    // Perfa needs to be running for "Allocated" series to show.
    myTransportService.setAgentStatus(AgentData.newBuilder().setStatus(AgentData.Status.ATTACHED).build());
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);

    long time = TimeUnit.MICROSECONDS.toNanos(2);
    AllocationsInfo liveAllocInfo = AllocationsInfo.newBuilder().setStartTime(0).setEndTime(Long.MAX_VALUE).setLegacy(false).build();
    MemoryData.AllocStatsSample allocStatsSample = MemoryData.AllocStatsSample.newBuilder()
      .setAllocStats(Memory.MemoryAllocStatsData.newBuilder().setJavaAllocationCount(200).setJavaFreeCount(100))
      .build();
    AllocationSamplingRateEvent trackingMode = AllocationSamplingRateEvent
      .newBuilder()
      .setSamplingRate(MemoryAllocSamplingData.newBuilder().setSamplingNumInterval(
        MemoryProfilerStage.LiveAllocationSamplingMode.FULL.getValue()
      ))
      .build();
    MemoryData memoryData = MemoryData.newBuilder()
      .setEndTimestamp(time)
      .addAllocationsInfo(liveAllocInfo)
      .addAllocSamplingRateEvents(trackingMode)
      .addAllocStatsSamples(allocStatsSample)
      .build();
    myService.setMemoryData(memoryData);
    MemoryProfilerStage.MemoryStageLegends legends = myStage.getTooltipLegends();
    myStage.getStudioProfilers().getTimeline().getTooltipRange().set(time, time);
    assertThat(legends.getObjectsLegend().getName()).isEqualTo("Allocated");
    assertThat(legends.getObjectsLegend().getValue()).isEqualTo("100");

    // Now change sampling mode to sampled, the Allocated legend should show "N/A"
    memoryData = memoryData.toBuilder()
      .clearAllocStatsSamples()
      .clearAllocSamplingRateEvents()
      .addAllocSamplingRateEvents(
        trackingMode.toBuilder().setSamplingRate(MemoryAllocSamplingData.newBuilder().setSamplingNumInterval(
          MemoryProfilerStage.LiveAllocationSamplingMode.SAMPLED.getValue()
        )))
      .addAllocStatsSamples(allocStatsSample.toBuilder()
                              .setAllocStats(Memory.MemoryAllocStatsData.newBuilder().setJavaAllocationCount(300).setJavaFreeCount(100)))
      .build();
    myService.setMemoryData(memoryData);
    assertThat(legends.getObjectsLegend().getValue()).isEqualTo("N/A");

    // Now change sampling mode to none, the Allocated legend should still show "N/A"
    memoryData = memoryData.toBuilder()
      .clearAllocSamplingRateEvents()
      .addAllocSamplingRateEvents(
        trackingMode.toBuilder().setSamplingRate(MemoryAllocSamplingData.newBuilder().setSamplingNumInterval(
          MemoryProfilerStage.LiveAllocationSamplingMode.NONE.getValue()
        )))
      .build();
    myService.setMemoryData(memoryData);
    assertThat(legends.getObjectsLegend().getValue()).isEqualTo("N/A");

    // Value should update once sampling mode is set back to full.
    memoryData = memoryData.toBuilder()
      .clearAllocSamplingRateEvents()
      .addAllocSamplingRateEvents(
        trackingMode.toBuilder().setSamplingRate(MemoryAllocSamplingData.newBuilder().setSamplingNumInterval(
          MemoryProfilerStage.LiveAllocationSamplingMode.FULL.getValue()
        )))
      .build();
    myService.setMemoryData(memoryData);
    assertThat(legends.getObjectsLegend().getValue()).isEqualTo("200");
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
    assertThat(legendNames)
      .containsExactly("Others", "Code", "Stack", "Graphics", "Native", "Java", "Allocated", "Tracking", "GC Duration", "Total")
      .inOrder();
  }

  @Test
  public void testSelectLatestCaptureDisabled() {
    myStage.enableSelectLatestCapture(false, null);
    myMockLoader.setReturnImmediateFuture(true);
    assertThat(myStage.getSelectedCapture()).isNull();

    // Start+Stop a capture session (allocation tracking)
    long infoStart = TimeUnit.MICROSECONDS.toNanos(5);
    long infoEnd = TimeUnit.MICROSECONDS.toNanos(10);
    myService.setExplicitAllocationsStatus(TrackStatus.newBuilder().setStartTime(infoStart).setStatus(Status.SUCCESS).build());
    myService.setExplicitAllocationsInfo(infoStart, Long.MAX_VALUE, true);
    myStage.trackAllocations(true);
    myService.setExplicitAllocationsInfo(infoStart, infoEnd, true);
    myStage.trackAllocations(false);
    assertThat(myStage.isTrackingAllocations()).isEqualTo(false);
    assertThat(myStage.getSelectedCapture()).isEqualTo(null);
    myAspectObserver.assertAndResetCounts(2, 0, 0, 0, 0, 0, 0, 0);

    // Prepares the AllocationsInfo with the correct start time in the FakeMemoryService.
    myService.setMemoryData(MemoryData.newBuilder().addAllocationsInfo(
      AllocationsInfo.newBuilder().setStartTime(infoStart).setEndTime(infoEnd).setLegacy(true)).build());

    // Advancing time (data range) - MemoryProfilerStage should not select the capture since the feature is disabled.
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(myStage.getSelectedCapture()).isNull();
    myAspectObserver.assertAndResetCounts(0, 0, 0, 0, 0, 0, 0, 0);
    assertThat(myStage.getProfilerMode()).isEqualTo(ProfilerMode.NORMAL);
  }

  @Test
  public void testSelectLatestCaptureEnabled() {
    myStage.enableSelectLatestCapture(true, MoreExecutors.directExecutor());
    myMockLoader.setReturnImmediateFuture(true);
    assertThat(myStage.getSelectedCapture()).isNull();

    // Start+Stop a capture session (allocation tracking)
    long infoStart = TimeUnit.MICROSECONDS.toNanos(5);
    long infoEnd = TimeUnit.MICROSECONDS.toNanos(10);
    myService.setExplicitAllocationsStatus(TrackStatus.newBuilder().setStartTime(infoStart).setStatus(Status.SUCCESS).build());
    myService.setExplicitAllocationsInfo(infoStart, Long.MAX_VALUE, true);
    myStage.trackAllocations(true);
    myService.setExplicitAllocationsInfo(infoStart, infoEnd, true);
    myStage.trackAllocations(false);
    assertThat(myStage.isTrackingAllocations()).isEqualTo(false);
    assertThat(myStage.getSelectedCapture()).isEqualTo(null);
    myAspectObserver.assertAndResetCounts(2, 0, 0, 0, 0, 0, 0, 0);

    // Prepares an unfinished AllocationsInfo with the correct start time in the FakeMemoryService.
    myService.setMemoryData(MemoryData.newBuilder().addAllocationsInfo(
      AllocationsInfo.newBuilder().setStartTime(infoStart).setEndTime(Long.MAX_VALUE).setLegacy(true)).build());

    // Advancing time (data range) - stage should not select it yet since the tracking session has not finished
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(myStage.getSelectedCapture()).isNull();
    myAspectObserver.assertAndResetCounts(0, 0, 0, 0, 0, 0, 0, 0);
    assertThat(myStage.getProfilerMode()).isEqualTo(ProfilerMode.NORMAL);

    // Prepares a finished AllocationsInfo with the correct start time in the FakeMemoryService.
    myService.setMemoryData(MemoryData.newBuilder().addAllocationsInfo(
      AllocationsInfo.newBuilder().setStartTime(infoStart).setEndTime(infoEnd).setLegacy(true).setSuccess(true)).build());
    myTransportService.addFile(Long.toString(infoStart), ByteString.copyFrom(FAKE_ALLOC_BUFFER));

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
    myService.setExplicitAllocationsStatus(TrackStatus.newBuilder().setStartTime(infoStart).setStatus(Status.SUCCESS).build());
    myService.setExplicitAllocationsInfo(infoStart, Long.MAX_VALUE, true);
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
      AllocationsInfo.newBuilder().setStartTime(infoStart).setEndTime(infoEnd).setLegacy(true)).build());
    myStage.getRangeSelectionModel().set(5, 10);
    assertThat(myStage.getInstructionsEaseOutModel().getPercentageComplete()).isWithin(0).of(1);
    assertThat(myStage.hasUserUsedMemoryCapture()).isTrue();
  }

  @Test
  public void testAllocationSamplingRateUpdates() {
    int[] samplingAspectChange = {0};
    myStage.getAspect().addDependency(myAspectObserver).onChange(MemoryProfilerAspect.LIVE_ALLOCATION_SAMPLING_MODE,
                                                                 () -> samplingAspectChange[0]++);

    // Ensure that the default is sampled.
    assertThat(myStage.getLiveAllocationSamplingMode()).isEqualTo(MemoryProfilerStage.LiveAllocationSamplingMode.SAMPLED);
    assertThat(samplingAspectChange[0]).isEqualTo(0);

    // Ensure that advancing the timer does not change the mode if there are no AllocationSamplingRange.
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(myStage.getLiveAllocationSamplingMode()).isEqualTo(MemoryProfilerStage.LiveAllocationSamplingMode.SAMPLED);
    assertThat(samplingAspectChange[0]).isEqualTo(0);

    AllocationSamplingRateEvent sampleMode = AllocationSamplingRateEvent.newBuilder()
      .setSamplingRate(MemoryAllocSamplingData.newBuilder().setSamplingNumInterval(1)).build();
    myService.setMemoryData(MemoryData.newBuilder().addAllocSamplingRateEvents(sampleMode).build());
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(myStage.getLiveAllocationSamplingMode()).isEqualTo(MemoryProfilerStage.LiveAllocationSamplingMode.FULL);
    assertThat(samplingAspectChange[0]).isEqualTo(1);

    AllocationSamplingRateEvent noneMode = AllocationSamplingRateEvent.newBuilder()
      .setSamplingRate(MemoryAllocSamplingData.newBuilder().setSamplingNumInterval(0)).build();
    myService.setMemoryData(MemoryData.newBuilder().addAllocSamplingRateEvents(noneMode).build());
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(myStage.getLiveAllocationSamplingMode()).isEqualTo(MemoryProfilerStage.LiveAllocationSamplingMode.NONE);
    assertThat(samplingAspectChange[0]).isEqualTo(2);
  }

  @Test
  public void testAllocationSamplingRateCorrectlyInitialized() {
    // If the sampling mode is already available from the memory service, make sure the memory stage is correctly initialized to that value.
    AllocationsInfo liveAllocInfo = AllocationsInfo.newBuilder().setStartTime(0).setEndTime(Long.MAX_VALUE).setLegacy(false).build();
    AllocationSamplingRateEvent fullTrackingMode = AllocationSamplingRateEvent
      .newBuilder()
      .setSamplingRate(MemoryAllocSamplingData.newBuilder().setSamplingNumInterval(1))
      .build();
    myService.setMemoryData(MemoryData.newBuilder().addAllocationsInfo(liveAllocInfo).addAllocSamplingRateEvents(fullTrackingMode).build());

    MemoryProfilerStage stage = new MemoryProfilerStage(myProfilers, myMockLoader);
    assertThat(stage.getLiveAllocationSamplingMode()).isEqualTo(MemoryProfilerStage.LiveAllocationSamplingMode.FULL);
  }

  @Test
  public void testAllocationSamplingModePersistsAcrossStages() {
    // Ensure that the default is sampled.
    assertThat(myStage.getLiveAllocationSamplingMode()).isEqualTo(MemoryProfilerStage.LiveAllocationSamplingMode.SAMPLED);
    myStage.requestLiveAllocationSamplingModeUpdate(MemoryProfilerStage.LiveAllocationSamplingMode.FULL);

    MemoryProfilerStage newStage1 = new MemoryProfilerStage(myProfilers, myMockLoader);
    assertThat(newStage1.getLiveAllocationSamplingMode()).isEqualTo(MemoryProfilerStage.LiveAllocationSamplingMode.FULL);
    newStage1.requestLiveAllocationSamplingModeUpdate(MemoryProfilerStage.LiveAllocationSamplingMode.NONE);

    MemoryProfilerStage newStage2 = new MemoryProfilerStage(myProfilers, myMockLoader);
    assertThat(newStage2.getLiveAllocationSamplingMode()).isEqualTo(MemoryProfilerStage.LiveAllocationSamplingMode.NONE);
  }
}
