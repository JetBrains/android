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

import com.android.tools.profilers.Notification;
import org.jetbrains.annotations.NotNull;

/**
 * CPU related notification constants.
 */
final class CpuProfilerNotifications {
  @NotNull
  static final Notification CAPTURE_START_FAILURE = createError(
    "Recording failed to start",
    "Try recording again, or "
  );

  @NotNull
  static Notification getCaptureStopFailure(String errorMessage) {
    return createError("Recording failed to stop (" + errorMessage + ")",
                       "Try recording another trace, or ");
  }

  @NotNull
  static final Notification PARSING_FAILURE = createError(
    "Trace data was not recorded",
    "The profiler was unable to parse the method trace data. " +
    "Try recording another method trace, or "
  );

  @NotNull
  static final Notification PREPROCESS_FAILURE = createError(
    "Trace data was not recorded",
    "The profiler was unable to pre-process the method trace data. " +
    "Try recording another method trace, or "
  );

  @NotNull
  static final Notification PARSING_ABORTED = createWarning(
    "Parsing trace file aborted",
    "Please record another trace."
  );

  @NotNull
  static final Notification IMPORT_TRACE_PARSING_FAILURE = createError(
    "Trace file was not parsed",
    "The profiler was unable to parse the trace file. Please make sure the file " +
    "selected is a valid trace. Alternatively, try importing another file, or "
  );

  @NotNull
  static final Notification ATRACE_BUFFER_OVERFLOW = createWarning(
    "System Trace Buffer Overflow Detected",
    "Your capture exceeded the buffer limit, some data may be missing. " +
    "Consider recording a shorter trace."
  );

  @NotNull
  private static Notification createNotification(@NotNull Notification.Severity severity,
                                                @NotNull String title,
                                                @NotNull String text,
                                                boolean reportBug) {
    if (reportBug) {
      var url = new Notification.UrlData("https://issuetracker.google.com/issues/new?component=192708", "report a bug");
      return new Notification(severity, title, text, url);
    } else {
      return new Notification(severity, title, text, null);
    }
  }

  @NotNull
  private static Notification createWarning(@NotNull String title, @NotNull String text) {
    return createNotification(Notification.Severity.WARNING, title, text, false);
  }

  @NotNull
  private static Notification createError(@NotNull String title, @NotNull String text) {
    return createNotification(Notification.Severity.ERROR, title, text, true);
  }
}
