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
package com.android.tools.idea.editors.literals.ui

import com.android.tools.idea.editors.literals.LiveLiteralsApplicationConfiguration
import com.android.tools.idea.editors.literals.internal.LiveLiteralsDeploymentReportService
import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableProvider
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.layout.panel
import org.jetbrains.android.util.AndroidBundle.message

class LiveLiteralsConfigurable : BoundSearchableConfigurable(
  message("live.literals.configurable.display.name"), "android.live.literals"
), Configurable.NoScroll {
  override fun createPanel(): DialogPanel {
    val literalsSettings = LiveLiteralsApplicationConfiguration.getInstance()

    return panel {
      row {
        checkBox(
          message("live.literals.configurable.enable.live.literals"),
          literalsSettings::isEnabled,
          message("live.literals.configurable.enable.live.literals.comment")
        )
      }
    }
  }

  override fun apply() {
    super.apply()

    if (!LiveLiteralsApplicationConfiguration.getInstance().isEnabled) {
      // Make sure we disable all the live literals services
      ProjectManager.getInstance().openProjects
        .map { LiveLiteralsDeploymentReportService.getInstance(it) }
        .forEach { it.stopAllMonitors() }
    }
    LiveLiteralsAvailableIndicatorFactory.updateAllWidgets()
  }
}

class LiveLiteralsConfigurableProvider: ConfigurableProvider() {
  override fun createConfigurable(): Configurable? = if (StudioFlags.COMPOSE_LIVE_LITERALS.get())
    LiveLiteralsConfigurable()
  else
    null

  override fun canCreateConfigurable(): Boolean = StudioFlags.COMPOSE_LIVE_LITERALS.get()
}