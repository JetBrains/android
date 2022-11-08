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
package com.android.tools.profilers.cpu;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.android.tools.adtui.model.Range;
import com.android.tools.profilers.cpu.nodemodel.SingleNameModel;
import com.android.tools.profilers.cpu.systemtrace.CpuThreadSliceInfo;
import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import com.android.tools.profilers.cpu.config.ProfilingConfiguration.TraceType;

// TODO: Add more variation of trace files (e.g trace with no threads)
public class CpuCaptureTest {

  @Test
  public void validCapture() throws ExecutionException, InterruptedException {
    CpuCapture capture = CpuProfilerTestUtils.getValidCapture();
    assertThat(capture).isNotNull();

    Range captureRange = capture.getRange();
    assertThat(captureRange).isNotNull();
    assertThat(captureRange.isEmpty()).isFalse();
    assertThat(capture.getDurationUs()).isEqualTo((long)captureRange.getLength());

    int main = capture.getMainThreadId();
    assertThat(capture.containsThread(main)).isTrue();
    CaptureNode mainNode = capture.getCaptureNode(main);
    assertThat(mainNode).isNotNull();
    assertThat(mainNode.getData()).isNotNull();
    assertThat(mainNode.getData().getName()).isEqualTo("main");

    Set<CpuThreadInfo> threads = capture.getThreads();
    assertThat(threads).isNotEmpty();
    for (CpuThreadInfo thread : threads) {
      assertThat(capture.getCaptureNode(thread.getId())).isNotNull();
      assertThat(capture.containsThread(thread.getId())).isTrue();
    }

    int inexistentThreadId = -1;
    assertThat(capture.containsThread(inexistentThreadId)).isFalse();
    assertThat(capture.getCaptureNode(inexistentThreadId)).isNull();
  }

  @Test
  public void noMainThreads() {
    CpuThreadInfo info = new CpuThreadInfo(10, "Thread1", false);
    Range range = new Range(0, 30);
    Map<CpuThreadInfo, CaptureNode> captureTrees =
      new ImmutableMap.Builder<CpuThreadInfo, CaptureNode>().put(info, new CaptureNode(new SingleNameModel("Thread1"))).build();
    CpuCapture capture =
      new BaseCpuCapture(20, TraceType.UNSPECIFIED, range, captureTrees);
    // Test if we don't have an actual main thread, we still get a main thread id.
    assertThat(capture.getMainThreadId()).isEqualTo(10);
    assertThat(capture.getCaptureNode(10).getData().getName()).isEqualTo("Thread1");
  }

  @Test
  public void validThreadNameOtherThanMain() {
    CpuThreadInfo valid = new CpuThreadInfo(10, "Valid", true);
    CpuThreadInfo other = new CpuThreadInfo(11, "Other");
    Range range = new Range(0, 30);
    Map<CpuThreadInfo, CaptureNode> captureTrees =
      new ImmutableMap.Builder<CpuThreadInfo, CaptureNode>().put(valid, new CaptureNode(new SingleNameModel("Valid")))
        .put(other, new CaptureNode(new SingleNameModel("Other"))).build();
    CpuCapture capture =
      new BaseCpuCapture(20, TraceType.UNSPECIFIED, range, captureTrees);
    // Test if we don't have a main thread, and we pass in an invalid name we still get a main thread id.
    assertThat(capture.getMainThreadId()).isEqualTo(10);
    assertThat(capture.getCaptureNode(10).getData().getName()).isEqualTo("Valid");
  }

  @Test
  public void multipleThreadsSameNameGetsCorrectMainThread() {
    CpuThreadSliceInfo main = new CpuThreadSliceInfo(10, "MainThread", 10, "MainThread");
    CpuThreadSliceInfo other = new CpuThreadSliceInfo(11, "Other", main.getProcessId(), main.getProcessName());
    CpuThreadInfo notMain = new CpuThreadSliceInfo(12, "MainThread", main.getProcessId(), main.getProcessName());
    Range range = new Range(0, 30);
    Map<CpuThreadInfo, CaptureNode> captureTrees =
      new ImmutableMap.Builder<CpuThreadInfo, CaptureNode>().put(notMain, new CaptureNode(new SingleNameModel("MainThread")))
        .put(other, new CaptureNode(new SingleNameModel("Other")))
        .put(main, new CaptureNode(new SingleNameModel("MainThread"))).build();
    CpuCapture capture =
      new BaseCpuCapture(20, TraceType.UNSPECIFIED, range, captureTrees);
    // Test if we don't have a main thread, and we pass in an invalid name we still get a main thread id.
    assertThat(capture.getMainThreadId()).isEqualTo(main.getProcessId());
    assertThat(capture.getCaptureNode(main.getProcessId()).getData().getName()).isEqualTo(main.getProcessName());
  }

  @Test
  public void corruptedTraceFileThrowsException() throws InterruptedException {
    File corruptedTrace = CpuProfilerTestUtils.getTraceFile("corrupted_trace.trace"); // Malformed trace file.
    CompletableFuture<CpuCapture> future = CpuProfilerTestUtils.getCaptureFuture(corruptedTrace, TraceType.ART);
    try {
      future.get();
      fail();
    }
    catch (ExecutionException e) {
      // An ExecutionException should happen when trying to get a capture.
      // It should be caused by an expected IllegalStateException thrown while parsing the trace bytes.
      Throwable executionExceptionCause = e.getCause();
      assertThat(executionExceptionCause).isInstanceOf(CpuCaptureParser.ParsingFailureException.class);

      // Expected BufferUnderflowException to be thrown in VmTraceParser.
      assertThat(executionExceptionCause.getCause()).isInstanceOf(BufferUnderflowException.class);
      // CpuCaptureParser#traceBytesToCapture catches the BufferUnderflowException and throw an IllegalStateException instead.
    }
  }

  @Test
  public void emptyTraceFileThrowsException() throws InterruptedException {
    File emptyTrace = CpuProfilerTestUtils.getTraceFile("empty_trace.trace");
    CompletableFuture<CpuCapture> future = CpuProfilerTestUtils.getCaptureFuture(emptyTrace, TraceType.ART);
    assertThat(future).isNotNull();

    try {
      future.get();
      fail();
    }
    catch (ExecutionException e) {
      assertThat(future.isCompletedExceptionally()).isTrue();
      // An ExecutionException should happen when trying to get a capture.
      // It should be caused by an expected IllegalStateException thrown while parsing the trace bytes.
      Throwable executionExceptionCause = e.getCause();
      assertThat(executionExceptionCause).isInstanceOf(CpuCaptureParser.ParsingFailureException.class);

      // Expected IOException to be thrown in VmTraceParser.
      assertThat(executionExceptionCause.getCause()).isInstanceOf(IOException.class);
      // CpuCaptureParser#traceBytesToCapture catches the IOException and throw an IllegalStateException instead.
    }
  }

  @Test
  public void emptyTraceIsAccepted() {
    Range range = new Range();
    Map<CpuThreadInfo, CaptureNode> captureTrees = new HashMap<>();
    CpuCapture capture = new BaseCpuCapture(20, TraceType.UNSPECIFIED, range, captureTrees);
    assertThat(capture.getCaptureNodes()).isEmpty();
    assertThat(capture.getMainThreadId()).isEqualTo(BaseCpuCapture.NO_THREAD_ID);
  }

  @Test
  public void parsingTraceWithWrongProfilerTypeShouldFail() throws InterruptedException {
    // Try to create a capture by passing an ART trace and simpleperf profiler type
    File artTrace = CpuProfilerTestUtils.getTraceFile("valid_trace.trace"); /* Valid ART trace */
    CompletableFuture<CpuCapture> future = CpuProfilerTestUtils.getCaptureFuture(artTrace, TraceType.SIMPLEPERF);

    try {
      future.get();
      fail();
    }
    catch (ExecutionException e) {
      // An ExecutionException should happen when trying to get a capture.
      // It should be caused by an expected IllegalStateException thrown while parsing the trace bytes.
      Throwable executionExceptionCause = e.getCause();
      assertThat(executionExceptionCause).isInstanceOf(CpuCaptureParser.ParsingFailureException.class);
      assertThat(executionExceptionCause).hasCauseThat().hasMessageThat().contains("magic number mismatch");
    }
  }

  @Test
  public void traceIdPassedInConstructor() {
    int traceId1 = 20;
    CpuThreadInfo info = new CpuThreadInfo(10, "main");
    Range range = new Range(0, 30);
    Map<CpuThreadInfo, CaptureNode> captureTrees =
      new ImmutableMap.Builder<CpuThreadInfo, CaptureNode>().put(info, new CaptureNode(new StubCaptureNodeModel())).build();

    CpuCapture capture = new BaseCpuCapture(traceId1, TraceType.UNSPECIFIED, range, captureTrees);
    assertThat(capture.getTraceId()).isEqualTo(traceId1);

    int traceId2 = 50;
    capture = new BaseCpuCapture(traceId2, TraceType.UNSPECIFIED, range, captureTrees);
    assertThat(capture.getTraceId()).isEqualTo(traceId2);
  }

  @Test
  public void profilerTypePassedInConstructor() {
    int traceId = 20;
    CpuThreadInfo info = new CpuThreadInfo(10, "main");
    Range range = new Range(0, 30);
    Map<CpuThreadInfo, CaptureNode> captureTrees =
      new ImmutableMap.Builder<CpuThreadInfo, CaptureNode>().put(info, new CaptureNode(new StubCaptureNodeModel())).build();

    CpuCapture capture = new BaseCpuCapture(traceId, TraceType.ART, range, captureTrees);
    assertThat(capture.getType()).isEqualTo(TraceType.ART);
    assertThat(capture.isDualClock()).isTrue();

    capture = new BaseCpuCapture(traceId, TraceType.SIMPLEPERF, true, null, range, captureTrees);
    assertThat(capture.getType()).isEqualTo(TraceType.SIMPLEPERF);
    assertThat(capture.isDualClock()).isTrue();

    capture = new BaseCpuCapture(traceId, TraceType.SIMPLEPERF, false, "fake message", range, captureTrees);
    assertThat(capture.getType()).isEqualTo(TraceType.SIMPLEPERF);
    assertThat(capture.isDualClock()).isFalse();
    assertThat(capture.getDualClockDisabledMessage()).isEqualTo("fake message");

    capture = new BaseCpuCapture(traceId, TraceType.PERFETTO, false, null, range, captureTrees);
    assertThat(capture.getType()).isEqualTo(TraceType.PERFETTO);
    assertThat(capture.isDualClock()).isFalse();

    capture = new BaseCpuCapture(traceId, TraceType.UNSPECIFIED, false, null, range, captureTrees);
    assertThat(capture.getType()).isEqualTo(TraceType.UNSPECIFIED);
    assertThat(capture.isDualClock()).isFalse();
  }
}
