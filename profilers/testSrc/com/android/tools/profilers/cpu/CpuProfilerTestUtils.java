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
import com.android.tools.idea.protobuf.ByteString;
import com.android.tools.idea.transport.faketransport.FakeTransportService;
import com.android.tools.idea.transport.faketransport.commands.StartTrace;
import com.android.tools.idea.transport.faketransport.commands.StopTrace;
import com.android.tools.profiler.proto.Commands;
import com.android.tools.profiler.proto.Trace;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.cpu.config.ProfilingConfiguration.TraceType;
import com.android.tools.profilers.sessions.SessionsManager;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;
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
  public static final String ATRACE_DATA_FILE = CPU_TRACES_DIR + "atrace.ctrace";
  public static final long FAKE_TRACE_ID = 6L;
  public static final int FAKE_STOPPING_DURATION_MS = 123;

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
    return TestUtils.resolveWorkspacePath(CPU_TRACES_DIR + filename).toFile();
  }

  public static CpuCapture getValidCapture(StudioProfilers profilers) throws ExecutionException, InterruptedException {
    return getCapture(profilers, getTraceFile("valid_trace.trace"), TraceType.ART);
  }

  public static CpuCapture getCapture(StudioProfilers profilers, @NotNull String fullFileName) {
    try {
      File file = TestUtils.resolveWorkspacePath(fullFileName).toFile();
      return getCapture(profilers, file, TraceType.ART);
    }
    catch (Exception e) {
      throw new RuntimeException("Failed with exception", e);
    }
  }

  public static CompletableFuture<CpuCapture> getCaptureFuture(StudioProfilers profilers, File traceFile, TraceType profilerType) {
    CpuCaptureParser parser = new CpuCaptureParser(profilers);
    return parser.parse(traceFile, FAKE_TRACE_ID, profilerType, 0, "", x -> {});
  }

  public static CompletableFuture<CpuCapture> getCaptureFuture(StudioProfilers profilers,
                                                               File traceFile,
                                                               TraceType profilerType,
                                                               int processIdHint,
                                                               String processNameHint) {
    CpuCaptureParser parser = new CpuCaptureParser(profilers);
    return parser.parse(traceFile, FAKE_TRACE_ID, profilerType, processIdHint, processNameHint, x -> {});
  }

  public static CpuCapture getCapture(StudioProfilers profilers, File traceFile, TraceType profilerType)
    throws ExecutionException, InterruptedException {
    return getCaptureFuture(profilers, traceFile, profilerType, 0, "").get();
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
   *
   * @return the capture's trace ID.
   */
  static long captureSuccessfully(CpuProfilerStage stage,
                                  FakeTransportService transportService,
                                  ByteString traceContent) throws InterruptedException {
    // Start a successful capture
    startCapturing(stage, transportService, true);
    return stopCapturing(stage, transportService, true, traceContent);
  }

  /**
   * This is a convenience method to start a capture successfully.
   * It sets and checks all the necessary states in the service and call {@link CpuProfilerStage#startCapturing}.
   * <p>
   * Note that a CpuTraceInfo will be auto-generated and added to the cpu service id'd by the current timer's timestamp. If this method
   * is called repeatedly in a single test, it is up to the caller to make sure to update the timestamp to not override the previously added
   * trace info.
   */
  static void startCapturing(CpuProfilerStage stage, FakeTransportService transportService, boolean success)
    throws InterruptedException {
    assertThat(stage.getCaptureState()).isEqualTo(CpuProfilerStage.CaptureState.IDLE);
    ((StartTrace)transportService.getRegisteredCommand(Commands.Command.CommandType.START_TRACE))
      .setStartStatus(Trace.TraceStartStatus.newBuilder()
                        .setStatus(success ? Trace.TraceStartStatus.Status.SUCCESS : Trace.TraceStartStatus.Status.FAILURE)
                        .build());

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
    // Trigger the TransportEventPoller to run and the CpuProfilerStage to pick up the in-progress trace.
    stage.getStudioProfilers().getUpdater().getTimer().tick(FakeTimer.ONE_SECOND_IN_NS);
    latch.await();
  }

  /**
   * This is a convenience method to checking the stopping and selecting (if successful) of a capture. Also, it verifies the
   * {@link CpuCaptureParser} is parsing the capture after we stop capturing.
   * <p>
   * Note that a CpuTraceInfo will be auto-generated and added to the cpu service id'd by the current timer's timestamp, so it is important
   * for the caller to NOT update the timer's timestamp between a start/stop capture to allow this method to replace the existing
   * in-progress trace info. However, if this method is called repeatedly in a single test, it is up to the caller to make sure to update
   * the timestamp to not override the previously added trace info.
   *
   * @return the capture's trace ID.
   */
  static long stopCapturing(CpuProfilerStage stage,
                            FakeTransportService transportService,
                            boolean success,
                            ByteString traceContent,
                            long traceDurationNs)
    throws InterruptedException {
    // Trace id is needed for the stop response.
    long traceId = stage.getStudioProfilers().getUpdater().getTimer().getCurrentTimeNs();
    StopTrace stopTraceCommand = (StopTrace)transportService.getRegisteredCommand(Commands.Command.CommandType.STOP_TRACE);
    stopTraceCommand.setStopStatus(
      Trace.TraceStopStatus.newBuilder()
        .setStatus(success ? Trace.TraceStopStatus.Status.SUCCESS : Trace.TraceStopStatus.Status.STOP_COMMAND_FAILED)
        .setStoppingDurationNs(TimeUnit.MILLISECONDS.toNanos(FAKE_STOPPING_DURATION_MS))
        .build());
    stopTraceCommand.setTraceDurationNs(traceDurationNs);

    CountDownLatch stopLatch;
    AspectObserver stopObserver = new AspectObserver();
    if (success) {
      stopLatch = waitForProfilingStateChangeSequence(stage, stopObserver, CpuProfilerStage.CaptureState.STOPPING);
    }
    else {
      stopLatch = waitForProfilingStateChangeSequence(stage, stopObserver, CpuProfilerStage.CaptureState.STOPPING,
                                                      CpuProfilerStage.CaptureState.IDLE);
    }
    stage.stopCapturing();

    transportService.addFile(Long.toString(traceId), traceContent);
    // Trigger the TransportEventPoller to run and the CpuTraceInfo to be picked up by the CpuProfilerStage.
    stage.getStudioProfilers().getUpdater().getTimer().tick(FakeTimer.ONE_SECOND_IN_NS);
    stopLatch.await();
    return traceId;
  }

  /**
   * Identical to {@link #stopCapturing(CpuProfilerStage, FakeTransportService, boolean, ByteString, long)} but defaults
   * to a 1-nanosecond capture for convenience.
   *
   * @return the capture's trace ID.
   */
  static long stopCapturing(CpuProfilerStage stage,
                            FakeTransportService transportService,
                            boolean success,
                            ByteString traceContent) throws InterruptedException {
    // Defaults to a 1-second capture.
    return stopCapturing(stage, transportService, success, traceContent, 1);
  }

  static void importSessionFromFile(StudioProfilers profilers, FakeTimer timer, String filename) {
    File trace = CpuProfilerTestUtils.getTraceFile(filename);
    SessionsManager sessionsManager = profilers.getSessionsManager();

    // Importing a session from a trace file should select a Common.SessionMetaData.SessionType.CPU_CAPTURE session.
    assertThat(sessionsManager.importSessionFromFile(trace)).isTrue();
    // Advance clock to collect artifacts in updater.
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
  }
}
