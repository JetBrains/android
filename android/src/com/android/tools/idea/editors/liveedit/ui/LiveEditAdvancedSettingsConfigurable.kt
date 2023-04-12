/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.editors.liveedit.ui

import com.android.tools.idea.editors.liveedit.LiveEditAdvancedConfiguration
import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableProvider
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.android.util.AndroidBundle


class LiveEditAdvancedSettingsConfigurable : BoundSearchableConfigurable(
  AndroidBundle.message("live.edit.advanced.settings.configurable.display.name"), "android.live.edit"
), Configurable.NoScroll {
  override fun createPanel(): DialogPanel {
    val liveEditSettings = LiveEditAdvancedConfiguration.getInstance()

    // https://plugins.jetbrains.com/docs/intellij/kotlin-ui-dsl-version-2.html
    return panel {
      row {
        checkBox(AndroidBundle.message("live.edit.configurable.enable.embedded.compiler"))
          .bindSelected(liveEditSettings::useEmbeddedCompiler)
          .comment(AndroidBundle.message("live.edit.configurable.enable.embedded.compiler.comment"))
      }
      row {
        checkBox(AndroidBundle.message("live.edit.configurable.enable.debug.mode"))
          .bindSelected(liveEditSettings::useDebugMode)
          .comment(AndroidBundle.message("live.edit.configurable.enable.debug.mode.comment"))
      }
      row {
        checkBox(AndroidBundle.message("live.edit.configurable.enable.inline.analysis"))
          .bindSelected(liveEditSettings::useInlineAnalysis)
          .comment(AndroidBundle.message("live.edit.configurable.enable.inline.analysis.comment"))
      }
      row {
        checkBox(AndroidBundle.message("live.edit.configurable.enable.partial.recompose"))
          .bindSelected(liveEditSettings::usePartialRecompose)
          .comment(AndroidBundle.message("live.edit.configurable.enable.partial.recompose.comment"))
      }
      row(AndroidBundle.message("live.edit.configurable.refresh.rate")) {
        intTextField(LiveEditAdvancedConfiguration.REFRESH_RATE_RANGE)
          .bindIntText(liveEditSettings::refreshRateMs)
          .columns(4)
        comment(AndroidBundle.message("live.edit.configurable.refresh.rate.comment"))
      }
    }
  }
}

class LiveEditAdvancedSettingsConfigurableProvider: ConfigurableProvider() {
  override fun createConfigurable(): Configurable? = if (StudioFlags.COMPOSE_DEPLOY_LIVE_EDIT_ADVANCED_SETTINGS_MENU.get()) LiveEditAdvancedSettingsConfigurable() else null

  override fun canCreateConfigurable(): Boolean {
    return StudioFlags.COMPOSE_DEPLOY_LIVE_EDIT_ADVANCED_SETTINGS_MENU.get()
  }
}