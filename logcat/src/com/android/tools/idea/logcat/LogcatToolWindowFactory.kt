/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.logcat

import com.android.tools.adtui.toolwindow.splittingtabs.SplittingTabsToolWindowFactory
import com.android.tools.adtui.toolwindow.splittingtabs.state.SplittingTabsStateProvider
import com.android.tools.idea.flags.StudioFlags
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.util.text.UniqueNameGenerator
import javax.swing.JComponent
import javax.swing.JLabel

internal class LogcatToolWindowFactory : SplittingTabsToolWindowFactory(), DumbAware {
  override fun shouldBeAvailable(project: Project): Boolean = StudioFlags.LOGCAT_V2_ENABLE.get()

  // During development of the base class SplittingTabsToolWindowFactory, having a fake tab name helps verify things work.
  override fun generateTabName(tabNames: Set<String>) =
    UniqueNameGenerator.generateUniqueName("Logcat", "", "", " (", ")") { !tabNames.contains(it) }

  // During development of the base class SplittingTabsToolWindowFactory, having a fake component helps verify things work.
  override fun generateChildComponent(clientState: String?): JComponent =
    LogcatPanel(LogcatPanelConfig.fromJson(clientState))

  private class LogcatPanel(state: LogcatPanelConfig?) : JLabel(), SplittingTabsStateProvider {

    init {
      text = state?.text ?: "Child ${++count}"
    }

    override fun getState(): String = LogcatPanelConfig.toJson(LogcatPanelConfig(text))

    companion object {
      var count: Int = 0
    }
  }

  data class LogcatPanelConfig(var text: String = "") {
    companion object {
      private val gson = Gson()

      /**
       * Decodes a JSON string into a [LogcatPanelConfig].
       */
      fun fromJson(json: String?): LogcatPanelConfig? {
        return try {
          gson.fromJson(json, LogcatPanelConfig::class.java)
        }
        catch (e: JsonSyntaxException) {
          Logger.getInstance(LogcatPanelConfig::class.java).warn("Invalid state", e)
          null
        }
      }

      /**
       * Encodes a [LogcatPanelConfig] into a JSON string.
       *
       * We replace all double quotes with single quotes because the XML serializer will replace double quotes with `@quot;` while single
       * quotes seem to be fine. This makes the JSON string more human readable.
       *
       * GSON can handle single quoted JSON strings.
       */
      fun toJson(config: LogcatPanelConfig): String = gson.toJson(config).replace(Regex("(?<!\\\\)\""), "'")
    }
  }
}