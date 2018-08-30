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
package com.android.tools.perf.idea.gradle.project.sync;

import static org.jetbrains.plugins.gradle.settings.DistributionType.DEFAULT_WRAPPED;

import com.android.testutils.VirtualTimeScheduler;
import com.android.tools.analytics.LoggedUsage;
import com.android.tools.analytics.TestUsageTracker;
import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.gradle.project.sync.GradleSyncIntegrationTestCase;
import com.android.tools.perflogger.Benchmark;
import com.android.tools.perflogger.Metric;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.GradleSyncStats;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.logging.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

public abstract class GradleSyncPerformanceTestCase extends GradleSyncIntegrationTestCase {
  private static final int INITIAL_DROPS = 5;
  private static final int NUM_SAMPLES = 10;
  private static final String BENCHMARK_PROJECT = "Android Studio Sync Test";
  private TestUsageTracker myUsageTracker;
  private VirtualTimeScheduler myScheduler;

  @Override
  public void setUp() throws Exception {
    FSRecords.invalidateCaches();
    super.setUp();
    // Setup up an instance of the JournalingUsageTracker using defined spool directory and
    // virtual time scheduler.
    myScheduler = new VirtualTimeScheduler();
    myUsageTracker = new TestUsageTracker(myScheduler);

    Project project = getProject();
    GradleProjectSettings projectSettings = new GradleProjectSettings();
    projectSettings.setDistributionType(DEFAULT_WRAPPED);
    GradleSettings.getInstance(project).setLinkedProjectsSettings(Collections.singletonList(projectSettings));
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      myScheduler.advanceBy(0);
      myUsageTracker.close();
      UsageTracker.cleanAfterTesting();
    }
    finally {
      super.tearDown();
    }
  }

  /**
   * Measure the following sync times:
   *   - Initial sync time.
   *   - Average over {@link NUM_SAMPLES} samples of subsequent syncs.
   * @throws Exception
   */
  public void testSyncTimes() throws Exception {
    UsageTracker.setWriterForTest(myUsageTracker); // Start logging data for performance dashboard
    loadProject(getRelativePath());
    ArrayList<Long> measurements = new ArrayList<>();
    Logger log = getLogger();

    try {
      Metric metric = new Metric(getMetricName());
      Benchmark initialBenchmark = new Benchmark.Builder("Initial sync time")
        .setProject(BENCHMARK_PROJECT)
        .build();
      Benchmark regularBenchmark = new Benchmark.Builder("Regular sync time")
        .setProject(BENCHMARK_PROJECT)
        .build();

      // Measure initial sync (already synced when loadProject was called)
      GradleSyncStats initialStats = getLastSyncStats();
      printStats("Initial sync", initialStats, log);
      metric.addSamples(initialBenchmark, new Metric.MetricSample(Instant.now().toEpochMilli(), initialStats.getTotalTimeMs()));

      // Drop some runs to stabilize readings
      for (int drop = 0; drop < INITIAL_DROPS; drop++) {
        requestSyncAndWait();
        GradleSyncStats droppedStats = getLastSyncStats();
        printStats("Dropped " + drop, droppedStats, log);
      }

      // perform actual samples
      for (int sample = 0; sample < NUM_SAMPLES; sample++) {
        requestSyncAndWait();
        GradleSyncStats sampleStats = getLastSyncStats();
        printStats("Sample " + sample, sampleStats, log);
        if (sampleStats != null) {
          measurements.add(sampleStats.getTotalTimeMs());
          metric.addSamples(regularBenchmark, new Metric.MetricSample(Instant.now().toEpochMilli(), sampleStats.getTotalTimeMs()));
        }
      }
      metric.commit();
    }
    catch(Exception e) {
      throw new RuntimeException(e);
    }
    finally {
      log.info("Average: " + measurements.stream().mapToLong(Long::longValue).average().orElse(0));
      log.info("min: " + measurements.stream().mapToLong(Long::longValue).min().orElse(0));
      log.info("max: " + measurements.stream().mapToLong(Long::longValue).max().orElse(0));
    }
  }

  private static void printStats(@NotNull String message, @NotNull GradleSyncStats stats, @NotNull Logger log) {
    log.info(message + ":");
    log.info("  Gradle: " + stats.getGradleTimeMs());
    log.info("     IDE: " + stats.getIdeTimeMs());
    log.info("   Total: " + stats.getTotalTimeMs());
  }

  @Nullable
  private GradleSyncStats getLastSyncStats() {
    ArrayList<LoggedUsage> usages = myUsageTracker.getUsages();
    for (int index = usages.size() - 1; index >= 0; index--) {
      LoggedUsage usage = usages.get(index);
      if (usage.getStudioEvent().getKind() == AndroidStudioEvent.EventKind.GRADLE_SYNC_ENDED) {
        return usage.getStudioEvent().getGradleSyncStats();
      }
    }
    return null;
  }

  @NotNull
  private Logger getLogger() {
    return Logger.getLogger(this.getClass().getName());
  }

  @NotNull
  public abstract String getRelativePath();

  @NotNull
  public abstract String getMetricName();
}
