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
package com.android.tools.idea.layoutinspector.metrics.statistics

import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.RecompositionData
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorSession
import com.intellij.openapi.actionSystem.AnActionEvent
import org.jetbrains.annotations.TestOnly

/**
 * Accumulators for various actions of interest.
 */
class SessionStatistics(model: InspectorModel) {
  private val live = LiveModeStatistics()
  private val rotation = RotationStatistics()
  private val memory = MemoryStatistics(model)
  private val compose = ComposeStatistics()
  private val system = SystemViewToggleStatistics()
  private val goto = GotoDeclarationStatistics()

  fun start() {
    live.start()
    rotation.start()
    memory.start()
    compose.start()
    system.start()
    goto.start()
  }

  fun save(data: DynamicLayoutInspectorSession.Builder) {
    live.save { data.liveBuilder }
    rotation.save { data.rotationBuilder }
    memory.save { data.memoryBuilder }
    compose.save { data.composeBuilder }
    system.save { data.systemBuilder }
    goto.save { data.gotoDeclarationBuilder }
  }

  fun selectionMadeFromImage(view: ViewNode?) {
    live.selectionMade()
    rotation.selectionMadeFromImage()
    compose.selectionMadeFromImage(view)
    system.selectionMade()
  }

  fun selectionMadeFromComponentTree(view: ViewNode?) {
    live.selectionMade()
    rotation.selectionMadeFromComponentTree()
    compose.selectionMadeFromComponentTree(view)
    system.selectionMade()
  }

  fun refreshButtonClicked() {
    live.refreshButtonClicked()
  }

  fun gotoSourceFromPropertyValue(view: ViewNode?) {
    compose.gotoSourceFromPropertyValue(view)
  }

  fun gotoSourceFromTreeActionMenu(event: AnActionEvent) {
    goto.gotoSourceFromTreeActionMenu(event)
  }

  fun gotoSourceFromDoubleClick() {
    goto.gotoSourceFromDoubleClick()
  }

  fun updateRecompositionStats(recompositions: RecompositionData, maxHighlight: Float) {
    compose.updateRecompositionStats(recompositions, maxHighlight)
  }

  fun resetRecompositionCountsClick() {
    compose.resetRecompositionCountsClick()
  }

  /**
   * Live mode changed.
   */
  var currentModeIsLive : Boolean
    get() = live.currentModeIsLive
    set(value) { live.currentModeIsLive = value }

  /**
   * 3D mode changed.
   */
  var currentMode3D : Boolean
    get() = rotation.currentMode3D
    set(value) { rotation.currentMode3D = value }

  /**
   * Whether the system nodes are currently being hidden.
   */
  var hideSystemNodes : Boolean
    get() = system.hideSystemNodes
    set(value) { system.hideSystemNodes = value }

  /**
   * Number of memory measurements
   */
  @get:TestOnly
  val memoryMeasurements: Int
    get() = memory.measurements
}
