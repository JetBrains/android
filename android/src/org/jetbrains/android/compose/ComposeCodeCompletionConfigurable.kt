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
package org.jetbrains.android.compose

import com.intellij.application.options.editor.CheckboxDescriptor
import com.intellij.application.options.editor.checkBox
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.layout.PropertyBinding
import com.intellij.ui.layout.panel
import org.jetbrains.android.uipreview.AndroidEditorSettings
import org.jetbrains.android.util.AndroidBundle

/**
 * Provides additional options in Settings | Editor | Code Completion section.
 *
 * Contains a checkbox that allows enable/disable [AndroidComposeInsertHandler].
 */
class ComposeCodeCompletionConfigurable : BoundConfigurable("Compose") {
  private val editor = AndroidEditorSettings.getInstance()

  private val checkboxDescriptor = CheckboxDescriptor(
    AndroidBundle.message("compose.enable.insertion.handler"),
    PropertyBinding(editor.globalState::isComposeInsertHandlerEnabled, editor.globalState::setComposeInsertHandlerEnabled)
  )

  override fun createPanel(): DialogPanel {
    return panel {
      row {
        titledRow("Compose") {
          row { checkBox(checkboxDescriptor) }
        }
      }
    }
  }
}
