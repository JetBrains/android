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

import com.android.tools.idea.editors.liveedit.LiveEditConfig
import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableProvider
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.layout.panel
import org.jetbrains.android.util.AndroidBundle


class LiveEditConfigurable : BoundSearchableConfigurable(
  AndroidBundle.message("live.edit.configurable.display.name"), "android.live.edit"
), Configurable.NoScroll {
  override fun createPanel(): DialogPanel {
    val liveEditSettings = LiveEditConfig.getInstance()

    // http://www.jetbrains.org/intellij/sdk/docs/user_interface_components/kotlin_ui_dsl.html
    return panel {
        row {
          checkBox(
            AndroidBundle.message("live.edit.configurable.enable.embedded.compiler"),
            liveEditSettings::useEmbeddedCompiler,
            AndroidBundle.message("live.edit.configurable.enable.embedded.compiler.comment")
          )
        }
    }
  }
}

class LiveEditConfigurableProvider: ConfigurableProvider() {
  override fun createConfigurable(): Configurable? = if (StudioFlags.COMPOSE_DEPLOY_LIVE_EDIT.get())
    LiveEditConfigurable()
  else
    null

  override fun canCreateConfigurable(): Boolean = StudioFlags.COMPOSE_DEPLOY_LIVE_EDIT.get()
}