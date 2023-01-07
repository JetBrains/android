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

import com.android.tools.idea.layoutinspector.LayoutInspectorOpenProjectsTracker
import com.android.tools.idea.layoutinspector.model.RecompositionData
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorAttachToProcess.ClientType
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorErrorInfo.AttachErrorCode
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorErrorInfo.AttachErrorState
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorSession
import com.intellij.openapi.actionSystem.AnActionEvent

/**
 * Accumulators for various actions of interest.
 */
interface SessionStatistics {
  /**
   * Reset all state accumulators.
   */
  fun start()

  /**
   * Save all state accumulators.
   */
  fun save(data: DynamicLayoutInspectorSession.Builder)

  /**
   * A selection was made from the Layout Inspector Image.
   */
  fun selectionMadeFromImage(view: ViewNode?)

  /**
   * A selection was made from the Layout Inspector Component Tree.
   */
  fun selectionMadeFromComponentTree(view: ViewNode?)

  /**
   * The refresh button was activated.
   */
  fun refreshButtonClicked()

  /**
   * Navigate to source from a property value.
   */
  fun gotoSourceFromPropertyValue(view: ViewNode?)

  /**
   * Navigate to source from the component tree via a menu action.
   */
  fun gotoSourceFromTreeActionMenu(event: AnActionEvent)

  /**
   * Navigate to source from the component tree via a double click.
   */
  fun gotoSourceFromDoubleClick()

  /**
   * The recomposition numbers changed.
   */
  fun updateRecompositionStats(recompositions: RecompositionData, maxHighlight: Float)

  /**
   * The recomposition numbers were reset.
   */
  fun resetRecompositionCountsClick()

  /**
   * The connection succeeded in attaching to the process.
   */
  fun attachSuccess()

  /**
   * The connection failed to attach to the process.
   */
  fun attachError(errorState: AttachErrorState?, errorCode: AttachErrorCode)

  /**
   * The compose inspector failed to initialize.
   */
  fun composeAttachError(errorCode: AttachErrorCode)

  /**
   * A frame was received.
   */
  fun frameReceived()

  /**
   * A debugger was detected. Indicate if the debugger [isPaused] during attach.
   */
  fun debuggerInUse(isPaused: Boolean)

  /**
   * Live mode changed.
   */
  var currentModeIsLive: Boolean

  /**
   * 3D mode changed.
   */
  var currentMode3D: Boolean

  /**
   * Whether the system nodes are currently being hidden.
   */
  var hideSystemNodes: Boolean

  /**
   * Whether recomposition counts are currently being shown.
   */
  var showRecompositions: Boolean

  /**
   * The current recomposition highlight color selected.
   */
  var recompositionHighlightColor: Int
}

class SessionStatisticsImpl(
  clientType: ClientType,
  areMultipleProjectsOpen: () -> Boolean = { LayoutInspectorOpenProjectsTracker.areMultipleProjectsOpen() }
) : SessionStatistics {
  private val attach = AttachStatistics(clientType, areMultipleProjectsOpen)
  private val live = LiveModeStatistics()
  private val rotation = RotationStatistics()
  private val compose = ComposeStatistics()
  private val system = SystemViewToggleStatistics()
  private val goto = GotoDeclarationStatistics()

  override fun start() {
    attach.start()
    live.start()
    rotation.start()
    compose.start()
    system.start()
    goto.start()
  }

  override fun save(data: DynamicLayoutInspectorSession.Builder) {
    attach.save { data.attachBuilder }
    live.save { data.liveBuilder }
    rotation.save { data.rotationBuilder }
    compose.save { data.composeBuilder }
    system.save { data.systemBuilder }
    goto.save { data.gotoDeclarationBuilder }
  }

  override fun selectionMadeFromImage(view: ViewNode?) {
    live.selectionMade()
    rotation.selectionMadeFromImage()
    compose.selectionMadeFromImage(view)
    system.selectionMade()
  }

  override fun selectionMadeFromComponentTree(view: ViewNode?) {
    live.selectionMade()
    rotation.selectionMadeFromComponentTree()
    compose.selectionMadeFromComponentTree(view)
    system.selectionMade()
  }

  override fun refreshButtonClicked() {
    live.refreshButtonClicked()
  }

  override fun gotoSourceFromPropertyValue(view: ViewNode?) {
    compose.gotoSourceFromPropertyValue(view)
  }

  override fun gotoSourceFromTreeActionMenu(event: AnActionEvent) {
    goto.gotoSourceFromTreeActionMenu(event)
  }

  override fun gotoSourceFromDoubleClick() {
    goto.gotoSourceFromDoubleClick()
  }

  override fun updateRecompositionStats(recompositions: RecompositionData, maxHighlight: Float) {
    compose.updateRecompositionStats(recompositions, maxHighlight)
  }

  override fun resetRecompositionCountsClick() {
    compose.resetRecompositionCountsClick()
  }

  override fun attachSuccess() {
    attach.attachSuccess()
  }

  override fun attachError(errorState: AttachErrorState?, errorCode: AttachErrorCode) {
    attach.attachError(errorState, errorCode)
  }

  override fun composeAttachError(errorCode: AttachErrorCode) {
    attach.composeAttachError(errorCode)
  }

  override fun frameReceived() {
    compose.frameReceived()
  }

  override fun debuggerInUse(isPaused: Boolean) {
    attach.debuggerInUse(isPaused)
  }

  override var currentModeIsLive : Boolean
    get() = live.currentModeIsLive
    set(value) { live.currentModeIsLive = value }

  override var currentMode3D: Boolean
    get() = rotation.currentMode3D
    set(value) { rotation.currentMode3D = value }

  override var hideSystemNodes: Boolean
    get() = system.hideSystemNodes
    set(value) { system.hideSystemNodes = value }

  override var showRecompositions: Boolean
    get() = compose.showRecompositions
    set(value) { compose.showRecompositions = value }

  override var recompositionHighlightColor: Int
    get() = compose.recompositionHighlightColor
    set(value) { compose.recompositionHighlightColor = value }
}
