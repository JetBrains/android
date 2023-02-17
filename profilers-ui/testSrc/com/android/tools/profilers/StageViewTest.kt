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
package com.android.tools.profilers

import com.android.tools.adtui.model.FakeTimer
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.transport.faketransport.FakeGrpcServer
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ApplicationRule
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit
import javax.swing.JComponent

class StageViewTest {
  @get:Rule
  val applicationRule = ApplicationRule()

  private val timer = FakeTimer()
  private val transportService = FakeTransportService(timer, false)

  @get:Rule
  var grpcServer = FakeGrpcServer.createFakeGrpcServer("StageViewTest", transportService)

  @Test
  fun testSelectionTimeLabel() {
    val profilers = StudioProfilers(ProfilerClient(grpcServer.channel), FakeIdeProfilerServices(), timer)
    val profilersView = StudioProfilersView(profilers, FakeIdeProfilerComponents())
    val stageView = object : StageView<FakeStage>(profilersView, FakeStage(profilers)) {
      override fun getToolbar(): JComponent? {
        return null
      }
    }

    val selectionRange = stageView.stage.timeline.selectionRange
    val selectionTimeLabel = stageView.selectionTimeLabel

    // Label should be empty initially for empty selection.
    assertThat(selectionTimeLabel.text).isEqualTo("")

    // Point selection
    val minUs = TimeUnit.MINUTES.toMicros(1)
    selectionRange.set(minUs.toDouble(), minUs.toDouble())
    assertThat(selectionTimeLabel.text).isEqualTo("01:00.000")

    // Range selection
    val maxUs = TimeUnit.HOURS.toMicros(1)
    selectionRange.max = maxUs.toDouble()
    assertThat(selectionTimeLabel.text).isEqualTo("01:00.000 - 01:00:00.000")
  }

  @Test
  fun testClickSelectionTimeLabel() {
    val timer = FakeTimer()
    val profilers = StudioProfilers(ProfilerClient(grpcServer.channel), FakeIdeProfilerServices(), timer)
    val profilersView = StudioProfilersView(profilers, FakeIdeProfilerComponents())
    val stageView = object : StageView<FakeStage>(profilersView, FakeStage(profilers)) {
      override fun getToolbar(): JComponent? {
        return null
      }
    }
    stageView.selectionTimeLabel.setSize(100, 20)
    val ui = FakeUi(stageView.selectionTimeLabel)

    val minuteUs = TimeUnit.MINUTES.toMicros(1).toDouble()
    stageView.stage.timeline.dataRange.set(0.0, minuteUs * 2)
    stageView.stage.timeline.viewRange.set(minuteUs, minuteUs * 2)
    assertThat(stageView.stage.timeline.viewRange.min).isEqualTo(minuteUs)
    assertThat(stageView.stage.timeline.viewRange.max).isEqualTo(minuteUs * 2)

    val pointTimeUs = minuteUs / 2
    stageView.stage.timeline.selectionRange.set(pointTimeUs, pointTimeUs)
    ui.mouse.click(1, 1)
    stageView.stage.timeline.update(TimeUnit.MINUTES.toNanos(2))
    assertThat(stageView.stage.timeline.viewRange.min).isEqualTo(0.0)
    assertThat(stageView.stage.timeline.viewRange.max).isEqualTo(minuteUs)

    stageView.stage.timeline.selectionRange.set(minuteUs, minuteUs + 1)
    ui.mouse.click(1,  1)
    stageView.stage.timeline.update(TimeUnit.MINUTES.toNanos(2))
    assertThat(stageView.stage.timeline.viewRange.min).isEqualTo(minuteUs - 0.1)
    assertThat(stageView.stage.timeline.viewRange.max).isEqualTo(minuteUs + 1.1)
  }
}
