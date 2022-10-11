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

import com.android.tools.idea.editors.literals.LiveLiteralsService
import com.android.tools.idea.editors.liveedit.LiveEditApplicationConfiguration
import com.android.tools.idea.editors.liveedit.LiveEditApplicationConfiguration.LiveEditMode.DISABLED
import com.android.tools.idea.editors.liveedit.LiveEditApplicationConfiguration.LiveEditMode.LIVE_EDIT
import com.android.tools.idea.editors.liveedit.LiveEditApplicationConfiguration.LiveEditMode.LIVE_LITERALS
import com.android.tools.idea.editors.literals.LiveEditService.Companion.LiveEditTriggerMode.LE_TRIGGER_MANUAL
import com.android.tools.idea.editors.literals.LiveEditService.Companion.LiveEditTriggerMode.LE_TRIGGER_AUTOMATIC
import com.android.tools.idea.editors.literals.ManualLiveEditTrigger
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.rendering.classloading.ProjectConstantRemapper
import com.intellij.ide.ActivityTracker
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableProvider
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.layout.CellBuilder
import com.intellij.ui.layout.panel
import org.jetbrains.android.util.AndroidBundle.message
import javax.swing.JRadioButton

class LiveEditConfigurable : BoundSearchableConfigurable(
  message("live.edit.configurable.display.name"), "android.live.edit"
), Configurable.NoScroll {
  override fun createPanel(): DialogPanel {
    val config = LiveEditApplicationConfiguration.getInstance()

    return panel {
      buttonGroup {
        row {
          radioButton(
            message("live.literals.configurable.select.live.literals"),
            { config.mode == LIVE_LITERALS },
            { enabled -> if (enabled) config.mode = LIVE_LITERALS },
            message("live.literals.configurable.select.live.literals.comment")
          )
        }

        if (StudioFlags.COMPOSE_DEPLOY_LIVE_EDIT.get()) {
            var rb : CellBuilder<JRadioButton>? = null
            row {
              rb = radioButton(
                message("live.edit.configurable.display.name"),
                { config.mode == LIVE_EDIT },
                { enabled -> if (enabled) config.mode = LIVE_EDIT },
                message("live.edit.configurable.display.name.comment")
              )
            }
            row { // Add a row to indent
              buttonGroup {
                row {
                  radioButton(
                    message("live.edit.mode.manual", ManualLiveEditTrigger.getShortCutString()),
                    { config.leTriggerMode == LE_TRIGGER_MANUAL },
                    { enabled ->
                      if (enabled) {
                      config.leTriggerMode = LE_TRIGGER_MANUAL
                    }},
                  )
                }
                row {
                  radioButton(
                    message("live.edit.mode.automatic"),
                    { config.leTriggerMode == LE_TRIGGER_AUTOMATIC },
                    { enabled ->
                      if (enabled) {
                        config.leTriggerMode = LE_TRIGGER_AUTOMATIC
                      }
                    },
                  )
                }
              }
            }

        }

        row {
          radioButton(
            message("live.edit.disable.all"),
            { config.mode == DISABLED },
            { enabled -> if (enabled) config.mode = DISABLED },
            message("live.edit.disable.all.description")
          )
        }
      }
    }
  }

  override fun apply() {
    super.apply()

    if (!LiveEditApplicationConfiguration.getInstance().isLiveLiterals) {
      // Make sure we disable all the live literals services
      ProjectManager.getInstance().openProjects
        .forEach {
          LiveLiteralsService.getInstance(it).onAvailabilityChange()
          ProjectConstantRemapper.getInstance(it).clearConstants(null)
        }
    }

    ActivityTracker.getInstance().inc()
  }
}

class LiveEditConfigurableProvider: ConfigurableProvider() {
  override fun createConfigurable(): Configurable = LiveEditConfigurable()
}