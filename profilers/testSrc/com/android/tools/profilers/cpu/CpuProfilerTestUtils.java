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
package com.android.tools.profilers.cpu;

import static com.google.common.truth.Truth.assertThat;

import com.android.testutils.TestUtils;
import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.adtui.model.FakeTimer;
import com.android.tools.idea.transport.faketransport.FakeTransportService;
import com.android.tools.profiler.proto.Cpu;
import com.android.tools.profiler.proto.Cpu.CpuTraceType;
import com.android.tools.profiler.proto.CpuProfiler;
import com.android.tools.profiler.protobuf3jarjar.ByteString;
import com.android.tools.profilers.FakeIdeProfilerServices;
import com.android.tools.profilers.ProfilersTestData;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;

/**
 * Common constants and methods used across CPU profiler tests.
 * Should not be instantiated.
 */
public class CpuProfilerTestUtils {

  private static final String CPU_TRACES_DIR = "tools/adt/idea/profilers/testData/cputraces/";
  public static final String CPU_UI_TRACES_DIR = "tools/adt/idea/profilers-ui/testData/cputraces/";
  public static final String ATRACE_MISSING_DATA_FILE = CPU_TRACES_DIR + "atrace_processid_1.ctrace";
  public static final String ATRACE_DATA_FILE = CPU_TRACES_DIR + "atrace.ctrace";

  private CpuProfilerTestUtils() {
  }

  @NotNull
  public static ByteString readValidTrace() throws IOException {
    return traceFileToByteString("valid_trace.trace");
  }

  public static ByteString traceFileToByteString(@NotNull String filename) throws IOException {
    return traceFileToByteString(getTraceFile(filename));
  }

  public static ByteString traceFileToByteString(@NotNull File file) throws IOException {
    return ByteString.copyFrom(Files.readAllBytes(file.toPath()));
  }

  public static File getTraceFile(@NotNull String filename) {
    return TestUtils.getWorkspaceFile(CPU_TRACES_DIR + filename);
  }

  public static CpuCapture getValidCapture() throws IOException, ExecutionException, InterruptedException {
    return getCapture(readValidTrace(), CpuTraceType.ART);
  }

  public static CpuCapture getCapture(@NotNull String fullFileName) {
    try {
      File file = TestUtils.getWorkspaceFile(fullFileName);
      return getCapture(traceFileToByteString(file), CpuTraceType.ART);
    }
    catch (Exception e) {
      throw new RuntimeException("Failed with exception", e);
    }
  }

  public static CpuCapture getCapture(ByteString traceBytes, CpuTraceType profilerType) throws ExecutionException, InterruptedException {
    CpuCaptureParser parser = new CpuCaptureParser(new FakeIdeProfilerServices());
    return parser.parse(ProfilersTestData.SESSION_DATA, FakeCpuService.FAKE_TRACE_ID, traceBytes, profilerType).get();
  }

  /**
   * Note: the AspectObserver is passed in because Aspect dependencies are weak references. Instantiating a temporary instance in this
   * method would mean that it can be GC'd before the aspect has a chance to fire.
   */
  static CountDownLatch waitForProfilingStateChangeSequence(CpuProfilerStage stage,
                                                            AspectObserver observer,
                                                            CpuProfilerStage.CaptureState... profilingStates) {
    // We expect one state change going to STARTING
    CountDownLatch latch = new CountDownLatch(profilingStates.length);
    stage.getAspect().addDependency(observer).onChange(CpuProfilerAspect.CAPTURE_STATE, () -> {
      assertThat(stage.getCaptureState()).isEqualTo(profilingStates[profilingStates.length - (int)latch.getCount()]);
      latch.countDown();
      // We are done listening to the input change sequence, remove the observer.
      if (latch.getCount() == 0) {
        stage.getAspect().removeDependencies(observer);
      }
    });

    return latch;
  }

  /**
   * Note: the AspectObserver is passed in because Aspect dependencies are weak references. Instantiating a temporary instance in this
   * method would mean that it can be GC'd before the aspect has a chance to fire.
   */
  static CountDownLatch waitForParsingStartFinish(CpuProfilerStage stage, AspectObserver observer) {
    CountDownLatch parsingLatch = new CountDownLatch(2);
    stage.getCaptureParser().getAspect().addDependency(observer).onChange(CpuProfilerAspect.CAPTURE_PARSING, () -> {
      if (parsingLatch.getCount() == 2) {
        assertThat(stage.getCaptureParser().isParsing()).isTrue();
      }
      else {
        assertThat(stage.getCaptureParser().isParsing()).isFalse();
      }
      parsingLatch.countDown();
      if (parsingLatch.getCount() == 0) {
        stage.getCaptureParser().getAspect().removeDependencies(observer);
      }
    });

    return parsingLatch;
  }

  /**
   * Convenience method for starting, stopping, and parsing a capture successfully.
   * <p>
   * Note that a CpuTraceInfo will be auto-generated and added to the cpu service id'd by the current timer's timestamp. If this method
   * is called repeatedly in a single test, it is up to the caller to make sure to update the timestamp to not override the previously added
   * trace info.
   */
  static void captureSuccessfully(CpuProfilerStage stage,
                                  FakeCpuService cpuService,
                                  FakeTransportService transportService,
                                  ByteString traceContent) throws InterruptedException {
    // Start a successful capture
    startCapturing(stage, cpuService, true);
    stopCapturing(stage, cpuService, transportService, true, traceContent);
    assertThat(stage.getCapture()).isNotNull();
  }

  /**
   * This is a convenience method to start a capture successfully.
   * It sets and checks all the necessary states in the service and call {@link CpuProfilerStage#startCapturing}.
   * <p>
   * Note that a CpuTraceInfo will be auto-generated and added to the cpu service id'd by the current timer's timestamp. If this method
   * is called repeatedly in a single test, it is up to the caller to make sure to update the timestamp to not override the previously added
   * trace info.
   */
  static void startCapturing(CpuProfilerStage stage, FakeCpuService cpuService, boolean success)
    throws InterruptedException {
    assertThat(stage.getCaptureState()).isEqualTo(CpuProfilerStage.CaptureState.IDLE);
    cpuService.setStartProfilingStatus(success
                                       ? com.android.tools.profiler.proto.CpuProfiler.CpuProfilingAppStartResponse.Status.SUCCESS
                                       : CpuProfiler.CpuProfilingAppStartResponse.Status.FAILURE);
    CountDownLatch latch;
    AspectObserver observer = new AspectObserver();
    if (success) {
      latch = waitForProfilingStateChangeSequence(stage, observer, CpuProfilerStage.CaptureState.STARTING,
                                                  CpuProfilerStage.CaptureState.CAPTURING);
    }
    else {
      latch =
        waitForProfilingStateChangeSequence(stage, observer, CpuProfilerStage.CaptureState.STARTING, CpuProfilerStage.CaptureState.IDLE);
    }
    stage.startCapturing();
    latch.await();

    if (success) {
      long traceId = stage.getStudioProfilers().getUpdater().getTimer().getCurrentTimeNs();
      // Inserts an in-progress trace info object, which the stage will see on the next time update.
      Cpu.CpuTraceInfo traceInfo = Cpu.CpuTraceInfo.newBuilder()
        .setTraceId(traceId)
        .setFromTimestamp(traceId)
        .setToTimestamp(-1)
        .setConfiguration(Cpu.CpuTraceConfiguration.newBuilder()
                            .setUserOptions(stage.getProfilerConfigModel().getProfilingConfiguration().toProto()))
        .build();
      cpuService.addTraceInfo(traceInfo);
      stage.getStudioProfilers().getUpdater().getTimer().tick(FakeTimer.ONE_SECOND_IN_NS);
    }
  }

  /**
   * This is a convenience method to checking the stopping and selecting (if successful) of a capture. Also, it verifies the
   * {@link CpuCaptureParser} is parsing the capture after we stop capturing.
   * <p>
   * Note that a CpuTraceInfo will be auto-generated and added to the cpu service id'd by the current timer's timestamp, so it is important
   * for the caller to NOT update the timer's timestamp between a start/stop capture to allow this method to replace the existing
   * in-progress trace info. However, if this method is called repeatedly in a single test, it is up to the caller to make sure to update
   * the timestamp to not override the previously added trace info.
   */
  static void stopCapturing(CpuProfilerStage stage,
                            FakeCpuService cpuService,
                            FakeTransportService transportService,
                            boolean success,
                            ByteString traceContent)
    throws InterruptedException {
    // Trace id is needed for the stop response.
    long traceId = stage.getStudioProfilers().getUpdater().getTimer().getCurrentTimeNs();
    cpuService.setTraceId(traceId);
    cpuService.setStopProfilingStatus(success
                                      ? CpuProfiler.CpuProfilingAppStopResponse.Status.SUCCESS
                                      : CpuProfiler.CpuProfilingAppStopResponse.Status.STOP_COMMAND_FAILED);

    // Wait for the stop request to finish and the CpuProfilerStage calls back on the main thread.
    // TODO: this is currently dependent on the implementation that the stopCapturingCallback is invoked via the main thread, we should
    // investigate if there is a better way to wait based on state changes.
    CountDownLatch stopLatch = new CountDownLatch(1);
    ((FakeIdeProfilerServices)stage.getStudioProfilers().getIdeServices()).setOnExecute(() -> {
      stopLatch.countDown();
    });
    stage.stopCapturing();
    stopLatch.await();

    // Inserts a trace info object, which the stage will see on the next time update and go through the fetching and parsing logic
    Cpu.CpuTraceInfo traceInfo = Cpu.CpuTraceInfo.newBuilder()
      .setTraceId(traceId)
      .setFromTimestamp(traceId)
      .setToTimestamp(traceId + 1)
      .setConfiguration(Cpu.CpuTraceConfiguration.newBuilder()
                          .setUserOptions(stage.getProfilerConfigModel().getProfilingConfiguration().toProto()))
      .build();
    cpuService.addTraceInfo(traceInfo);
    transportService.addFile(Long.toString(traceId), traceContent);

    // If the capture is unsuccessful, stage goes back to IDLE, otherwise STOPPING and wait for the incoming TraceInfo.
    if (!success) {
      assertThat(stage.getCaptureState()).isEqualTo(CpuProfilerStage.CaptureState.IDLE);
      assertThat(stage.getCapture()).isNull();
    }
    else {
      assertThat(stage.getCaptureState()).isEqualTo(CpuProfilerStage.CaptureState.STOPPING);
    }

    // If the trace is empty, then parsing will not happen.
    if (traceContent == null || traceContent.isEmpty()) {
      stage.getStudioProfilers().getUpdater().getTimer().tick(FakeTimer.ONE_SECOND_IN_NS);
    }
    else {
      AspectObserver observer = new AspectObserver();
      CountDownLatch parsingLatch = waitForParsingStartFinish(stage, observer);
      stage.getStudioProfilers().getUpdater().getTimer().tick(FakeTimer.ONE_SECOND_IN_NS);
      parsingLatch.await();
    }
  }
}
