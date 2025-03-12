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

import com.android.tools.idea.layoutinspector.model
import com.android.tools.idea.layoutinspector.model.COMPOSE1
import com.android.tools.idea.layoutinspector.model.COMPOSE2
import com.android.tools.idea.layoutinspector.model.ROOT
import com.android.tools.idea.layoutinspector.ui.toolbar.actions.RECOMPOSITION_COLOR_BLUE_ARGB
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorAttachToProcess.ClientType.APP_INSPECTION_CLIENT
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorSession
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test

class SessionStatisticsTest {

  companion object {
    @JvmField @ClassRule val rule = ApplicationRule()
  }

  @get:Rule val disposableRule = DisposableRule()

  @Test
  fun doNotSaveEmptyData() {
    val stats =
      SessionStatisticsImpl(
        APP_INSPECTION_CLIENT,
        areMultipleProjectsOpen = { false },
        isAutoConnectEnabled = { true },
        isEmbeddedLayoutInspector = { true },
      )
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
    assertThat(result.attach.multipleProjectsOpen).isFalse()
    assertThat(result.attach.autoConnectEnabled).isTrue()
    assertThat(result.attach.isEmbeddedLayoutInspector).isTrue()
    assertThat(result.attach.debuggerAttached).isFalse()
    assertThat(result.attach.debuggerPausedDuringAttach).isFalse()
    assertThat(result.attach.attachDurationMs).isEqualTo(0)
  }

  @Test
  fun doSaveData() {
    val model =
      model(disposableRule.disposable) {
        view(ROOT, 2, 4, 6, 8, qualifiedName = "rootType") {
          compose(COMPOSE1, "Button", "button.kt", 123, composeCount = 0, composeSkips = 0) {
            compose(COMPOSE2, "Text", "text.kt", 234, composeCount = 0, composeSkips = 0)
          }
        }
      }
    val stats =
      SessionStatisticsImpl(
        APP_INSPECTION_CLIENT,
        areMultipleProjectsOpen = { true },
        isAutoConnectEnabled = { true },
        isEmbeddedLayoutInspector = { true },
      )
    val compose1 = model[COMPOSE1]
    stats.start()
    model.notifyModified(structuralChange = true)
    stats.hideSystemNodes = true
    stats.attachSuccess()
    stats.recompositionHighlightColor = RECOMPOSITION_COLOR_BLUE_ARGB
    stats.showRecompositions = true
    stats.frameReceived()
    stats.frameReceived()
    stats.gotoSourceFromTreeDoubleClick()
    stats.selectionMadeFromComponentTree(compose1)
    stats.debuggerInUse(false)

    val data = DynamicLayoutInspectorSession.newBuilder()
    stats.save(data)
    val result = data.build()
    assertThat(result.hasLive()).isTrue()
    assertThat(result.hasRotation()).isTrue()
    assertThat(result.hasCompose()).isTrue()
    assertThat(result.hasSystem()).isTrue()
    assertThat(result.hasGotoDeclaration()).isTrue()
    assertThat(result.hasAttach()).isTrue()

    assertThat(result.live.clicksWithoutLiveUpdates).isEqualTo(1)
    assertThat(result.rotation.componentTreeClicksIn2D).isEqualTo(1)
    assertThat(result.compose.componentTreeClicks).isEqualTo(1)
    assertThat(result.compose.framesWithRecompositionCountsOn).isEqualTo(2)
    assertThat(result.compose.framesWithRecompositionColorBlue).isEqualTo(2)
    assertThat(result.system.clicksWithHiddenSystemViews).isEqualTo(1)
    assertThat(result.gotoDeclaration.doubleClicks).isEqualTo(1)
    assertThat(result.attach.clientType).isEqualTo(APP_INSPECTION_CLIENT)
    assertThat(result.attach.success).isTrue()
    assertThat(result.attach.multipleProjectsOpen).isTrue()
    assertThat(result.attach.autoConnectEnabled).isTrue()
    assertThat(result.attach.isEmbeddedLayoutInspector).isTrue()
    assertThat(result.attach.debuggerAttached).isTrue()
    assertThat(result.attach.debuggerPausedDuringAttach).isFalse()
  }

  @Test
  fun testHasMultipleProjectsIsUpdated() {
    var hasMultipleProjects = false
    var isAutoConnectEnabled = false
    val stats =
      SessionStatisticsImpl(
        APP_INSPECTION_CLIENT,
        areMultipleProjectsOpen = { hasMultipleProjects },
        isAutoConnectEnabled = { isAutoConnectEnabled },
      )

    stats.start()

    val data1 = DynamicLayoutInspectorSession.newBuilder()
    stats.save(data1)
    val result1 = data1.build()
    assertThat(result1.attach.multipleProjectsOpen).isFalse()

    hasMultipleProjects = true
    isAutoConnectEnabled = true
    stats.start()

    val data2 = DynamicLayoutInspectorSession.newBuilder()
    stats.save(data2)
    val result2 = data2.build()
    assertThat(result2.attach.multipleProjectsOpen).isTrue()
    assertThat(result2.attach.autoConnectEnabled).isTrue()
  }
}
