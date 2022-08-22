/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.metrics.statistics

import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.layoutinspector.model.ComposeViewNode
import com.android.tools.idea.layoutinspector.model.RecompositionData
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.ui.HIGHLIGHT_COLOR_ORANGE
import com.android.tools.idea.layoutinspector.ui.HIGHLIGHT_COLOR_RED
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorCompose
import org.junit.Test

class ComposeStatisticsTest {
  @Test
  fun testStart() {
    val compose = ComposeStatistics()
    compose.gotoSourceFromPropertyValue(mock<ComposeViewNode>())
    compose.selectionMadeFromComponentTree(mock<ComposeViewNode>())
    compose.selectionMadeFromComponentTree(mock<ComposeViewNode>())
    compose.selectionMadeFromImage(mock<ComposeViewNode>())
    compose.start()
    val data = DynamicLayoutInspectorCompose.newBuilder()
    compose.save { data }
    assertThat(data.imageClicks).isEqualTo(0)
    assertThat(data.componentTreeClicks).isEqualTo(0)
    assertThat(data.goToSourceFromPropertyValueClicks).isEqualTo(0)
    assertThat(data.maxRecompositionCount).isEqualTo(0)
    assertThat(data.maxRecompositionSkips).isEqualTo(0)
    assertThat(data.maxRecompositionHighlight).isEqualTo(0f)
    assertThat(data.framesWithRecompositionCountsOn).isEqualTo(0)
    assertThat(data.framesWithRecompositionColorRed).isEqualTo(0)
    assertThat(data.framesWithRecompositionColorBlue).isEqualTo(0)
    assertThat(data.framesWithRecompositionColorGreen).isEqualTo(0)
    assertThat(data.framesWithRecompositionColorYellow).isEqualTo(0)
    assertThat(data.framesWithRecompositionColorPurple).isEqualTo(0)
    assertThat(data.framesWithRecompositionColorOrange).isEqualTo(0)
  }

  @Test
  fun testToggleBackAndForth() {
    val compose = ComposeStatistics()
    compose.start()

    compose.gotoSourceFromPropertyValue(mock<ComposeViewNode>())
    compose.gotoSourceFromPropertyValue(mock<ComposeViewNode>())
    compose.gotoSourceFromPropertyValue(mock<ViewNode>())
    compose.selectionMadeFromComponentTree(mock<ComposeViewNode>())
    compose.selectionMadeFromComponentTree(mock<ComposeViewNode>())
    compose.selectionMadeFromComponentTree(mock<ViewNode>())
    compose.selectionMadeFromImage(mock<ComposeViewNode>())
    compose.selectionMadeFromImage(mock<ViewNode>())
    val data = DynamicLayoutInspectorCompose.newBuilder()
    compose.save { data }
    assertThat(data.imageClicks).isEqualTo(1)
    assertThat(data.componentTreeClicks).isEqualTo(2)
    assertThat(data.goToSourceFromPropertyValueClicks).isEqualTo(2)
  }

  @Test
  fun testRecompositionCounts() {
    val compose = ComposeStatistics()
    compose.showRecompositions = true
    compose.recompositionHighlightColor = HIGHLIGHT_COLOR_RED
    compose.frameReceived()
    compose.frameReceived()
    compose.updateRecompositionStats(RecompositionData(12, 33, 2.1f), 2.1f)
    compose.updateRecompositionStats(RecompositionData(34, 51, 1.1f), 5.1f)
    compose.resetRecompositionCountsClick()
    compose.resetRecompositionCountsClick()
    compose.recompositionHighlightColor = HIGHLIGHT_COLOR_ORANGE
    compose.frameReceived()
    compose.updateRecompositionStats(RecompositionData(5, 10, 1.1f), 1.1f)
    compose.updateRecompositionStats(RecompositionData(17, 103, 4.1f), 9.1f)
    val data = DynamicLayoutInspectorCompose.newBuilder()
    compose.save { data }
    assertThat(data.maxRecompositionCount).isEqualTo(34)
    assertThat(data.maxRecompositionSkips).isEqualTo(103)
    assertThat(data.maxRecompositionHighlight).isWithin(0.01f).of(9.10f)
    assertThat(data.recompositionResetClicks).isEqualTo(2)
    assertThat(data.framesWithRecompositionCountsOn).isEqualTo(3)
    assertThat(data.framesWithRecompositionColorRed).isEqualTo(2)
    assertThat(data.framesWithRecompositionColorBlue).isEqualTo(0)
    assertThat(data.framesWithRecompositionColorGreen).isEqualTo(0)
    assertThat(data.framesWithRecompositionColorYellow).isEqualTo(0)
    assertThat(data.framesWithRecompositionColorPurple).isEqualTo(0)
    assertThat(data.framesWithRecompositionColorOrange).isEqualTo(1)
  }
}
