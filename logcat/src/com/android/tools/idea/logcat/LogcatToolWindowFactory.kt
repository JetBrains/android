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
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.logcat.filters.LogcatFilterColorSettingsPage
import com.android.tools.idea.logcat.messages.LogcatColors
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.options.colors.ColorSettingsPages
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.text.UniqueNameGenerator
import org.jetbrains.annotations.VisibleForTesting

internal class LogcatToolWindowFactory : SplittingTabsToolWindowFactory(), DumbAware {
  init {
    if (isLogcatV2Enabled()) {
      ColorSettingsPages.getInstance().registerPage(LogcatFilterColorSettingsPage())
    }
  }

  private val logcatColors: LogcatColors = LogcatColors()

  override fun shouldBeAvailable(project: Project) = isLogcatV2Enabled()

  override fun generateTabName(tabNames: Set<String>) =
    UniqueNameGenerator.generateUniqueName("Logcat", "", "", " (", ")") { !tabNames.contains(it) }

  override fun createChildComponent(project: Project, popupActionGroup: ActionGroup, clientState: String?) =
    LogcatMainPanel(project, popupActionGroup, logcatColors, LogcatPanelConfig.fromJson(clientState)).also {
      logcatPresenters.add(it)
      Disposer.register(it) { logcatPresenters.remove(it) }
    }

  companion object {
    @VisibleForTesting
    internal val logcatPresenters = mutableListOf<LogcatPresenter>()
  }

}

private fun isLogcatV2Enabled() = StudioFlags.LOGCAT_V2_ENABLE.get()
