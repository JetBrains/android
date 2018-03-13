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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.util.HashMap;
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
   * Used as ID of imported traces. Importing a trace will happen once per session,
   * so we can have an arbitrary ID as it's going to be unique within a session.
   */
  @VisibleForTesting
  static final int IMPORTED_TRACE_ID = 42;

  /**
   * Maps a trace id to a corresponding {@link CompletableFuture<CpuCapture>}.
   */
  private final Map<Integer, CompletableFuture<CpuCapture>> myCaptures;

  /**
   * Maps a trace id to the path of a temporary file containing the trace content.
   */
  private final Map<Integer, String> myTraceFiles;

  /**
   * Services containing the {@link java.util.concurrent.Executor} responsible for parsing the capture.
   * This is also used for determining if large trace files should be parsed.
   */
  @NotNull
  private final IdeProfilerServices myServices;

  public CpuCaptureParser(@NotNull IdeProfilerServices services) {
    myServices = services;
    myCaptures = new HashMap<>();
    myTraceFiles = new HashMap<>();
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

  @Nullable
  String getTraceFilePath(int traceId) {
    return myTraceFiles.get(traceId);
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
        myCaptures.put(traceId, createCaptureFuture(session, traceId, traceData, profilerType));
      }
      else {
        Runnable yesCallback = () -> {
          getLogger().warn(String.format("Parsing long (%d bytes) trace file.", traceData.size()));
          // User decided to proceed with capture. Start parsing and create the future object corresponding to the capture.
          myCaptures.put(traceId, createCaptureFuture(session, traceId, traceData, profilerType));
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

  /**
   * Parses a {@link File} into a {@link CompletableFuture<CpuCapture>} that executes in {@link IdeProfilerServices#getPoolExecutor()}.
   * Return null if the file doesn't exist or point to a directory.
   *
   * When a trace file is considered large (see {@link #MAX_SUPPORTED_TRACE_SIZE}), a dialog should be displayed so they user can decide if
   * they want to abort the trace parsing or continue with it.
   */
  @Nullable
  public CompletableFuture<CpuCapture> parse(File traceFile) {
    if (!traceFile.exists() || traceFile.isDirectory()) {
      // Nothing to be parsed. We shouldn't even try to do it.
      getLogger().info("Trace not parsed, as its path doesn't exist or points to a directory.");
      return null;
    }

    long fileLength = traceFile.length();
    if (fileLength > MAX_SUPPORTED_TRACE_SIZE) {
      // Trace is too big. Ask the user if they want to proceed with parsing.
      Runnable yesCallback = () -> {
        getLogger().warn(String.format("Parsing long (%d bytes) trace file.", fileLength));
        // User decided to proceed. Try parsing the trace file.
        myCaptures.put(IMPORTED_TRACE_ID,
                       CompletableFuture.supplyAsync(() -> tryParsingFileWithDifferentParsers(traceFile), myServices.getPoolExecutor()));
      };

      Runnable noCallback = () -> {
        // User aborted the parsing before it starts. Return null and don't try to parse the file.
        getLogger().warn(String.format("Parsing of a long (%d bytes) trace file was aborted by the user.", fileLength));
        myCaptures.put(IMPORTED_TRACE_ID, null);
      };

      // Open the dialog warning the user the file is too large and asking them if they want to proceed with parsing.
      myServices.openParseLargeTracesDialog(yesCallback, noCallback);
    }
    else {
      // Trace file is not too big to be parsed. Parse it normally.
      myCaptures.put(IMPORTED_TRACE_ID,
                     CompletableFuture.supplyAsync(() -> tryParsingFileWithDifferentParsers(traceFile), myServices.getPoolExecutor()));
    }
    return myCaptures.get(IMPORTED_TRACE_ID);
  }

  /**
   * Try parsing a given {@link File} into a {@link CpuCapture} using {@link ArtTraceParser}, then {@link SimpleperfTraceParser}
   * (if simpleperf flag is enabled), then {@link AtraceParser} (if atrace flag is enabled). Return null if the file can't be parsed by any
   * of them.
   */
  private CpuCapture tryParsingFileWithDifferentParsers(File traceFile) {
    try {
      // First try parsing the trace file as an ART trace.
      ArtTraceParser artTraceParser = new ArtTraceParser();
      return artTraceParser.parse(traceFile, IMPORTED_TRACE_ID);
    }
    catch (Exception ignored) {
      // We should go on and try parsing the file as a simpleperf or atrace trace.
    }

    if (myServices.getFeatureConfig().isSimpleperfEnabled()) {
      try {
        // Then, try parsing the file as a simpleperf trace if its flag is enabled.
        // TODO (b/74525724): When obtaining package name directly from simpleperf traces, don't pass "unknown" to the constructor.
        SimpleperfTraceParser simpleperfParser = new SimpleperfTraceParser("unknown");
        return simpleperfParser.parse(traceFile, IMPORTED_TRACE_ID);
      }
      catch (Exception ignored) {
        // We should go on and try parsing the file as an atrace trace.
      }
    }

    if (myServices.getFeatureConfig().isAtraceEnabled()) {
      try {
        // Finally, try parsing the file as an atrace trace if its flag is enabled.
        // TODO (b/74526422): Figure out how to get the app process ID from atrace trace file, so we can pass to AtraceParser constructor.
        AtraceParser atraceParser = new AtraceParser(1);
        return atraceParser.parse(traceFile, IMPORTED_TRACE_ID);
      }
      catch (Exception ignored) {
        // Ignore the exception and continue the flow to return null
      }
    }
    // File couldn't be parsed by any of the parsers. Log the issue and return null.
    getLogger().warn(String.format("Parsing %s has failed.", traceFile.getPath()));
    return null;
  }

  private CompletableFuture<CpuCapture> createCaptureFuture(@NotNull Common.Session session, int traceId, ByteString traceBytes,
                                                            CpuProfilerType profilerType) {
    return CompletableFuture.supplyAsync(() -> traceBytesToCapture(session, traceId, traceBytes, profilerType),
                                         myServices.getPoolExecutor());
  }

  private CpuCapture traceBytesToCapture(@NotNull Common.Session session, int traceId, @NotNull ByteString traceData,
                                         CpuProfilerType profilerType) {
    // TODO: Remove layers, analyze whether we can keep the whole file in memory.
    try {
      File trace = FileUtil.createTempFile(String.format("cpu_trace_%d", traceId), ".trace", true);
      try (FileOutputStream out = new FileOutputStream(trace)) {
        out.write(traceData.toByteArray());
      }
      myTraceFiles.put(traceId, trace.getAbsolutePath());

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

      return parser.parse(trace, traceId);
    }
    catch (IOException | BufferUnderflowException e) {
      throw new IllegalStateException(e);
    }
  }
}
