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
package com.android.tools.idea.emulator

import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import java.awt.Component
import java.awt.event.ActionEvent
import javax.swing.AbstractAction

/**
 * Custom action that closes the dialog with the given exit code. Intended to be used in the context
 * of the `createActions` argument when calling the [com.intellij.ui.components.dialog] function.
 */
internal class CloseDialogAction(
  private val dialogPanel: DialogPanel,
  name: String,
  private val exitCode: Int,
  isDefault: Boolean = false
) : AbstractAction(name) {

  init {
    if (isDefault) {
      putValue(DialogWrapper.DEFAULT_ACTION, true)
    }
  }

  override fun actionPerformed(event: ActionEvent) {
    val wrapper = DialogWrapper.findInstance(event.source as? Component)
    dialogPanel.apply()
    wrapper?.close(exitCode)
  }
}
