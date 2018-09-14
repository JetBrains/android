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

import com.android.annotations.VisibleForTesting;
import com.android.tools.profilers.IdeProfilerServices;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Shows balloon notifications related to CPU Profiler.
 */
class CpuProfilerNotification {
  @VisibleForTesting
  enum Notification {
    CAPTURE_START_FAILURE("Recording failed to start", "Try recording again, or ", true),

    CAPTURE_STOP_FAILURE("Recording failed to stop", "Try recording another method trace, or ", true),

    PARSING_FAILURE("Trace data was not recorded",
                    "The profiler was unable to parse the method trace data. " + "Try recording another method trace, or ",
                    true),

    PARSING_ABORTED("Parsing trace file aborted",
                    "The CPU profiler was closed before the recorded trace file could be " +
                    "parsed. Please record another trace."),

    IMPORT_TRACE_PARSING_FAILURE("Trace file was not parsed",
                                 "The profiler was unable to parse the trace file. Please make sure the file " +
                                 "selected is a valid trace. Alternatively, try importing another file, or ",
                                 true),

    IMPORT_TRACE_PARSING_ABORTED("Parsing trace file aborted",
                                 "The profiler changed to a different session before the imported " +
                                 "trace file could be parsed. Please try importing your trace " +
                                 "file again."),

    ATRACE_BUFFER_OVERFLOW("System Trace Buffer Overflow Detected",
                           "Your capture exceeded the buffer limit, some data may be missing. " +
                           "Consider recording a shorter trace.");

    @NotNull private final String myTitle;
    @NotNull private final String myText;
    private final boolean myReportBug;

    Notification(@NotNull String title, @NotNull String text, boolean reportBug) {
      myText = text;
      myTitle = title;
      myReportBug  = reportBug;
    }

    Notification(@NotNull String title, @NotNull String text) {
      this(title, text, false);
    }

    @NotNull
    String getTitle() {
      return myTitle;
    }

    @NotNull
    String getText() {
      return myText;
    }

    @Nullable
    String getUrl() {
      return myReportBug ? "https://issuetracker.google.com/issues/new?component=192754" : null;
    }

    @Nullable
    String getUrlText() {
      return myReportBug ? "report a bug" : null;
    }
  }

  @NotNull private final IdeProfilerServices myIdeServices;

  CpuProfilerNotification(@NotNull IdeProfilerServices ideServices) {
    myIdeServices = ideServices;
  }

  void showCaptureStartFailure() {
    showErrorNotification(Notification.CAPTURE_START_FAILURE);
  }

  void showCaptureStopFailure() {
    showErrorNotification(Notification.CAPTURE_STOP_FAILURE);
  }

  void showParsingAborted() {
    showErrorNotification(Notification.PARSING_ABORTED);
  }

  void showParsingFailure() {
    showErrorNotification(Notification.PARSING_FAILURE);
  }

  void showImportTraceParsingAborted() {
    showErrorNotification(Notification.IMPORT_TRACE_PARSING_ABORTED);
  }

  void showImportTraceParsingFailure() {
    showErrorNotification(Notification.IMPORT_TRACE_PARSING_FAILURE);
  }

  void showATraceBufferOverflow() {
    showWarningNotification(Notification.ATRACE_BUFFER_OVERFLOW);
  }

  private void showWarningNotification(@NotNull Notification notification) {
    myIdeServices.showWarningBalloon(notification.getTitle(), notification.getText(), notification.getUrl(), notification.getUrlText());
  }

  private void showErrorNotification(@NotNull Notification notification) {
    myIdeServices.showErrorBalloon(notification.getTitle(), notification.getText(), notification.getUrl(), notification.getUrlText());
  }
}
