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
package com.android.tools.idea.logcat.messages

import com.android.tools.idea.logcat.LogcatBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.dialog

/**
 * A variant of [LogcatFormatDialogBase] that sets the options for a specific FormattingOptions object.
 */
internal class LogcatFormatDialog(
  private val project: Project,
  private val formattingOptions: FormattingOptions,
  apply: ApplyAction,
) : LogcatFormatDialogBase(project, apply) {

  override fun createDialogWrapper(): DialogWrapper =
    dialog(
      project = project,
      title = LogcatBundle.message("logcat.header.options.title"),
      resizable = true,
      modality = DialogWrapper.IdeModalityType.PROJECT,
      panel = createPanel(formattingOptions),
      ok = {
        onApply(isApplyButton = false)
        emptyList()
      }
    )
}
