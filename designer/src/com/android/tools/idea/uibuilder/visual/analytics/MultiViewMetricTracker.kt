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
package com.android.tools.idea.uibuilder.visual.analytics

import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.uibuilder.visual.ConfigurationSet
import com.android.tools.idea.uibuilder.visual.UserDefinedCustom
import com.google.wireless.android.sdk.stats.MultiViewEvent
import com.google.wireless.android.sdk.stats.MultiViewEvent.MultiViewEventType

fun trackOpenConfigSet(surface: DesignSurface<*>, configSet: ConfigurationSet) {
  when (configSet) {
    ConfigurationSet.PixelDevices -> trackSetCategory(surface, MultiViewEventType.OPEN_PIXEL_DEVICES)
    ConfigurationSet.WearDevices -> trackSetCategory(surface, MultiViewEventType.OPEN_WEAR_DEVICES)
    ConfigurationSet.ProjectLocal -> trackSetCategory(surface, MultiViewEventType.OPEN_PROJECT_LOCALES)
    ConfigurationSet.ColorBlindMode -> trackSetCategory(surface, MultiViewEventType.OPEN_COLOR_BLIND_MODE)
    ConfigurationSet.LargeFont -> trackSetCategory(surface, MultiViewEventType.OPEN_LARGE_FONT)
    ConfigurationSet.WindowSizeDevices -> trackSetCategory(surface, MultiViewEventType.OPEN_REFERENCE_DEVICES)
    is UserDefinedCustom -> trackSetCategory(surface, MultiViewEventType.OPEN_USER_DEFINED_CUSTOM_SETS)
    else -> trackSetCategory(surface, MultiViewEventType.UNKNOWN_EVENT_TYPE)
  }
}

fun trackLayoutValidationToggleIssuePanel(surface: DesignSurface<*>, visibility: Boolean) {
  val event = if (visibility) MultiViewEvent.ToggleIssuePanel.OPEN_ISSUE_PANEL
  else MultiViewEvent.ToggleIssuePanel.CLOSE_ISSUE_PANEL
  InternalMultiViewMetricTrackerFactory.getInstance(surface).trackToggleIssuePanel(event)
}

private fun trackSetCategory(surface: DesignSurface<*>, eventType: MultiViewEventType) {
  InternalMultiViewMetricTrackerFactory.getInstance(surface).trackSetCategory(eventType)
}
