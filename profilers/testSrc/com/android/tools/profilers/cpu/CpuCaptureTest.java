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
import com.google.protobuf3jarjar.ByteString;
import org.junit.Test;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.*;

// TODO: Add more variation of trace files (e.g trace with no threads)
public class CpuCaptureTest {

  @Test
  public void validCapture() throws IOException, ExecutionException, InterruptedException {
    CpuCapture capture = CpuProfilerTestUtils.getValidCapture();
    assertNotNull(capture);

    Range captureRange = capture.getRange();
    assertNotNull(captureRange);
    assertFalse(captureRange.isEmpty());
    assertEquals((long)captureRange.getLength(), capture.getDuration());

    int main = capture.getMainThreadId();
    assertTrue(capture.containsThread(main));
    CaptureNode mainNode = capture.getCaptureNode(main);
    assertNotNull(mainNode);
    assertNotNull(mainNode.getData());
    assertEquals("main", mainNode.getData().getName());

    Set<CpuThreadInfo> threads = capture.getThreads();
    assertFalse(threads.isEmpty());
    for (CpuThreadInfo thread : threads) {
      assertNotNull(capture.getCaptureNode(thread.getId()));
      assertTrue(capture.containsThread(thread.getId()));
    }

    int inexistentThreadId = -1;
    assertFalse(capture.containsThread(inexistentThreadId));
    assertNull(capture.getCaptureNode(inexistentThreadId));
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
      assertTrue(executionExceptionCause instanceof IllegalStateException);

      // Expected BufferUnderflowException to be thrown in VmTraceParser.
      assertTrue(executionExceptionCause.getCause() instanceof BufferUnderflowException);
      // CpuCaptureParser#traceBytesToCapture catches the BufferUnderflowException and throw an IllegalStateException instead.
    }
    assertNull(capture);
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
      assertTrue(executionExceptionCause instanceof IllegalStateException);

      // Expected IOException to be thrown in VmTraceParser.
      assertTrue(executionExceptionCause.getCause() instanceof IOException);
      // CpuCaptureParser#traceBytesToCapture catches the IOException and throw an IllegalStateException instead.
    }
    assertNull(capture);
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
      assertTrue(executionExceptionCause instanceof IllegalStateException);

      // Exception expected to be thrown because a valid profiler type was not set.
      assertTrue(executionExceptionCause.getMessage().contains("Trace file cannot be parsed"));
    }
  }

  @Test
  public void parsingTraceWithWrongProfilerTypeShouldFail() throws IOException, ExecutionException, InterruptedException {
    try {
      // Try to create a capture by passing an ART trace and simpleperf profiler type
      CpuProfilerTestUtils.getCapture(CpuProfilerTestUtils.readValidTrace() /* Valid ART trace */, CpuProfiler.CpuProfilerType.SIMPLE_PERF);
      fail();
    } catch (ExecutionException e) {
      // An ExecutionException should happen when trying to get a capture.
      // It should be caused by an expected IllegalStateException thrown while parsing the trace bytes.
      Throwable executionExceptionCause = e.getCause();
      assertTrue(executionExceptionCause instanceof IllegalStateException);

      // Expected BufferUnderflowException to be thrown in SimplePerfTraceParser.
      assertTrue(executionExceptionCause.getCause() instanceof BufferUnderflowException);
      // CpuCaptureParser#traceBytesToCapture  catches the BufferUnderflowException and throw an IllegalStateException instead.
    }
  }
}
