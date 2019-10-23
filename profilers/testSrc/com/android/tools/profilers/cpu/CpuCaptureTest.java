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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.android.tools.adtui.model.Range;
import com.android.tools.profiler.proto.Cpu;
import com.android.tools.profiler.protobuf3jarjar.ByteString;
import com.android.tools.profilers.FakeTraceParser;
import com.android.tools.profilers.cpu.art.ArtTraceParser;
import com.android.tools.profilers.cpu.atrace.AtraceParser;
import com.android.tools.profilers.cpu.atrace.CpuThreadSliceInfo;
import com.android.tools.profilers.cpu.nodemodel.SingleNameModel;
import com.android.tools.profilers.cpu.simpleperf.SimpleperfTraceParser;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.junit.Test;

// TODO: Add more variation of trace files (e.g trace with no threads)
public class CpuCaptureTest {

  @Test
  public void validCapture() throws IOException, ExecutionException, InterruptedException {
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
      new CpuCapture(new FakeTraceParser(range, captureTrees, true), 20, Cpu.CpuTraceType.UNSPECIFIED_TYPE);
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
      new CpuCapture(new FakeTraceParser(range, captureTrees, true), 20, Cpu.CpuTraceType.UNSPECIFIED_TYPE);
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
      new CpuCapture(new FakeTraceParser(range, captureTrees, true), 20, Cpu.CpuTraceType.UNSPECIFIED_TYPE);
    // Test if we don't have a main thread, and we pass in an invalid name we still get a main thread id.
    assertThat(capture.getMainThreadId()).isEqualTo(main.getProcessId());
    assertThat(capture.getCaptureNode(main.getProcessId()).getData().getName()).isEqualTo(main.getProcessName());
  }

  @Test
  public void corruptedTraceFileThrowsException() throws IOException, InterruptedException {
    CpuCapture capture = null;
    try {
      ByteString corruptedTrace = CpuProfilerTestUtils.traceFileToByteString("corrupted_trace.trace"); // Malformed trace file.
      capture = CpuProfilerTestUtils.getCapture(corruptedTrace, Cpu.CpuTraceType.ART);
      fail();
    }
    catch (ExecutionException e) {
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
  public void emptyTraceFileThrowsException() throws IOException, InterruptedException {
    CpuCapture capture = null;
    try {
      ByteString emptyTrace = CpuProfilerTestUtils.traceFileToByteString("empty_trace.trace");
      capture = CpuProfilerTestUtils.getCapture(emptyTrace, Cpu.CpuTraceType.ART);
      fail();
    }
    catch (ExecutionException e) {
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
  public void missingCaptureDataThrowsException() throws IOException, InterruptedException {
    CpuCapture capture = null;
    try {
      Range range = new Range(0, 30);
      Map<CpuThreadInfo, CaptureNode> captureTrees = new HashMap<>();
      capture = new CpuCapture(new FakeTraceParser(range, captureTrees, true), 20, Cpu.CpuTraceType.UNSPECIFIED_TYPE);
      fail();
    }
    catch (IllegalStateException e) {
      assertEquals(e.getMessage(), "Trace file contained no CPU data.");
    }
    assertThat(capture).isNull();
  }

  @Test
  public void profilerTypeMustBeSpecified() throws IOException, InterruptedException {
    try {
      CpuProfilerTestUtils.getCapture(CpuProfilerTestUtils.readValidTrace(), Cpu.CpuTraceType.UNSPECIFIED_TYPE);
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
  public void parsingTraceWithWrongProfilerTypeShouldFail() throws IOException, InterruptedException {
    try {
      // Try to create a capture by passing an ART trace and simpleperf profiler type
      CpuProfilerTestUtils.getCapture(CpuProfilerTestUtils.readValidTrace() /* Valid ART trace */, Cpu.CpuTraceType.SIMPLEPERF);
      fail();
    }
    catch (ExecutionException e) {
      // An ExecutionException should happen when trying to get a capture.
      // It should be caused by an expected IllegalStateException thrown while parsing the trace bytes.
      Throwable executionExceptionCause = e.getCause();
      assertThat(executionExceptionCause).isInstanceOf(IllegalStateException.class);
      assertThat(executionExceptionCause.getMessage()).contains("magic number mismatch");
    }
  }

  @Test
  public void dualClockPassedInConstructor() {
    CpuThreadInfo info = new CpuThreadInfo(10, "main");
    Range range = new Range(0, 30);
    Map<CpuThreadInfo, CaptureNode> captureTrees =
      new ImmutableMap.Builder<CpuThreadInfo, CaptureNode>().put(info, new CaptureNode(new StubCaptureNodeModel())).build();
    CpuCapture capture =
      new CpuCapture(new FakeTraceParser(range, captureTrees, true), 20, Cpu.CpuTraceType.UNSPECIFIED_TYPE);
    assertThat(capture.isDualClock()).isTrue();

    capture = new CpuCapture(new FakeTraceParser(range, captureTrees, false), 20, Cpu.CpuTraceType.UNSPECIFIED_TYPE);
    assertThat(capture.isDualClock()).isFalse();
  }

  @Test
  public void traceIdPassedInConstructor() {
    int traceId1 = 20;
    CpuThreadInfo info = new CpuThreadInfo(10, "main");
    Range range = new Range(0, 30);
    Map<CpuThreadInfo, CaptureNode> captureTrees =
      new ImmutableMap.Builder<CpuThreadInfo, CaptureNode>().put(info, new CaptureNode(new StubCaptureNodeModel())).build();
    TraceParser parser = new FakeTraceParser(range, captureTrees, false);

    CpuCapture capture = new CpuCapture(parser, traceId1, Cpu.CpuTraceType.UNSPECIFIED_TYPE);
    assertThat(capture.getTraceId()).isEqualTo(traceId1);

    int traceId2 = 50;
    capture = new CpuCapture(parser, traceId2, Cpu.CpuTraceType.UNSPECIFIED_TYPE);
    assertThat(capture.getTraceId()).isEqualTo(traceId2);
  }

  @Test
  public void profilerTypePassedInConstructor() {
    int traceId = 20;
    CpuThreadInfo info = new CpuThreadInfo(10, "main");
    Range range = new Range(0, 30);
    Map<CpuThreadInfo, CaptureNode> captureTrees =
      new ImmutableMap.Builder<CpuThreadInfo, CaptureNode>().put(info, new CaptureNode(new StubCaptureNodeModel())).build();
    TraceParser parser = new FakeTraceParser(range, captureTrees, false);

    CpuCapture capture = new CpuCapture(parser, traceId, Cpu.CpuTraceType.ART);
    assertThat(capture.getType()).isEqualTo(Cpu.CpuTraceType.ART);

    capture = new CpuCapture(parser, traceId, Cpu.CpuTraceType.SIMPLEPERF);
    assertThat(capture.getType()).isEqualTo(Cpu.CpuTraceType.SIMPLEPERF);

    capture = new CpuCapture(parser, traceId, Cpu.CpuTraceType.ATRACE);
    assertThat(capture.getType()).isEqualTo(Cpu.CpuTraceType.ATRACE);

    capture = new CpuCapture(parser, traceId, Cpu.CpuTraceType.UNSPECIFIED_TYPE);
    assertThat(capture.getType()).isEqualTo(Cpu.CpuTraceType.UNSPECIFIED_TYPE);
  }

  @Test
  public void dualClockSupportDiffersFromParser() {
    ArtTraceParser artParser = new ArtTraceParser();
    assertThat(artParser.supportsDualClock()).isTrue();

    SimpleperfTraceParser simpleperfTraceParser = new SimpleperfTraceParser();
    assertThat(simpleperfTraceParser.supportsDualClock()).isFalse();

    AtraceParser atraceParser = new AtraceParser(123);
    assertThat(atraceParser.supportsDualClock()).isFalse();
  }
}
