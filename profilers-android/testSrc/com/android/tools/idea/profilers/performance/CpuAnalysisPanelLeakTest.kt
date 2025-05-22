/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.test.testutils.TestUtils.resolveWorkspacePath
import com.android.tools.adtui.model.AspectObserver
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profilers.FakeIdeProfilerComponents
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.ProfilersTestData
import com.android.tools.profilers.SessionProfilersView
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.cpu.CpuCaptureStage
import com.android.tools.profilers.cpu.CpuProfilerTestUtils
import com.android.tools.profilers.cpu.analysis.CpuAnalysisPanel
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.system.measureNanoTime

class CpuAnalysisPanelLeakTest {
  private val log = makeLogger("Range change time (ns)", "selection")
  private val timer = FakeTimer()
  @get:Rule
  var grpcChannel = FakeGrpcChannel("CpuCaptureStageTestChannel", FakeTransportService(timer, true))
  @get:Rule
  val myEdtRule = EdtRule()
  @get:Rule
  val applicationRule = ApplicationRule()
  @get:Rule
  val disposableRule = DisposableRule()

  private lateinit var profilers: StudioProfilers
  private val services = FakeIdeProfilerServices()
  private lateinit var stage: CpuCaptureStage
  private lateinit var panel: CpuAnalysisPanel

  @Before
  fun setUp() {
    profilers = StudioProfilers(ProfilerClient(grpcChannel.channel), services, timer)
    stage = CpuCaptureStage.create(profilers, ProfilersTestData.DEFAULT_CONFIG,
                                   resolveWorkspacePath(CpuProfilerTestUtils.ATRACE_DATA_FILE).toFile(), 123L)
    panel = CpuAnalysisPanel(SessionProfilersView(profilers, FakeIdeProfilerComponents(), disposableRule.disposable), stage)
  }

  @Test
  fun `selecting tabs then back to Summary should not slow down range-selection`() {
    val observer = AspectObserver()
    val stateLatch = CountDownLatch(1)
    stage.aspect.addDependency(observer).onChange(CpuCaptureStage.Aspect.STATE) {
      val N = 200
      val r = stage.timeline.viewRange
      val lo = r.min
      val hi = r.max
      val d = (hi - lo) / (N + 1)

      fun change() {
        r.set(lo, hi)
        repeat(N) { r.set(lo, r.max - d) }
        repeat(N) { r.set(lo, r.max + d) }
        repeat(N) { r.set(r.min + d, hi) }
        repeat(N) { r.set(r.min - d, hi) }
      }

      change() // "warm up"
      log("before", measureNanoTime(::change))
      (1 until panel.tabView.tabCount).forEach { panel.tabView.selectedIndex = it }
      panel.tabView.selectedIndex = 0 // "Summary"
      log("after", measureNanoTime(::change))

      stateLatch.countDown()
    }

    stage.enter()
    stateLatch.await(10, TimeUnit.SECONDS)
  }
}