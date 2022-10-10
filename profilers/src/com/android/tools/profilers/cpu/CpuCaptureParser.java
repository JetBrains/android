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
import com.android.tools.idea.protobuf.ByteString;
import com.android.tools.profiler.proto.Trace.UserOptions.TraceType;
import com.android.tools.profilers.IdeProfilerServices;
import com.android.tools.profilers.cpu.art.ArtTraceParser;
import com.android.tools.profilers.cpu.config.ProfilingConfiguration;
import com.android.tools.profilers.cpu.config.UnspecifiedConfiguration;
import com.android.tools.profilers.cpu.simpleperf.SimpleperfTraceParser;
import com.android.tools.profilers.cpu.systemtrace.AtraceParser;
import com.android.tools.profilers.cpu.systemtrace.AtraceProducer;
import com.android.tools.profilers.cpu.systemtrace.PerfettoProducer;
import com.android.tools.profilers.perfetto.PerfettoParser;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.diagnostic.Logger;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
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

  public CpuCaptureParser(@NotNull IdeProfilerServices services) {
    myServices = services;
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
    @NotNull File traceFile, long traceId, @NotNull TraceType preferredProfilerType, int processIdHint, String processNameHint) {
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
        .whenCompleteAsync(new TraceResultHandler(traceFile, traceId, isImportedTrace), myServices.getMainExecutor());
    myCaptures.put(traceId, cpuCapture);
    return cpuCapture;
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
    private FileHeaderParsingFailureException(@NotNull String traceFilePath, @NotNull TraceType type) {
      super(String.format("Trace file '%s' expected to be of type %s but failed header verification.", traceFilePath, type));
    }

    private FileHeaderParsingFailureException(@NotNull String traceFilePath, @NotNull TraceType type, @NotNull Throwable cause) {
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
        } catch (IOException e) {
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
      myServices.openYesNoDialog("The trace file generated is large, and Android Studio may become unresponsive while " +
                                 "it parses the data. Do you want to continue?\n\n" +
                                 "Warning: If you select \"No\", Android Studio discards the trace data and you will need " +
                                 "to capture a new method trace.",
                                 "Trace File Too Large",
                                 yesCallback,
                                 noCallback);
    }
  }

  /**
   * This step contains the parse logic itself, including selecting (or detecting) the appropriate parser technology.
   */
  private static final class ProcessTraceAction implements Function<Void, CpuCapture> {
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
    private final Supplier<TraceParser> PERFETTO_PARSER_SUPPLIER = () -> new PerfettoParser(getMainProcessSelector(), getProfilerServices());

    // Specific file tests used in parseToCapture before attempting to parse the whole trace.
    private static final Predicate<File> NO_OP_FILE_TESTER = null;
    private static final Predicate<File> ATRACE_FILE_TESTER = (t) -> AtraceProducer.verifyFileHasAtraceHeader(t);
    private static final Predicate<File> PERFETTO_FILE_TESTER = (t) -> PerfettoProducer.verifyFileHasPerfettoTraceHeader(t);

    private ProcessTraceAction(
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

    @Nullable
    private CpuCapture parseToCapture(
      @NotNull File traceFile, long traceId, @NotNull TraceType profilerType) {

      boolean unknownType = TraceType.UNSPECIFIED_TYPE.equals(profilerType);

      if (unknownType || profilerType == TraceType.ART) {
        CpuCapture capture =
          tryToParseWith(TraceType.ART, traceFile, traceId, !unknownType, NO_OP_FILE_TESTER, ART_PARSER_SUPPLIER);
        if (capture != null) {
          return capture;
        }
      }

      if (unknownType || profilerType == TraceType.SIMPLEPERF) {
        CpuCapture capture =
          tryToParseWith(TraceType.SIMPLEPERF, traceFile, traceId, !unknownType, NO_OP_FILE_TESTER, SIMPLEPERF_PARSER_SUPPLIER);
        if (capture != null) {
          return capture;
        }
      }

      if (unknownType || profilerType == TraceType.ATRACE) {
        CpuCapture capture =
          tryToParseWith(TraceType.ATRACE, traceFile, traceId, !unknownType, ATRACE_FILE_TESTER, ATRACE_PARSER_SUPPLIER);
        if (capture != null) {
          return capture;
        }
      }

      if (unknownType || profilerType == TraceType.PERFETTO) {
        CpuCapture capture =
          tryToParseWith(TraceType.PERFETTO, traceFile, traceId, !unknownType, PERFETTO_FILE_TESTER, PERFETTO_PARSER_SUPPLIER);
        if (capture != null) {
          return capture;
        }
      }

      if (unknownType) {
        throw new UnknownParserParsingFailureException(traceFile.getAbsolutePath());
      }
      return null;
    }

    @NotNull
    private MainProcessSelector getMainProcessSelector() {
      return new MainProcessSelector(processNameHint, processIdHint, services);
    }

    @NotNull
    private IdeProfilerServices getProfilerServices() {
      return services;
    }

    @Nullable
    private static CpuCapture tryToParseWith(@NotNull TraceType type,
                                             @NotNull File traceFile,
                                             long traceId,
                                             boolean expectedToBeCorrectParser,
                                             @Nullable Predicate<File> traceInputVerification,
                                             @NotNull Supplier<TraceParser> parserSupplier) {

      if (traceInputVerification != null) {
        boolean inputVerification;
        try {
          inputVerification = traceInputVerification.test(traceFile);
        } catch (Throwable t) {
          throw new FileHeaderParsingFailureException(traceFile.getAbsolutePath(), type, t);
        }
        if (!inputVerification) {
          if (expectedToBeCorrectParser) {
            throw new FileHeaderParsingFailureException(traceFile.getAbsolutePath(), type);
          }
          else {
            return null;
          }
        }
      }

      TraceParser parser = parserSupplier.get();

      try {
        return parser.parse(traceFile, traceId);
      }
      catch (ProcessSelectorDialogAbortedException e) {
        throw new CancellationException("User aborted process choice dialog.");
      }
      catch (Throwable t) {
        // If we expected this to be the correct parser or we already checked that this parser can take this trace, then we need to throw
        if (expectedToBeCorrectParser || traceInputVerification != null) {
          throw new ParsingFailureException(String.format("Trace file '%s' failed to be parsed as %s.", traceFile.getAbsolutePath(), type),
                                            t);
        }
        else {
          return null;
        }
      }
    }


  }

  /**
   * This step update the parser state as finished and is also responsible to handle the metric
   * reporting logic.
   */
  private final class TraceResultHandler implements BiConsumer<CpuCapture, Throwable> {
    @NotNull
    private final File traceFile;
    private final long traceId;
    private final boolean isImportedTrace;

    private TraceResultHandler(@NotNull File traceFile, long traceId, boolean isImportedTrace) {
      this.traceFile = traceFile;
      this.traceId = traceId;
      this.isImportedTrace = isImportedTrace;
    }

    @Override
    public void accept(CpuCapture capture, Throwable throwable) {
      updateParsingStateWhenDone();

      CpuCaptureMetadata metadata =
        myCaptureMetadataMap.computeIfAbsent(traceId, (id) -> new CpuCaptureMetadata(
          new UnspecifiedConfiguration(ProfilingConfiguration.DEFAULT_CONFIGURATION_NAME)));
      metadata.setTraceFileSizeBytes((int)traceFile.length());

      if (capture != null) {
        metadata.setStatus(CpuCaptureMetadata.CaptureStatus.SUCCESS);
        // Set the parsing time at least 1 millisecond, to make it easy to verify in tests.
        metadata.setParsingTimeMs(Math.max(1, System.currentTimeMillis() - myParsingStartTimeMs));
        metadata.setCaptureDurationMs(TimeUnit.MICROSECONDS.toMillis(capture.getDurationUs()));
        metadata.setRecordDurationMs(calculateRecordDurationMs(capture));
      }
      else if (throwable != null) {
        LOGGER.warn("Unable to parse capture: " + throwable.getMessage(), throwable.getCause());
        if (throwable.getCause() instanceof CancellationException) {
          metadata.setStatus(CpuCaptureMetadata.CaptureStatus.USER_ABORTED_PARSING);
          myServices.showNotification(CpuProfilerNotifications.PARSING_ABORTED);
        }
        else if (throwable.getCause() instanceof PreProcessorFailureException) {
          myServices.showNotification(CpuProfilerNotifications.PREPROCESS_FAILURE);
          // More granular preprocess failures are logged by preprocessors. Skip logging here.
          return;
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
        if (isImportedTrace && capture != null) {
          myServices.getFeatureTracker().trackImportTrace(capture.getType(), true);
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
  }
}
