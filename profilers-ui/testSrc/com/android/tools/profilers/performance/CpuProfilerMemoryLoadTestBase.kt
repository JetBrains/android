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
package com.android.tools.profilers.performance

import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.perflogger.Benchmark
import com.android.tools.profilers.FakeIdeProfilerComponents
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.FakeProfilerService
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.StudioProfilersView
import com.android.tools.profilers.cpu.CpuProfilerStage
import com.android.tools.profilers.cpu.CpuProfilerStageView
import com.android.tools.profilers.cpu.FakeCpuService
import com.android.tools.profilers.event.FakeEventService
import com.android.tools.profilers.memory.FakeMemoryService
import com.android.tools.profilers.network.FakeNetworkService
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.Disposer
import org.junit.After
import org.junit.Rule
import java.io.File
import java.lang.management.ManagementFactory
import kotlin.system.measureTimeMillis

/**
 * Base class for loading the CpuProfilerStageView and measuring the used memory as well as the high water mark. This class starts the
 * CpuProfilerStage and CpuProfilerStageView then reports the memory used and the max memory after entering the stage. The stage enter
 * is triggered by setting the stage on the StudioProfilers.
 */
open class CpuProfilerMemoryLoadTestBase {
  val myMemoryBenchmark = Benchmark.Builder("CpuProfiler Import Trace Memory (kb)").setProject("Android Studio Profilers").build()
  val myTimingBenchmark = Benchmark.Builder("CpuProfiler Import Trace Time (millis)").setProject("Android Studio Profilers").build()
  val myTimer = FakeTimer()
  val myComponents = FakeIdeProfilerComponents()
  val myIdeServices = FakeIdeProfilerServices()
  val myCpuService = FakeCpuService()
  var myProfilersView: StudioProfilersView? = null

  @get:Rule
  val myGrpcChannel = FakeGrpcChannel(
    "CpuProfilerMemoryLoadTestBase", myCpuService, FakeTransportService(myTimer), FakeProfilerService(myTimer),
    FakeMemoryService(), FakeEventService(), FakeNetworkService.newBuilder().build()
  )


  @After
  fun cleanup() {
    Disposer.dispose(myProfilersView!!)
    ensureGc()
  }

  private fun logMemoryUsed(metricName: String) {
    val memoryUsage = ManagementFactory.getMemoryMXBean()
    ensureGc()
    myMemoryBenchmark.log(metricName + "-Used", memoryUsage.heapMemoryUsage.used / 1024)
  }

  private fun ensureGc() {
    for (x in 0..10) System.gc()
    System.runFinalization()
    for (x in 0..10) ManagementFactory.getMemoryMXBean().gc()
  }

  /**
   * Helper function for setting up the CpuProfilerStage and passing in a file to be parsed as an imported trace.
   * The name is used as a prefix for the metrics to be recorded. The format is as follows
   *  [name]-Load-Capture-Used
   */
  protected fun loadCaptureAndReport(name:String, fileName:File?) {
    // Start as clean as we can.
    ensureGc()
    // Enable flags that allow us to import all traces.
    myIdeServices.enableImportTrace(true)
    myIdeServices.enableAtrace(true)
    myIdeServices.enablePerfetto(true)
    val profilers = StudioProfilers(ProfilerClient(myGrpcChannel.name), myIdeServices, myTimer)
    profilers.setPreferredProcess(FakeTransportService.FAKE_DEVICE_NAME, FakeTransportService.FAKE_PROCESS_NAME, null)
    val stage = CpuProfilerStage(profilers, fileName)
    var cpuStageView: CpuProfilerStageView? = null
    // One second must be enough for new devices (and processes) to be picked up
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    myTimingBenchmark.log(name, measureTimeMillis {
    myProfilersView = StudioProfilersView(profilers, myComponents)
      // Setting the stage enters the stage and triggers the parsing of the CpuCapture
      stage.studioProfilers.stage = stage
      cpuStageView = CpuProfilerStageView(myProfilersView!!, stage)
    })
    logMemoryUsed(name + "-Load-Capture")
    // Test the stage view just to hold a reference in case the compiler attempts to be smart.
    assertThat(cpuStageView).isNotNull()
  }
}