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
package com.android.tools.idea.common.assistant

import com.android.tools.analytics.UsageTracker.log
import com.google.common.annotations.VisibleForTesting
import com.google.common.base.Stopwatch
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.DesignEditorHelpPanelEvent
import com.google.wireless.android.sdk.stats.DesignEditorHelpPanelEvent.HelpPanelAction
import com.google.wireless.android.sdk.stats.DesignEditorHelpPanelEvent.HelpPanelType

open class AssistantPanelMetricsTracker(private val type: HelpPanelType) {
  @VisibleForTesting val timer: Stopwatch = Stopwatch.createUnstarted()

  fun logOpen() {
    if (timer.isRunning) {
      timer.stop().reset()
    }

    val event = createEventBuilder(type, HelpPanelAction.OPEN)
    timer.start()
    logEvent(event)
  }

  fun logClose() {
    if (timer.isRunning) {
      timer.stop()
    }

    val event = createEventBuilder(type, HelpPanelAction.CLOSE)
    event.timeToCloseMs = timer.elapsed().toMillis()
    logEvent(event)
  }

  fun logButtonClicked() {
    val event = createEventBuilder(type, HelpPanelAction.BUTTON_CLICKED)
    logEvent(event)
  }

  fun logReachedEnd() {
    val event = createEventBuilder(type, HelpPanelAction.REACHED_END)
    logEvent(event)
  }

  @VisibleForTesting
  open fun logEvent(event: DesignEditorHelpPanelEvent.Builder) {
    log(
      AndroidStudioEvent.newBuilder()
        .setKind(AndroidStudioEvent.EventKind.DESIGN_EDITOR_HELP_PANEL_EVENT)
        .setDesignEditorHelpPanelEvent(event)
    )
  }

  private fun createEventBuilder(
    type: HelpPanelType,
    action: HelpPanelAction,
  ): DesignEditorHelpPanelEvent.Builder {
    val event: DesignEditorHelpPanelEvent.Builder = DesignEditorHelpPanelEvent.newBuilder()
    event.helpPanelType = type
    event.action = action
    return event
  }
}
