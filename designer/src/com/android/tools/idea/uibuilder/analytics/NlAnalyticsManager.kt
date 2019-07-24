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
package com.android.tools.idea.uibuilder.analytics

import com.android.tools.idea.common.analytics.DesignerAnalyticsManager
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.uibuilder.surface.SceneMode
import com.android.tools.idea.uibuilder.type.LayoutEditorFileType
import com.google.wireless.android.sdk.stats.LayoutEditorEvent
import com.google.wireless.android.sdk.stats.LayoutEditorState

/**
 * Handles analytics that are specific to the UI builder. Acts as an interface between [NlDesignSurface] and the usage tracker, being
 * responsible for converting the surface state to data that can be tracked.
 */
class NlAnalyticsManager(surface: DesignSurface) : DesignerAnalyticsManager(surface) {

  private val nlSurface : NlDesignSurface = surface as NlDesignSurface

  override val surfaceType
    get() = when (nlSurface.sceneMode) {
      SceneMode.SCREEN_ONLY -> LayoutEditorState.Surfaces.SCREEN_SURFACE
      SceneMode.BLUEPRINT_ONLY -> LayoutEditorState.Surfaces.BLUEPRINT_SURFACE
      SceneMode.BOTH -> LayoutEditorState.Surfaces.BOTH
      SceneMode.SCREEN_COMPOSE_ONLY -> LayoutEditorState.Surfaces.SCREEN_SURFACE
    }

  override val surfaceMode
    get() = if (StudioFlags.NELE_SPLIT_EDITOR.get()) {
      super.surfaceMode
    } else {
      if (nlSurface.isPreviewSurface) LayoutEditorState.Mode.PREVIEW_MODE else LayoutEditorState.Mode.DESIGN_MODE
    }

  override
  val layoutType
    get() =
      if (nlSurface.layoutType is LayoutEditorFileType) (nlSurface.layoutType as LayoutEditorFileType).getLayoutEditorStateType()
      // TODO(b/120469076): track VECTOR type as well
      else super.layoutType

  fun trackClearAllConstraints() = track(LayoutEditorEvent.LayoutEditorEventType.CLEAR_ALL_CONSTRAINTS)

  fun trackInferConstraints() = track(LayoutEditorEvent.LayoutEditorEventType.INFER_CONSTRAINS)

  fun trackAddHorizontalGuideline() = track(LayoutEditorEvent.LayoutEditorEventType.ADD_HORIZONTAL_GUIDELINE)

  fun trackAddVerticalGuideline() = track(LayoutEditorEvent.LayoutEditorEventType.ADD_VERTICAL_GUIDELINE)

  fun trackAlign() = track(LayoutEditorEvent.LayoutEditorEventType.ALIGN)

  fun trackDefaultMargins() = track(LayoutEditorEvent.LayoutEditorEventType.DEFAULT_MARGINS)

  fun trackThemeChange() = track(LayoutEditorEvent.LayoutEditorEventType.THEME_CHANGE)

  fun trackApiLevelChange() = track(LayoutEditorEvent.LayoutEditorEventType.API_LEVEL_CHANGE)

  fun trackLanguageChange() = track(LayoutEditorEvent.LayoutEditorEventType.LANGUAGE_CHANGE)

  fun trackDeviceChange() = track(LayoutEditorEvent.LayoutEditorEventType.DEVICE_CHANGE)

  fun trackToggleAutoConnect(selected: Boolean) =
    if (selected) track(LayoutEditorEvent.LayoutEditorEventType.TURN_ON_AUTOCONNECT)
    else track(LayoutEditorEvent.LayoutEditorEventType.TURN_OFF_AUTOCONNECT)
}
