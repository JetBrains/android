/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.perf;

import static com.android.tools.idea.gradle.project.sync.perf.TestProjectPaths.SIMPLE_APPLICATION;
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
import java.util.List;
import java.util.logging.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.junit.FixMethodOrder;
import org.junit.runners.MethodSorters;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public abstract class GradleSyncPerfTestCase extends GradleSyncIntegrationTestCase {
  private static final int DEFAULT_INITIAL_DROPS = 5;
  private static final int DEFAULT_NUM_SAMPLES = 10;
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
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  @Override
  public void setUpFixture() throws Exception {
    super.setUpFixture();
    myFixture.setTestDataPath(getModulePath("sync-perf-tests") + "/testData");
  }

  /**
   * This test is run first in order to have gradle daemon already running before actual metrics are done.
   * @throws Exception
   */
  public void testInitialization() throws Exception {
    UsageTracker.setWriterForTest(myUsageTracker); // Start logging data for performance dashboard
    loadProject(SIMPLE_APPLICATION, null, getGradleVersion(), getAGPVersion());
    Logger log = getLogger();

    try {
      // Measure initial sync (already synced when loadProject was called)
      GradleSyncStats initialStats = getLastSyncStats();
      printStats("initial (initialization)", initialStats, log);

      // Drop some runs to stabilize readings
      for (int drop = 1; drop <= DEFAULT_INITIAL_DROPS; drop++) {
        requestSyncAndWait();
        GradleSyncStats droppedStats = getLastSyncStats();
        printStats("dropped (initialization) " + drop, droppedStats, log);
      }
    }
    catch(Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Measure the following sync times:
   *   - Initial sync time.
   *   - Average over {@link GradleSyncPerfTestCase#getNumSamples()} samples of subsequent syncs.
   * @throws Exception
   */
  public void testSyncTimes() throws Exception {
    UsageTracker.setWriterForTest(myUsageTracker); // Start logging data for performance dashboard
    loadProject(getRelativePath(), null, getGradleVersion(), getAGPVersion());
    ArrayList<Long> measurements = new ArrayList<>();
    Logger log = getLogger();

    try {
      String scenarioName = getScenarioName();
      Benchmark initialBenchmark = new Benchmark.Builder("Initial sync time")
        .setProject(BENCHMARK_PROJECT)
        .build();
      Benchmark regularBenchmark = new Benchmark.Builder("Regular sync time")
        .setProject(BENCHMARK_PROJECT)
        .build();
      Benchmark scenarioBenchmark = new Benchmark.Builder(scenarioName)
        .setProject(BENCHMARK_PROJECT)
        .build();

      Metric metricScenario = new Metric(scenarioName);
      Metric metricInitialTotal = new Metric("Initial_Total");
      Metric metricInitialIDE = new Metric("Initial_IDE");
      Metric metricInitialGradle = new Metric("Initial_Gradle");
      Metric metricRegularTotal = new Metric("Regular_Total");
      Metric metricRegularIDE = new Metric("Regular_IDE");
      Metric metricRegularGradle = new Metric("Regular_Gradle");

      // Measure initial sync (already synced when loadProject was called)
      GradleSyncStats initialStats = getLastSyncStats();
      printStats("initial sync", initialStats, log);
      long currentTime = Instant.now().toEpochMilli();
      metricScenario.addSamples(initialBenchmark, new Metric.MetricSample(currentTime, initialStats.getTotalTimeMs()));
      metricInitialGradle.addSamples(scenarioBenchmark, new Metric.MetricSample(currentTime, initialStats.getGradleTimeMs()));
      metricInitialIDE.addSamples(scenarioBenchmark, new Metric.MetricSample(currentTime, initialStats.getIdeTimeMs()));
      metricInitialTotal.addSamples(scenarioBenchmark, new Metric.MetricSample(currentTime, initialStats.getTotalTimeMs()));

      // Drop some runs to stabilize readings
      for (int drop = 1; drop <= getNumDrops(); drop++) {
        requestSyncAndWait();
        GradleSyncStats droppedStats = getLastSyncStats();
        printStats("dropped " + drop, droppedStats, log);
      }

      // perform actual samples
      for (int sample = 1; sample <= getNumSamples(); sample++) {
        requestSyncAndWait();
        GradleSyncStats sampleStats = getLastSyncStats();
        printStats("sample " + sample, sampleStats, log);
        if (sampleStats != null) {
          measurements.add(sampleStats.getTotalTimeMs());
          currentTime = Instant.now().toEpochMilli();
          metricScenario.addSamples(regularBenchmark, new Metric.MetricSample(currentTime, sampleStats.getTotalTimeMs()));
          metricRegularGradle.addSamples(scenarioBenchmark, new Metric.MetricSample(currentTime, sampleStats.getGradleTimeMs()));
          metricRegularIDE.addSamples(scenarioBenchmark, new Metric.MetricSample(currentTime, sampleStats.getIdeTimeMs()));
          metricRegularTotal.addSamples(scenarioBenchmark, new Metric.MetricSample(currentTime, sampleStats.getTotalTimeMs()));
        }
      }
      metricScenario.commit();
      metricInitialGradle.commit(scenarioName);
      metricInitialIDE.commit(scenarioName);
      metricInitialTotal.commit(scenarioName);
      metricRegularGradle.commit(scenarioName);
      metricRegularIDE.commit(scenarioName);
      metricRegularTotal.commit(scenarioName);
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

  private void printStats(@NotNull String message, @NotNull GradleSyncStats stats, @NotNull Logger log) {
    log.info(getScenarioName() + " " + message + ":");
    log.info("  Gradle: " + stats.getGradleTimeMs());
    log.info("     IDE: " + stats.getIdeTimeMs());
    log.info("   Total: " + stats.getTotalTimeMs());
  }

  @Nullable
  private GradleSyncStats getLastSyncStats() {
    List<LoggedUsage> usages = myUsageTracker.getUsages();
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
  public abstract String getProjectName();

  @NotNull
  public String getScenarioName() {
    StringBuilder scenarioName = new StringBuilder(getProjectName());
    scenarioName.append(useSingleVariantSyncInfrastructure() ? "_SVS" : "_FULL");
    String myAGPVersion = getAGPVersion();
    if (myAGPVersion != null) {
      scenarioName.append("_AGP").append(myAGPVersion);
    }
    String myGradleVersion = getGradleVersion();
    if (myGradleVersion != null) {
      scenarioName.append("_Gradle").append(myGradleVersion);
    }
    return scenarioName.toString();
  }

  public int getNumSamples() {
    return DEFAULT_NUM_SAMPLES;
  }

  public int getNumDrops() {
    return DEFAULT_INITIAL_DROPS;
  }

  public String getAGPVersion() {
    return null;
  }

  public String getGradleVersion() {
    return null;
  }
}
