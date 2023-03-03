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

import com.android.testutils.TestUtils.resolveWorkspacePath
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.adtui.model.Range
import com.android.tools.idea.transport.TransportService
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.idea.transport.faketransport.TransportServiceTestImpl
import com.android.tools.perflib.heap.io.InMemoryBuffer
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.memory.MemoryProfiler
import com.android.tools.profilers.memory.adapters.HeapDumpCaptureObject
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.registerServiceInstance
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class MemoryProfilerHeapDumpTest {
  private val benchmark = benchmarkMemoryAndTime("Heap Dump", "Load-Capture")
  private val ideServices = FakeIdeProfilerServices()
  private val timer = FakeTimer()
  private val transportService = FakeTransportService(timer)

  @get:Rule
  val appRule = ApplicationRule()

  @get:Rule
  val grpcChannel = FakeGrpcChannel(javaClass.simpleName, transportService)

  @Before
  fun setUp() {
    ApplicationManager.getApplication().registerServiceInstance(TransportService::class.java, TransportServiceTestImpl(transportService))
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
    val file = resolveWorkspacePath("tools/adt/idea/profilers/testData/hprofs/performance/$name.hprof").toFile()
    val profilers = StudioProfilers(ProfilerClient(grpcChannel.channel), ideServices, timer)
    assertThat(profilers.sessionsManager.importSessionFromFile(file)).isTrue()
    val dumpInfo = MemoryProfiler.getHeapDumpsForSession(profilers.client, profilers.session, Range(Double.MIN_VALUE, Double.MAX_VALUE))[0]
    val capture = HeapDumpCaptureObject(profilers.client, profilers.session, dumpInfo, null, ideServices.featureTracker, ideServices)
    benchmark(name) { capture.load(InMemoryBuffer(file.readBytes())) }
  }
}