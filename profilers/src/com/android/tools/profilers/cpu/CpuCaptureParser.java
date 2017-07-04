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

import com.android.tools.profiler.proto.CpuProfiler;
import com.android.tools.profilers.cpu.art.ArtTraceParser;
import com.android.tools.profilers.cpu.simpleperf.SimplePerfTraceParser;
import com.google.protobuf3jarjar.ByteString;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Manages the parsing of traces into {@link CpuCapture} objects and provide a way to retrieve them.
 */
public class CpuCaptureParser {

  /**
   * Maps a trace id to a corresponding {@link CompletableFuture<CpuCapture>}.
   */
  private final Map<Integer, CompletableFuture<CpuCapture>> myCaptures;

  /**
   * Used to parse the captures.
   * Ideally this is done by another thread, so trace parsing doesn't block the main application thread.
   */
  private final Executor myParsingExecutor;

  public CpuCaptureParser(@NotNull Executor parsingExecutor) {
    myParsingExecutor = parsingExecutor;
    myCaptures = new HashMap<>();
  }

  /**
   * Returns a capture (or a promise of one) in case {@link #parse} was already called for the given trace id.
   *
   */
  @Nullable
  public CompletableFuture<CpuCapture> getCapture(int traceId) {
    return myCaptures.get(traceId);
  }

  /**
   * Creates a {@link CompletableFuture<CpuCapture>} from given trace bytes and the profiler type used to obtain the trace.
   * Uses {@link #myParsingExecutor} to create the actual {@link CpuCapture} object. Adds it to the captures map using the trace id as key.
   * Finally, returns the {@link CompletableFuture<CpuCapture>} created.
   */
  @NotNull
  public CompletableFuture<CpuCapture> parse(int traceId, @NotNull ByteString traceData, CpuProfiler.CpuProfilerType profilerType) {
    if (myCaptures.containsKey(traceId)) {
      // Trace is being parsed or is already parsed. There is not need to start parsing again.
      return myCaptures.get(traceId);
    }
    CompletableFuture<CpuCapture> capture =
      CompletableFuture.supplyAsync(() -> traceBytesToCapture(traceData, profilerType), myParsingExecutor);
    myCaptures.put(traceId, capture);
    return capture;
  }

  private static CpuCapture traceBytesToCapture(@NotNull ByteString traceData, CpuProfiler.CpuProfilerType profilerType) {
    // TODO: Remove layers, analyze whether we can keep the whole file in memory.
    try {
      File trace = FileUtil.createTempFile("cpu_trace", ".trace");
      try (FileOutputStream out = new FileOutputStream(trace)) {
        out.write(traceData.toByteArray());
      }

      TraceParser parser;
      if (profilerType == CpuProfiler.CpuProfilerType.ART) {
        parser = new ArtTraceParser();
      }
      else if (profilerType == CpuProfiler.CpuProfilerType.SIMPLE_PERF) {
        parser = new SimplePerfTraceParser();
      }
      else {
        throw new IllegalStateException("Trace file cannot be parsed. Profiler type (ART or simpleperf) needs to be set.");
      }

      parser.parse(trace);
      return new CpuCapture(parser.getRange(), parser.getCaptureTrees());
    }
    catch (IOException | BufferUnderflowException e) {
      throw new IllegalStateException(e);
    }
  }
}
