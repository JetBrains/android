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
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.concurrent.TimeUnit
import javax.swing.JComponent

class StageViewTest {
  @Test
  fun testSelectionTimeLabel() {
    val timer = FakeTimer()
    val grpcChannel = FakeGrpcChannel("StageViewTest")
    val profilers = StudioProfilers(grpcChannel.client, FakeIdeProfilerServices(), timer)
    val profilersView = StudioProfilersView(profilers, FakeIdeProfilerComponents())
    val stageView = object : StageView<FakeStage>(profilersView, FakeStage(profilers)) {
      override fun getToolbar(): JComponent? {
        return null
      }
    }

    val selectionRange = profilers.timeline.selectionRange
    val selectionTimeLabel = stageView.selectionTimeLabel

    // Label should be empty initially for empty selection.
    assertThat(selectionTimeLabel.text).isEqualTo("")

    // Point selection
    val minUs = TimeUnit.MINUTES.toMicros(1)
    selectionRange.set(minUs.toDouble(), minUs.toDouble())
    assertThat(selectionTimeLabel.text).isEqualTo("00:01:00.000")

    // Range selection
    val maxUs = TimeUnit.HOURS.toMicros(1)
    selectionRange.max = maxUs.toDouble()
    assertThat(selectionTimeLabel.text).isEqualTo("00:01:00.000 - 01:00:00.000")
  }
}