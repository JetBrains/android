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
package com.android.tools.idea.layoutinspector

import com.android.tools.idea.layoutinspector.metrics.statistics.SessionStatistics
import com.android.tools.idea.layoutinspector.model.RecompositionData
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorErrorInfo
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorSession
import com.intellij.openapi.actionSystem.AnActionEvent

class FakeSessionStats : SessionStatistics {
  var goToSourcesFromRendererInvocations = 0
  var setOnDeviceRenderingInvocations = 0

  override fun start() {}

  override fun save(data: DynamicLayoutInspectorSession.Builder) {}

  override fun selectionMadeFromImage(view: ViewNode?) {}

  override fun selectionMadeFromComponentTree(view: ViewNode?) {}

  override fun refreshButtonClicked() {}

  override fun gotoSourceFromPropertyValue(view: ViewNode?) {}

  override fun gotoSourceFromTreeActionMenu(event: AnActionEvent) {}

  override fun gotoSourceFromTreeDoubleClick() {}

  override fun gotoSourceFromRenderDoubleClick() {
    goToSourcesFromRendererInvocations += 1
  }

  override fun updateRecompositionStats(recompositions: RecompositionData, maxHighlight: Float) {}

  override fun resetRecompositionCountsClick() {}

  override fun attachSuccess() {}

  override fun attachError(errorCode: DynamicLayoutInspectorErrorInfo.AttachErrorCode) {}

  override fun composeAttachError(errorCode: DynamicLayoutInspectorErrorInfo.AttachErrorCode) {}

  override fun frameReceived() {}

  override fun debuggerInUse(isPaused: Boolean) {}

  override fun setOnDeviceRendering(enabled: Boolean) {
    setOnDeviceRenderingInvocations += 1
  }

  override fun isXr(isXr: Boolean) {}

  override var currentModeIsLive = true
  override var currentMode3D = true
  override var hideSystemNodes = true
  override var showRecompositions = true
  override var recompositionHighlightColor = 1
  override var currentProgress = DynamicLayoutInspectorErrorInfo.AttachErrorState.MODEL_UPDATED
}
