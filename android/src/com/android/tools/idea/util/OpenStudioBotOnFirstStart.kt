/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.util

import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.flags.StudioFlags
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.StudioBotToolWindowAutoOpenEvent
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service

@Service
class OpenStudioBotOnFirstStart {
  // Saved on the first time. This is to persist the initial value of STUDIO_BOT_FIRST_TIME_SHOWN,
  // because shouldShow is checked multiple times from different places
  val showing = StudioFlags.STUDIOBOT_SHOW_ON_FIRST_OPEN.get() && !PropertiesComponent.getInstance().getBoolean(STUDIO_BOT_FIRST_TIME_SHOWN)

  // Should only automatically show if flag is on, and if it has never been shown before
  fun shouldShowToolWindow(): Boolean {
    if (!StudioFlags.STUDIOBOT_SHOW_ON_FIRST_OPEN.get()) {
      return false
    }

    // Only auto-show once ever
    if (!PropertiesComponent.getInstance().getBoolean(STUDIO_BOT_FIRST_TIME_SHOWN)) {
      PropertiesComponent.getInstance().setValue(STUDIO_BOT_FIRST_TIME_SHOWN, true)

      UsageTracker.log(
        AndroidStudioEvent.newBuilder()
          .setKind(AndroidStudioEvent.EventKind.STUDIO_BOT_TOOL_WINDOW_AUTO_OPEN_EVENT)
          .setStudioBotToolWindowAutoOpenEvent(
            StudioBotToolWindowAutoOpenEvent.newBuilder().setType(StudioBotToolWindowAutoOpenEvent.Type.AUTO_OPEN)
          )
      )
    }
    return showing
  }

  companion object {
    @JvmStatic
    fun shouldShow(): Boolean {
      return getInstance().shouldShowToolWindow()
    }

    private fun getInstance() = ApplicationManager.getApplication().service<OpenStudioBotOnFirstStart>()

    const val STUDIO_BOT_FIRST_TIME_SHOWN = "studio.bot.first.time.shown"
  }
}
