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

import com.android.tools.adtui.model.Range;
import com.android.tools.profiler.proto.CpuProfiler;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf3jarjar.ByteString;
import org.junit.Test;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

// TODO: Add more variation of trace files (e.g trace with no threads)
public class CpuCaptureTest {

  @Test
  public void validCapture() throws IOException, ExecutionException, InterruptedException {
    CpuCapture capture = CpuProfilerTestUtils.getValidCapture();
    assertThat(capture).isNotNull();

    Range captureRange = capture.getRange();
    assertThat(captureRange).isNotNull();
    assertThat(captureRange.isEmpty()).isFalse();
    assertThat(capture.getDuration()).isEqualTo((long)captureRange.getLength());

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
  public void corruptedTraceFileThrowsException() throws IOException, ExecutionException, InterruptedException {
    CpuCapture capture = null;
    try {
      ByteString corruptedTrace = CpuProfilerTestUtils.traceFileToByteString("corrupted_trace.trace"); // Malformed trace file.
      capture = CpuProfilerTestUtils.getCapture(corruptedTrace, CpuProfiler.CpuProfilerType.ART);
      fail();
    } catch (ExecutionException e) {
      // An ExecutionException should happen when trying to get a capture.
      // It should be caused by an expected IllegalStateException thrown while parsing the trace bytes.
      Throwable executionExceptionCause = e.getCause();
      assertThat(executionExceptionCause).isInstanceOf(IllegalStateException.class);

      // Expected BufferUnderflowException to be thrown in VmTraceParser.
      assertThat(executionExceptionCause.getCause()).isInstanceOf(BufferUnderflowException.class);
      // CpuCaptureParser#traceBytesToCapture catches the BufferUnderflowException and throw an IllegalStateException instead.
    }
    assertThat(capture).isNull();
  }

  @Test
  public void emptyTraceFileThrowsException() throws IOException, ExecutionException, InterruptedException {
    CpuCapture capture = null;
    try {
      ByteString emptyTrace = CpuProfilerTestUtils.traceFileToByteString("empty_trace.trace");
      capture = CpuProfilerTestUtils.getCapture(emptyTrace, CpuProfiler.CpuProfilerType.ART);
      fail();
    } catch (ExecutionException e) {
      // An ExecutionException should happen when trying to get a capture.
      // It should be caused by an expected IllegalStateException thrown while parsing the trace bytes.
      Throwable executionExceptionCause = e.getCause();
      assertThat(executionExceptionCause).isInstanceOf(IllegalStateException.class);

      // Expected IOException to be thrown in VmTraceParser.
      assertThat(executionExceptionCause.getCause()).isInstanceOf(IOException.class);
      // CpuCaptureParser#traceBytesToCapture catches the IOException and throw an IllegalStateException instead.
    }
    assertThat(capture).isNull();
  }

  @Test
  public void profilerTypeMustBeSpecified() throws IOException, ExecutionException, InterruptedException {
    try {
      CpuProfilerTestUtils.getCapture(CpuProfilerTestUtils.readValidTrace(), CpuProfiler.CpuProfilerType.UNSPECIFIED_PROFILER);
      fail();
    }
    catch (ExecutionException e) {
      // An ExecutionException should happen when trying to get a capture.
      // It should be caused by an expected IllegalStateException thrown while parsing the trace bytes.
      Throwable executionExceptionCause = e.getCause();
      assertThat(executionExceptionCause).isInstanceOf(IllegalStateException.class);

      // Exception expected to be thrown because a valid profiler type was not set.
      assertThat(executionExceptionCause.getMessage()).contains("Trace file cannot be parsed");
    }
  }

  @Test
  public void parsingTraceWithWrongProfilerTypeShouldFail() throws IOException, ExecutionException, InterruptedException {
    try {
      // Try to create a capture by passing an ART trace and simpleperf profiler type
      CpuProfilerTestUtils.getCapture(CpuProfilerTestUtils.readValidTrace() /* Valid ART trace */, CpuProfiler.CpuProfilerType.SIMPLEPERF);
      fail();
    } catch (ExecutionException e) {
      // An ExecutionException should happen when trying to get a capture.
      // It should be caused by an expected IllegalStateException thrown while parsing the trace bytes.
      Throwable executionExceptionCause = e.getCause();
      assertThat(executionExceptionCause).isInstanceOf(IllegalStateException.class);

      // Expected BufferUnderflowException to be thrown in SimpleperfTraceParser.
      assertThat(executionExceptionCause.getCause()).isInstanceOf(BufferUnderflowException.class);
      // CpuCaptureParser#traceBytesToCapture  catches the BufferUnderflowException and throw an IllegalStateException instead.
    }
  }

  @Test
  public void dualClockPassedInConstructor() {
    CpuThreadInfo info = new CpuThreadInfo(10, "main");
    Range range = new Range(0, 30);
    Map<CpuThreadInfo, CaptureNode> captureTrees =
      new ImmutableMap.Builder<CpuThreadInfo, CaptureNode>().put(info, new CaptureNode()).build();
    CpuCapture capture = new CpuCapture(range, captureTrees, true);
    assertThat(capture.isDualClock()).isTrue();

    capture = new CpuCapture(range, captureTrees, false);
    assertThat(capture.isDualClock()).isFalse();
  }
}
