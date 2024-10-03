/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.flags


import com.intellij.openapi.Disposable
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.options.Configurable.Promo
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import icons.StudioIcons
import javax.swing.Icon

class StudioLabsSettingsConfigurable :
  BoundSearchableConfigurable(
    displayName = "Studio Labs",
    helpTopic = "Sign up for new features",
    _id = "studio.settings.StudioLabsConfigurable",
  ),
  Promo,
  Disposable {

  override fun createPanel(): DialogPanel = panel {
    val promptLibraryFlag = StudioFlags.STUDIOBOT_PROMPT_LIBRARY_ENABLED
    row {
      checkBox("Enable Prompt Library")
        .comment("Allows to store frequently used prompts for quick access. " +
                 "Optionally share prompts with other people working on a same project.")
        .enabled(true)
        .bindSelected(
          getter = { promptLibraryFlag.get() },
          setter = { promptLibraryFlag.override(it) }
        )
    }
  }

  override fun getPromoIcon(): Icon {
    return StudioIcons.Shell.Menu.STUDIO_LABS
  }

  override fun dispose() {}
}
