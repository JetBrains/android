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

import com.google.common.truth.Truth.assertThat
import org.mockito.Mockito.mock

import com.android.tools.adtui.actions.ZoomType
import com.android.tools.idea.common.surface.DesignSurface
import com.google.wireless.android.sdk.stats.LayoutEditorEvent
import org.jetbrains.android.AndroidTestBase

class DesignerAnalyticsManagerTest : AndroidTestBase() {

  private lateinit var myAnalyticsManager: DesignerAnalyticsManager

  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()
    val surface = mock(DesignSurface::class.java)
    myAnalyticsManager = DesignerAnalyticsManager(surface)
  }

  fun testBasicTracking() {
    myAnalyticsManager.trackUnknownEvent()
    assertThat(CommonUsageTracker.NOP_TRACKER.lastTrackedEvent).isEqualTo(LayoutEditorEvent.LayoutEditorEventType.UNKNOWN_EVENT_TYPE)

    myAnalyticsManager.trackShowIssuePanel()
    assertThat(CommonUsageTracker.NOP_TRACKER.lastTrackedEvent).isEqualTo(LayoutEditorEvent.LayoutEditorEventType.SHOW_LINT_MESSAGES)
  }

  fun testZoomTracking() {
    var type = ZoomType.ACTUAL
    myAnalyticsManager.trackZoom(type)
    assertThat(CommonUsageTracker.NOP_TRACKER.lastTrackedEvent).isEqualTo(LayoutEditorEvent.LayoutEditorEventType.ZOOM_ACTUAL)

    type = ZoomType.IN
    myAnalyticsManager.trackZoom(type)
    assertThat(CommonUsageTracker.NOP_TRACKER.lastTrackedEvent).isEqualTo(LayoutEditorEvent.LayoutEditorEventType.ZOOM_IN)

    type = ZoomType.OUT
    myAnalyticsManager.trackZoom(type)
    assertThat(CommonUsageTracker.NOP_TRACKER.lastTrackedEvent).isEqualTo(LayoutEditorEvent.LayoutEditorEventType.ZOOM_OUT)

    type = ZoomType.FIT
    myAnalyticsManager.trackZoom(type)
    assertThat(CommonUsageTracker.NOP_TRACKER.lastTrackedEvent).isEqualTo(LayoutEditorEvent.LayoutEditorEventType.ZOOM_FIT)

    type = ZoomType.FIT_INTO
    myAnalyticsManager.trackZoom(type)
    assertThat(CommonUsageTracker.NOP_TRACKER.lastTrackedEvent).isEqualTo(LayoutEditorEvent.LayoutEditorEventType.ZOOM_FIT)
  }

  fun testIssuePanelTracking() {
    myAnalyticsManager.trackIssuePanel(true)
    assertThat(CommonUsageTracker.NOP_TRACKER.lastTrackedEvent).isEqualTo(LayoutEditorEvent.LayoutEditorEventType.MINIMIZE_ERROR_PANEL)

    myAnalyticsManager.trackIssuePanel(false)
    assertThat(CommonUsageTracker.NOP_TRACKER.lastTrackedEvent).isEqualTo(LayoutEditorEvent.LayoutEditorEventType.RESTORE_ERROR_PANEL)
  }
}
