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

import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.CpuProfiler.CpuProfilerType;
import com.android.tools.profiler.protobuf3jarjar.ByteString;
import com.android.tools.profilers.IdeProfilerServices;
import com.android.tools.profilers.cpu.art.ArtTraceParser;
import com.android.tools.profilers.cpu.atrace.AtraceParser;
import com.android.tools.profilers.cpu.simpleperf.SimpleperfTraceParser;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.diagnostic.Logger;
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

/**
 * Manages the parsing of traces into {@link CpuCapture} objects and provide a way to retrieve them.
 */
public class CpuCaptureParser {

  /**
   * Maximum supported trace size, in bytes.
   * Users should be warned when traces are larger than this value and can opt not to parse them.
   */
  @VisibleForTesting
  static final int MAX_SUPPORTED_TRACE_SIZE = 1024 * 1024 * 100; // 100MB

  /**
   * Maps a trace id to a corresponding {@link CompletableFuture<CpuCapture>}.
   */
  private final Map<Integer, CompletableFuture<CpuCapture>> myCaptures;

  /**
   * Services containing the {@link java.util.concurrent.Executor} responsible for parsing the capture.
   * This is also used for determining if large trace files should be parsed.
   */
  @NotNull
  private final IdeProfilerServices myServices;

  public CpuCaptureParser(@NotNull IdeProfilerServices services) {
    myServices = services;
    myCaptures = new HashMap<>();
  }

  private static Logger getLogger() {
    return Logger.getInstance(CpuCaptureParser.class);
  }

  /**
   * Returns a capture (or a promise of one) in case {@link #parse} was already called for the given trace id.
   */
  @Nullable
  public CompletableFuture<CpuCapture> getCapture(int traceId) {
    return myCaptures.get(traceId);
  }

  /**
   * Creates a {@link CompletableFuture<CpuCapture>} from given trace bytes and the profiler type used to obtain the trace.
   * Uses {@link IdeProfilerServices#getPoolExecutor()} to create the actual {@link CpuCapture} object. Adds it to the captures map using
   * the trace id as key. Finally, returns the {@link CompletableFuture<CpuCapture>} created.
   */
  @Nullable
  public CompletableFuture<CpuCapture> parse(@NotNull Common.Session session,
                                             int traceId,
                                             @NotNull ByteString traceData,
                                             CpuProfilerType profilerType) {
    if (!myCaptures.containsKey(traceId)) {
      // Trace is not being parsed nor is already parsed. We need to start parsing it.
      if (traceData.size() <= MAX_SUPPORTED_TRACE_SIZE) {
        // Trace size is supported. Start parsing normally and create the future object corresponding to the capture.
        myCaptures.put(traceId, createCaptureFuture(session, traceData, profilerType));
      }
      else {
        Runnable yesCallback = () -> {
          getLogger().warn(String.format("Parsing long (%d bytes) trace file.", traceData.size()));
          // User decided to proceed with capture. Start parsing and create the future object corresponding to the capture.
          myCaptures.put(traceId, createCaptureFuture(session, traceData, profilerType));
        };

        Runnable noCallback = () -> {
          // User aborted the parsing before it starts. Add an entry for the trace id to the map with a null value.
          // This way, next time our model requests this trace capture, we return early.
          getLogger().warn(String.format("Parsing of a long (%d bytes) trace file was aborted by the user.", traceData.size()));
          myCaptures.put(traceId, null);
        };
        // Open the dialog warning the user the trace is too large and asking them if they want to proceed with parsing.
        myServices.openParseLargeTracesDialog(yesCallback, noCallback);
      }
    }

    return myCaptures.get(traceId);
  }

  private CompletableFuture<CpuCapture> createCaptureFuture(@NotNull Common.Session session,
                                                            ByteString traceBytes,
                                                            CpuProfilerType profilerType) {
    return CompletableFuture.supplyAsync(() -> traceBytesToCapture(session, traceBytes, profilerType), myServices.getPoolExecutor());
  }

  private CpuCapture traceBytesToCapture(@NotNull Common.Session session,
                                         @NotNull ByteString traceData,
                                         CpuProfilerType profilerType) {
    // TODO: Remove layers, analyze whether we can keep the whole file in memory.
    try {
      File trace = FileUtil.createTempFile("cpu_trace", ".trace");
      try (FileOutputStream out = new FileOutputStream(trace)) {
        out.write(traceData.toByteArray());
      }

      TraceParser parser;
      if (profilerType == CpuProfilerType.ART) {
        parser = new ArtTraceParser();
      }
      else if (profilerType == CpuProfilerType.SIMPLEPERF) {
        parser = new SimpleperfTraceParser(myServices.getApplicationId());
      }
      else if (profilerType == CpuProfilerType.ATRACE) {
        parser = new AtraceParser(session.getPid());
      }
      else {
        throw new IllegalStateException("Trace file cannot be parsed. Profiler type (ART, simpleperf, or atrace) needs to be set.");
      }

      return parser.parse(trace);
    }
    catch (IOException | BufferUnderflowException e) {
      throw new IllegalStateException(e);
    }
  }
}
