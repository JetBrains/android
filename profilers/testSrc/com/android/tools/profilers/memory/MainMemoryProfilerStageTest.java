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
import static com.android.tools.idea.transport.faketransport.FakeTransportService.FAKE_PROCESS;
import static com.android.tools.profilers.memory.ClassGrouping.ARRANGE_BY_CLASS;
import static com.android.tools.profilers.memory.ClassGrouping.ARRANGE_BY_PACKAGE;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assume.assumeTrue;

import com.android.ddmlib.allocations.AllocationsParserTest;
import com.android.sdklib.AndroidVersion;
import com.android.tools.adtui.model.DataSeries;
import com.android.tools.adtui.model.FakeTimer;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.adtui.model.filter.Filter;
import com.android.tools.idea.protobuf.ByteString;
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel;
import com.android.tools.idea.transport.faketransport.FakeTransportService;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Memory;
import com.android.tools.profiler.proto.Memory.AllocationsInfo;
import com.android.tools.profiler.proto.Memory.HeapDumpInfo;
import com.android.tools.profiler.proto.Memory.TrackStatus.Status;
import com.android.tools.profiler.proto.MemoryProfiler.MemoryData;
import com.android.tools.profiler.proto.Trace;
import com.android.tools.profilers.FakeFeatureTracker;
import com.android.tools.profilers.FakeProfilerService;
import com.android.tools.profilers.ProfilerClient;
import com.android.tools.profilers.ProfilersTestData;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.cpu.FakeCpuService;
import com.android.tools.profilers.event.FakeEventService;
import com.android.tools.profilers.memory.adapters.CaptureObject;
import com.android.tools.profilers.memory.adapters.FakeCaptureObject;
import com.android.tools.profilers.memory.adapters.FakeInstanceObject;
import com.android.tools.profilers.memory.adapters.InstanceObject;
import com.android.tools.profilers.memory.adapters.LegacyAllocationCaptureObject;
import com.android.tools.profilers.memory.adapters.classifiers.ClassSet;
import com.android.tools.profilers.memory.adapters.classifiers.ClassifierSet;
import com.android.tools.profilers.memory.adapters.classifiers.HeapSet;
import com.android.tools.profilers.sessions.SessionsManager;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.wireless.android.sdk.stats.AndroidProfilerEvent;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public final class MainMemoryProfilerStageTest extends MemoryProfilerTestBase {
  @NotNull private final ByteBuffer FAKE_ALLOC_BUFFER = AllocationsParserTest.putAllocationInfo(
    ArrayUtil.EMPTY_STRING_ARRAY, ArrayUtil.EMPTY_STRING_ARRAY, ArrayUtil.EMPTY_STRING_ARRAY, new int[0][], new short[0][][]
  );

  @NotNull private final FakeMemoryService myService = new FakeMemoryService();
  @NotNull private final FakeTransportService myTransportService;

  @Rule
  public final FakeGrpcChannel myGrpcChannel;

  public MainMemoryProfilerStageTest(int featureLevel) {
    super();
    myTransportService = new FakeTransportService(myTimer, true, featureLevel);
    myGrpcChannel = new FakeGrpcChannel("MemoryProfilerStageTestChannel", myService, myTransportService, new FakeProfilerService(myTimer),
                                        new FakeCpuService(), new FakeEventService());
  }

  @Override
  protected void onProfilersCreated(StudioProfilers profilers) {
    myIdeProfilerServices.enableEventsPipeline(true);
  }

  @Override
  protected FakeGrpcChannel getGrpcChannel() {
    return myGrpcChannel;
  }

  @Test
  public void testToggleAllocationTrackingFailedStatuses() {
    myStage.trackAllocations(false);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(myStage.isTrackingAllocations()).isEqualTo(false);
    myAspectObserver.assertAndResetCounts(1, 0, 0, 0, 0, 0, 0, 0);

    MemoryProfilerTestUtils
      .startTrackingHelper(myStage, myTransportService, myTimer, -1, Status.NOT_ENABLED, true);
    assertThat(myStage.isTrackingAllocations()).isEqualTo(false);
    myAspectObserver.assertAndResetCounts(1, 0, 0, 0, 0, 0, 0, 0);

    MemoryProfilerTestUtils
      .stopTrackingHelper(myStage, myTransportService, myTimer, -1, Status.FAILURE_UNKNOWN, true);
    assertThat(myStage.isTrackingAllocations()).isEqualTo(false);
    myAspectObserver.assertAndResetCounts(1, 0, 0, 0, 0, 0, 0, 0);
  }

  @Test
  public void testToggleAllocationTracking() {
    assumePreO(true);
    // Enable the auto capture selection mechanism.
    myStage.enableSelectLatestCapture(true, MoreExecutors.directExecutor());

    // Starting a tracking session
    long infoStart = TimeUnit.MICROSECONDS.toNanos(5);
    long infoEnd = TimeUnit.MICROSECONDS.toNanos(10);
    MemoryProfilerTestUtils
      .startTrackingHelper(myStage, myTransportService, myTimer, infoStart, Status.SUCCESS, true);
    assertThat(myStage.isTrackingAllocations()).isEqualTo(true);
    assertThat(myStage.getCaptureSelection().getSelectedCapture()).isEqualTo(null);
    myAspectObserver.assertAndResetCounts(1, 0, 0, 0, 0, 0, 0, 0);

    // Attempting to start a in-progress session
    MemoryProfilerTestUtils
      .startTrackingHelper(myStage, myTransportService, myTimer, infoStart, Status.IN_PROGRESS, true);
    assertThat(myStage.isTrackingAllocations()).isEqualTo(true);
    assertThat(myStage.getCaptureSelection().getSelectedCapture()).isEqualTo(null);
    myAspectObserver.assertAndResetCounts(1, 0, 0, 0, 0, 0, 0, 0);

    // Stops the tracking session.
    myTransportService.addFile(Long.toString(infoStart), ByteString.copyFrom(FAKE_ALLOC_BUFFER));
    MemoryProfilerTestUtils
      .stopTrackingHelper(myStage, myTransportService, myTimer, infoStart, Status.SUCCESS, true);
    assertThat(myStage.isTrackingAllocations()).isEqualTo(false);
    assertThat(myStage.getCaptureSelection().getSelectedCapture()).isInstanceOf(LegacyAllocationCaptureObject.class);
    myAspectObserver.assertAndResetCounts(1, 1, 0, 0, 0, 0, 0, 0);
    LegacyAllocationCaptureObject capture = (LegacyAllocationCaptureObject)myStage.getCaptureSelection().getSelectedCapture();
    assertThat(capture.isDoneLoading()).isFalse();
    assertThat(capture.isError()).isFalse();

    // Finish the load task.
    myMockLoader.runTask();
    assertThat(myStage.getCaptureSelection().getSelectedCapture()).isEqualTo(capture);
    assertThat(capture.isDoneLoading()).isTrue();
    assertThat(capture.isError()).isFalse();
    myAspectObserver.assertAndResetCounts(0, 0, 1, 0, 1, 0, 0, 0);
  }

  @Test
  public void testToggleNativeAllocationTracking() {
    assertThat(myStage.isTrackingAllocations()).isFalse();
    assertThat(((FakeFeatureTracker)myIdeProfilerServices.getFeatureTracker()).isTrackRecordAllocationsCalled()).isFalse();
    // Set time to 1 second (in ns) before starting tracking to verify start time field of TraceStartStatus event.
    myTimer.setCurrentTimeNs(FakeTimer.ONE_SECOND_IN_NS);
    // Validate we enabled tracking allocations.
    myStage.toggleNativeAllocationTracking();
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(myStage.isTrackingAllocations()).isTrue();
    assertThat(myStage.myNativeAllocationTracking).isTrue();
    // Make sure start time is equivalent to fake time set above
    assertThat(myStage.getPendingCaptureStartTime()).isEqualTo(1000000000);
    // Validate timeline streaming has begun.
    assertThat(myStage.getTimeline().isStreaming()).isTrue();
    assertThat(((FakeFeatureTracker)myIdeProfilerServices.getFeatureTracker()).isTrackRecordAllocationsCalled()).isTrue();
    // Validate we disabled tracking allocations.
    myStage.toggleNativeAllocationTracking();
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(myStage.isTrackingAllocations()).isFalse();
    assertThat(myStage.myNativeAllocationTracking).isFalse();
  }

  @Test
  public void testAllocationTrackingSetStreaming() {
    myStage.getTimeline().setStreaming(false);
    assertThat(myStage.getTimeline().isStreaming()).isFalse();

    // Stopping tracking should not cause streaming.
    MemoryProfilerTestUtils
      .stopTrackingHelper(myStage, myTransportService, myTimer, -1, Status.NOT_ENABLED, true);
    assertThat(myStage.getTimeline().isStreaming()).isFalse();

    long infoStart = TimeUnit.MICROSECONDS.toNanos(5);
    MemoryProfilerTestUtils
      .startTrackingHelper(myStage, myTransportService, myTimer, infoStart, Status.SUCCESS, true);
    assertThat(myStage.getTimeline().isStreaming()).isTrue();
  }

  @Test
  public void testAllocationTrackingStateOnTransition() {
    assumePreO(true);
    myStage.enter();
    assertThat(myStage.isTrackingAllocations()).isFalse();

    long infoStart = TimeUnit.MICROSECONDS.toNanos(5);
    long infoEnd = TimeUnit.MICROSECONDS.toNanos(10);
    MemoryProfilerTestUtils
      .startTrackingHelper(myStage, myTransportService, myTimer, infoStart, Status.SUCCESS, true);
    assertThat(myStage.isTrackingAllocations()).isTrue();
    myStage.exit();
    myStage.enter();
    assertThat(myStage.isTrackingAllocations()).isTrue();

    MemoryProfilerTestUtils
      .stopTrackingHelper(myStage, myTransportService, myTimer, infoStart, Status.SUCCESS, true);
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
    MemoryProfilerTestUtils.heapDumpHelper(myStage, myTransportService,
                                           Memory.HeapDumpStatus.Status.FAILURE_UNKNOWN);
    assertThat(myStage.getCaptureSelection().getSelectedCapture()).isNull();

    MemoryProfilerTestUtils.heapDumpHelper(myStage, myTransportService,
                                           Memory.HeapDumpStatus.Status.IN_PROGRESS);
    assertThat(myStage.getCaptureSelection().getSelectedCapture()).isNull();

    MemoryProfilerTestUtils.heapDumpHelper(myStage, myTransportService,
                                           Memory.HeapDumpStatus.Status.UNSPECIFIED);
    assertThat(myStage.getCaptureSelection().getSelectedCapture()).isNull();

    // TODO need to add a mock heap dump here to test the success path
  }

  @Test
  public void testHeapDumpSetStreaming() {
    myStage.getTimeline().setStreaming(false);
    assertThat(myStage.getTimeline().isStreaming()).isFalse();
    myMockLoader.setReturnImmediateFuture(true);
    myStage.requestHeapDump();
    assertThat(myStage.getTimeline().isStreaming()).isTrue();
  }

  @Test
  public void defaultHeapSetTest() {
    String fakeClassName1 = "SAMPLE_CLASS1", fakeClassName2 = "SAMPLE_CLASS2";

    myMockLoader.setReturnImmediateFuture(true);

    FakeCaptureObject capture0 = new FakeCaptureObject.Builder().setHeapIdToNameMap(ImmutableMap.of(0, "default", 1, "app")).build();
    FakeInstanceObject instanceObject = new FakeInstanceObject.Builder(capture0, 1, fakeClassName1).setHeapId(0).build();
    capture0.addInstanceObjects(ImmutableSet.of(instanceObject));

    myStage.selectCaptureDuration(new CaptureDurationData<>(1, false, false, new CaptureEntry<CaptureObject>(new Object(), () -> capture0)),
                                  null);
    assertThat(myStage.getCaptureSelection().getSelectedCapture()).isEqualTo(capture0);
    assertThat(myStage.getCaptureSelection().getSelectedHeapSet()).isNotNull();
    assertThat(myStage.getCaptureSelection().getSelectedHeapSet().getName()).isEqualTo("default");

    FakeCaptureObject capture1 = new FakeCaptureObject.Builder().setHeapIdToNameMap(ImmutableMap.of(0, "default", 1, "app")).build();
    instanceObject = new FakeInstanceObject.Builder(capture1, 1, fakeClassName1).setHeapId(1).build();
    capture1.addInstanceObjects(ImmutableSet.of(instanceObject));

    myStage.selectCaptureDuration(new CaptureDurationData<>(1, false, false, new CaptureEntry<CaptureObject>(new Object(), () -> capture1)),
                                  null);
    assertThat(myStage.getCaptureSelection().getSelectedCapture()).isEqualTo(capture1);
    assertThat(myStage.getCaptureSelection().getSelectedHeapSet()).isNotNull();
    assertThat(myStage.getCaptureSelection().getSelectedHeapSet().getName()).isEqualTo("app");

    FakeCaptureObject capture2 = new FakeCaptureObject.Builder().setHeapIdToNameMap(ImmutableMap.of(0, "default", 1, "app")).build();
    instanceObject = new FakeInstanceObject.Builder(capture2, 1, fakeClassName1).setHeapId(0).build();
    FakeInstanceObject otherInstanceObject = new FakeInstanceObject.Builder(capture2, 2, fakeClassName2).setHeapId(1).build();
    capture2.addInstanceObjects(ImmutableSet.of(instanceObject, otherInstanceObject));

    myStage.selectCaptureDuration(new CaptureDurationData<>(1, false, false, new CaptureEntry<CaptureObject>(new Object(), () -> capture2)),
                                  null);
    assertThat(myStage.getCaptureSelection().getSelectedCapture()).isEqualTo(capture2);
    assertThat(myStage.getCaptureSelection().getSelectedHeapSet()).isNotNull();
    assertThat(myStage.getCaptureSelection().getSelectedHeapSet().getName()).isEqualTo("app");
  }

  @Test
  public void testSelectionRangeUpdateOnCaptureSelection() {
    long startTimeUs = 5;
    long endTimeUs = 10;
    FakeCaptureObject captureObject = new FakeCaptureObject.Builder().setStartTime(TimeUnit.MICROSECONDS.toNanos(startTimeUs))
      .setEndTime(TimeUnit.MICROSECONDS.toNanos(endTimeUs)).build();

    Range selectionRange = myStage.getTimeline().getSelectionRange();
    Object captureKey = new Object();
    myStage
      .selectCaptureDuration(new CaptureDurationData<>(1, false, false, new CaptureEntry<CaptureObject>(captureKey, () -> captureObject)),
                             null);
    assertThat(myStage.getCaptureSelection().getSelectedCapture()).isEqualTo(captureObject);
    assertThat((long)selectionRange.getMin()).isEqualTo(startTimeUs);
    assertThat((long)selectionRange.getMax()).isEqualTo(endTimeUs);
  }

  @Test
  public void testMemoryObjectSelection() {
    final String sampleClassName = "SAMPLE_CLASS1";
    FakeCaptureObject captureObject = new FakeCaptureObject.Builder().setStartTime(5).setEndTime(10).build();
    InstanceObject mockInstance =
      new FakeInstanceObject.Builder(captureObject, 1, sampleClassName).setName("SAMPLE_INSTANCE")
        .setDepth(1).setShallowSize(2).setRetainedSize(3).build();
    captureObject.addInstanceObjects(Collections.singleton(mockInstance));

    Object captureKey = new Object();
    myStage
      .selectCaptureDuration(new CaptureDurationData<>(1, false, false, new CaptureEntry<CaptureObject>(captureKey, () -> captureObject)),
                             null);
    assertThat(myStage.getCaptureSelection().getSelectedCapture()).isEqualTo(captureObject);
    assertThat(myStage.getCaptureSelection().getSelectedHeapSet()).isNull();
    assertThat(myStage.getCaptureSelection().getClassGrouping()).isEqualTo(ARRANGE_BY_CLASS);
    assertThat(myStage.getCaptureSelection().getSelectedClassSet()).isNull();
    assertThat(myStage.getCaptureSelection().getSelectedInstanceObject()).isNull();
    myAspectObserver.assertAndResetCounts(0, 1, 0, 0, 0, 0, 0, 0);
    myMockLoader.runTask();
    myAspectObserver.assertAndResetCounts(0, 0, 1, 0, 1, 0, 0, 0);

    // Make sure the same capture selected shouldn't result in aspects getting raised again.
    myStage
      .selectCaptureDuration(new CaptureDurationData<>(1, false, false, new CaptureEntry<CaptureObject>(captureKey, () -> captureObject)),
                             null);
    myMockLoader.runTask();
    assertThat(myStage.getCaptureSelection().getSelectedCapture()).isEqualTo(captureObject);
    assertThat(myStage.getCaptureSelection().getSelectedHeapSet()).isNotNull();
    assertThat(myStage.getCaptureSelection().getClassGrouping()).isEqualTo(ARRANGE_BY_CLASS);
    assertThat(myStage.getCaptureSelection().getSelectedClassSet()).isNull();
    assertThat(myStage.getCaptureSelection().getSelectedInstanceObject()).isNull();
    myAspectObserver.assertAndResetCounts(0, 0, 0, 0, 0, 0, 0, 0);

    HeapSet heapSet = captureObject.getHeapSet(CaptureObject.DEFAULT_HEAP_ID);
    assertThat(heapSet).isNotNull();
    myStage.getCaptureSelection().selectHeapSet(heapSet);
    assertThat(myStage.getCaptureSelection().getSelectedCapture()).isEqualTo(captureObject);
    assertThat(myStage.getCaptureSelection().getSelectedHeapSet()).isEqualTo(heapSet);
    assertThat(myStage.getCaptureSelection().getClassGrouping()).isEqualTo(ARRANGE_BY_CLASS);
    assertThat(myStage.getCaptureSelection().getSelectedClassSet()).isNull();
    assertThat(myStage.getCaptureSelection().getSelectedInstanceObject()).isNull();
    myAspectObserver.assertAndResetCounts(0, 0, 0, 0, 0, 0, 0, 0);

    myStage.getCaptureSelection().selectHeapSet(heapSet);
    assertThat(myStage.getCaptureSelection().getSelectedCapture()).isEqualTo(captureObject);
    assertThat(myStage.getCaptureSelection().getSelectedHeapSet()).isEqualTo(heapSet);
    assertThat(myStage.getCaptureSelection().getClassGrouping()).isEqualTo(ARRANGE_BY_CLASS);
    assertThat(myStage.getCaptureSelection().getSelectedClassSet()).isNull();
    assertThat(myStage.getCaptureSelection().getSelectedInstanceObject()).isNull();
    myAspectObserver.assertAndResetCounts(0, 0, 0, 0, 0, 0, 0, 0);

    String filterString = "Filter";
    myStage.getCaptureSelection().getFilterHandler().setFilter(new Filter(filterString));
    assertThat(myStage.getCaptureSelection().getSelectedCapture()).isEqualTo(captureObject);
    assertThat(myStage.getCaptureSelection().getSelectedHeapSet()).isEqualTo(heapSet);
    assertThat(myStage.getCaptureSelection().getClassGrouping()).isEqualTo(ARRANGE_BY_CLASS);
    assertThat(myStage.getCaptureSelection().getSelectedClassSet()).isNull();
    assertThat(myStage.getCaptureSelection().getSelectedInstanceObject()).isNull();
    myAspectObserver.assertAndResetCounts(0, 0, 0, 0, 0, 0, 0, 0);

    assertThat(myStage.getCaptureSelection().getClassGrouping()).isEqualTo(ARRANGE_BY_CLASS);
    myStage.getCaptureSelection().setClassGrouping(ARRANGE_BY_PACKAGE);
    // Retain Filter after Grouping change
    assertThat(myStage.getCaptureSelection().getFilterHandler().getFilter().getFilterString()).isEqualTo(filterString);
    // Reset Filter
    myStage.getCaptureSelection().getFilterHandler().setFilter(Filter.EMPTY_FILTER);
    assertThat(myStage.getCaptureSelection().getClassGrouping()).isEqualTo(ARRANGE_BY_PACKAGE);
    myAspectObserver.assertAndResetCounts(0, 0, 0, 1, 0, 0, 0, 0);

    myStage.getCaptureSelection().setClassGrouping(ARRANGE_BY_CLASS);
    assertThat(myStage.getCaptureSelection().getClassGrouping()).isEqualTo(ARRANGE_BY_CLASS);
    myAspectObserver.assertAndResetCounts(0, 0, 0, 1, 0, 0, 0, 0);

    ClassifierSet classifierSet = heapSet.findContainingClassifierSet(mockInstance);
    assertThat(classifierSet).isInstanceOf(ClassSet.class);
    ClassSet classSet = (ClassSet)classifierSet;
    myStage.getCaptureSelection().selectClassSet(classSet);
    assertThat(myStage.getCaptureSelection().getSelectedCapture()).isEqualTo(captureObject);
    assertThat(myStage.getCaptureSelection().getSelectedHeapSet()).isEqualTo(heapSet);
    assertThat(myStage.getCaptureSelection().getClassGrouping()).isEqualTo(ARRANGE_BY_CLASS);
    assertThat(myStage.getCaptureSelection().getSelectedClassSet()).isEqualTo(classSet);
    assertThat(myStage.getCaptureSelection().getSelectedInstanceObject()).isNull();
    myAspectObserver.assertAndResetCounts(0, 0, 0, 0, 0, 1, 0, 0);

    myStage.getCaptureSelection().selectClassSet(classSet);
    assertThat(myStage.getCaptureSelection().getSelectedCapture()).isEqualTo(captureObject);
    assertThat(myStage.getCaptureSelection().getSelectedHeapSet()).isEqualTo(heapSet);
    assertThat(myStage.getCaptureSelection().getClassGrouping()).isEqualTo(ARRANGE_BY_CLASS);
    assertThat(myStage.getCaptureSelection().getSelectedClassSet()).isEqualTo(classSet);
    assertThat(myStage.getCaptureSelection().getSelectedInstanceObject()).isNull();
    myAspectObserver.assertAndResetCounts(0, 0, 0, 0, 0, 0, 0, 0);

    myStage.getCaptureSelection().selectInstanceObject(mockInstance);
    assertThat(myStage.getCaptureSelection().getSelectedCapture()).isEqualTo(captureObject);
    assertThat(myStage.getCaptureSelection().getSelectedHeapSet()).isEqualTo(heapSet);
    assertThat(myStage.getCaptureSelection().getClassGrouping()).isEqualTo(ARRANGE_BY_CLASS);
    assertThat(myStage.getCaptureSelection().getSelectedClassSet()).isEqualTo(classSet);
    assertThat(myStage.getCaptureSelection().getSelectedInstanceObject()).isEqualTo(mockInstance);
    myAspectObserver.assertAndResetCounts(0, 0, 0, 0, 0, 0, 1, 0);

    myStage.getCaptureSelection().selectInstanceObject(mockInstance);
    assertThat(myStage.getCaptureSelection().getSelectedCapture()).isEqualTo(captureObject);
    assertThat(myStage.getCaptureSelection().getSelectedHeapSet()).isEqualTo(heapSet);
    assertThat(myStage.getCaptureSelection().getClassGrouping()).isEqualTo(ARRANGE_BY_CLASS);
    assertThat(myStage.getCaptureSelection().getSelectedClassSet()).isEqualTo(classSet);
    assertThat(myStage.getCaptureSelection().getSelectedInstanceObject()).isEqualTo(mockInstance);
    myAspectObserver.assertAndResetCounts(0, 0, 0, 0, 0, 0, 0, 0);

    // Test the reverse direction, to make sure children MemoryObjects are nullified in the selection.
    myStage.getCaptureSelection().selectClassSet(null);
    assertThat(myStage.getCaptureSelection().getSelectedCapture()).isEqualTo(captureObject);
    assertThat(myStage.getCaptureSelection().getSelectedHeapSet()).isEqualTo(heapSet);
    assertThat(myStage.getCaptureSelection().getClassGrouping()).isEqualTo(ARRANGE_BY_CLASS);
    assertThat(myStage.getCaptureSelection().getSelectedClassSet()).isNull();
    assertThat(myStage.getCaptureSelection().getSelectedInstanceObject()).isNull();
    myAspectObserver.assertAndResetCounts(0, 0, 0, 0, 0, 1, 1, 0);

    // However, if a selection didn't change (e.g. null => null), it shouldn't trigger an aspect change either.
    myStage.getCaptureSelection().selectHeapSet(null);
    assertThat(myStage.getCaptureSelection().getSelectedCapture()).isEqualTo(captureObject);
    assertThat(myStage.getCaptureSelection().getSelectedHeapSet()).isNull();
    assertThat(myStage.getCaptureSelection().getClassGrouping()).isEqualTo(ARRANGE_BY_CLASS);
    assertThat(myStage.getCaptureSelection().getSelectedClassSet()).isNull();
    assertThat(myStage.getCaptureSelection().getSelectedInstanceObject()).isNull();
    myAspectObserver.assertAndResetCounts(0, 0, 0, 0, 1, 0, 0, 0);
  }

  @Test
  public void testSelectNewCaptureWhileLoading() {
    CaptureObject mockCapture1 = new FakeCaptureObject.Builder().setCaptureName("SAMPLE_CAPTURE1").setStartTime(5).setEndTime(10).build();
    CaptureObject mockCapture2 = new FakeCaptureObject.Builder().setCaptureName("SAMPLE_CAPTURE2").setStartTime(10).setEndTime(15).build();

    myStage
      .selectCaptureDuration(new CaptureDurationData<>(1, false, false, new CaptureEntry<>(new Object(), () -> mockCapture1)),
                             null);
    assertThat(myStage.getCaptureSelection().getSelectedCapture()).isEqualTo(mockCapture1);
    myAspectObserver.assertAndResetCounts(0, 1, 0, 0, 0, 0, 0, 0);

    // Make sure selecting a new capture while the first one is loading will select the new one
    myStage
      .selectCaptureDuration(new CaptureDurationData<>(1, false, false, new CaptureEntry<>(new Object(), () -> mockCapture2)),
                             null);
    assertThat(myStage.getCaptureSelection().getSelectedCapture()).isEqualTo(mockCapture2);
    myAspectObserver.assertAndResetCounts(0, 1, 0, 0, 0, 0, 0, 0);

    myMockLoader.runTask();
    assertThat(myStage.getCaptureSelection().getSelectedCapture()).isEqualTo(mockCapture2);
    myAspectObserver.assertAndResetCounts(0, 0, 1, 0, 0, 0, 0, 0);
  }

  @Test
  public void testCaptureLoadingFailure() {
    long startTimeUs = 5;
    long endTimeUs = 10;
    CaptureObject mockCapture1 = new FakeCaptureObject.Builder()
      .setCaptureName("SAMPLE_CAPTURE1")
      .setStartTime(TimeUnit.MICROSECONDS.toNanos(startTimeUs))
      .setEndTime(TimeUnit.MICROSECONDS.toNanos(endTimeUs))
      .setError(true)
      .build();
    Range selectionRange = myStage.getTimeline().getSelectionRange();

    myStage
      .selectCaptureDuration(new CaptureDurationData<>(1, false, false, new CaptureEntry<>(new Object(), () -> mockCapture1)),
                             null);
    assertThat(myStage.getCaptureSelection().getSelectedCapture()).isEqualTo(mockCapture1);

    assertThat((long)selectionRange.getMin()).isEqualTo(startTimeUs);
    assertThat((long)selectionRange.getMax()).isEqualTo(endTimeUs);
    myAspectObserver.assertAndResetCounts(0, 1, 0, 0, 0, 0, 0, 0);

    myMockLoader.runTask();
    assertThat(myStage.getCaptureSelection().getSelectedCapture()).isEqualTo(null);
    assertThat(selectionRange.isEmpty()).isTrue();
    myAspectObserver.assertAndResetCounts(0, 1, 1, 0, 0, 0, 0, 0);
  }

  @Test
  public void implicitSelectionOfNativeAllocationArtifactProtoIsMadePostRecording() {
    StudioProfilers profiler = new StudioProfilers(
      new ProfilerClient(myGrpcChannel.getChannel()),
      myIdeProfilerServices,
      myTimer
    );

    SessionsManager manager = profiler.getSessionsManager();
    Common.Device device = Common.Device.newBuilder().setDeviceId(1).setState(Common.Device.State.ONLINE).build();
    Common.Process process1 = Common.Process.newBuilder().setPid(10).setState(Common.Process.State.ALIVE).build();

    Long session1Timestamp = 1L;
    myTimer.setCurrentTimeNs(session1Timestamp);
    manager.beginSession(1, device, process1);
    manager.update();
    manager.endCurrentSession();
    manager.update();
    Common.Session session1 = manager.getSelectedSession();

    long nativeHeapTimestamp = 30L;
    Memory.MemoryNativeSampleData nativeHeapInfo = Memory.MemoryNativeSampleData.newBuilder().setStartTime(nativeHeapTimestamp).setEndTime(
      nativeHeapTimestamp + 1).build();
    Common.Event.Builder nativeHeapData =
      ProfilersTestData.generateMemoryNativeSampleData(nativeHeapTimestamp, nativeHeapTimestamp + 1, nativeHeapInfo);
    myTransportService.addEventToStream(device.getDeviceId(), nativeHeapData.setPid(session1.getPid()).build());
    manager.update();

    // Makes sure native memory allocation artifact proto is implicitly selected after recording
    assertThat(manager.getSelectedArtifactProto()).isInstanceOf(Memory.MemoryNativeSampleData.class);
  }

  @Test
  public void testTooltipLegends() {
    long time = TimeUnit.MICROSECONDS.toNanos(2);
    Memory.MemoryUsageData data = Memory.MemoryUsageData.newBuilder()
      .setJavaMem(10)
      .setNativeMem(20)
      .setGraphicsMem(30)
      .setStackMem(40)
      .setCodeMem(50)
      .setOthersMem(60)
      .build();
    myTransportService.addEventToStream(FAKE_DEVICE_ID, ProfilersTestData.generateMemoryUsageData(2, data)
      .setPid(FAKE_PROCESS.getPid()).build());
    myStage.getDetailedMemoryUsage().getMemoryRange().set(0, 1024 /* 1 KB */); // Sets the scale B/KB/MB/GB.
    MemoryStageLegends legends = myStage.getTooltipLegends();
    myStage.getTimeline().getTooltipRange().set(time, time);
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
    assumePreO(false);
    long time = TimeUnit.MICROSECONDS.toNanos(2);
    AllocationsInfo liveAllocInfo = AllocationsInfo.newBuilder().setStartTime(0).setEndTime(Long.MAX_VALUE).setLegacy(false).build();
    MemoryData memoryData = MemoryData.getDefaultInstance();
    myTransportService.addEventToStream(
      FAKE_DEVICE_ID, ProfilersTestData.generateMemoryAllocationInfoData(0, FAKE_PROCESS.getPid(), liveAllocInfo).build());
    myTransportService.addEventToStream(
      FAKE_DEVICE_ID, ProfilersTestData.generateMemoryAllocStatsData(FAKE_PROCESS.getPid(), 0, 100).build());
    myTransportService.addEventToStream(
      FAKE_DEVICE_ID, ProfilersTestData
        .generateMemoryAllocSamplingData(FAKE_PROCESS.getPid(), 0, MainMemoryProfilerStage.LiveAllocationSamplingMode.FULL.getValue())
        .build());
    MemoryStageLegends legends = myStage.getTooltipLegends();
    myStage.getTimeline().getTooltipRange().set(time, time);
    assertThat(legends.getObjectsLegend().getName()).isEqualTo("Allocated");
    assertThat(legends.getObjectsLegend().getValue()).isEqualTo("100");

    // Now change sampling mode to sampled, the Allocated legend should show "N/A"
    myTransportService.addEventToStream(
      FAKE_DEVICE_ID, ProfilersTestData.generateMemoryAllocStatsData(FAKE_PROCESS.getPid(), 1, 200).build());
    myTransportService.addEventToStream(
      FAKE_DEVICE_ID, ProfilersTestData
        .generateMemoryAllocSamplingData(FAKE_PROCESS.getPid(), 1, MainMemoryProfilerStage.LiveAllocationSamplingMode.SAMPLED.getValue())
        .build());
    assertThat(legends.getObjectsLegend().getValue()).isEqualTo("N/A");

    // Now change sampling mode to none, the Allocated legend should still show "N/A"
    myTransportService.addEventToStream(
      FAKE_DEVICE_ID, ProfilersTestData
        .generateMemoryAllocSamplingData(FAKE_PROCESS.getPid(), 2, MainMemoryProfilerStage.LiveAllocationSamplingMode.NONE.getValue())
        .build());
    assertThat(legends.getObjectsLegend().getValue()).isEqualTo("N/A");

    // Value should update once sampling mode is set back to full.
    myTransportService.addEventToStream(
      FAKE_DEVICE_ID, ProfilersTestData
        .generateMemoryAllocSamplingData(FAKE_PROCESS.getPid(), 3, MainMemoryProfilerStage.LiveAllocationSamplingMode.FULL.getValue())
        .build());
    assertThat(legends.getObjectsLegend().getValue()).isEqualTo("200");
  }

  @Test
  public void testLegendsOrder() {
    MemoryStageLegends legends = myStage.getLegends();
    List<String> legendNames = ContainerUtil.map(legends.getLegends(), legend -> legend.getName());
    assertThat(legendNames).containsExactly("Total", "Java", "Native", "Graphics", "Stack", "Code", "Others", "Allocated")
      .inOrder();
  }

  @Test
  public void testTooltipLegendsOrder() {
    MemoryStageLegends legends = myStage.getTooltipLegends();
    List<String> legendNames = ContainerUtil.map(legends.getLegends(), legend -> legend.getName());
    assertThat(legendNames)
      .containsExactly("Others", "Code", "Stack", "Graphics", "Native", "Java", "Allocated", "Tracking", "GC Duration", "Total")
      .inOrder();
  }

  @Test
  public void testSelectLatestCaptureDisabled() {
    myStage.enableSelectLatestCapture(false, null);
    myMockLoader.setReturnImmediateFuture(true);
    assertThat(myStage.getCaptureSelection().getSelectedCapture()).isNull();

    // Start+Stop a capture session (allocation tracking)
    long infoStart = TimeUnit.MICROSECONDS.toNanos(5);
    long infoEnd = TimeUnit.MICROSECONDS.toNanos(10);
    MemoryProfilerTestUtils
      .startTrackingHelper(myStage, myTransportService, myTimer, infoStart, Status.SUCCESS, true);
    MemoryProfilerTestUtils
      .stopTrackingHelper(myStage, myTransportService, myTimer, infoStart, Status.SUCCESS, true);

    assertThat(myStage.isTrackingAllocations()).isEqualTo(false);
    assertThat(myStage.getCaptureSelection().getSelectedCapture()).isEqualTo(null);
    myAspectObserver.assertAndResetCounts(2, 0, 0, 0, 0, 0, 0, 0);

    // Advancing time (data range) - MemoryProfilerStage should not select the capture since the feature is disabled.
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(myStage.getCaptureSelection().getSelectedCapture()).isNull();
    myAspectObserver.assertAndResetCounts(0, 0, 0, 0, 0, 0, 0, 0);
  }

  @Test
  public void testSelectLatestCaptureEnabled() {
    assumePreO(true);
    myStage.enableSelectLatestCapture(true, MoreExecutors.directExecutor());
    myMockLoader.setReturnImmediateFuture(true);
    assertThat(myStage.getCaptureSelection().getSelectedCapture()).isNull();

    // Start+Stop a capture session (allocation tracking)
    long infoStart = TimeUnit.MICROSECONDS.toNanos(5);
    long infoEnd = TimeUnit.MICROSECONDS.toNanos(10);
    MemoryProfilerTestUtils
      .startTrackingHelper(myStage, myTransportService, myTimer, infoStart, Status.SUCCESS, true);
    myTransportService.addFile(Long.toString(infoStart), ByteString.copyFrom(FAKE_ALLOC_BUFFER));
    MemoryProfilerTestUtils
      .stopTrackingHelper(myStage, myTransportService, myTimer, infoStart, Status.SUCCESS, true);

    assertThat(myStage.isTrackingAllocations()).isEqualTo(false);
    assertThat(myStage.getCaptureSelection().getSelectedCapture()).isInstanceOf(LegacyAllocationCaptureObject.class);
    myAspectObserver.assertAndResetCounts(2, 1, 1, 0, 1, 0, 0, 0);
  }

  @Test
  public void testHasUserUsedCaptureViaHeapDump() {
    assertThat(myStage.hasUserUsedMemoryCapture()).isFalse();
    myStage.requestHeapDump();
    assertThat(myStage.hasUserUsedMemoryCapture()).isTrue();
  }

  @Test
  public void testHasUserUsedCaptureViaLegacyTracking() {
    assertThat(myStage.hasUserUsedMemoryCapture()).isFalse();

    long infoStart = TimeUnit.MICROSECONDS.toNanos(5);
    MemoryProfilerTestUtils
      .startTrackingHelper(myStage, myTransportService, myTimer, infoStart, Status.SUCCESS, true);
    assertThat(myStage.hasUserUsedMemoryCapture()).isTrue();
  }

  @Test
  public void testHasUserUsedCaptureViaSelection() {
    assertThat(myStage.hasUserUsedMemoryCapture()).isFalse();
    long infoStart = TimeUnit.MICROSECONDS.toNanos(5);
    long infoEnd = TimeUnit.MICROSECONDS.toNanos(10);
    AllocationsInfo info = AllocationsInfo.newBuilder().setStartTime(infoStart).setEndTime(infoEnd).setLegacy(true).build();

    myTransportService.addEventToStream(
      FAKE_DEVICE_ID, ProfilersTestData.generateMemoryAllocationInfoData(infoStart, FAKE_PROCESS.getPid(), info).build());
    myStage.getRangeSelectionModel().set(5, 10);
    assertThat(myStage.hasUserUsedMemoryCapture()).isTrue();
  }

  @Test
  public void testAllocationSamplingRateUpdates() {
    int[] samplingAspectChange = {0};
    myStage.getAspect().addDependency(myAspectObserver).onChange(MemoryProfilerAspect.LIVE_ALLOCATION_SAMPLING_MODE,
                                                                 () -> samplingAspectChange[0]++);

    // Ensure that the sampling mode is none if it's never set.
    assertThat(myStage.getLiveAllocationSamplingMode()).isEqualTo(MainMemoryProfilerStage.LiveAllocationSamplingMode.NONE);
    assertThat(samplingAspectChange[0]).isEqualTo(0);

    // Ensure that advancing the timer does not change the mode if there are no AllocationSamplingRange.
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(myStage.getLiveAllocationSamplingMode()).isEqualTo(MainMemoryProfilerStage.LiveAllocationSamplingMode.NONE);
    assertThat(samplingAspectChange[0]).isEqualTo(0);

    Memory.AllocationsInfo info = Memory.AllocationsInfo.newBuilder().setStartTime(0).setEndTime(Long.MAX_VALUE).build();
    myTransportService.addEventToStream(
      FAKE_DEVICE_ID,
      ProfilersTestData.generateMemoryAllocationInfoData(0, FAKE_PROCESS.getPid(), info).setIsEnded(false).build());
    myTransportService.addEventToStream(
      FAKE_DEVICE_ID, ProfilersTestData.generateMemoryAllocSamplingData(FAKE_PROCESS.getPid(), 1, 1).build());
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(myStage.getLiveAllocationSamplingMode()).isEqualTo(MainMemoryProfilerStage.LiveAllocationSamplingMode.FULL);
    assertThat(samplingAspectChange[0]).isEqualTo(1);

    myTransportService.addEventToStream(
      FAKE_DEVICE_ID, ProfilersTestData.generateMemoryAllocSamplingData(FAKE_PROCESS.getPid(), 1, 0).build());
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(myStage.getLiveAllocationSamplingMode()).isEqualTo(MainMemoryProfilerStage.LiveAllocationSamplingMode.NONE);
    assertThat(samplingAspectChange[0]).isEqualTo(2);
  }

  @Test
  public void testAllocationSamplingRateCorrectlyInitialized() {
    // If the sampling mode is already available from the memory service, make sure the memory stage is correctly initialized to that value.
    Memory.AllocationsInfo info = Memory.AllocationsInfo.newBuilder().setStartTime(0).setEndTime(Long.MAX_VALUE).build();
    myTransportService.addEventToStream(
      FAKE_DEVICE_ID,
      ProfilersTestData.generateMemoryAllocationInfoData(0, FAKE_PROCESS.getPid(), info).setIsEnded(false).build());
    myTransportService.addEventToStream(
      FAKE_DEVICE_ID, ProfilersTestData.generateMemoryAllocSamplingData(FAKE_PROCESS.getPid(), 0, 1).build());
    MainMemoryProfilerStage stage = new MainMemoryProfilerStage(myProfilers, myMockLoader);
    assertThat(stage.getLiveAllocationSamplingMode()).isEqualTo(MainMemoryProfilerStage.LiveAllocationSamplingMode.FULL);
  }

  @Test
  public void testAllocationSamplingModePersistsAcrossStages() {
    // Ensure that the default is none.
    assertThat(myStage.getLiveAllocationSamplingMode()).isEqualTo(MainMemoryProfilerStage.LiveAllocationSamplingMode.NONE);
    myStage.requestLiveAllocationSamplingModeUpdate(MainMemoryProfilerStage.LiveAllocationSamplingMode.FULL);

    MainMemoryProfilerStage newStage1 = new MainMemoryProfilerStage(myProfilers, myMockLoader);
    assertThat(newStage1.getLiveAllocationSamplingMode()).isEqualTo(MainMemoryProfilerStage.LiveAllocationSamplingMode.NONE);
    newStage1.requestLiveAllocationSamplingModeUpdate(MainMemoryProfilerStage.LiveAllocationSamplingMode.NONE);

    MainMemoryProfilerStage newStage2 = new MainMemoryProfilerStage(myProfilers, myMockLoader);
    assertThat(newStage2.getLiveAllocationSamplingMode()).isEqualTo(MainMemoryProfilerStage.LiveAllocationSamplingMode.NONE);
  }

  @Test
  public void testClassifierComboBoxModel() {
    final int EXPECTED_SIZE = 3;
    // Default no capture object means no filtering.
    assertThat(myStage.getCaptureSelection().getClassGroupingModel().getSize()).isEqualTo(5);
    // Update is called on selecting a capture object
    FakeCaptureObject capture = new FakeCaptureObject.Builder().setHeapIdToNameMap(ImmutableMap.of(0, "default", 1, "app")).build();
    FakeInstanceObject instanceObject = new FakeInstanceObject.Builder(capture, 1, "class").setHeapId(0).build();
    capture.addInstanceObjects(ImmutableSet.of(instanceObject));
    myStage.selectCaptureDuration(new CaptureDurationData<>(1, false, false, new CaptureEntry<CaptureObject>(new Object(), () -> capture)),
                                  null);
    assertThat(myStage.getCaptureSelection().getClassGroupingModel().getSize()).isEqualTo(EXPECTED_SIZE);
    for (int i = 0; i < EXPECTED_SIZE; i++) {
      assertThat(capture.isGroupingSupported(myStage.getCaptureSelection().getClassGroupingModel().getElementAt(i))).isTrue();
    }
    // Selecting no capture means no filtering.
    myStage.selectCaptureDuration(null, null);
    assertThat(myStage.getCaptureSelection().getClassGroupingModel().getSize()).isEqualTo(5);
  }

  @Test
  public void testStateSetOnEnterWhenOngoingCapture() {
    myTransportService.addEventToStream(FAKE_DEVICE_ID, Common.Event.newBuilder()
      .setPid(FAKE_PROCESS.getPid())
      .setCommandId(1)
      .setKind(Common.Event.Kind.TRACE_STATUS)
      .setTimestamp(myTimer.getCurrentTimeNs())
      .setGroupId(myTimer.getCurrentTimeNs())
      .setTraceStatus(Trace.TraceStatusData.newBuilder()
                        .setTraceStartStatus(Trace.TraceStartStatus.newBuilder()
                                               .setStartTimeNs(myTimer.getCurrentTimeNs())
                                               .setStatus(Trace.TraceStartStatus.Status.SUCCESS)
                                               .build()).build())
      .build());
    MainMemoryProfilerStage stage = new MainMemoryProfilerStage(myProfilers, myMockLoader);
    assertThat(stage.isTrackingAllocations()).isFalse();
    stage.enter();
    assertThat(stage.isTrackingAllocations()).isTrue();
  }

  @Test
  public void testNativeRecordingStopsWhenSessionDies() {
    assertThat(myStage.isTrackingAllocations()).isFalse();
    assertThat(((FakeFeatureTracker)myIdeProfilerServices.getFeatureTracker()).isTrackRecordAllocationsCalled()).isFalse();
    // Validate we enable tracking allocations
    myStage.toggleNativeAllocationTracking();
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(myStage.isTrackingAllocations()).isTrue();
    assertThat(((FakeFeatureTracker)myIdeProfilerServices.getFeatureTracker()).isTrackRecordAllocationsCalled()).isTrue();
    // Stopping the active session should stop allocation tracking
    myProfilers.getSessionsManager().endCurrentSession();
    // First tick sends the END_SESSION command and triggers the stop recording
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    // Second tick sends the STOP_NATIVE_HEAP_SAMPLE command and updates state
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(myStage.isTrackingAllocations()).isFalse();
  }

  @Test
  public void selectingHeapDumpGoesToSeparateStage() {
    myIdeProfilerServices.enableEventsPipeline(true);

    HeapDumpInfo info = HeapDumpInfo.newBuilder().build();

    myTransportService.addEventToStream(ProfilersTestData.SESSION_DATA.getStreamId(),
                                        ProfilersTestData.generateMemoryHeapDumpData(info.getStartTime(), info.getStartTime(), info)
                                          .setPid(ProfilersTestData.SESSION_DATA.getPid())
                                          .build());

    DataSeries<CaptureDurationData<? extends CaptureObject>> series =
      CaptureDataSeries.ofHeapDumpSamples(new ProfilerClient(myGrpcChannel.getChannel()), ProfilersTestData.SESSION_DATA,
                                          myIdeProfilerServices.getFeatureTracker(), myStage);
    List<SeriesData<CaptureDurationData<? extends CaptureObject>>> dataList = series.getDataForRange(new Range(0, Double.MAX_VALUE));

    myStage.selectCaptureDuration(dataList.get(0).value, null);
    assertThat(myProfilers.getStage()).isInstanceOf(MemoryCaptureStage.class);
    assertThat(myProfilers.getStage().getStageType()).isEqualTo(AndroidProfilerEvent.Stage.MEMORY_HEAP_DUMP_STAGE);
  }

  @Test
  public void selectingFinishedAllocationSessionSwitchesToAllocationStage() {
    CaptureObject obj = new FakeCaptureObject.Builder().build();
    CaptureEntry<CaptureObject> entry = new CaptureEntry<>(0, () -> obj);
    AllocationDurationData<CaptureObject> data = new AllocationDurationData<>(0, entry, 0.0, 1.0);
    myStage.selectCaptureDuration(data, null);
    assertThat(myProfilers.getStage()).isInstanceOf(AllocationStage.class);
  }

  private void assumePreO(boolean assumedPreO) {
    assumeTrue(myStage.getDeviceForSelectedSession().getFeatureLevel() < AndroidVersion.VersionCodes.O == assumedPreO);
  }

  @Parameterized.Parameters
  public static List<Integer> configs() {
    return Arrays.asList(AndroidVersion.VersionCodes.N, AndroidVersion.VersionCodes.O);
  }
}
