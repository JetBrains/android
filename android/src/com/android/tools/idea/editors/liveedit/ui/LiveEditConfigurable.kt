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

import com.android.tools.idea.editors.liveedit.LiveEditAnActionListener
import com.android.tools.idea.editors.liveedit.LiveEditApplicationConfiguration
import com.android.tools.idea.editors.liveedit.LiveEditApplicationConfiguration.LiveEditMode.DISABLED
import com.android.tools.idea.editors.liveedit.LiveEditApplicationConfiguration.LiveEditMode.LIVE_EDIT
import com.android.tools.idea.editors.liveedit.LiveEditApplicationConfiguration.LiveEditMode.LIVE_LITERALS
import com.android.tools.idea.editors.liveedit.LiveEditService.Companion.LiveEditTriggerMode.AUTOMATIC
import com.android.tools.idea.editors.liveedit.LiveEditService.Companion.LiveEditTriggerMode.ON_HOTKEY
import com.android.tools.idea.editors.liveedit.LiveEditService.Companion.LiveEditTriggerMode.ON_SAVE
import com.intellij.ide.ActivityTracker
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableProvider
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.bind
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected
import org.jetbrains.android.util.AndroidBundle.message

class LiveEditConfigurable : BoundSearchableConfigurable(
  message("live.edit.configurable.display.name"), "Configure Android Live Edit", ID
), Configurable.NoScroll {
  companion object {
    const val ID = "live.edit.configurable"
  }

  override fun createPanel(): DialogPanel {
    val config = LiveEditApplicationConfiguration.getInstance()
    val shortcut = ActionManager.getInstance().getAction(MANUAL_LIVE_EDIT_ACTION_ID)
      .shortcutSet.shortcuts.firstOrNull()?.let { KeymapUtil.getShortcutText(it) } ?: ""

    // https://plugins.jetbrains.com/docs/intellij/kotlin-ui-dsl-version-2.html
    return panel {
      buttonsGroup {
        row {
          radioButton(
            message("live.literals.configurable.select.live.literals"),
            LIVE_LITERALS
          ).comment(message("live.literals.configurable.select.live.literals.comment"))
        }.visible(false)

        lateinit var rb : Cell<JBRadioButton>
        row {
          rb = radioButton(
            message("live.edit.configurable.display.name"),
            LIVE_EDIT
          ).comment(message("live.edit.configurable.display.name.comment"))
        }
        row { // Add a row to indent
          this@buttonsGroup.buttonsGroup(indent = true) {
            row {
              radioButton(
                message("live.edit.mode.automatic"),
                AUTOMATIC
              ).enabledIf(rb.selected)
            }
            row {
              radioButton(
                message("live.edit.mode.manual.onkey", shortcut),
                ON_HOTKEY
              ).enabledIf(rb.selected)
            }
            row {
              radioButton(
                message("live.edit.mode.manual.onsave", LiveEditAnActionListener.getLiveEditTriggerShortCutString()),
                ON_SAVE
              ).enabledIf(rb.selected)
            }
          }.bind(config::leTriggerMode)
        }

        row {
          radioButton(
            message("live.edit.disable.all"),
            DISABLED
          ).comment(message("live.edit.disable.all.description"))
        }
      }.bind(config::mode)
    }
  }

  override fun apply() {
    super.apply()
    ActivityTracker.getInstance().inc()
  }
}

class LiveEditConfigurableProvider: ConfigurableProvider() {
  override fun createConfigurable(): Configurable = LiveEditConfigurable()
}