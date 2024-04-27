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
package com.android.tools.idea.modes.essentials

import com.android.tools.idea.flags.StudioFlags
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.options.ex.Settings
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.android.util.AndroidBundle

class EssentialsModeSettingsPage : BoundConfigurable(
  AndroidBundle.message("configurable.EssentialsModeSettingsPage.displayName")), SearchableConfigurable {
  override fun createPanel(): DialogPanel {
    return panel {
      enabled(StudioFlags.ESSENTIALS_MODE_VISIBLE.get())
      group(AndroidBundle.message("configurable.EssentialsModeSettingsPage.displayName")) {
        row {
          checkBox("Enable Essentials Mode")
            .bindSelected({ EssentialsMode.isEnabled() }, { EssentialsMode.setEnabled(it, null) })
          rowComment("Switching to Essentials Mode may improve IDE performance. " +
                     "Essentials mode turns on Essential Highlighting which waits to perform code highlighting " +
                     "and analysis until receiving a file saving action. File saving actions can " +
                     "occur explicitly by invoking it via ${KeymapUtil.getShortcutText("SaveAll")} or from the menu by " +
                     "navigating to File \u2192 Save All. " +
                     "File saving actions can also occur implicitly on occasion for example, when the IDE loses focus")
        }
      }
      group("Related Features") {
        row {
          text("In addition to Essential Highlighting, enabling Essentials Mode also enables a number of related features.  " +
               "Their state can be controlled using the links below.")
        }
        row {
          text("<a>Compose Preview resource usage</a>") {
            DataManager.getInstance().dataContextFromFocusAsync.onSuccess { context: DataContext? ->
              if (context == null) return@onSuccess
              val settings = Settings.KEY.getData(context) ?: return@onSuccess
              settings.select(settings.find("nele.options"))
            }
          }
        }
      }
    }
  }

  override fun getId(): String = "essentials.mode.settings"
}
