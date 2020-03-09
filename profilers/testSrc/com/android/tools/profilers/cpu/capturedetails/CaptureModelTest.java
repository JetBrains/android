/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.profilers.cpu.capturedetails;

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.adtui.model.FakeTimer;
import com.android.tools.adtui.model.Range;
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel;
import com.android.tools.idea.transport.faketransport.FakeTransportService;
import com.android.tools.perflib.vmtrace.ClockType;
import com.android.tools.profiler.proto.Cpu;
import com.android.tools.profilers.FakeFeatureTracker;
import com.android.tools.profilers.FakeIdeProfilerServices;
import com.android.tools.profilers.FakeProfilerService;
import com.android.tools.profilers.FakeTraceParser;
import com.android.tools.profilers.ProfilerClient;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.cpu.BaseCpuCapture;
import com.android.tools.profilers.cpu.CaptureNode;
import com.android.tools.profilers.cpu.CpuCapture;
import com.android.tools.profilers.cpu.CpuProfilerStage;
import com.android.tools.profilers.cpu.CpuProfilerTestUtils;
import com.android.tools.profilers.cpu.CpuThreadInfo;
import com.android.tools.profilers.cpu.FakeCpuService;
import com.android.tools.profilers.cpu.TraceParser;
import com.android.tools.profilers.cpu.nodemodel.JavaMethodModel;
import com.android.tools.profilers.event.FakeEventService;
import com.android.tools.profilers.memory.FakeMemoryService;
import com.android.tools.profilers.network.FakeNetworkService;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class CaptureModelTest {
  private final FakeTimer myTimer = new FakeTimer();
  private final FakeCpuService myCpuService = new FakeCpuService();

  @Rule
  public FakeGrpcChannel myGrpcChannel =
    new FakeGrpcChannel("CpuProfilerStageTestChannel", myCpuService, new FakeTransportService(myTimer), new FakeProfilerService(myTimer),
                        new FakeMemoryService(), new FakeEventService(), FakeNetworkService.newBuilder().build());

  private CaptureModel myModel;

  private CpuProfilerStage myStage;

  @Before
  public void setUp() {
    StudioProfilers profilers = new StudioProfilers(new ProfilerClient(myGrpcChannel.getName()), new FakeIdeProfilerServices(), myTimer);
    // One second must be enough for new devices (and processes) to be picked up
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    myStage = new CpuProfilerStage(profilers);
    myStage.getStudioProfilers().setStage(myStage);
    myStage.enter();
    myModel = new CaptureModel(myStage);
  }

  @Test
  public void testClockTypeGetsReset() {
    CaptureNode root = new CaptureNode(new JavaMethodModel("methodName", "className"));

    CpuThreadInfo info = new CpuThreadInfo(101, "main");
    TraceParser globalOnlyClockSupported = new FakeTraceParser(new Range(0, 30),
                                                               new ImmutableMap.Builder<CpuThreadInfo, CaptureNode>()
                                                                 .put(info, root)
                                                                 .build(), false);
    TraceParser dualClockSupported = new FakeTraceParser(new Range(0, 30),
                                                         new ImmutableMap.Builder<CpuThreadInfo, CaptureNode>()
                                                           .put(info, root)
                                                           .build(), true);

    CpuCapture globalOnlyCapture = new BaseCpuCapture(globalOnlyClockSupported, 200, Cpu.CpuTraceType.UNSPECIFIED_TYPE);
    CpuCapture dualCapture1 = new BaseCpuCapture(dualClockSupported, 200, Cpu.CpuTraceType.UNSPECIFIED_TYPE);
    CpuCapture dualCapture2 = new BaseCpuCapture(dualClockSupported, 200, Cpu.CpuTraceType.UNSPECIFIED_TYPE);
    myModel.setCapture(globalOnlyCapture);
    assertThat(myModel.getClockType()).isEqualTo(ClockType.GLOBAL);
    myModel.setClockType(ClockType.THREAD);
    assertThat(myModel.getClockType()).isEqualTo(ClockType.GLOBAL);

    myModel.setCapture(dualCapture1);
    assertThat(myModel.getClockType()).isEqualTo(ClockType.GLOBAL);
    myModel.setClockType(ClockType.THREAD);
    assertThat(myModel.getClockType()).isEqualTo(ClockType.THREAD);

    // If we set a capture that supports dual clock we don't change the clock.
    myModel.setCapture(dualCapture2);
    assertThat(myModel.getClockType()).isEqualTo(ClockType.THREAD);

    // If we set a capture that does not support dual clock we reset back to global.
    myModel.setCapture(globalOnlyCapture);
    assertThat(myModel.getClockType()).isEqualTo(ClockType.GLOBAL);

    // If we set a capture that does support dual clock after setting to global we stick to global.
    myModel.setCapture(dualCapture1);
    assertThat(myModel.getClockType()).isEqualTo(ClockType.GLOBAL);
  }

  @Test
  public void testDetailsFeatureTracking() {
    FakeFeatureTracker tracker = (FakeFeatureTracker)myStage.getStudioProfilers().getIdeServices().getFeatureTracker();

    assertThat(tracker.getLastCaptureDetailsType()).isNull();

    myModel.setDetails(CaptureDetails.Type.CALL_CHART);
    assertThat(tracker.getLastCaptureDetailsType()).isEqualTo(CaptureDetails.Type.CALL_CHART);

    myModel.setDetails(CaptureDetails.Type.FLAME_CHART);
    assertThat(tracker.getLastCaptureDetailsType()).isEqualTo(CaptureDetails.Type.FLAME_CHART);

    myModel.setDetails(CaptureDetails.Type.TOP_DOWN);
    assertThat(tracker.getLastCaptureDetailsType()).isEqualTo(CaptureDetails.Type.TOP_DOWN);

    myModel.setDetails(CaptureDetails.Type.BOTTOM_UP);
    assertThat(tracker.getLastCaptureDetailsType()).isEqualTo(CaptureDetails.Type.BOTTOM_UP);
  }

  @Test
  public void detailsFeatureTrackingIgnoresEventWithTheSameType() throws IOException, ExecutionException, InterruptedException {
    FakeFeatureTracker tracker = (FakeFeatureTracker)myStage.getStudioProfilers().getIdeServices().getFeatureTracker();
    myModel.setCapture(CpuProfilerTestUtils.getValidCapture());
    // Using BOTTOM_UP because CALL_CHART is the default
    myModel.setDetails(CaptureDetails.Type.BOTTOM_UP);
    assertThat(tracker.getLastCaptureDetailsType()).isEqualTo(CaptureDetails.Type.BOTTOM_UP);
    tracker.resetLastCaptureDetailsType();
    myModel.setDetails(CaptureDetails.Type.BOTTOM_UP);
    assertThat(tracker.getLastCaptureDetailsType()).isNull();
  }

  @Test
  public void detailsSetWithoutACaptureReturnsNullDetails() {
    myModel.setCapture(null);
    myModel.setDetails(CaptureDetails.Type.CALL_CHART);
    assertThat(myModel.getDetails()).isNull();
  }
}