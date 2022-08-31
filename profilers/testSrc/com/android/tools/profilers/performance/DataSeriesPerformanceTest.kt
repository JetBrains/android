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
package com.android.tools.profilers.performance

import com.android.testutils.TestUtils
import com.android.tools.adtui.model.DataSeries
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.adtui.model.Range
import com.android.tools.adtui.model.SeriesData
import com.android.tools.datastore.DataStoreDatabase
import com.android.tools.datastore.DataStoreService
import com.android.tools.datastore.FakeLogService
import com.android.tools.datastore.poller.PollRunner
import com.android.tools.idea.transport.faketransport.FakeTransportService.FAKE_DEVICE_NAME
import com.android.tools.idea.transport.faketransport.FakeTransportService.FAKE_PROCESS_NAME
import com.android.tools.perflogger.Benchmark
import com.android.tools.perflogger.Metric
import com.android.tools.perflogger.WindowDeviationAnalyzer
import com.android.tools.profiler.proto.Common
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.cpu.CpuThreadCountDataSeries
import com.android.tools.profilers.cpu.CpuThreadStateDataSeries
import com.android.tools.profilers.cpu.CpuUsage
import com.android.tools.profilers.energy.EnergyUsage
import com.android.tools.profilers.event.LifecycleEventDataSeries
import com.android.tools.profilers.event.UserEventDataSeries
import com.android.tools.profilers.memory.AllocStatsDataSeries
import com.android.tools.profilers.memory.LegacyGcStatsDataSeries
import com.android.tools.profilers.memory.MainMemoryProfilerStage
import com.android.tools.profilers.memory.MemoryDataSeries
import com.android.tools.profilers.memory.adapters.LiveAllocationCaptureObject
import com.google.common.util.concurrent.MoreExecutors
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

class DataSeriesPerformanceTest {
  companion object {
    private val START_TIME = TimeUnit.SECONDS.toNanos(0)
    private val END_TIME = TimeUnit.MINUTES.toNanos(30)
    private val INTERVAL = TimeUnit.MILLISECONDS.toNanos(200)
    private val QUERY_INTERVAL = TimeUnit.SECONDS.toNanos(60)
  }

  private val ticker = PollTicker()
  private lateinit var service: DataStoreService
  private lateinit var client: ProfilerClient
  private lateinit var session: Common.Session
  private val cpuBenchmark = Benchmark.Builder("DataSeries Query Timings (Nanos)").setProject("Android Studio Profilers").build()
  private val memoryBenchmark = Benchmark.Builder("DataSeries Memory (kb)").setProject("Android Studio Profilers").build()

  @Before
  fun setup() {
    service = DataStoreService("TestService", TestUtils.createTempDirDeletedOnExit().toString(),
                               Consumer<Runnable> { ticker.run(it) }, FakeLogService())
    for (namespace in service.databases.keys) {
      val db = service.databases[namespace]!!
      val performantDatabase = namespace.myCharacteristic == DataStoreDatabase.Characteristic.PERFORMANT
      val manager = DataGeneratorManager(db.connection, performantDatabase)
      manager.beginSession(0x123456789)
      // Generate data in an interlaced fashion to better match studio sampled data.
      for (i in START_TIME..END_TIME step INTERVAL) {
        // Adding variability in the timing so generators can use it to better represent sparse data.
        manager.generateData(i)
      }
      db.connection.commit()
      session = manager.endSession()
    }
    client = ProfilerClient("TestService")
  }

  @After
  fun tearDown() {
    service.shutdown()
  }

  @Test
  fun runPerformanceTest() {
    val timer = FakeTimer()
    val studioProfilers = StudioProfilers(client, FakeIdeProfilerServices(), timer)
    studioProfilers.setPreferredProcess(FAKE_DEVICE_NAME, FAKE_PROCESS_NAME, null)
    val dataSeriesToTest = mapOf(Pair("Cpu-Usage",
                                      CpuUsage.buildDataSeries(client.transportClient, session, null)),
                                 Pair("Cpu-Thread-Count",
                                      CpuThreadCountDataSeries(client.transportClient, session.streamId, session.pid)),
                                 Pair("Cpu-Thread-State",
                                      CpuThreadStateDataSeries(client.transportClient, session.streamId, session.pid, 1, null)),
                                 Pair("Event-Activities", LifecycleEventDataSeries(studioProfilers, false)),
                                 Pair("Event-Interactions", UserEventDataSeries(studioProfilers)),
                                 Pair("Energy-Usage", EnergyUsage.buildDataSeries(client.transportClient, session)),
                                 Pair("Memory-GC-Stats",
                                      LegacyGcStatsDataSeries(client.memoryClient, session)),
                                 Pair("Memory-Series", MemoryDataSeries(client.memoryClient, session) { sample -> sample.timestamp }),
                                 Pair("Memory-Allocation",
                                      AllocStatsDataSeries(studioProfilers) { sample -> sample.javaAllocationCount.toLong() }),
                                 Pair("Memory-LiveAllocation", TestLiveAllocationSeries(studioProfilers, session))
    )
    val nameToMetrics = mutableMapOf<String, Metric>()
    val queryStep = QUERY_INTERVAL / 2
    logMemoryUsed("Before-Query-Memory-Used")
    for (i in START_TIME..END_TIME step queryStep) {
      for (name in dataSeriesToTest.keys) {
        if (!nameToMetrics.containsKey(name)) {
          nameToMetrics[name] = Metric(name)
        }
        // We ignore the first query as it does a bunch of cache optimizations we don't want to account for.
        collectAndReportAverageTimes(i, nameToMetrics[name]!!, dataSeriesToTest[name]!!, i != START_TIME)
      }
    }
    nameToMetrics.values.forEach {
      it.setAnalyzers(cpuBenchmark, setOf(WindowDeviationAnalyzer.Builder()
                                            .addMeanTolerance(WindowDeviationAnalyzer.MeanToleranceParams.Builder().build())
                                            .build()))
      it.commit()
    }
    logMemoryUsed("After-Query-Memory-Used")
  }

  private fun logMemoryUsed(metricName: String) {
    val rt = Runtime.getRuntime()

    for (x in 0..10) System.gc()
    val usedKB = (rt.totalMemory() - rt.freeMemory()) / 1024
    memoryBenchmark.log(metricName, usedKB)
  }

  private fun <T> collectAndReportAverageTimes(offset: Long, metric: Metric, series: DataSeries<T>, recordMetric: Boolean) {
    val startTime = System.nanoTime()
    series.getDataForRange(Range(offset.toDouble(), (offset + QUERY_INTERVAL).toDouble()))
    if (recordMetric) {
      metric.addSamples(cpuBenchmark, Metric.MetricSample(Instant.now().toEpochMilli(), (System.nanoTime() - startTime)))
    }
  }

  private class TestLiveAllocationSeries(profilers: StudioProfilers, session: Common.Session) : DataSeries<Long> {
    companion object {
      val LOAD_JOINER = MoreExecutors.directExecutor()
    }

    override fun getDataForRange(range: Range): List<SeriesData<Long>> {
      liveAllocation.load(range, LOAD_JOINER)
      return mutableListOf()
    }

    val liveAllocation: LiveAllocationCaptureObject

    init {
      val stage = MainMemoryProfilerStage(profilers)
      liveAllocation = LiveAllocationCaptureObject(profilers.client, session, 0, MoreExecutors.newDirectExecutorService(), stage)
    }
  }

  private class PollTicker {
    private var lastRunner: Runnable? = null

    fun run(runner: Runnable) {
      lastRunner = runner
      run()
    }

    fun run() {
      if (lastRunner is PollRunner) {
        val poller = lastRunner as PollRunner
        poller.poll()
      }
      else {
        lastRunner!!.run()
      }
    }
  }
}
