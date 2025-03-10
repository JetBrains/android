/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.backup

import com.android.tools.idea.backup.DialogFactory.DialogButton
import com.intellij.CommonBundle
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class DialogFactoryImpl : DialogFactory {
  override suspend fun showDialog(
    project: Project,
    title: String,
    message: String,
    buttons: List<DialogButton>,
  ) {
    withContext(Dispatchers.EDT) {
      val buttonTexts = buttons.map { it.text }
      val actionMap = buttons.associateBy { it.text }
      @Suppress("UnstableApiUsage")
      val button =
        MessageDialogBuilder.Message(title, message)
          .buttons(*(buttonTexts + CommonBundle.getOkButtonText()).toTypedArray())
          .defaultButton("OK")
          .asWarning()
          .show(project)
      if (button != null) {
        actionMap[button]?.onClick?.invoke()
      }
    }
  }
}
