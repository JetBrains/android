/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static com.android.tools.profilers.Notification.createError;
import static com.android.tools.profilers.Notification.createWarning;

import com.android.tools.profiler.proto.Trace;
import com.android.tools.profilers.Notification;
import org.jetbrains.annotations.NotNull;

/**
 * CPU related notification constants.
 */
public final class CpuProfilerNotifications {
  @NotNull
  public static final Notification CAPTURE_START_FAILURE = createError(
    "Recording failed to start",
    "Try recording again, or "
  );

  @NotNull
  public static final Notification CAPTURE_START_FAILURE_TRACER_ALREADY_RUNNING = createError(
    "Recording failed to start",
    "Another profiler or tracer instance may be running. Stop the other instance and try again, or "
  );

  @NotNull
  static Notification getCaptureStartFailure(long errorCode) {
    if (errorCode == Trace.TraceStartStatus.ErrorCode.TRACER_ALREADY_RUNNING_UNABLE_RUN_PERFETTO_VALUE) {
      return CAPTURE_START_FAILURE_TRACER_ALREADY_RUNNING;
    }

    // Default error notification for capture start failures.
    return CAPTURE_START_FAILURE;
  }

  @NotNull
  static Notification getCaptureStopFailure(String errorMessage) {
    return createError("Recording failed to stop (" + errorMessage + ")",
                       "Try recording another trace, or ");
  }

  @NotNull
  public static final Notification PARSING_FAILURE = createError(
    "Trace data was not recorded",
    "The profiler was unable to parse the method trace data. " +
    "Try recording another method trace, or "
  );

  @NotNull
  public static final Notification PREPROCESS_FAILURE = createError(
    "Trace data was not recorded",
    "The profiler was unable to pre-process the method trace data. " +
    "Try recording another method trace, or "
  );

  @NotNull
  public static final Notification PARSING_ABORTED = createWarning(
    "Parsing trace file aborted",
    "Please record another trace."
  );

  @NotNull
  static final Notification IMPORT_TRACE_PARSING_FAILURE = createError(
    "Trace file was not parsed",
    "The profiler was unable to parse the trace file. Please make sure the file " +
    "selected is a valid trace. Alternatively, try importing another file, or "
  );
}
