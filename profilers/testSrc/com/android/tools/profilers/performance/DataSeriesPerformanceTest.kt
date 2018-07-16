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
import com.android.tools.adtui.model.Range
import com.android.tools.datastore.DataStoreDatabase
import com.android.tools.datastore.DataStoreService
import com.android.tools.datastore.DeviceId
import com.android.tools.datastore.FakeLogService
import com.android.tools.datastore.poller.PollRunner
import com.android.tools.perflogger.Benchmark
import com.android.tools.perflogger.Metric
import com.android.tools.profiler.proto.Common
import com.android.tools.profilers.FakeGrpcChannel
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.cpu.FakeCpuService
import com.android.tools.profilers.energy.EnergyDuration
import com.android.tools.profilers.energy.EnergyEventsDataSeries
import com.android.tools.profilers.energy.EnergyUsageDataSeries
import com.android.tools.profilers.energy.MergedEnergyEventsDataSeries
import com.android.tools.profilers.event.ActivityEventDataSeries
import com.android.tools.profilers.event.SimpleEventDataSeries
import io.grpc.inprocess.InProcessChannelBuilder
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

class DataSeriesPerformanceTest {
  companion object {
    private val START_TIME = TimeUnit.SECONDS.toNanos(0)
    private val END_TIME = TimeUnit.MINUTES.toNanos(60)
    private val INTERVAL = TimeUnit.MILLISECONDS.toNanos(200)
    private val QUERY_INTERVAL = TimeUnit.SECONDS.toNanos(30)
  }
  private val ticker = PollTicker()
  private lateinit var service: DataStoreService
  private lateinit var client: ProfilerClient
  private lateinit var session: Common.Session
  private val benchmark = Benchmark.Builder("Database Perf").setProject("Android Studio Profilers").build()

  @get:Rule
  var grpcChannel = FakeGrpcChannel("DataSeriesPerformanceTest", FakeCpuService())

  @Before
  fun setup() {
    service = DataStoreService("TestService", TestUtils.createTempDirDeletedOnExit().absolutePath,
                               Consumer<Runnable> { ticker.run(it) }, FakeLogService())
    val channel = InProcessChannelBuilder.forName("DataSeriesPerformanceTest").build()
    service.connect(channel)
    service.setConnectedClients(DeviceId.of(-1), channel)
    for (namespace in service.databases.keys) {
      val db = service.databases[namespace]!!
      val performantDatabase = namespace.myCharacteristic == DataStoreDatabase.Characteristic.PERFORMANT
      val manager = DataGeneratorManager(db.connection, performantDatabase)
      manager.beginSession(0x123456789)
      // Generate data in an interlaced fashion to better match studio sampled data.
      for(i in START_TIME..END_TIME step INTERVAL) {
        // Adding variability in the timing so generators can use it to better represent sparse data.
        manager.generateData(i)
      }
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
    val dataSeriesToTest = mapOf(Pair("Event Activities", ActivityEventDataSeries(client, session, false)),
                                 Pair("Event Interactions", SimpleEventDataSeries(client, session)),
                                 Pair("Energy Usage", EnergyUsageDataSeries(client, session)),
                                 Pair("Energy Events", MergedEnergyEventsDataSeries(EnergyEventsDataSeries(client, session), EnergyDuration.Kind.WAKE_LOCK, EnergyDuration.Kind.JOB))
    )
    for(name in dataSeriesToTest.keys) {
      collectAndReportAverageTimes(name, dataSeriesToTest[name]!!)
    }
  }

  private fun <T> collectAndReportAverageTimes(name: String, series: DataSeries<T>) {
    val queryStep = QUERY_INTERVAL / 2
    val metric = Metric(name)
    for(i in START_TIME..END_TIME step queryStep) {
      val startTime = System.nanoTime()
      series.getDataForXRange(Range(i.toDouble(), (i + QUERY_INTERVAL).toDouble()))
      metric.addSamples(benchmark, Metric.MetricSample(Instant.now().toEpochMilli(), (System.nanoTime() - startTime)))
    }
    metric.commit()
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
