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
package com.android.tools.idea.profilers.performance

import com.android.testutils.TestUtils
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.adtui.model.Range
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.perflib.heap.io.InMemoryBuffer
import com.android.tools.perflogger.Benchmark
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.FakeProfilerService
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.cpu.FakeCpuService
import com.android.tools.profilers.event.FakeEventService
import com.android.tools.profilers.memory.FakeMemoryService
import com.android.tools.profilers.memory.MemoryProfiler
import com.android.tools.profilers.memory.adapters.HeapDumpCaptureObject
import com.android.tools.profilers.network.FakeNetworkService
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.system.measureTimeMillis

class MemoryProfilerHeapDumpTest {
  private val memoryBenchmark = Benchmark.Builder("Heap Dump Memory (mb)")
    .setProject("Android Studio Profilers")
    .build()
  private val timingBenchmark = Benchmark.Builder("Heap Dump Time (millis)")
    .setProject("Android Studio Profilers")
    .build()

  private val ideServices = FakeIdeProfilerServices()
  private val timer = FakeTimer()

  @get:Rule
  val grpcChannel = FakeGrpcChannel(javaClass.simpleName,
                                    FakeCpuService(),
                                    FakeTransportService(timer),
                                    FakeProfilerService(timer),
                                    FakeMemoryService(),
                                    FakeEventService(),
                                    FakeNetworkService.newBuilder().build())

  @Before
  fun init() {
    ideServices.enableSeparateHeapDumpUi(true)
  }

  @Test
  fun `measure loading of github heap dump`() {
    testFile("github")
  }

  @Test
  fun `measure loading of sunflower heap dump`() {
    testFile("sunflower")
  }

  private fun testFile(name: String) {
    val file = TestUtils.getWorkspaceFile("tools/adt/idea/profilers/testData/hprofs/performance/$name.hprof")
    val profilers = StudioProfilers(ProfilerClient(grpcChannel.channel), ideServices, timer)
    assertThat(profilers.sessionsManager.importSessionFromFile(file)).isTrue()
    val dumpInfo =
      MemoryProfiler.getHeapDumpsForSession(profilers.client, profilers.session, Range(0.0, 1.0), ideServices)[0]
    val capture =
      HeapDumpCaptureObject(profilers.client, profilers.session, dumpInfo, null, ideServices.featureTracker, ideServices)

    val beforeMem = getMemoryUsed()
    val elapsedMillis = measureTimeMillis { capture.load(InMemoryBuffer(file.readBytes())) }
    val afterMem = getMemoryUsed()
    timingBenchmark.log("$name-Load-Capture", elapsedMillis)
    memoryBenchmark.log("$name-Load-Capture-Used", (afterMem - beforeMem) / (1024 * 1024))
  }
}