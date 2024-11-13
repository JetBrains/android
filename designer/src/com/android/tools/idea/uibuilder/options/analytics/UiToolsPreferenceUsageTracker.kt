/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.options.analytics

import com.android.tools.analytics.AnalyticsSettings
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.uibuilder.options.analytics.ResourceUsageType.DEFAULT
import com.android.tools.idea.uibuilder.options.analytics.ResourceUsageType.DEFAULT_WITHOUT_LIVE_UPDATES
import com.android.tools.idea.uibuilder.options.analytics.ResourceUsageType.ESSENTIAL
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.UiToolsPreferencesEvent
import com.google.wireless.android.sdk.stats.UiToolsPreferencesEvent.LayoutMode
import com.google.wireless.android.sdk.stats.UiToolsPreferencesEvent.ResourceUsage
import com.google.wireless.android.sdk.stats.UiToolsPreferencesEvent.ViewMode
import com.google.wireless.android.sdk.stats.UiToolsPreferencesEvent.newBuilder
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import org.jetbrains.android.uipreview.AndroidEditorSettings.EditorMode
import org.jetbrains.android.uipreview.AndroidEditorSettings.EditorMode.CODE
import org.jetbrains.android.uipreview.AndroidEditorSettings.EditorMode.DESIGN
import org.jetbrains.android.uipreview.AndroidEditorSettings.EditorMode.SPLIT
import org.jetbrains.android.uipreview.AndroidEditorSettings.LayoutType
import org.jetbrains.android.uipreview.AndroidEditorSettings.LayoutType.GALLERY
import org.jetbrains.android.uipreview.AndroidEditorSettings.LayoutType.GRID

/**
 * Usage tracker to collect all the metrics of the settings panel found in Preferences > Editor > Ui
 * Tools. This tracker tracks settings preferences of [NlOptionsConfigurable].
 */
interface UiToolsPreferenceUsageTracker {

  /**
   * Tracks the default mode the user choose to show editor modes for files or layout mode to show
   * Previews
   *
   * @param resourcesViewMode The default editor mode for resources files.
   * @param kotlinViewMode The default editor mode for Kotlin files.
   * @param shouldAlwaysShowSplitMode Tracks if the user choose to always show split mode on
   *   Previews.
   * @param previewLayoutType The default layout type for Previews.
   * @param trackPadSensitivity The trackpad sensitivity when zooming.
   * @param resourceUsage The user's preferences for essential mode and live updates.
   */
  fun track(
    resourcesViewMode: EditorMode? = null,
    kotlinViewMode: EditorMode? = null,
    shouldAlwaysShowSplitMode: Boolean? = null,
    previewLayoutType: LayoutType? = null,
    trackPadSensitivity: Int? = null,
    resourceUsage: ResourceUsageType? = null,
  ) {
    val builder = newBuilder()
    resourcesViewMode?.let {
      builder.setResourcesViewMode(
        when (it) {
          CODE -> ViewMode.CODE
          SPLIT -> ViewMode.SPLIT
          DESIGN -> ViewMode.DESIGN
        }
      )
    }
    kotlinViewMode?.let {
      builder.setEditorViewMode(
        when (it) {
          CODE -> ViewMode.CODE
          SPLIT -> ViewMode.SPLIT
          DESIGN -> ViewMode.DESIGN
        }
      )
    }
    shouldAlwaysShowSplitMode?.let { builder.setShowSplitModeOnAnnotations(it) }
    previewLayoutType?.let {
      builder.setPreviewLayoutMode(
        when (it) {
          GRID -> LayoutMode.GRID
          GALLERY -> LayoutMode.GALLERY
        }
      )
    }
    trackPadSensitivity?.let { builder.setTrackPadSensitivityLevel(it) }
    resourceUsage?.let {
      builder.setResourceUsage(
        when (it) {
          DEFAULT -> ResourceUsage.DEFAULT
          DEFAULT_WITHOUT_LIVE_UPDATES -> ResourceUsage.DEFAULT_WITHOUT_LIVE_UPDATES
          ESSENTIAL -> ResourceUsage.ESSENTIAL
        }
      )
    }

    logEvent { builder.build() }
  }

  fun logEvent(preferencesEventProvider: () -> UiToolsPreferencesEvent)

  companion object {
    fun getInstance(): UiToolsPreferenceUsageTracker {
      return if (AnalyticsSettings.optedIn) UiToolsPreferenceUsageTrackerImpl
      else UiToolsPreferenceNoOpUsageTracker
    }
  }
}

private object UiToolsPreferenceUsageTrackerImpl : UiToolsPreferenceUsageTracker {
  private val executorService =
    ThreadPoolExecutor(0, 1, 1, TimeUnit.MINUTES, LinkedBlockingQueue(10))

  override fun logEvent(preferencesEventProvider: () -> UiToolsPreferencesEvent) {
    try {
      executorService.execute {
        val uiToolsPreferencesEvent = preferencesEventProvider()
        val studioEvent =
          AndroidStudioEvent.newBuilder()
            .setCategory(AndroidStudioEvent.EventCategory.LAYOUT_EDITOR)
            .setKind(AndroidStudioEvent.EventKind.LAYOUT_EDITOR_EVENT)
            .setUiToolsPreferencesEvent(uiToolsPreferencesEvent)
        UsageTracker.log(studioEvent)
      }
    } catch (ignore: RejectedExecutionException) {}
  }
}

private object UiToolsPreferenceNoOpUsageTracker : UiToolsPreferenceUsageTracker {
  override fun logEvent(preferencesEventProvider: () -> UiToolsPreferencesEvent) = Unit
}

enum class ResourceUsageType {
  DEFAULT,
  DEFAULT_WITHOUT_LIVE_UPDATES,
  ESSENTIAL,
}
