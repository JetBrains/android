/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.common.analytics

import com.android.tools.adtui.actions.ZoomType
import com.android.tools.idea.common.editor.DesignerEditorPanel
import com.android.tools.idea.common.surface.DesignSurface
import com.google.wireless.android.sdk.stats.LayoutEditorEvent
import com.google.wireless.android.sdk.stats.LayoutEditorState

/**
 * Handles analytics that are common across design tools. Acts as an interface between [DesignSurface] and [CommonUsageTracker].
 */
open class DesignerAnalyticsManager(protected var surface: DesignSurface) {

  open val surfaceType = LayoutEditorState.Surfaces.UNKNOWN_SURFACES

  private var panelState: DesignerEditorPanel.State = DesignerEditorPanel.State.DEACTIVATED

  val editorMode
    get() = when (panelState) {
    DesignerEditorPanel.State.FULL -> LayoutEditorState.Mode.DESIGN_MODE
    // We map split mode to PREVIEW_MODE to keep consistency with past data
    DesignerEditorPanel.State.SPLIT -> LayoutEditorState.Mode.PREVIEW_MODE
    DesignerEditorPanel.State.DEACTIVATED -> LayoutEditorState.Mode.UNKOWN_MODE
  }

  open val layoutType = LayoutEditorState.Type.UNKNOWN_TYPE

  fun trackShowIssuePanel() = track(LayoutEditorEvent.LayoutEditorEventType.SHOW_LINT_MESSAGES)

  fun trackUnknownEvent() = track(LayoutEditorEvent.LayoutEditorEventType.UNKNOWN_EVENT_TYPE)

  fun trackZoom(type: ZoomType) = when (type) {
    ZoomType.ACTUAL -> track(LayoutEditorEvent.LayoutEditorEventType.ZOOM_ACTUAL)
    ZoomType.IN -> track(LayoutEditorEvent.LayoutEditorEventType.ZOOM_IN)
    ZoomType.OUT -> track(LayoutEditorEvent.LayoutEditorEventType.ZOOM_OUT)
    ZoomType.FIT_INTO, ZoomType.FIT -> track(LayoutEditorEvent.LayoutEditorEventType.ZOOM_FIT)
    else -> Unit // ignore unrecognized zoom type.
  }

  /**
   * Sets the [DesignerEditorPanel.State] so all related tracking, like rendering has the information about the state. If you want to log
   * an editor mode change that was triggered by the user, you should use [trackSelectEditorMode] instead.
   */
  fun setEditorModeWithoutTracking(panelState: DesignerEditorPanel.State) {
    this.panelState = panelState
  }

  fun trackSelectEditorMode(panelState: DesignerEditorPanel.State) = when (panelState) {
    DesignerEditorPanel.State.FULL -> track(LayoutEditorEvent.LayoutEditorEventType.SELECT_VISUAL_MODE)
    DesignerEditorPanel.State.SPLIT -> track(LayoutEditorEvent.LayoutEditorEventType.SELECT_SPLIT_MODE)
    DesignerEditorPanel.State.DEACTIVATED -> track(LayoutEditorEvent.LayoutEditorEventType.SELECT_TEXT_MODE)
  }

  fun trackIssuePanel(minimized: Boolean) =
    if (minimized) track(LayoutEditorEvent.LayoutEditorEventType.MINIMIZE_ERROR_PANEL)
    else track(LayoutEditorEvent.LayoutEditorEventType.RESTORE_ERROR_PANEL)

  protected fun track(type: LayoutEditorEvent.LayoutEditorEventType) = CommonUsageTracker.getInstance(surface).logAction(type)
}
