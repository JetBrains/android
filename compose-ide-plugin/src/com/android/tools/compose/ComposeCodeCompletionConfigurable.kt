/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.compose

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel

/**
 * Provides additional options in Settings | Editor | Code Completion section.
 *
 * Contains a checkbox that allows enable/disable [ComposeInsertHandler].
 */
class ComposeCodeCompletionConfigurable : BoundConfigurable("Compose") {
  private val settings = ComposeSettings.getInstance()

  override fun createPanel(): DialogPanel {
    return panel {
      group("Compose") {
        row {
          checkBox(ComposeBundle.message("compose.enable.insertion.handler"))
            .bindSelected(settings.state::isComposeInsertHandlerEnabled)
        }
      }
    }
  }
}
