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
package com.android.tools.idea.diagnostics;

import com.android.annotations.concurrency.GuardedBy;
import com.android.tools.idea.diagnostics.report.DiagnosticReport;
import com.android.tools.idea.diagnostics.report.FreezeReport;
import com.intellij.concurrency.JobScheduler;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DiagnosticReportBuilder {
  private static final Logger LOG = Logger.getInstance("#com.android.tools.idea.diagnostics.DiagnosticReportBuilder");

  public final static long MAX_DURATION_MS =
    Long.getLong("studio.diagnostic.uiFreezeSampling.maxDurationMs", TimeUnit.SECONDS.toMillis(60));
  public final static long INTERVAL_MS =
    Long.getLong("studio.diagnostic.uiFreezeSampling.intervalMs", 100);
  public final static long FRAME_IGNORE_THRESHOLD_MS =
    Long.getLong("studio.diagnostic.uiFreezeSampling.frameIgnoreThresholdMs", 200);
  public static final int MAX_REPORTS =
    Integer.getInteger("studio.diagnostic.uiFreezeSampling.maxReports",
                       ApplicationManager.getApplication().isEAP() ? 10 : 3);

  private final @NotNull Object LOCK = new Object();
  private final long myStartTime;
  private final long myFreezeTimeBeforeCreated;
  private final @NotNull DiagnosticReportIdePerformanceListener.Controller myController;
  private final @NotNull ScheduledFuture<?> myFutureStop;
  @GuardedBy("LOCK")
  private boolean myIsStopped;
  private @NotNull List<DiagnosticReportContributor> myReportContributors;
  private final @NotNull Map<String, Path> myBinaryReportPaths;
  @GuardedBy("LOCK")
  private boolean myIsTimedOut;

  public DiagnosticReportBuilder(long intervalMs,
                                 long maxSamplingTimeMs,
                                 long frameTimeIgnoreThresholdMs,
                                 long freezeTimeBeforeCreatedMs,
                                 @NotNull DiagnosticReportIdePerformanceListener.Controller controller) {
    if (intervalMs <= 0) {
      throw new IllegalArgumentException("intervalMs must be > 0");
    }
    if (maxSamplingTimeMs < 0) {
      throw new IllegalArgumentException("maxSamplingTimeMs must be >= 0");
    }
    myController = controller;
    myReportContributors = Arrays.asList(
      new ThreadSamplingReportContributor(),
      new MemoryUseReportContributor(),
      new ActionsReportContributor()
    );
    myBinaryReportPaths = new TreeMap<>();

    myFreezeTimeBeforeCreated = freezeTimeBeforeCreatedMs;
    DiagnosticReportConfiguration configuration =
      new DiagnosticReportConfiguration(intervalMs, maxSamplingTimeMs, frameTimeIgnoreThresholdMs);

    for (DiagnosticReportContributor contributor : myReportContributors) {
      try {
        contributor.setup(configuration);
      }
      catch (Throwable t) {
        LOG.error(t);
      }
    }

    myStartTime = System.currentTimeMillis();
    for (DiagnosticReportContributor contributor : myReportContributors) {
      try {
        contributor.startCollection(freezeTimeBeforeCreatedMs);
      }
      catch (Throwable t) {
        LOG.error(t);
      }
    }

    myFutureStop = JobScheduler.getScheduler().schedule(this::stopAfterTimeout, maxSamplingTimeMs, TimeUnit.MILLISECONDS);
  }

  public void addBinaryReportPath(String reportName, Path reportPath) {
    myBinaryReportPaths.put(reportName, reportPath);
  }

  private void stopAfterTimeout() {
    synchronized (LOCK) {
      if (myIsStopped) {
        return;
      }
      myIsTimedOut = true;
      stop();
    }
  }

  @Nullable
  private DiagnosticReport generateReport(long totalDurationMs) {
    synchronized (LOCK) {
      Map<String, Path> reportPaths = new HashMap<>();
      for (DiagnosticReportContributor contributor : myReportContributors) {
        contributor.generateReport((name, contents) -> {
          Path path = myController.saveReportFile(name, contents);
          // Contributors should not overwrite each other reports.
          if (path != null) {
            assert !reportPaths.containsKey(name);
            reportPaths.put(name, path);
          }
        });
      }
      if (!reportPaths.containsKey("hotPathStackTrace")) {
        return null;
      }
      Path hotPathStackTrace = reportPaths.remove("hotPathStackTrace");
      return new FreezeReport(hotPathStackTrace, reportPaths, myBinaryReportPaths, myIsTimedOut, totalDurationMs, null);
    }
  }

  public void stop() {
    synchronized (LOCK) {
      if (myIsStopped) {
        return;
      }
      long stopTime = System.currentTimeMillis();
      long totalDurationMs = stopTime - myStartTime + myFreezeTimeBeforeCreated;
      for (DiagnosticReportContributor contributor : myReportContributors) {
        try {
          contributor.stopCollection(totalDurationMs);
        }
        catch (Throwable t) {
          LOG.error(t);
        }
      }
      myIsStopped = true;
      myFutureStop.cancel(false);

      DiagnosticReport report = generateReport(totalDurationMs);
      myController.reportReady(report);
    }
  }

  public static void registerPerformanceListener(Consumer<DiagnosticReport> reportCallback) {
    new DiagnosticReportIdePerformanceListener(reportCallback).registerOn(ApplicationManager.getApplication());
  }
}
