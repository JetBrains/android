/*
 * Copyright (C) 2025 The Android Open Source Project
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

import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorStateReads
import org.junit.Test

class StateReadsStatisticsTest {

  @Test
  fun testStart() {
    val stateReads = toggleBackAndForth()
    stateReads.start()
    val data = DynamicLayoutInspectorStateReads.newBuilder()
    stateReads.save { data }
    assertThat(data.observingAllSelected).isEqualTo(0)
    assertThat(data.observingNodeByIdSelected).isEqualTo(0)
    assertThat(data.observingSubTreeByIdSelected).isEqualTo(0)
    assertThat(data.pagesShownObservingAll).isEqualTo(0)
    assertThat(data.pagesShownObservingById).isEqualTo(0)
    assertThat(data.nextRecompositionChosen).isEqualTo(0)
    assertThat(data.prevRecompositionChosen).isEqualTo(0)
    assertThat(data.stackTraceLinksClicked).isEqualTo(0)
    assertThat(data.aiLinksClicked).isEqualTo(0)
    assertThat(data.build()).isEqualTo(DynamicLayoutInspectorStateReads.getDefaultInstance())
  }

  @Test
  fun testToggleBackAndForth() {
    val stateReads = toggleBackAndForth()
    val data = DynamicLayoutInspectorStateReads.newBuilder()
    stateReads.save { data }
    assertThat(data.observingAllSelected).isEqualTo(1)
    assertThat(data.observingNodeByIdSelected).isEqualTo(1)
    assertThat(data.observingSubTreeByIdSelected).isEqualTo(1)
    assertThat(data.pagesShownObservingAll).isEqualTo(1)
    assertThat(data.pagesShownObservingById).isEqualTo(2)
    assertThat(data.nextRecompositionChosen).isEqualTo(1)
    assertThat(data.prevRecompositionChosen).isEqualTo(1)
    assertThat(data.stackTraceLinksClicked).isEqualTo(1)
    assertThat(data.aiLinksClicked).isEqualTo(1)
  }

  private fun toggleBackAndForth(): StateReadStatistics {
    val stateReads = StateReadStatistics()
    stateReads.observingAllSelected()
    stateReads.stateReadsShown()
    stateReads.observingSingleNodeSelected()
    stateReads.stateReadsShown()
    stateReads.observingSubTreeSelected()
    stateReads.stateReadsShown()
    stateReads.nextRecompositionChosen()
    stateReads.prevRecompositionChosen()
    stateReads.gotoSourceFromStackTrace()
    stateReads.explainWithAiClicked()
    return stateReads
  }
}
