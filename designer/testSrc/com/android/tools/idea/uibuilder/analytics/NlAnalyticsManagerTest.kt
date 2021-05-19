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

import com.android.tools.idea.common.analytics.CommonUsageTracker
import com.android.tools.idea.common.editor.DesignerEditorPanel
import com.android.tools.idea.common.type.DefaultDesignerFileType
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.uibuilder.surface.NlScreenViewProvider
import com.android.tools.idea.uibuilder.type.AnimatedVectorFileType
import com.android.tools.idea.uibuilder.type.LayoutFileType
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.LayoutEditorEvent
import com.google.wireless.android.sdk.stats.LayoutEditorState
import org.jetbrains.android.AndroidTestBase
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

class NlAnalyticsManagerTest : AndroidTestBase() {

  private lateinit var analyticsManager: NlAnalyticsManager

  private lateinit var surface: NlDesignSurface

  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()
    surface = mock(NlDesignSurface::class.java)
    `when`(surface.screenViewProvider).thenReturn(NlScreenViewProvider.RENDER_AND_BLUEPRINT)
    analyticsManager = NlAnalyticsManager(surface)
  }

  fun testBasicTracking() {
    analyticsManager.trackAlign()
    assertThat(CommonUsageTracker.NOP_TRACKER.lastTrackedEvent).isEqualTo(LayoutEditorEvent.LayoutEditorEventType.ALIGN)

    analyticsManager.trackToggleAutoConnect(true)
    assertThat(CommonUsageTracker.NOP_TRACKER.lastTrackedEvent).isEqualTo(LayoutEditorEvent.LayoutEditorEventType.TURN_ON_AUTOCONNECT)

    analyticsManager.trackToggleAutoConnect(false)
    assertThat(CommonUsageTracker.NOP_TRACKER.lastTrackedEvent).isEqualTo(LayoutEditorEvent.LayoutEditorEventType.TURN_OFF_AUTOCONNECT)
  }

  fun testLayoutType() {
    `when`(surface.layoutType).thenReturn(DefaultDesignerFileType)
    assertThat(analyticsManager.layoutType).isEqualTo(LayoutEditorState.Type.UNKNOWN_TYPE) // By default, we don't infer any types

    `when`(surface.layoutType).thenReturn(LayoutFileType)
    assertThat(analyticsManager.layoutType).isEqualTo(LayoutEditorState.Type.LAYOUT)

    `when`(surface.layoutType).thenReturn(AnimatedVectorFileType)
    assertThat(analyticsManager.layoutType).isEqualTo(LayoutEditorState.Type.DRAWABLE)
  }

  fun testSurfaceType() {
    assertThat(analyticsManager.surfaceType).isEqualTo(LayoutEditorState.Surfaces.BOTH) // Set in setup

    `when`(surface.screenViewProvider).thenReturn(NlScreenViewProvider.BLUEPRINT)
    assertThat(analyticsManager.surfaceType).isEqualTo(LayoutEditorState.Surfaces.BLUEPRINT_SURFACE)
  }

  fun testSurfaceMode() {
    analyticsManager.setEditorModeWithoutTracking(DesignerEditorPanel.State.FULL)
    assertThat(analyticsManager.editorMode).isEqualTo(LayoutEditorState.Mode.DESIGN_MODE)

    analyticsManager.setEditorModeWithoutTracking(DesignerEditorPanel.State.SPLIT)
    // Split mode is mapped to PREVIEW_MODE when using the split editor
    assertThat(analyticsManager.editorMode).isEqualTo(LayoutEditorState.Mode.PREVIEW_MODE)
  }
}
