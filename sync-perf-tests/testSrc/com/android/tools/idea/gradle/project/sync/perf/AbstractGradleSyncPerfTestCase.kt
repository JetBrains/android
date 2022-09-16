/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.perf

import com.android.testutils.VirtualTimeScheduler
import com.android.tools.analytics.LoggedUsage
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.analytics.UsageTracker.cleanAfterTesting
import com.android.tools.analytics.UsageTracker.setWriterForTest
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.perflogger.Benchmark
import com.android.tools.perflogger.Metric
import com.android.tools.perflogger.Metric.MetricSample
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.GradleSyncStats
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.jetbrains.android.AndroidTestBase.getModulePath
import org.jetbrains.plugins.gradle.internal.daemon.GradleDaemonServices
import org.jetbrains.plugins.gradle.settings.DistributionType
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.junit.After
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runners.MethodSorters
import java.io.File
import java.time.Duration
import java.time.Instant
import java.util.ArrayList
import java.util.Scanner
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Logger

/**
 * Abstract class that contains the logic for running sync perf tests over a project. It has two methods:
 *   - testInitialization: Loads and sync SIMPLE_APPLICATION initialDrops times so Gradle daemon initialization time is not considered
 *   - testSyncTimes: Loads the project pointed by relativePath and syncs it initialDrops + numSamples, recording only the last numSamples
 *                    times.
 *
 *   This is a parameterized test class, running each test using tip of tree AGP and Gradle or Gradle 5.5 and AGP 3.5.0.
 */
@RunsInEdt
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
abstract class AbstractGradleSyncPerfTestCase {
  protected val projectRule = AndroidGradleProjectRule()
  @get:Rule
  val ruleChain = org.junit.rules.RuleChain.outerRule(projectRule).around(EdtRule())!!

  companion object {
    const val MEMORY_MEASUREMENT_INTERVAL_MILLIS: Long = 1000
    const val HISTOGRAM_SAMPLES = 80
    const val HISTOGRAM_LEVELS = 20
    const val BENCHMARK_PROJECT = "Android Studio Sync Test"
  }

  private var myUsageTracker: TestUsageTracker? = null
  private var myScheduler: VirtualTimeScheduler? = null

  abstract val relativePath: String
  abstract val projectName: String
  open val initialDrops: Int = 5
  open val numSamples: Int = 10
  open val useModelV2: Boolean = false

  @Before
  @Throws(Exception::class)
  open fun setUp() {
    FSRecords.invalidateCaches()
    myScheduler = VirtualTimeScheduler()
    myUsageTracker = TestUsageTracker(myScheduler!!)
    val projectSettings = GradleProjectSettings()
    projectSettings.distributionType = DistributionType.DEFAULT_WRAPPED
    GradleSettings.getInstance(projectRule.project).linkedProjectsSettings = listOf(projectSettings)

    projectRule.fixture.testDataPath = getModulePath ("sync-perf-tests") + File.separator + "testData"
    disableExpensivePlatformAssertions(projectRule.fixture)
    StudioFlags.GRADLE_SYNC_USE_V2_MODEL.override(useModelV2)
  }

  @After
  open fun tearDown() {
    StudioFlags.GRADLE_SYNC_USE_V2_MODEL.clearOverride()
    try {
      myScheduler!!.advanceBy(0)
      myUsageTracker!!.close()
      cleanAfterTesting()
    }
    catch (_: Throwable) {
    }
  }

  /**
   * This test is run first in order to have gradle daemon already running before actual metrics are done.
   * @throws Exception
   */
  @Throws(java.lang.Exception::class)
  @Test
  open fun testInitialization() {
    setWriterForTest(myUsageTracker!!) // Start logging data for performance dashboard
    loadProject(TestProjectPaths.SIMPLE_APPLICATION)
    val log: Logger = getLogger()
    try { // Measure initial sync (already synced when loadProject was called)
      val initialStats: GradleSyncStats? = getLastSyncStats()
      printStats("initial (initialization)", initialStats, log)
      // Drop some runs to stabilize readings
      for (drop in 1..initialDrops) {
        projectRule.requestSyncAndWait()
        val droppedStats: GradleSyncStats? = getLastSyncStats()
        printStats("dropped (initialization) $drop", droppedStats, log)
      }
    }
    catch (e: java.lang.Exception) {
      throw RuntimeException(e)
    }
  }

  /**
   * Measure the following sync times:
   * - Initial sync time.
   * - Average over [AbstractGradleSyncPerfTestCase.numSamples] samples of subsequent syncs.
   * @throws Exception
   */
  @Throws(java.lang.Exception::class)
  @Test
  open fun testSyncTimes() {
    setWriterForTest(myUsageTracker!!) // Start logging data for performance dashboard
    val scenarioName = getScenarioName()
    val memoryThread = MemoryMeasurementThread(scenarioName)
    memoryThread.start()
    loadProject(relativePath)
    val measurements = ArrayList<Long>()
    val log = getLogger()
    try {
      val initialBenchmark = Benchmark.Builder("Initial sync time")
        .setProject(BENCHMARK_PROJECT)
        .build()
      val regularBenchmark = Benchmark.Builder("Regular sync time")
        .setProject(BENCHMARK_PROJECT)
        .build()
      val scenarioBenchmark = Benchmark.Builder(scenarioName)
        .setProject(BENCHMARK_PROJECT)
        .build()

      val metricScenario = Metric(scenarioName)
      val metricInitialTotal = Metric("Initial_Total")
      val metricInitialIDE = Metric("Initial_IDE")
      val metricInitialGradle = Metric("Initial_Gradle")
      val metricRegularTotal = Metric("Regular_Total")
      val metricRegularIDE = Metric("Regular_IDE")
      val metricRegularGradle = Metric("Regular_Gradle")
      // Measure initial sync (already synced when loadProject was called)
      val initialStats = getLastSyncStats()
      printStats("initial sync", initialStats, log)
      var currentTime = Instant.now().toEpochMilli()
      metricScenario.addSamples(initialBenchmark, MetricSample(currentTime, initialStats!!.totalTimeMs))
      metricInitialGradle.addSamples(scenarioBenchmark, MetricSample(currentTime, initialStats.gradleTimeMs))
      metricInitialIDE.addSamples(scenarioBenchmark, MetricSample(currentTime, initialStats.ideTimeMs))
      metricInitialTotal.addSamples(scenarioBenchmark, MetricSample(currentTime, initialStats.totalTimeMs))
      // Drop some runs to stabilize readings
      for (drop in 1..initialDrops) {
        projectRule.requestSyncAndWait()
        val droppedStats = getLastSyncStats()
        printStats("dropped $drop", droppedStats, log)
      }
      // perform actual samples
      for (sample in 1..numSamples) {
        projectRule.requestSyncAndWait()
        val sampleStats = getLastSyncStats()
        printStats("sample $sample", sampleStats, log)
        if (sampleStats != null) {
          measurements.add(sampleStats.totalTimeMs)
          currentTime = Instant.now().toEpochMilli()
          metricScenario.addSamples(regularBenchmark, MetricSample(currentTime, sampleStats.totalTimeMs))
          metricRegularGradle.addSamples(scenarioBenchmark, MetricSample(currentTime, sampleStats.gradleTimeMs))
          metricRegularIDE.addSamples(scenarioBenchmark, MetricSample(currentTime, sampleStats.ideTimeMs))
          metricRegularTotal.addSamples(scenarioBenchmark, MetricSample(currentTime, sampleStats.totalTimeMs))
        }
      }
      memoryThread.stopReadings()
      metricScenario.commit("Time")
      metricInitialGradle.commit(scenarioName)
      metricInitialIDE.commit(scenarioName)
      metricInitialTotal.commit(scenarioName)
      metricRegularGradle.commit(scenarioName)
      metricRegularIDE.commit(scenarioName)
      metricRegularTotal.commit(scenarioName)
    }
    catch (e: java.lang.Exception) {
      throw RuntimeException(e)
    }
    finally {
      memoryThread.stopReadings()
      logSummary("Time", measurements, log)
      memoryThread.logSummary(log)
    }
  }

  private fun loadProject(projectPath: String) {
    // For V2 we test with the most recent AGP/Gradle combination.
    // For V1 we pin to the last AGP/Gradle combination that supports V1 only.
    if (useModelV2) {
      projectRule.loadProject(projectPath)
    } else {
      projectRule.loadProject(projectPath, agpVersion = AgpVersionSoftwareEnvironmentDescriptor.AGP_71)
    }
  }

  private fun logSummary(name: String, values: ArrayList<Long>, log: Logger) {
    log.info("$name average: ${values.average()}")
    log.info("$name min: ${values.minOrNull()}")
    log.info("$name max: ${values.maxOrNull()}")
  }

  private fun showHistogram(values: ArrayList<Long>, log: Logger) {
    val maximum = values.maxOrNull()
    if (maximum == null) {
      log.info("***NO VALUES WERE CAPTURED***")
      return
    }
    val samples = ArrayList<Long>()
    for (i in 0 until HISTOGRAM_SAMPLES) {
      val start = (i * values.size) / HISTOGRAM_SAMPLES
      val end = ((i + 1) * values.size) / HISTOGRAM_SAMPLES
      if (end <= start) {
        // No single value will be used in this sample
        continue
      }
      var currentSample = values[start]
      for (j in start until end) {
        val current = values[j]
        if (current > currentSample) {
          currentSample = current
        }
      }
      samples.add(currentSample)
    }
    log.info(buildString {
      append("Memory usage:")
      for (level in HISTOGRAM_LEVELS - 1 downTo 0) {
        append("\n")
        val currentLevelValue = (maximum * level) / HISTOGRAM_LEVELS
        for (sample in samples) {
          append(if (sample > currentLevelValue) "X" else " ")
        }
      }
      append("\n")
      for (sample in samples) {
        append("-")
      }
    })
  }

  private fun getLogger(): Logger {
    return Logger.getLogger(this.javaClass.name)
  }

  private fun getLastSyncStats(): GradleSyncStats? {
    val usages: List<LoggedUsage> = myUsageTracker!!.usages
    for (index in usages.indices.reversed()) {
      val usage = usages[index]
      if (usage.studioEvent.kind == AndroidStudioEvent.EventKind.GRADLE_SYNC_ENDED) {
        return usage.studioEvent.gradleSyncStats
      }
    }
    return null
  }

  private fun printStats(message: String, stats: GradleSyncStats?, log: Logger) {
    log.info("${getScenarioName()} $message:")
    if (stats == null) {
      log.info("  <null_stats>")
    }
    else {
      log.info("  Gradle: " + stats.gradleTimeMs)
      log.info("     IDE: " + stats.ideTimeMs)
      log.info("   Total: " + stats.totalTimeMs)
    }
  }

  private fun getScenarioName(): String {
    val scenarioName = StringBuilder(projectName)
    scenarioName.append("_SVS")
    return scenarioName.toString()
  }

  private inner class MemoryMeasurementThread(private val scenarioName: String): Thread() {
    private inner class ThreadMetric(val metricName: String, val sampler: () -> Long?) {
      private val metric = Metric("${scenarioName}_$metricName")
      val samples = ArrayList<Long>()

      fun sample(benchmark: Benchmark,  timeStamp: Long) {
        val value = sampler.invoke() ?: return
        samples.add(value)
        metric.addSamples(benchmark, MetricSample(timeStamp, value))
      }

      fun commit() {
        metric.commit("Memory$metricName")
      }
    }

    var daemonPid: Long? = null
    private var stopRunning = AtomicBoolean(false)
    private val threadMetrics = listOf(
      ThreadMetric("Test") { getUsedMemoryIde() },
      ThreadMetric("TestRsz") { getUsedMemoryIdeRsz() },
      ThreadMetric( "GradleRsz") { getUsedMemoryGradleRsz()}
    )

    override fun run() {
      val memoryBenchmark = Benchmark.Builder("Memory usage")
        .setProject(BENCHMARK_PROJECT)
        .build()


      var nextReading = Instant.now()
      while (!stopRunning.get()) {
        val currentInstant = Instant.now()
        if (currentInstant < nextReading) {
          val waitMillis = Duration.between(currentInstant, nextReading).toMillis()
          sleep(waitMillis)
          continue
        }
        val epochMilli = currentInstant.toEpochMilli()
        for (metric in threadMetrics) {
          metric.sample(memoryBenchmark, epochMilli)
        }

        nextReading = nextReading.plusMillis(MEMORY_MEASUREMENT_INTERVAL_MILLIS)
        if (nextReading < currentInstant) {
          val toSkip: Long = 1 + (Duration.between(nextReading, currentInstant).toMillis() / MEMORY_MEASUREMENT_INTERVAL_MILLIS)
          nextReading = currentInstant.plusMillis(MEMORY_MEASUREMENT_INTERVAL_MILLIS * toSkip)
        }
      }

      for (metric in threadMetrics) {
        metric.commit()
      }
    }

    private fun getFirstBusyDaemonPid(): Long? {
      val daemon = GradleDaemonServices.getDaemonsStatus().find {it.status == "BUSY"} ?: return null
      return daemon.pid
    }

    private fun getUsedMemoryIde(): Long {
      val runtime = Runtime.getRuntime()
      return runtime.totalMemory() - runtime.freeMemory()
    }

    private fun getUsedMemoryIdeRsz(): Long? {
      val handler = ProcessHandle.current()?: return null
      return getProcessRsz(handler.pid(), "Test")
    }

    private fun getUsedMemoryGradleRsz(): Long? {
      val busyPid = getFirstBusyDaemonPid()
      if (busyPid != null) {
        daemonPid = busyPid
      }
      if (daemonPid == null) {
        return null
      }
      return getProcessRsz(daemonPid!!, "Gradle")
    }

    private fun getProcessRsz(pId: Long, processName: String): Long? {
      val runtime = Runtime.getRuntime()
      // resident set size in kilobytes of the daemon process and its subprocesses
      val command = "ps -p $pId --no-heading -o rsz"
      val process = runtime.exec(command)
      val result = process.waitFor()
      if (result != 0) {
        try {
          val errorMessage = Scanner(process.errorStream).useDelimiter("\\A").next()
          getLogger().warning("Error $result while running \"$command\" for $processName, message: \"$errorMessage\"")
        }
        catch (ignored: Exception) {
        }
        return null
      }
      try {
        val inputStream = process.inputStream
        val usedKB = Scanner(inputStream).useDelimiter("\\A").next().trim().toLong()
        return usedKB * 1024
      }
      catch (exc: Exception) {
        getLogger().warning("Exception while parsing output of command \"$command\" for $processName: $exc")
      }
      return null
    }

    fun stopReadings() {
      if (isAlive) {
        stopRunning.set(true)
        join(2 * MEMORY_MEASUREMENT_INTERVAL_MILLIS)
      }
    }

    fun logSummary(log: Logger) {
      for (metric in threadMetrics) {
        val samples = metric.samples
        logSummary("Memory ${metric.metricName}", samples, log)
        showHistogram(samples, log)
      }
    }
  }
}
