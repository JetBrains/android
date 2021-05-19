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
    "Try recording again, or ",
    true);

  @NotNull
  static final Notification CAPTURE_STOP_FAILURE = createError(
    "Recording failed to stop",
    "Try recording another method trace, or ",
    true);

  @NotNull
  static final Notification PARSING_FAILURE = createError(
    "Trace data was not recorded",
    "The profiler was unable to parse the method trace data. " +
    "Try recording another method trace, or ",
    true);

  @NotNull
  static final Notification PREPROCESS_FAILURE = createError(
    "Trace data was not recorded",
    "The profiler was unable to pre-process the method trace data. " +
    "Try recording another method trace, or ",
    true);

  @NotNull
  static final Notification PARSING_ABORTED = createError(
    "Parsing trace file aborted",
    "The CPU profiler was closed before the recorded trace file could be " +
    "parsed. Please record another trace.",
    false);

  @NotNull
  static final Notification IMPORT_TRACE_PARSING_FAILURE = createError(
    "Trace file was not parsed",
    "The profiler was unable to parse the trace file. Please make sure the file " +
    "selected is a valid trace. Alternatively, try importing another file, or ",
    true);

  @NotNull
  static final Notification IMPORT_TRACE_PARSING_ABORTED = createError(
    "Parsing trace file aborted",
    "The profiler changed to a different session before the imported " +
    "trace file could be parsed. Please try importing your trace " +
    "file again.",
    false);

  @NotNull
  static final Notification ATRACE_BUFFER_OVERFLOW = createWarning(
    "System Trace Buffer Overflow Detected",
    "Your capture exceeded the buffer limit, some data may be missing. " +
    "Consider recording a shorter trace.",
    false);

  @NotNull
  private static Notification createNotification(@NotNull Notification.Severity severity,
                                                @NotNull String title,
                                                @NotNull String text,
                                                boolean reportBug) {
    Notification.Builder builder = new Notification.Builder(title, text).setSeverity(severity);
    if (reportBug) {
      builder.setUrl("https://issuetracker.google.com/issues/new?component=192708", "report a bug");
    }
    return builder.build();
  }

  @NotNull
  private static Notification createWarning(@NotNull String title, @NotNull String text, boolean reportBug) {
    return createNotification(Notification.Severity.WARNING, title, text, reportBug);
  }

  @NotNull
  private static Notification createError(@NotNull String title, @NotNull String text, boolean reportBug) {
    return createNotification(Notification.Severity.ERROR, title, text, reportBug);
  }
}
