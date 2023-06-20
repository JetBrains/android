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

import com.android.tools.profiler.proto.Trace;
import com.android.tools.profilers.cpu.config.ProfilingConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Metadata of a {@link CpuCapture}, such as capture duration, parsing time, profiler type, etc.
 */
public class CpuCaptureMetadata {

  public enum CaptureStatus {
    /** Capture finished successfully. */
    SUCCESS,
    /** There was a failure when trying to stop the capture. */
    /** Deprecated by STOP_FAILED_* enum constants. */
    @Deprecated
    STOP_CAPTURING_FAILURE,
    /** Deprecated by PARSING_FAILED_* enum constants. */
    @Deprecated
    PARSING_FAILURE,
    /** User aborted parsing the trace after being notified it was too large. */
    USER_ABORTED_PARSING,
    /** There was a failure when trying to pre-process the trace. */
    PREPROCESS_FAILURE,
    /** There was no ongoing capture to stop. */
    STOP_FAILED_NO_GOING_PROFILING,
    /** The profiled app process died. */
    STOP_FAILED_APP_PROCESS_DIED,
    /** The PID of the profiled app process changed (it's another process). */
    STOP_FAILED_APP_PID_CHANGED,
    /** The profiler process (e.g., simpleperf process) died. */
    STOP_FAILED_PROFILER_PROCESS_DIED,
    /** The shell/DDMS command to stop capture didn't return successfully. */
    STOP_FAILED_STOP_COMMAND_FAILED,
    /** The capture didn't stop after the stop command. */
    STOP_FAILED_STILL_PROFILING_AFTER_STOP,
    /** The wait for the trace file to complete couldn't be initiated. */
    STOP_FAILED_CANNOT_START_WAITING,
    /** The wait for the trace file to complete timed out. */
    STOP_FAILED_WAIT_TIMEOUT,
    /** The wait for the trace file to complete had unspecified failure. */
    STOP_FAILED_WAIT_FAILED,
    /** Couldn't read events while waiting for the trace file to complete. */
    STOP_FAILED_CANNOT_READ_WAIT_EVENT,
    /** Couldn't copy/move the trace file within the device. */
    STOP_FAILED_CANNOT_COPY_FILE,
    /** Couldn't form the trace file into the format expected by Studio. */
    STOP_FAILED_CANNOT_FORM_FILE,
    /** Couldn't read the content of the trace file. */
    STOP_FAILED_CANNOT_READ_FILE,
    /** The trace file doesn't exist or is a directory. */
    PARSING_FAILED_PATH_INVALID,
    /** The trace file cannot be read. */
    PARSING_FAILED_READ_ERROR,
    /** Couldn't identify correct parser for the trace file. */
    PARSING_FAILED_PARSER_UNKNOWN,
    /** The trace file failed file header verification. */
    PARSING_FAILED_FILE_HEADER_ERROR,
    /** The trace file cannot be parsed by the identified parser, e.g. ART parser for Java method traces. */
    PARSING_FAILED_PARSER_ERROR,
    /** The trace file failed to be parsed due to unknown reasons. */
    PARSING_FAILED_CAUSE_UNKNOWN
    ;

    public static CaptureStatus fromStopStatus(Trace.TraceStopStatus.Status status) {
      switch (status) {
        case NO_ONGOING_PROFILING:
          return STOP_FAILED_NO_GOING_PROFILING;
        case APP_PROCESS_DIED:
          return STOP_FAILED_APP_PROCESS_DIED;
        case APP_PID_CHANGED:
          return STOP_FAILED_APP_PID_CHANGED;
        case PROFILER_PROCESS_DIED:
          return STOP_FAILED_PROFILER_PROCESS_DIED;
        case STOP_COMMAND_FAILED:
          return STOP_FAILED_STOP_COMMAND_FAILED;
        case STILL_PROFILING_AFTER_STOP:
          return STOP_FAILED_STILL_PROFILING_AFTER_STOP;
        case CANNOT_START_WAITING:
          return STOP_FAILED_CANNOT_START_WAITING;
        case WAIT_TIMEOUT:
          return STOP_FAILED_WAIT_TIMEOUT;
        case WAIT_FAILED:
          return STOP_FAILED_WAIT_FAILED;
        case CANNOT_READ_WAIT_EVENT:
          return STOP_FAILED_CANNOT_READ_WAIT_EVENT;
        case CANNOT_COPY_FILE:
          return STOP_FAILED_CANNOT_COPY_FILE;
        case CANNOT_FORM_FILE:
          return STOP_FAILED_CANNOT_FORM_FILE;
        case CANNOT_READ_FILE:
          return STOP_FAILED_CANNOT_READ_FILE;
        default:
          return SUCCESS;
      }
    }
  }

  /**
   * Whether the capture + parsing finished successfully or if there was an error in the capturing or parsing steps.
   * It's null until set by {@link setStatus(CaptureStatus)}.
   */
  @Nullable
  private CaptureStatus myStatus;

  /**
   * Duration (in milliseconds) of the capture, from the time user pressed "Start recording" to the time they pressed "Stop".
   * If {@link #myStatus} is {@link CaptureStatus#SUCCESS}, the duration is calculated from the capture itself, from the precise start/stop
   * timestamps. Otherwise, the duration is actually an estimate as it's calculated by checking the device time when the user clicks start
   * and when they click stop.
   */
  private long myCaptureDurationMs;

  /**
   * Duration (in milliseconds) from the first trace data timestamp to the last one.
   */
  private long myRecordDurationMs;

  /**
   * Size (in bytes) of the trace file parsed into capture.
   */
  private int myTraceFileSizeBytes;

  /**
   * How much time (in milliseconds) taken to parse the trace file.
   */
  private long myParsingTimeMs;

  /**
   * How much time (in milliseconds) taken to stop the recording.
   */
  private int myStoppingTimeMs;

  /**
   * Whether the trace contains Compose Tracing nodes
   */
  private @Nullable Boolean myHasComposeTracingNodes;

  /**
   * {@link ProfilingConfiguration} used to start the capture.
   */
  private @NotNull ProfilingConfiguration myProfilingConfiguration;

  public CpuCaptureMetadata(@NotNull ProfilingConfiguration configuration) {
    myProfilingConfiguration = configuration;
  }

  @Nullable
  public CaptureStatus getStatus() {
    return myStatus;
  }

  public void setStatus(CaptureStatus status) {
    myStatus = status;
  }

  public int getTraceFileSizeBytes() {
    return myTraceFileSizeBytes;
  }

  public void setTraceFileSizeBytes(int traceFileSizeBytes) {
    myTraceFileSizeBytes = traceFileSizeBytes;
  }

  public long getCaptureDurationMs() {
    return myCaptureDurationMs;
  }

  public void setCaptureDurationMs(long captureDurationMs) {
    myCaptureDurationMs = captureDurationMs;
  }

  public int getStoppingTimeMs() {
    return myStoppingTimeMs;
  }

  public void setStoppingTimeMs(int stoppingTimeMs) {
    myStoppingTimeMs = stoppingTimeMs;
  }

  public @Nullable Boolean getHasComposeTracingNodes() {
    return myHasComposeTracingNodes;
  }

  public void setHasComposeTracingNodes(@Nullable Boolean hasComposeTracingNodes) {
    myHasComposeTracingNodes = hasComposeTracingNodes;
  }

  public long getParsingTimeMs() {
    return myParsingTimeMs;
  }

  public void setParsingTimeMs(long parsingTimeMs) {
    myParsingTimeMs = parsingTimeMs;
  }

  public long getRecordDurationMs() {
    return myRecordDurationMs;
  }

  public void setRecordDurationMs(long recordDurationMs) {
    myRecordDurationMs = recordDurationMs;
  }

  @NotNull
  public ProfilingConfiguration getProfilingConfiguration() {
    return myProfilingConfiguration;
  }
}
