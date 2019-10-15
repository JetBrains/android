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

import com.android.tools.adtui.model.AspectModel;
import com.android.tools.adtui.model.Range;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Cpu.CpuTraceType;
import com.android.tools.idea.protobuf.ByteString;
import com.android.tools.profilers.IdeProfilerServices;
import com.android.tools.profilers.cpu.art.ArtTraceParser;
import com.android.tools.profilers.cpu.atrace.AtraceParser;
import com.android.tools.profilers.cpu.atrace.AtraceProducer;
import com.android.tools.profilers.cpu.atrace.CpuThreadSliceInfo;
import com.android.tools.profilers.cpu.atrace.PerfettoProducer;
import com.android.tools.profilers.cpu.simpleperf.SimpleperfTraceParser;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import java.io.File;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
  static final long IMPORTED_TRACE_ID = 42L;

  /**
   * Maps a trace id to a corresponding {@link CompletableFuture<CpuCapture>}.
   */
  private final Map<Long, CompletableFuture<CpuCapture>> myCaptures;

  /**
   * Services containing the {@link java.util.concurrent.Executor} responsible for parsing the capture.
   * This is also used for determining if large trace files should be parsed.
   */
  @NotNull
  private final IdeProfilerServices myServices;

  private final AspectModel<CpuProfilerAspect> myAspect = new AspectModel<>();

  /**
   * Whether there is a parsing in progress. This value is set to true in {@link #updateParsingStateWhenStarting()} followed by a
   * {@link CpuProfilerAspect#CAPTURE_PARSING} being fired, and set to false by {@link #updateParsingStateWhenDone(CompletableFuture)},
   * which should be called around every {@link CompletableFuture<CpuCapture>} created by this class.
   */
  private boolean myIsParsing;

  /**
   * Unix epoch time when capture parsing started.
   */
  private long myParsingStartTimeMs;

  /**
   * Hint to the parser what process to look for. This is used when parsing Perfetto/Atrace captures that were recorded from the UI.
   */
  private String myProcessNameHint;

  /**
   * Hint to the parser what process id to look for. This is used if a process with the process name hint was not found.
   */
  private int myProcessIdHint;

  /**
   * Metadata associated with parsing a capture. If an entry exists, the metadata will be populated and uploaded to metrics.
   */
  private Map<Long, CpuCaptureMetadata> myCaptureMetadataMap = new HashMap<>();

  public CpuCaptureParser(@NotNull IdeProfilerServices services) {
    myServices = services;
    myCaptures = new HashMap<>();
  }

  private static Logger getLogger() {
    return Logger.getInstance(CpuCaptureParser.class);
  }

  public AspectModel<CpuProfilerAspect> getAspect() {
    return myAspect;
  }

  /**
   * Returns a capture (or a promise of one) in case {@link #parse} was already called for the given trace id.
   */
  @Nullable
  public CompletableFuture<CpuCapture> getCapture(long traceId) {
    return myCaptures.get(traceId);
  }

  /**
   * Next time a capture associated with the traceId is parsed, record and send the parsing metadata.
   *
   * @param traceId  the trace id of the capture to track
   * @param metadata the initial set of metadata unrelated to parsing (e.g. configuration, record duration, etc_
   */
  void trackCaptureMetadata(long traceId, @NotNull CpuCaptureMetadata metadata) {
    myCaptureMetadataMap.put(traceId, metadata);
  }

  /**
   * Abort every capture parsing that might still be in progress.
   */
  public void abortParsing() {
    myCaptures.forEach((id, capture) -> {
      boolean isCaptureCancelled = capture.cancel(true);
      if (!isCaptureCancelled) {
        getLogger().warn(String.format("Parsing of capture %d was not properly cancelled.", id));
      }
    });
  }

  public boolean isParsing() {
    return myIsParsing;
  }

  public long getParsingElapsedTimeMs() {
    return System.currentTimeMillis() - myParsingStartTimeMs;
  }

  public void setProcessNameHint(@Nullable String processName, int processIdHint) {
    myProcessNameHint = processName;
    myProcessIdHint = processIdHint;
  }

  /**
   * Updates {@link #myIsParsing} to false once the given {@link CompletableFuture<CpuCapture>} is done.
   */
  @Nullable
  private void updateParsingStateWhenDone(@Nullable CompletableFuture<CpuCapture> future) {
    if (future != null) {
      future.whenCompleteAsync((capture, exception) -> {
        myIsParsing = false;
        myAspect.changed(CpuProfilerAspect.CAPTURE_PARSING);
      }, myServices.getMainExecutor());
    }
    else {
      myIsParsing = false;
      myAspect.changed(CpuProfilerAspect.CAPTURE_PARSING);
    }
  }

  /**
   * Updates {@link #myIsParsing} to true and fire {@link CpuProfilerAspect#CAPTURE_PARSING} to notify the listeners about it.
   */
  @VisibleForTesting // In order to be accessible from com.android.tools.profilers.cpu.capturedetails
  public void updateParsingStateWhenStarting() {
    myParsingStartTimeMs = System.currentTimeMillis();
    myIsParsing = true;
    myAspect.changed(CpuProfilerAspect.CAPTURE_PARSING);
  }

  /**
   * Parses a {@link File} into a {@link CompletableFuture<CpuCapture>} that executes in {@link IdeProfilerServices#getPoolExecutor()}.
   * Return null if the file doesn't exist or point to a directory.
   * <p>
   * When a trace file is considered large (see {@link #MAX_SUPPORTED_TRACE_SIZE}), a dialog should be displayed so they user can decide if
   * they want to abort the trace parsing or continue with it.
   */
  @Nullable
  public CompletableFuture<CpuCapture> parse(@NotNull File traceFile) {
    if (!traceFile.exists() || traceFile.isDirectory()) {
      // Nothing to be parsed. We shouldn't even try to do it.
      getLogger().info("Trace not parsed, as its path doesn't exist or points to a directory.");
      return null;
    }

    updateParsingStateWhenStarting();
    long fileLength = traceFile.length();
    if (fileLength > MAX_SUPPORTED_TRACE_SIZE) {
      // Trace is too big. Ask the user if they want to proceed with parsing.
      Runnable yesCallback = () -> {
        getLogger().warn(String.format("Parsing long (%d bytes) trace file.", fileLength));
        // User decided to proceed. Try parsing the trace file.
        myCaptures.put(IMPORTED_TRACE_ID, createCaptureFuture(traceFile));
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
      myCaptures.put(IMPORTED_TRACE_ID, createCaptureFuture(traceFile));
    }

    updateParsingStateWhenDone(myCaptures.get(IMPORTED_TRACE_ID));
    return myCaptures.get(IMPORTED_TRACE_ID);
  }

  private CompletableFuture<CpuCapture> createCaptureFuture(@NotNull File traceFile) {
    CompletableFuture<CpuCapture> future =
      CompletableFuture.supplyAsync(() -> tryParsingFileWithDifferentParsers(traceFile), myServices.getPoolExecutor());
    return future;
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

    try {
      // Then, try parsing the file as a simpleperf trace.
      SimpleperfTraceParser simpleperfParser = new SimpleperfTraceParser();
      return simpleperfParser.parse(traceFile, IMPORTED_TRACE_ID);
    }
    catch (Exception ignored) {
      // We should go on and try parsing the file as an atrace trace.
    }

    // If atrace flag is enabled, check the file header to see if it's an atrace file.
    if (myServices.getFeatureConfig().isAtraceEnabled()) {
      try {
        if (AtraceProducer.verifyFileHasAtraceHeader(traceFile) ||
            (myServices.getFeatureConfig().isPerfettoEnabled() && PerfettoProducer.verifyFileHasPerfettoTraceHeader(traceFile))) {
          // Atrace files contain multiple processes. For imported Atrace files we don't have a
          // session that can tell us which process the user is interested in. So for all imported
          // trace files we ask the user to select a process. The list of processes the user can
          // choose from is parsed from the Atrace file.
          AtraceParser parser = new AtraceParser(traceFile);
          // Any process matching the application id of the current project will be sorted to
          // the top of our process list.
          CpuThreadSliceInfo[] processList = parser.getProcessList(myServices.getApplicationId());
          // Attempt to find users intended process.
          CpuThreadSliceInfo selected = null;
          // 1) Use hint if available.
          if (StringUtil.isNotEmpty(myProcessNameHint)) {
            selected = Arrays.stream(processList).filter(it -> myProcessNameHint.endsWith(it.getProcessName())).findFirst().orElse(null);
          }

          // 2) If we don't have a process based on named find one based on id.
          if (selected == null && myProcessIdHint > 0) {
            selected = Arrays.stream(processList).filter(it -> it.getProcessId() == myProcessIdHint).findFirst().orElse(null);
          }

          // 3) Ask the user for input.
          if (selected == null) {
            selected = myServices.openListBoxChooserDialog("Select a process",
                                                           "Select the process you want to analyze.",
                                                           processList,
                                                           (t) -> t.getProcessName());
          }
          if (selected != null) {
            parser.setSelectProcess(selected);
            return parser.parse(traceFile, IMPORTED_TRACE_ID);
          }
        }
      }
      catch (Exception ex) {
        // We failed to find a proper process, or the file was not atrace.
      }
    }

    // File couldn't be parsed by any of the parsers. Log the issue and return null.
    getLogger().warn(String.format("Parsing %s has failed.", traceFile.getPath()));
    return null;
  }

  /**
   * Creates a {@link CompletableFuture<CpuCapture>} from given trace bytes and the profiler type used to obtain the trace.
   * Uses {@link IdeProfilerServices#getPoolExecutor()} to create the actual {@link CpuCapture} object. Adds it to the captures map using
   * the trace id as key. Finally, returns the {@link CompletableFuture<CpuCapture>} created.
   */
  @Nullable
  public CompletableFuture<CpuCapture> parse(@NotNull Common.Session session,
                                             long traceId,
                                             @NotNull ByteString traceData,
                                             CpuTraceType profilerType) {
    updateParsingStateWhenStarting();
    CpuCaptureMetadata metadata = myCaptureMetadataMap.containsKey(traceId) ?
                                  myCaptureMetadataMap.get(traceId) : new CpuCaptureMetadata(new ProfilingConfiguration());

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

    CompletableFuture<CpuCapture> future = myCaptures.get(traceId);
    if (future != null) {
      future.whenCompleteAsync((capture, exception) -> {
        if (capture != null) {
          // Update capture metadata
          metadata.setStatus(CpuCaptureMetadata.CaptureStatus.SUCCESS);
          metadata.setParsingTimeMs(System.currentTimeMillis() - myParsingStartTimeMs);
          metadata.setCaptureDurationMs(TimeUnit.MICROSECONDS.toMillis(capture.getDurationUs()));
          metadata.setRecordDurationMs(calculateRecordDurationMs(capture));
        }
        else if (future.isCancelled()) {
          metadata.setStatus(CpuCaptureMetadata.CaptureStatus.USER_ABORTED_PARSING);
          myServices.showNotification(CpuProfilerNotifications.PARSING_ABORTED);
        }
        else if (exception != null) {
          metadata.setStatus(CpuCaptureMetadata.CaptureStatus.PARSING_FAILURE);
          getLogger().warn("Unable to parse capture: " + exception.getMessage(), exception);
          myServices.showNotification(CpuProfilerNotifications.PARSING_FAILURE);
        }

        // Only log the first time.
        // TODO b/133325202 this feels quite ugly... as it requires the stage to set the metadata entry when tracing is stopped, then when
        // the parsing subsequently happens, multiple places populate the metadata instance (e.g. this method and createCaptureFuture)
        // before it can be sent. Investigate whether there is a way to keep all logic in one place.
        if (myCaptureMetadataMap.containsKey(traceId)) {
          myServices.getFeatureTracker().trackCaptureTrace(metadata);
          myCaptureMetadataMap.remove(traceId);
        }
      }, myServices.getMainExecutor());
    }
    else {
      metadata.setTraceFileSizeBytes(traceData.size());
      metadata.setStatus(CpuCaptureMetadata.CaptureStatus.USER_ABORTED_PARSING);
      myServices.showNotification(CpuProfilerNotifications.PARSING_ABORTED);

      // TODO b/133325202 this feels quite ugly... as it requires the stage to set the metadata entry when tracing is stop, then when the
      // parsing subsequently happens, multiple places populate the metadata instance (e.g. this method and createCaptureFuture) before it
      // can be sent. Investigate whether there is a way to keep all logic in one place.
      if (myCaptureMetadataMap.containsKey(traceId)) {
        myServices.getFeatureTracker().trackCaptureTrace(metadata);
        myCaptureMetadataMap.remove(traceId);
      }
    }

    updateParsingStateWhenDone(future);
    return future;
  }

  /**
   * Iterates the threads of the capture to find the node with the minimum start time and the one with the maximum end time.
   * Maximum end - minimum start result in the record duration.
   */
  private static long calculateRecordDurationMs(CpuCapture capture) {
    Range maxDataRange = new Range();
    for (CpuThreadInfo thread : capture.getThreads()) {
      CaptureNode threadMainNode = capture.getCaptureNode(thread.getId());
      assert threadMainNode != null;
      maxDataRange.expand(threadMainNode.getStartGlobal(), threadMainNode.getEndGlobal());
    }
    return TimeUnit.MICROSECONDS.toMillis((long)maxDataRange.getLength());
  }

  private CompletableFuture<CpuCapture> createCaptureFuture(@NotNull Common.Session session, long traceId, ByteString traceBytes,
                                                            CpuTraceType profilerType) {
    CpuCaptureMetadata metadata = myCaptureMetadataMap.containsKey(traceId) ?
                                  myCaptureMetadataMap.get(traceId) : new CpuCaptureMetadata(new ProfilingConfiguration());

    CompletableFuture<CpuCapture> future =
      CompletableFuture.supplyAsync(() -> {
        ByteString processedBytes = traceBytes;
        metadata.setTraceFileSizeBytes(processedBytes.size());
        boolean failedToPreProcess = processedBytes.equals(TracePreProcessor.FAILURE);
        if (failedToPreProcess) {
          metadata.setStatus(CpuCaptureMetadata.CaptureStatus.PREPROCESS_FAILURE);
          return null;
        }
        return traceBytesToCapture(session, traceId, processedBytes, profilerType);
      }, myServices.getPoolExecutor())
        .thenApplyAsync(capture -> {
          if (metadata.getStatus() == CpuCaptureMetadata.CaptureStatus.PREPROCESS_FAILURE) {
            myServices.showNotification(CpuProfilerNotifications.PREPROCESS_FAILURE);
            getLogger().warn("Unable to pre-process trace file.");
          }
          return capture;
        }, myServices.getMainExecutor());

    return future;
  }

  private CpuCapture traceBytesToCapture(@NotNull Common.Session session, long traceId, @NotNull ByteString traceData,
                                         CpuTraceType profilerType) {
    // TODO: Remove layers, analyze whether we can keep the whole file in memory.
    try {
      File trace = CpuCaptureStage.saveCapture(traceId, traceData);
      TraceParser parser;
      if (profilerType == CpuTraceType.ART) {
        parser = new ArtTraceParser();
      }
      else if (profilerType == CpuTraceType.SIMPLEPERF) {
        parser = new SimpleperfTraceParser();
      }
      else if (profilerType == CpuTraceType.ATRACE) {
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
