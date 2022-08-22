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
package com.android.tools.idea.layoutinspector.metrics.statistics

import com.android.tools.idea.concurrency.waitForCondition
import com.android.tools.idea.layoutinspector.model
import com.android.tools.idea.layoutinspector.model.COMPOSE1
import com.android.tools.idea.layoutinspector.model.COMPOSE2
import com.android.tools.idea.layoutinspector.model.ROOT
import com.android.tools.idea.layoutinspector.ui.HIGHLIGHT_COLOR_BLUE
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorAttachToProcess.ClientType.APP_INSPECTION_CLIENT
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorSession
import com.intellij.testFramework.ApplicationRule
import org.junit.ClassRule
import org.junit.Test
import java.util.concurrent.TimeUnit

class SessionStatisticsTest {

  companion object {
    @JvmField
    @ClassRule
    val rule = ApplicationRule()
  }

  @Test
  fun doNotSaveEmptyData() {
    val stats = SessionStatisticsImpl(APP_INSPECTION_CLIENT, model {})
    val data = DynamicLayoutInspectorSession.newBuilder()
    stats.frameReceived()
    stats.save(data)
    val result = data.build()
    assertThat(result.hasLive()).isFalse()
    assertThat(result.hasRotation()).isFalse()
    assertThat(result.hasMemory()).isFalse()
    assertThat(result.hasCompose()).isFalse()
    assertThat(result.hasSystem()).isFalse()
    assertThat(result.hasGotoDeclaration()).isFalse()
    assertThat(result.hasAttach()).isTrue() // except for attach data
    assertThat(result.attach.clientType).isEqualTo(APP_INSPECTION_CLIENT)
  }

  @Test
  fun doSaveData() {
    val model = model {
      view(ROOT, 2, 4, 6, 8, qualifiedName = "rootType") {
        compose(COMPOSE1, "Button", "button.kt", 123, composeCount = 0, composeSkips = 0) {
          compose(COMPOSE2, "Text", "text.kt", 234, composeCount = 0, composeSkips = 0)
        }
      }
    }
    val stats = SessionStatisticsImpl(APP_INSPECTION_CLIENT, model)
    val compose1 = model[COMPOSE1]
    stats.start()
    model.notifyModified(structuralChange = true)
    stats.hideSystemNodes = true
    stats.attachSuccess()
    stats.recompositionHighlightColor = HIGHLIGHT_COLOR_BLUE
    stats.showRecompositions = true
    stats.frameReceived()
    stats.frameReceived()
    stats.gotoSourceFromDoubleClick()
    stats.selectionMadeFromComponentTree(compose1)
    waitForCondition(10, TimeUnit.SECONDS) { stats.memoryMeasurements > 0 }

    val data = DynamicLayoutInspectorSession.newBuilder()
    stats.save(data)
    val result = data.build()
    assertThat(result.hasLive()).isTrue()
    assertThat(result.hasRotation()).isTrue()
    assertThat(result.hasMemory()).isTrue()
    assertThat(result.hasCompose()).isTrue()
    assertThat(result.hasSystem()).isTrue()
    assertThat(result.hasGotoDeclaration()).isTrue()
    assertThat(result.hasAttach()).isTrue()

    assertThat(result.live.clicksWithoutLiveUpdates).isEqualTo(1)
    assertThat(result.rotation.componentTreeClicksIn2D).isEqualTo(1)
    assertThat(result.memory.initialSnapshot.captureSizeMb).isEqualTo(0)
    assertThat(result.compose.componentTreeClicks).isEqualTo(1)
    assertThat(result.compose.framesWithRecompositionCountsOn).isEqualTo(2)
    assertThat(result.compose.framesWithRecompositionColorBlue).isEqualTo(2)
    assertThat(result.system.clicksWithHiddenSystemViews).isEqualTo(1)
    assertThat(result.gotoDeclaration.doubleClicks).isEqualTo(1)
    assertThat(result.attach.clientType).isEqualTo(APP_INSPECTION_CLIENT)
    assertThat(result.attach.success).isTrue()
  }
}
