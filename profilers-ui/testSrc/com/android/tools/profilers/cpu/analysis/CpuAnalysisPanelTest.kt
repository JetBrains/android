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
package com.android.tools.profilers.cpu.analysis

import com.android.testutils.TestUtils.resolveWorkspacePath
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
import com.android.tools.profilers.cpu.CpuProfilerUITestUtils
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunsInEdt
class CpuAnalysisPanelTest {

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
                                   resolveWorkspacePath(CpuProfilerUITestUtils.ATRACE_TRACE_PATH).toFile(), 123L)
    panel = CpuAnalysisPanel(SessionProfilersView(profilers, FakeIdeProfilerComponents(), disposableRule.disposable), stage)
  }

  @Test
  fun tabsUpdatedOnCaptureCompleted() {
    val observer = AspectObserver()
    val stateLatch = CountDownLatch(1)
    stage.aspect.addDependency(observer).onChange(CpuCaptureStage.Aspect.STATE) {
      assertThat(panel.tabView.tabCount).isNotEqualTo(0)
      stateLatch.countDown()
    }
    assertThat(panel.tabView.tabCount).isEqualTo(0)
    stage.enter()
    assertThat(stateLatch.await(5, TimeUnit.SECONDS)).isTrue()
  }

  @Test
  fun tabsAreOnlyPopulatedWhenSelected() {
    stage.enter()
    assertThat(panel.tabView.selectedIndex).isEqualTo(0)
    assertThat(panel.tabView.getComponentAt(0)).isInstanceOf(CpuAnalysisSummaryTab::class.java)
    panel.tabView.selectedIndex = 1
    assertThat(panel.tabView.getComponentAt(0)).isNotInstanceOf(CpuAnalysisSummaryTab::class.java)
  }
}