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
package com.android.tools.idea.diagnostics;

import static java.nio.file.Files.newDirectoryStream;

import com.android.annotations.NonNull;
import com.android.tools.idea.diagnostics.report.DiagnosticReport;
import com.intellij.diagnostic.IdePerformanceListener;
import com.intellij.diagnostic.ThreadDump;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.messages.MessageBusConnection;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class DiagnosticReportIdePerformanceListener implements IdePerformanceListener {
  private static final Logger LOG = Logger.getInstance(DiagnosticReportIdePerformanceListener.class);

  private final Consumer<DiagnosticReport> myReportCallback;
  private @Nullable DiagnosticReportBuilder myBuilder;
  private @Nullable ReportContext myContext;
  private int myReportsCollected;
  private @Nullable MessageBusConnection myMessageBusConnection;

  private static class ReportContext {
    private Path myThreadDumpPath;
  }

  public DiagnosticReportIdePerformanceListener(Consumer<DiagnosticReport> reportCallback) {
    myReportCallback = reportCallback;
  }

  @Override
  public void dumpedThreads(@NotNull Path toFile, @NotNull ThreadDump dump) {
    ReportContext currentContext = myContext;
    if (currentContext != null && currentContext.myThreadDumpPath == null) {
      currentContext.myThreadDumpPath = toFile.getParent();
    }
  }

  @Override
  public void uiFreezeStarted(@NotNull Path reportDir) {
    LOG.info("uiFreezeStarted");
    if (myBuilder != null) {
      return;
    }
    final ReportContext context = new ReportContext();
    myContext = context;
    // TODO: probably could initialize myThreadDumpPath from the reportDir here

    // Unfortunately, current API does not give us exact value how long the UI was frozen before uiFreezeStarted()
    // event is triggered. This is the best approximation of that value.
    final int freezeTimeBeforeCreatedMs = Registry.intValue("performance.watcher.unresponsive.interval.ms");
    myBuilder = new DiagnosticReportBuilder(
      DiagnosticReportBuilder.INTERVAL_MS,
      DiagnosticReportBuilder.MAX_DURATION_MS,
      DiagnosticReportBuilder.FRAME_IGNORE_THRESHOLD_MS,
      freezeTimeBeforeCreatedMs,
      new Controller(context)
    );
  }

  private void reportReady(DiagnosticReport report) {
    myReportCallback.accept(report);
  }

  @Override
  public void uiFreezeFinished(long durationMs, @Nullable Path reportDir) {
    int lengthInSeconds = (int)(durationMs / 1000);
    LOG.info(String.format(Locale.US, "uiFreezeFinished: duration = %d seconds", lengthInSeconds));
    DiagnosticReportBuilder localBuilder = myBuilder;
    if (localBuilder == null) {
      return;
    }
    try {
      myReportsCollected++;
      if (DiagnosticReportBuilder.MAX_REPORTS != -1 && myReportsCollected >= DiagnosticReportBuilder.MAX_REPORTS) {
        LOG.info("Stopped collecting UI freeze reports after " + myReportsCollected + " reports.");
        unregister();
      }
      if (myContext != null) {
        if (myContext.myThreadDumpPath == null) {
          Path directoryForFreeze = tryCreateDirectoryForFreeze(lengthInSeconds);
          if (directoryForFreeze == null) {
            return;
          }
          myContext.myThreadDumpPath = directoryForFreeze;
        }
        Path localReportPath = getPathForReportName("profileDiagnostics", myContext);
        // If the report has already been generated (freeze took too long), append a line with a real
        // freeze duration.
        if (localReportPath != null) {
          if (Files.exists(localReportPath)) {
            try {
              Files.write(localReportPath, ("UI freeze lasted " + lengthInSeconds + " seconds.\n").getBytes(), StandardOpenOption.APPEND);
            }
            catch (IOException e) {
              // Non fatal exception
              LOG.warn("Exception while appending to a report.", e);
            }
          }
        }
      }
    } finally {
      myBuilder = null;
      myContext = null;
      localBuilder.stop();
    }
  }

  private static Path tryCreateDirectoryForFreeze(long freezeInSeconds) {
    String dirName = "uiFreeze-" + formatTime(System.currentTimeMillis()) + "-" + freezeInSeconds + "sec";
    Path freezeDir = Paths.get(PathManager.getLogPath(), dirName);
    try {
      if (Files.exists(freezeDir)) {
        return null;
      }
      Files.createDirectories(freezeDir);
      return freezeDir;
    }
    catch (IOException e) {
      return null;
    }
  }

  private static String formatTime(long timeMs) {
      return new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date(timeMs));
  }

  @NonNull
  private static Path tryFixReportPath(@NotNull Path path) {
    Path reportDirectory = path.getParent();
    if (Files.isDirectory(reportDirectory)) {
      return path;
    }
    final String directoryGlob = reportDirectory.getFileName().toString() + "-*";
    try (final DirectoryStream<Path> paths = newDirectoryStream(reportDirectory.getParent(), directoryGlob)) {
      final Iterator<Path> iterator = paths.iterator();
      if (iterator.hasNext()) {
        reportDirectory = iterator.next();
      }
    }
    catch (IOException e) {
      LOG.warn(e);
    }
    if (reportDirectory == null) {
      // No directory to store the report, return original value
      return path;
    }
    return reportDirectory.resolve(path.getFileName());
  }

  @Nullable
  private static Path getPathForReportName(@NotNull String reportName,
                            @NotNull ReportContext context) {
    Path threadDumpPath = context.myThreadDumpPath;
    if (threadDumpPath == null) {
      return null;
    }

    Path reportPath = threadDumpPath.resolve("diagnosticReport-" + reportName + ".txt");
    return tryFixReportPath(reportPath);
  }

  /**
   * Save report to a file.
   *
   * @return Path to a report file or {@code null} if report could not be saved.
   */
  @Nullable
  private static Path saveReportFile(@NotNull String reportName,
                      @NotNull String reportContents,
                      ReportContext context) {
    Path reportPath = getPathForReportName(reportName, context);
    if (reportPath == null) {
      return null;
    }

    if (Files.exists(reportPath)) {
      return reportPath;
    }
    try (PrintWriter out = new PrintWriter(reportPath.toFile(), "UTF-8")) {
      out.write(reportContents);
      LOG.info(String.format("Freeze report saved: %s", reportPath));
    }
    catch (IOException e) {
      LOG.warn(e);
    }
    return reportPath;
  }

  public void registerOn(Application application) {
    assert myMessageBusConnection == null;

    myMessageBusConnection = application.getMessageBus().connect();
    myMessageBusConnection.subscribe(IdePerformanceListener.TOPIC, this);
  }

  public void unregister() {
    assert myMessageBusConnection != null;

    myMessageBusConnection.disconnect();
    myMessageBusConnection = null;
  }

  public class Controller {
    private final ReportContext myContext;

    public Controller(ReportContext context) {
      myContext = context;
    }

    public Path saveReportFile(String reportName, String reportContents) {
      return DiagnosticReportIdePerformanceListener.saveReportFile(reportName, reportContents, myContext);
    }

    public void reportReady(DiagnosticReport report) {
      DiagnosticReportIdePerformanceListener.this.reportReady(report);
    }

  }
}
