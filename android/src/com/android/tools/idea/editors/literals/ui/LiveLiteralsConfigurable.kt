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

import com.android.tools.idea.editors.literals.FasterPreviewApplicationConfiguration
import com.android.tools.idea.editors.literals.LiveLiteralsApplicationConfiguration
import com.android.tools.idea.editors.literals.LiveLiteralsService
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.rendering.classloading.ProjectConstantRemapper
import com.intellij.ide.ActivityTracker
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableProvider
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.layout.panel
import org.jetbrains.android.uipreview.ModuleClassLoaderOverlays
import org.jetbrains.android.util.AndroidBundle.message
import org.jetbrains.kotlin.idea.util.projectStructure.allModules

class LiveLiteralsConfigurable : BoundSearchableConfigurable(
  message("live.literals.configurable.display.name"), "android.live.literals"
), Configurable.NoScroll {
  override fun createPanel(): DialogPanel {
    val literalsSettings = LiveLiteralsApplicationConfiguration.getInstance()
    val fasterPreviewSettings = FasterPreviewApplicationConfiguration.getInstance()

    return panel {
      if (StudioFlags.COMPOSE_LIVE_LITERALS.get()) {
        row {
          checkBox(
            message("live.literals.configurable.enable.live.literals"),
            literalsSettings::isEnabled,
            message("live.literals.configurable.enable.live.literals.comment")
          )
        }
      }

      if (StudioFlags.COMPOSE_LIVE_EDIT_PREVIEW.get()) {
        row {
          checkBox(
            message("faster.preview.configurable.enable"),
            fasterPreviewSettings::isEnabled,
            message("faster.preview.configurable.enable.comment")
          )
        }
      }
    }
  }

  override fun apply() {
    super.apply()

    if (!LiveLiteralsApplicationConfiguration.getInstance().isEnabled) {
      // Make sure we disable all the live literals services
      ProjectManager.getInstance().openProjects
        .forEach {
          LiveLiteralsService.getInstance(it).onAvailabilityChange()
          ProjectConstantRemapper.getInstance(it).clearConstants(null)
        }
    }
    if (!FasterPreviewApplicationConfiguration.getInstance().isEnabled) {
      ProjectManager.getInstance().openProjects
        .flatMap { it.allModules() }
        .forEach {
          ModuleClassLoaderOverlays.getInstance(it).overlayPath = null
        }
    }
    ActivityTracker.getInstance().inc()
  }
}

class LiveLiteralsConfigurableProvider: ConfigurableProvider() {
  override fun createConfigurable(): Configurable? = if (canCreateConfigurable())
    LiveLiteralsConfigurable()
  else
    null

  override fun canCreateConfigurable(): Boolean = StudioFlags.COMPOSE_LIVE_LITERALS.get() || StudioFlags.COMPOSE_LIVE_EDIT_PREVIEW.get()
}