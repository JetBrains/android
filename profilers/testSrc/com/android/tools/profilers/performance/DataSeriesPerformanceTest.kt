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
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService.FAKE_DEVICE_NAME
import com.android.tools.idea.transport.faketransport.FakeTransportService.FAKE_PROCESS_NAME
import com.android.tools.perflogger.Benchmark
import com.android.tools.perflogger.Metric
import com.android.tools.perflogger.WindowDeviationAnalyzer
import com.android.tools.profiler.proto.Common
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.cpu.CpuProfilerStage
import com.android.tools.profilers.cpu.CpuUsage
import com.android.tools.profilers.cpu.CpuUsageDataSeries
import com.android.tools.profilers.cpu.FakeCpuService
import com.android.tools.profilers.cpu.LegacyCpuThreadCountDataSeries
import com.android.tools.profilers.cpu.LegacyCpuThreadStateDataSeries
import com.android.tools.profilers.energy.EnergyDuration
import com.android.tools.profilers.energy.EnergyEventsDataSeries
import com.android.tools.profilers.energy.EnergyUsageDataSeries
import com.android.tools.profilers.energy.MergedEnergyEventsDataSeries
import com.android.tools.profilers.event.LifecycleEventDataSeries
import com.android.tools.profilers.event.UserEventDataSeries
import com.android.tools.profilers.memory.AllocStatsDataSeries
import com.android.tools.profilers.memory.FakeMemoryService
import com.android.tools.profilers.memory.GcStatsDataSeries
import com.android.tools.profilers.memory.MemoryDataSeries
import com.android.tools.profilers.memory.MemoryProfilerStage
import com.android.tools.profilers.memory.adapters.LiveAllocationCaptureObject
import com.android.tools.profilers.network.NetworkOpenConnectionsDataSeries
import com.android.tools.profilers.network.NetworkTrafficDataSeries
import com.google.common.util.concurrent.MoreExecutors
import io.grpc.inprocess.InProcessChannelBuilder
import org.junit.After
import org.junit.Before
import org.junit.Rule
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
    service = DataStoreService("TestService", TestUtils.createTempDirDeletedOnExit().absolutePath,
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
    val dataSeriesToTest = mapOf(Pair("Event-Activities", LifecycleEventDataSeries(studioProfilers, false)),
                                 Pair("Event-Interactions", UserEventDataSeries(studioProfilers)),
                                 Pair("Energy-Usage", EnergyUsageDataSeries(client, session)),
                                 Pair("Energy-Events",
                                      MergedEnergyEventsDataSeries(EnergyEventsDataSeries(client, session), EnergyDuration.Kind.WAKE_LOCK,
                                                                   EnergyDuration.Kind.JOB)),
                                 Pair("Cpu-Usage",
                                      CpuUsageDataSeries(client.cpuClient, session) { dataList -> CpuUsage.extractData(dataList, false) }),
                                 Pair("Cpu-Thread-Count",
                                      LegacyCpuThreadCountDataSeries(client.cpuClient, session)),
                                 Pair("Cpu-Thread-State",
                                      LegacyCpuThreadStateDataSeries(client.cpuClient, session, 1, null)),
                                 Pair("Network-Open-Connections", NetworkOpenConnectionsDataSeries(client.networkClient, session)),
                                 Pair("Network-Traffic", NetworkTrafficDataSeries(client.networkClient, session,
                                                                                  NetworkTrafficDataSeries.Type.BYTES_RECEIVED)),
                                 Pair("Memory-GC-Stats", GcStatsDataSeries(client.memoryClient, session)),
                                 Pair("Memory-Series", MemoryDataSeries(client.memoryClient, session) { sample -> sample.timestamp }),
                                 Pair("Memory-Allocation",
                                      AllocStatsDataSeries(studioProfilers, client.memoryClient) { sample -> sample.timestamp }),
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
    System.gc()
    val usedKB = (rt.totalMemory() - rt.freeMemory()) / 1024
    memoryBenchmark.log(metricName, usedKB)
  }

  private fun <T> collectAndReportAverageTimes(offset: Long, metric: Metric, series: DataSeries<T>, recordMetric: Boolean) {
    val startTime = System.nanoTime()
    series.getDataForXRange(Range(offset.toDouble(), (offset + QUERY_INTERVAL).toDouble()))
    if (recordMetric) {
      metric.addSamples(cpuBenchmark, Metric.MetricSample(Instant.now().toEpochMilli(), (System.nanoTime() - startTime)))
    }
  }

  private class TestLiveAllocationSeries(profilers: StudioProfilers, session: Common.Session) : DataSeries<Long> {
    companion object {
      val LOAD_JOINER = MoreExecutors.directExecutor()
    }

    override fun getDataForXRange(xRange: Range?): MutableList<SeriesData<Long>> {
      liveAllocation.load(xRange, LOAD_JOINER)
      return mutableListOf()
    }

    val liveAllocation: LiveAllocationCaptureObject

    init {
      val stage = MemoryProfilerStage(profilers)
      liveAllocation = LiveAllocationCaptureObject(profilers.client.memoryClient, session, 0, MoreExecutors.newDirectExecutorService(),
                                                   stage)
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
