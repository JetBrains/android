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
package com.android.tools.profilers.cpu.analysis

import com.android.tools.adtui.model.FakeTimer
import com.android.tools.adtui.model.Range
import com.android.tools.adtui.ui.HideablePanel
import com.android.tools.idea.transport.faketransport.FakeGrpcServer
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profilers.FakeIdeProfilerComponents
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.SessionProfilersView
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.StudioProfilersView
import com.android.tools.profilers.cpu.CpuCapture
import com.android.tools.profilers.cpu.systemtrace.CounterModel
import com.android.tools.profilers.cpu.systemtrace.ProcessModel
import com.android.tools.profilers.cpu.systemtrace.SystemTraceCpuCaptureBuilder
import com.android.tools.profilers.cpu.systemtrace.SystemTraceCpuCaptureBuilderTest
import com.android.tools.profilers.cpu.systemtrace.ThreadModel
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import java.util.concurrent.TimeUnit

class FullTraceSummaryDetailsViewTest {
  companion object {
    private val CAPTURE_RANGE = Range(0.0, Double.MAX_VALUE)
  }

  @get:Rule
  val applicationRule = ApplicationRule()

  @get:Rule
  val disposableRule = DisposableRule()

  private val timer = FakeTimer()
  private val transportService = FakeTransportService(timer, false)

  @get:Rule
  var grpcServer = FakeGrpcServer.createFakeGrpcServer("FullTraceSummaryDetailsViewTest", transportService)

  private lateinit var profilersView: StudioProfilersView

  @Before
  fun setUp() {
    val profilers = StudioProfilers(ProfilerClient(grpcServer.channel), FakeIdeProfilerServices())
    profilersView = SessionProfilersView(profilers, FakeIdeProfilerComponents(), disposableRule.disposable)
  }

  @Test
  fun componentsArePopulated() {
    val selectionRange = Range(TimeUnit.SECONDS.toMicros(1).toDouble(), TimeUnit.SECONDS.toMicros(60).toDouble())
    val model = FullTraceAnalysisSummaryTabModel(CAPTURE_RANGE, selectionRange).apply {
      dataSeries.add(Mockito.mock(CpuCapture::class.java))
    }
    val view = FullTraceSummaryDetailsView(profilersView, model)

    assertThat(view.timeRangeLabel.text).isEqualTo("00:01.000 - 00:01:00.000")
    assertThat(view.durationLabel.text).isEqualTo("59 s")
    assertThat(view.energyUsedLabel.text).isEqualTo("0 µWs")
  }

  @Test
  fun rangeChangeUpdatesLabels() {
    val selectionRange = Range(0.0, 0.0)
    val model = FullTraceAnalysisSummaryTabModel(CAPTURE_RANGE, selectionRange).apply {
      dataSeries.add(Mockito.mock(CpuCapture::class.java))
    }
    val view = FullTraceSummaryDetailsView(profilersView, model)

    assertThat(view.timeRangeLabel.text).isEqualTo("00:00.000 - 00:00.000")
    assertThat(view.durationLabel.text).isEqualTo("0 μs")
    assertThat(view.energyUsedLabel.text).isEqualTo("0 µWs")

    selectionRange.set(TimeUnit.MILLISECONDS.toMicros(1).toDouble(), TimeUnit.MILLISECONDS.toMicros(2).toDouble())
    assertThat(view.timeRangeLabel.text).isEqualTo("00:00.001 - 00:00.002")
    assertThat(view.durationLabel.text).isEqualTo("1 ms")
    assertThat(view.energyUsedLabel.text).isEqualTo("0 µWs")

  }

  @Test
  fun `total power row and power table not present with no power rail data`() {
    val processes = mapOf(
      1 to ProcessModel(
        1, "Process",
        mapOf(1 to ThreadModel(1, 1, "Thread", listOf(), listOf(), listOf())),
        mapOf()))

    val systemTraceCpuCaptureModel = SystemTraceCpuCaptureBuilderTest.TestModel(processes, emptyMap(), emptyList(), emptyList(),
                                                                                emptyList(),
                                                                                emptyList())
    val builder = SystemTraceCpuCaptureBuilder(systemTraceCpuCaptureModel)
    val systemTraceCpuCapture = builder.build(0L, 1, Range())

    val selectionRange = Range(TimeUnit.SECONDS.toMicros(1).toDouble(), TimeUnit.SECONDS.toMicros(60).toDouble())
    val model = FullTraceAnalysisSummaryTabModel(CAPTURE_RANGE, selectionRange).apply {
      dataSeries.add(systemTraceCpuCapture)
    }

    val view = FullTraceSummaryDetailsView(profilersView, model)

    // Four components expected, as there are two rows of common data (Time Range and Duration), each with a key and value component.
    assertThat(view.commonSection.componentCount).isEqualTo(4)
    // Two components expected: the common section and the usage instructions section.
    assertThat(view.components.size).isEqualTo(2)
  }

  @Test
  fun `total power row and power table not present with power rail data`() {
    val processes = mapOf(
      1 to ProcessModel(
        1, "Process",
        mapOf(1 to ThreadModel(1, 1, "Thread", listOf(), listOf(), listOf())),
        mapOf()))

    val powerRails = listOf(
      CounterModel("power.rails.ddr.a", sortedMapOf(1L to 100.0, 2L to 200.0)))

    val systemTraceCpuCaptureModel = SystemTraceCpuCaptureBuilderTest.TestModel(processes, emptyMap(), emptyList(), powerRails, emptyList(),
                                                                                emptyList())

    val builder = SystemTraceCpuCaptureBuilder(systemTraceCpuCaptureModel)
    val systemTraceCpuCapture = builder.build(0L, 1, Range())

    val selectionRange = Range(TimeUnit.SECONDS.toMicros(1).toDouble(), TimeUnit.SECONDS.toMicros(60).toDouble())
    val model = FullTraceAnalysisSummaryTabModel(CAPTURE_RANGE, selectionRange).apply {
      dataSeries.add(systemTraceCpuCapture)
    }

    val view = FullTraceSummaryDetailsView(profilersView, model)

    // Six components expected, as there are three rows of common data (Time Range, Duration, and Total Power), each with a key and value component.
    assertThat(view.commonSection.componentCount).isEqualTo(6)
    // Three components expected: the common section, the power table section, and the usage instructions section.
    assertThat(view.components.size).isEqualTo(3)

    // Assert that the power rail table section is a {@link HideablePanel}
    assertThat(view.components[1]).isInstanceOf(HideablePanel::class.java)

    // Assert that the power rail table has the correct dimensions and column headers.
    assertThat(view.powerRailTable).isNotNull()
    assertThat(view.powerRailTable!!.table.columnCount).isEqualTo(3)
    // Note: header is not included in the row count.
    assertThat(view.powerRailTable!!.table.rowCount).isEqualTo(1)
    assertThat(view.powerRailTable!!.table.tableHeader.columnModel.getColumn(0).headerValue).isEqualTo("Rail name")
    assertThat(view.powerRailTable!!.table.tableHeader.columnModel.getColumn(1).headerValue).isEqualTo("Average power (mW)")
    assertThat(view.powerRailTable!!.table.tableHeader.columnModel.getColumn(2).headerValue).isEqualTo("Cumulative consumption (µWs)")
  }
}