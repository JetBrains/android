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
import com.intellij.ui.UIBundle
import com.intellij.ui.components.JBTextField
import com.intellij.ui.layout.Cell
import com.intellij.ui.layout.CellBuilder
import com.intellij.ui.layout.panel
import org.jetbrains.android.util.AndroidBundle
import kotlin.reflect.KMutableProperty0


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
        row {
          checkBox(
            AndroidBundle.message("live.edit.configurable.enable.debug.mode"),
            liveEditSettings::useDebugMode,
            AndroidBundle.message("live.edit.configurable.enable.debug.mode.comment")
          )
        }
        row(AndroidBundle.message("live.edit.configurable.refresh.rate")) {
          cell {
            // Workaround for bug https://youtrack.jetbrains.com/issue/IDEA-287095
            // Delete this line and uncomment next once fixed.
            createIntTextField(this, liveEditSettings::refreshRateMs, 4, LiveEditConfig.REFRESH_RATE_RANGE)
            //intTextField(liveEditSettings::refreshRateMs, 4, LiveEditConfig.REFRESH_RATE_RANGE)
            commentNoWrap(AndroidBundle.message("live.edit.configurable.refresh.rate.comment"))
          }
        }
    }
  }
}

// Delete this method once bug IDEA-287095 is fixed
fun createIntTextField(cell: Cell, binding :KMutableProperty0<Int>, columns: Int? = null, range: IntRange? = null): CellBuilder<JBTextField> {
  return cell.textField(
    { binding.get().toString() },
    { value -> value.toIntOrNull()?.let { intValue -> binding.set(range?.let { intValue.coerceIn(it.first, it.last) } ?: intValue) } },
    columns
  ).withValidationOnInput {
    val value = it.text.toIntOrNull()
    when {
      value == null -> error(UIBundle.message("please.enter.a.number"))
      range != null && value !in range -> error(UIBundle.message("please.enter.a.number.from.0.to.1", range.first, range.last))
      else -> null
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