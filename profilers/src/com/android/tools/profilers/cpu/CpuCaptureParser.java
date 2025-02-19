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

import static com.google.wireless.android.sdk.stats.CpuCaptureMetadata.CaptureStatus.UNKNOWN_STATUS;
import static com.google.wireless.android.sdk.stats.CpuCaptureMetadata.CaptureStatus.valueOf;
import static com.google.wireless.android.sdk.stats.CpuImportTraceMetadata.ImportStatus.IMPORT_TRACE_FAILURE;
import static com.google.wireless.android.sdk.stats.CpuImportTraceMetadata.ImportStatus.IMPORT_TRACE_SUCCESS;

import com.android.tools.adtui.model.AspectModel;
import com.android.tools.adtui.model.Range;
import com.android.tools.idea.protobuf.ByteString;
import com.android.tools.profilers.IdeProfilerServices;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.cpu.art.ArtTraceParser;
import com.android.tools.profilers.cpu.compose.ComposeTracingConstants;
import com.android.tools.profilers.cpu.config.ProfilingConfiguration;
import com.android.tools.profilers.cpu.config.ProfilingConfiguration.TraceType;
import com.android.tools.profilers.cpu.config.UnspecifiedConfiguration;
import com.android.tools.profilers.cpu.nodemodel.SystemTraceNodeModel;
import com.android.tools.profilers.cpu.simpleperf.SimpleperfTraceParser;
import com.android.tools.profilers.cpu.systemtrace.AtraceParser;
import com.android.tools.profilers.perfetto.PerfettoParser;
import com.android.tools.profilers.tasks.TaskEventTrackerUtils;
import com.android.tools.profilers.tasks.TaskFinishedState;
import com.android.tools.profilers.tasks.TaskProcessingFailedMetadata;
import com.google.common.annotations.VisibleForTesting;
import com.google.wireless.android.sdk.stats.CpuImportTraceMetadata;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.diagnostic.Logger;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
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
  public static final int MAX_SUPPORTED_TRACE_SIZE = 1024 * 1024 * 100; // 100MB

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

  @NotNull
  private final StudioProfilers myProfilers;

  private final AspectModel<CpuProfilerAspect> myAspect = new AspectModel<>();

  /**
   * Whether there is a parsing in progress. This value is set to true in {@link #updateParsingStateWhenStarting()} followed by a
   * {@link CpuProfilerAspect#CAPTURE_PARSING} being fired, and set to false by {@link #updateParsingStateWhenDone()},
   * which should be called around every {@link CompletableFuture<CpuCapture>} created by this class.
   */
  private boolean myIsParsing;

  /**
   * Unix epoch time when capture parsing started.
   */
  private long myParsingStartTimeMs;

  /**
   * Metadata associated with parsing a capture.
   */
  private final Map<Long, CpuCaptureMetadata> myCaptureMetadataMap = new HashMap<>();

  /**
   * If an entry exist we have already published metrics for the loaded capture. We use a static set because
   * the CpuCaptureParser is recreated each time a new capture is loaded.
   */
  private static final Set<String> myPreviouslyLoadedCaptures = new HashSet<>();

  private static final Logger LOGGER = Logger.getInstance(CpuCaptureParser.class);

  public CpuCaptureParser(@NotNull StudioProfilers profilers) {
    myProfilers = profilers;
    myServices = profilers.getIdeServices();
    myCaptures = new HashMap<>();
  }

  public AspectModel<CpuProfilerAspect> getAspect() {
    return myAspect;
  }

  @VisibleForTesting
  static void clearPreviouslyLoadedCaptures() {
    myPreviouslyLoadedCaptures.clear();
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
        LOGGER.warn(String.format("Parsing of capture %d was not properly cancelled.", id));
      }
    });
  }

  /**
   * Whether there is a parsing in progress.
   */
  public boolean isParsing() {
    return myIsParsing;
  }

  public long getParsingElapsedTimeMs() {
    return System.currentTimeMillis() - myParsingStartTimeMs;
  }

  /**
   * Updates {@link #myIsParsing} to false once the given {@link CompletableFuture<CpuCapture>} is done.
   */
  private void updateParsingStateWhenDone() {
    myIsParsing = false;
    myAspect.changed(CpuProfilerAspect.CAPTURE_PARSING);
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
   * Parses a {@link File} into a {@link CompletableFuture<CpuCapture>}.
   * <p>
   * When a trace file is considered large (see {@link #MAX_SUPPORTED_TRACE_SIZE}), a dialog should be displayed so they user can decide if
   * they want to abort the trace parsing or continue with it.
   */
  @NotNull
  public CompletableFuture<CpuCapture> parse(
    // Consider passing a CompletableFuture<File> instead of a File here, so we can chain all of them properly.
    @NotNull File traceFile,
    long traceId,
    @NotNull TraceType preferredProfilerType,
    int processIdHint,
    String processNameHint,
    @NotNull Consumer<TaskFinishedState> trackTaskFinished) {
    if (myCaptures.containsKey(traceId)) {
      return myCaptures.get(traceId);
    }

    // If we don't have a hint for the process id, we assume it was an imported trace as we have no extra information.
    boolean isImportedTrace = (processIdHint == 0);

    CompletableFuture<CpuCapture> cpuCapture =
      CompletableFuture.runAsync(new TraceFileValidationAction(traceFile), myServices.getPoolExecutor())
        .thenRunAsync(new ParsingStartAction(traceFile), myServices.getMainExecutor())
        .thenApplyAsync(
          new ProcessTraceAction(traceFile, traceId, preferredProfilerType, processIdHint, processNameHint, myServices),
          myServices.getPoolExecutor())
        .whenCompleteAsync(new TraceResultHandler(traceFile, traceId, isImportedTrace, trackTaskFinished), myServices.getMainExecutor());
    myCaptures.put(traceId, cpuCapture);
    return cpuCapture;
  }

  // Added this method in this class (outside TraceResultHandler) to also be accessible in CpuProfilerStage
  @NotNull
  public static com.google.wireless.android.sdk.stats.CpuCaptureMetadata getCpuCaptureMetadata(@NotNull CpuCaptureMetadata metadata) {
    com.google.wireless.android.sdk.stats.CpuCaptureMetadata.Builder result =
      com.google.wireless.android.sdk.stats.CpuCaptureMetadata.newBuilder()
        .setCaptureStatus(metadata.getStatus() != null ? valueOf(metadata.getStatus().name()): UNKNOWN_STATUS)
        .setCaptureDurationMs(metadata.getCaptureDurationMs())
        .setParsingTimeMs(metadata.getParsingTimeMs())
        .setRecordDurationMs(metadata.getRecordDurationMs())
        .setStoppingTimeMs(metadata.getStoppingTimeMs())
        .setHasComposeTracingNodes(Boolean.TRUE.equals(metadata.getHasComposeTracingNodes()))
        .setTraceFileSizeBytes(metadata.getTraceFileSizeBytes())
        .setArtStopTimeoutSec(metadata.getArtStopTimeoutSec());

    if (metadata.getCpuProfilerEntryPoint() != null) {
      result.setCpuProfilerEntryPoint(
        com.google.wireless.android.sdk.stats.CpuCaptureMetadata.CpuProfilerEntryPoint.valueOf(metadata.getCpuProfilerEntryPoint().name()));
    }
    return result.build();
  }

  /**
   * Represents an error during a cpu capture parsing, that can't be worked around and results in
   * a failed parse attempt.
   */
  public static class ParsingFailureException extends RuntimeException {
    private ParsingFailureException() {
      super();
    }

    protected ParsingFailureException(@NotNull String string) {
      super(string);
    }

    protected ParsingFailureException(@NotNull String string, @NotNull Throwable cause) {
      super(string, cause);
    }
  }

  /**
   * Represents a pre-processor error for a cpu capture, signaling that the parsing attempt cannot
   * continue.
   */
  public static class PreProcessorFailureException extends ParsingFailureException {
    private PreProcessorFailureException() {
      super();
    }
  }

  public static class InvalidPathParsingFailureException extends ParsingFailureException {
    private InvalidPathParsingFailureException(@NotNull String traceFilePath) {
      super(String.format("Trace not parsed. Path doesn't exist or points to a directory: '%s'.", traceFilePath));
    }
  }

  public static class ReadErrorParsingFailureException extends ParsingFailureException {
    private ReadErrorParsingFailureException(@NotNull String traceFilePath, @NotNull IOException readError) {
      super(String.format("Trace not parsed. Unable to read file: '%s'.", traceFilePath), readError);
    }
  }

  public static class UnknownParserParsingFailureException extends ParsingFailureException {
    private UnknownParserParsingFailureException(@NotNull String traceFilePath) {
      super(String.format("Trace not parsed. Couldn't identify the correct parser for '%s'.", traceFilePath));
    }
  }

  public static class FileHeaderParsingFailureException extends ParsingFailureException {
    public FileHeaderParsingFailureException(@NotNull String traceFilePath, @NotNull TraceType type) {
      super(String.format("Trace file '%s' expected to be of type %s but failed header verification.", traceFilePath, type));
    }

    public FileHeaderParsingFailureException(@NotNull String traceFilePath, @NotNull TraceType type, @NotNull Throwable cause) {
      super(String.format("Trace file '%s' expected to be of type %s but failed header verification.", traceFilePath, type), cause);
    }
  }

  /**
   * Performs basic checks on the trace file to be parsed:
   * <ul>
   *   <li>The File exists and is not a directory.</li>
   *   <li>There were no issues on pre-processor that executed in the file.</li>
   * </ul>
   */
  private static final class TraceFileValidationAction implements Runnable {
    @NotNull
    private final File traceFile;

    private TraceFileValidationAction(@NotNull File traceFile) {
      this.traceFile = traceFile;
    }

    @Override
    public void run() {
      if (!traceFile.exists() || traceFile.isDirectory()) {
        // Nothing to be parsed. We shouldn't even try to do it.
        throw new InvalidPathParsingFailureException(traceFile.getAbsolutePath());
      }

      // In order to check if this is a PreProcessor failure, we check if the file is the same size as the marker (few bytes)
      // Before trying to load it up and compare the values.
      // TODO(b/151617426): We should have a better way to signal this type of failure than writing "Failure" as the file content.
      if (traceFile.length() == TracePreProcessor.FAILURE.size()) {
        try (InputStream is = new FileInputStream(traceFile)) {
          ByteString fileContent = ByteString.readFrom(is);

          if (TracePreProcessor.FAILURE.equals(fileContent)) {
            throw new PreProcessorFailureException();
          }
        }
        catch (IOException e) {
          throw new ReadErrorParsingFailureException(traceFile.getAbsolutePath(), e);
        }
      }
    }
  }

  /**
   * This step updates the overall parser status to parsing while also asking the user for
   * confirmation if the file is very big.
   */
  private final class ParsingStartAction implements Runnable {
    @NotNull
    private final File traceFile;

    private ParsingStartAction(@NotNull File traceFile) {
      this.traceFile = traceFile;
    }

    @Override
    public void run() {
      updateParsingStateWhenStarting();

      long traceLengthBytes = traceFile.length();
      if (traceFile.length() > MAX_SUPPORTED_TRACE_SIZE) {
        // If the user decided to proceed, we have nothing to do.
        Runnable yesCallback = () -> {
        };

        // If the user decides to abort, we throw a CancellationException in order to break the computation chain.
        Runnable noCallback = () -> {
          throw new CancellationException(
            String.format("Parsing of a long (%d bytes) trace file was aborted by the user.", traceLengthBytes));
        };

        openParseLargeTracesDialog(yesCallback, noCallback);
      }
    }

    private void openParseLargeTracesDialog(Runnable yesCallback, Runnable noCallback) {
      myServices.openYesNoDialog("The trace file generated is large, and " +
                                 ApplicationNamesInfo.getInstance().getFullProductName() +
                                 " may become unresponsive while " +
                                 "it parses the data. Do you want to continue?\n\n" +
                                 "Warning: If you select \"No\", " +
                                 ApplicationNamesInfo.getInstance().getFullProductName() +
                                 " discards the trace data and you will need " +
                                 "to capture a new method trace.",
                                 "Trace File Too Large",
                                 yesCallback,
                                 noCallback);
    }
  }

  /**
   * This step contains the parse logic itself, including selecting (or detecting) the appropriate parser technology.
   */
  @VisibleForTesting
  public static final class ProcessTraceAction implements Function<Void, CpuCapture> {
    @NotNull
    private final File traceFile;

    private final long traceId;

    @NotNull
    private final TraceType preferredProfilerType;

    private final int processIdHint;

    @NotNull
    private final String processNameHint;

    @NotNull
    private final IdeProfilerServices services;

    // Parsers used by parseToCapture
    private static final Supplier<TraceParser> ART_PARSER_SUPPLIER = () -> new ArtTraceParser();
    private static final Supplier<TraceParser> SIMPLEPERF_PARSER_SUPPLIER = () -> new SimpleperfTraceParser();
    private final Supplier<TraceParser> ATRACE_PARSER_SUPPLIER = () -> new AtraceParser(getMainProcessSelector());
    private final Supplier<TraceParser> PERFETTO_PARSER_SUPPLIER =
      () -> new PerfettoParser(getMainProcessSelector(), getProfilerServices());

    @VisibleForTesting
    public ProcessTraceAction(
      @NotNull File traceFile, long traceId, @NotNull TraceType preferredProfilerType,
      int processIdHint, @Nullable String processNameHint, @NotNull IdeProfilerServices services) {

      this.traceFile = traceFile;
      this.traceId = traceId;
      this.preferredProfilerType = preferredProfilerType;
      this.processIdHint = processIdHint;
      this.processNameHint = processNameHint != null ? processNameHint : "";
      this.services = services;
    }

    @Override
    public CpuCapture apply(Void aVoid) {
      return parseToCapture(traceFile, traceId, preferredProfilerType);
    }

    private CpuCapture parseToCapture(@NotNull File traceFile, long traceId, @NotNull TraceType profilerType) {
      final TraceType traceType = CpuCaptureParserUtil.getFileTraceType(traceFile, profilerType);
      if (traceType == null) {
        // None of the types able to parse the given file
        throw new UnknownParserParsingFailureException(traceFile.getAbsolutePath());
      }
      return parseWith(traceType, traceFile, traceId);
    }

    @NotNull
    private MainProcessSelector getMainProcessSelector() {
      return new MainProcessSelector(processNameHint, processIdHint, services);
    }

    @NotNull
    private IdeProfilerServices getProfilerServices() {
      return services;
    }

    private CpuCapture parseWith(@NotNull TraceType type, @NotNull File traceFile, long traceId) {
      Supplier<TraceParser> parserSupplier = getParserSupplier(type);
      TraceParser parser = parserSupplier.get();
      try {
        return parser.parse(traceFile, traceId);
      }
      catch (ProcessSelectorDialogAbortedException e) {
        throw new CancellationException("User aborted process choice dialog.");
      }
      catch (Throwable e) {
          throw new CpuCaptureParser.ParsingFailureException(
            String.format("Trace file '%s' failed to be parsed as %s.", traceFile.getAbsolutePath(), type), e);
      }
    }

    @VisibleForTesting
    Supplier<TraceParser> getParserSupplier(@NotNull TraceType type) {
      return switch (type) {
        case ART -> ART_PARSER_SUPPLIER;
        case SIMPLEPERF -> SIMPLEPERF_PARSER_SUPPLIER;
        case ATRACE -> ATRACE_PARSER_SUPPLIER;
        case PERFETTO -> PERFETTO_PARSER_SUPPLIER;
        default -> null;
      };
    }
  }

  /**
   * This step update the parser state as finished and is also responsible to handle the metric
   * reporting logic.
   * <p>
   * Note: Metadata related work (in the {@link #accept} method) is not expected to be run on the main thread, and therefore its computation
   * is not on the critical path.
   */
  private final class TraceResultHandler implements BiConsumer<CpuCapture, Throwable> {
    @NotNull
    private final File traceFile;
    private final long traceId;
    private final boolean isImportedTrace;
    private final Consumer<TaskFinishedState> trackTaskFinished;

    private TraceResultHandler(@NotNull File traceFile,
                               long traceId,
                               boolean isImportedTrace,
                               @NotNull Consumer<TaskFinishedState> trackTaskFinished) {
      this.traceFile = traceFile;
      this.traceId = traceId;
      this.isImportedTrace = isImportedTrace;
      this.trackTaskFinished = trackTaskFinished;
    }

    @Override
    public void accept(CpuCapture capture, Throwable throwable) {
      updateParsingStateWhenDone();

      CpuCaptureMetadata metadata =
        myCaptureMetadataMap.computeIfAbsent(traceId, (id) -> new CpuCaptureMetadata(
          new UnspecifiedConfiguration(ProfilingConfiguration.DEFAULT_CONFIGURATION_NAME)));
      metadata.setTraceFileSizeBytes((int)traceFile.length());

      if (metadata.getProfilingConfiguration().getTraceType() == TraceType.ART) {
        metadata.setArtStopTimeoutSec(CpuProfilerStage.CPU_ART_STOP_TIMEOUT_SEC);
      }

      if (capture != null) {
        metadata.setStatus(CpuCaptureMetadata.CaptureStatus.SUCCESS);
        // Set the parsing time at least 1 millisecond, to make it easy to verify in tests.
        metadata.setParsingTimeMs(Math.max(1, System.currentTimeMillis() - myParsingStartTimeMs));
        metadata.setCaptureDurationMs(TimeUnit.MICROSECONDS.toMillis(capture.getDurationUs()));
        metadata.setRecordDurationMs(calculateRecordDurationMs(capture));
        metadata.setHasComposeTracingNodes(checkHasComposeTracingNodes(capture));
      }
      else if (throwable != null) {
        LOGGER.warn("Unable to parse capture: " + throwable.getMessage(), throwable.getCause());
        if (throwable.getCause() instanceof CancellationException) {
          metadata.setStatus(CpuCaptureMetadata.CaptureStatus.USER_ABORTED_PARSING);
          myServices.showNotification(CpuProfilerNotifications.PARSING_ABORTED);
          // Track that the task has finished with user cancellation.
          trackTaskFinished.accept(TaskFinishedState.USER_CANCELLED);
        }
        else if (throwable.getCause() instanceof PreProcessorFailureException) {
          myServices.showNotification(CpuProfilerNotifications.PREPROCESS_FAILURE);
          // More granular preprocess failures are logged by preprocessors. Skip logging here.
          if (!myServices.getFeatureConfig().isTaskBasedUxEnabled()) {
            return;
          }
          metadata.setStatus(CpuCaptureMetadata.CaptureStatus.PREPROCESS_FAILURE);
        }
        else if (throwable.getCause() instanceof InvalidPathParsingFailureException) {
          metadata.setStatus(CpuCaptureMetadata.CaptureStatus.PARSING_FAILED_PATH_INVALID);
          myServices.showNotification(CpuProfilerNotifications.PARSING_FAILURE);
        }
        else if (throwable.getCause() instanceof ReadErrorParsingFailureException) {
          metadata.setStatus(CpuCaptureMetadata.CaptureStatus.PARSING_FAILED_READ_ERROR);
          myServices.showNotification(CpuProfilerNotifications.PARSING_FAILURE);
        }
        else if (throwable.getCause() instanceof UnknownParserParsingFailureException) {
          metadata.setStatus(CpuCaptureMetadata.CaptureStatus.PARSING_FAILED_PARSER_UNKNOWN);
          myServices.showNotification(CpuProfilerNotifications.PARSING_FAILURE);
        }
        else if (throwable.getCause() instanceof FileHeaderParsingFailureException) {
          metadata.setStatus(CpuCaptureMetadata.CaptureStatus.PARSING_FAILED_FILE_HEADER_ERROR);
          myServices.showNotification(CpuProfilerNotifications.PARSING_FAILURE);
        }
        else if (throwable.getCause() instanceof ParsingFailureException) {
          metadata.setStatus(CpuCaptureMetadata.CaptureStatus.PARSING_FAILED_PARSER_ERROR);
          myServices.showNotification(CpuProfilerNotifications.PARSING_FAILURE);
        }
        else {
          metadata.setStatus(CpuCaptureMetadata.CaptureStatus.PARSING_FAILED_CAUSE_UNKNOWN);
          myServices.showNotification(CpuProfilerNotifications.PARSING_FAILURE);
        }
      }
      else {
        LOGGER.warn("Unable to parse capture: no throwable.");
        metadata.setStatus(CpuCaptureMetadata.CaptureStatus.PARSING_FAILED_CAUSE_UNKNOWN);
        myServices.showNotification(CpuProfilerNotifications.PARSING_FAILURE);
      }

      // Don't report metrics for the same trace/capture twice. Can we track this in the metadata somehow?
      if (!myPreviouslyLoadedCaptures.contains(traceFile.getAbsolutePath())) {
        myPreviouslyLoadedCaptures.add(traceFile.getAbsolutePath());
        myServices.getFeatureTracker().trackCaptureTrace(metadata);
        // We also have a specific set of metrics for imported:
        if (isImportedTrace) {
          var type = capture != null
                     ? capture.getType() // get info from capture
                     : metadata.getProfilingConfiguration().getTraceType(); // best effort to get info
          var isSuccess = capture != null;
          if (myServices.getFeatureConfig().isTaskBasedUxEnabled()) {
            if (!isSuccess) {
              TaskEventTrackerUtils.trackProcessingTaskFailed(myProfilers, myProfilers.getSessionsManager().isSessionAlive(),
                                                              new TaskProcessingFailedMetadata(metadata));
            }
          }
          else {
            myServices.getFeatureTracker()
              .trackImportTrace(createCpuImportTraceMetadata(type, isSuccess, metadata.getHasComposeTracingNodes()));
          }
        }
        myCaptureMetadataMap.remove(traceId);
      }
    }

    /**
     * Iterates the threads of the capture to find the node with the minimum start time and the one with the maximum end time.
     * Maximum end - minimum start result in the record duration.
     */
    private long calculateRecordDurationMs(CpuCapture capture) {
      Range maxDataRange = new Range();
      for (CaptureNode node : capture.getCaptureNodes()) {
        maxDataRange.expand(node.getStartGlobal(), node.getEndGlobal());
      }
      return TimeUnit.MICROSECONDS.toMillis((long)maxDataRange.getLength());
    }

    private @NotNull Boolean checkHasComposeTracingNodes(CpuCapture capture) {
      var pattern = Pattern.compile(ComposeTracingConstants.COMPOSABLE_TRACE_EVENT_REGEX);

      var queue = new ArrayDeque<CaptureNode>();
      var seen = new HashSet<CaptureNode>();
      for (CaptureNode node : capture.getCaptureNodes()) if (seen.add(node)) queue.add(node);

      while (!queue.isEmpty()) {
        var node = queue.removeFirst();
        var data = node.getData();
        if (data instanceof SystemTraceNodeModel && pattern.matcher(data.getFullName()).find()) return true;
        for (CaptureNode child : node.childrenList) if (seen.add(child)) queue.addLast(child);
      }

      return false;
    }

    private CpuImportTraceMetadata createCpuImportTraceMetadata(@NotNull TraceType profilerType,
                                                                boolean isSuccess,
                                                                @Nullable Boolean hasComposeTracingNodes) {
      CpuImportTraceMetadata.Builder metadata = CpuImportTraceMetadata.newBuilder();
      metadata.setImportStatus(isSuccess ? IMPORT_TRACE_SUCCESS : IMPORT_TRACE_FAILURE);
      metadata.setTechnology(technologyForProfilerType(profilerType));
      if (hasComposeTracingNodes != null) metadata.setHasComposeTracingNodes(hasComposeTracingNodes);
      return metadata.build();
    }

    private CpuImportTraceMetadata.Technology technologyForProfilerType(@NotNull TraceType profilerType) {
      switch (profilerType) {
        case ART:
          return CpuImportTraceMetadata.Technology.ART_TECHNOLOGY;
        case SIMPLEPERF:
          return CpuImportTraceMetadata.Technology.SIMPLEPERF_TECHNOLOGY;
        case ATRACE:
          return CpuImportTraceMetadata.Technology.ATRACE_TECHNOLOGY;
        case PERFETTO:
          return CpuImportTraceMetadata.Technology.PERFETTO_TECHNOLOGY;
        default:
          return CpuImportTraceMetadata.Technology.UNKNOWN_TECHNOLOGY;
      }
    }
  }
}
