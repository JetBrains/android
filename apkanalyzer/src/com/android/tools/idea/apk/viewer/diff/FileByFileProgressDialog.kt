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
package com.android.tools.idea.apk.viewer.diff

import com.android.tools.adtui.model.formatter.NumberFormatter.formatFileSize
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.openapi.ui.DialogWrapper.CANCEL_EXIT_CODE
import com.intellij.openapi.ui.DialogWrapper.OK_EXIT_CODE
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.Dimension
import java.awt.EventQueue.invokeLater
import javax.swing.JLabel
import javax.swing.JProgressBar

/**
 * A dialog that displays a progress bar when file-by-file analysis is being generated
 *
 * @param onCancel A cancel [Runnable] to execute if the `Cancel` is clicked. We use a Runnable
 *   rather than a Kotlin `() -> Unit` lambda because we need to be callable from Java.
 */
internal class FileByFileProgressDialog {
  private val status = JLabel("Initializing...")
  private val progressBar = JProgressBar().apply { isIndeterminate = true }

  private val panel =
    BorderLayoutPanel().apply {
      minimumSize = Dimension(400, 50)
      addToCenter(status)
      addToBottom(progressBar)
    }

  private val dialogBuilder =
    DialogBuilder().apply {
      setTitle("Analyzing Diffs")
      setCenterPanel(panel)
      addCancelAction()
    }

  fun showDialog(onCancel: Runnable) {
    with(dialogBuilder) {
      setCancelOperation {
        onCancel.run()
        dialogWrapper.close(CANCEL_EXIT_CODE)
      }
      show()
    }
  }

  fun closeDialog() {
    invokeLater { dialogBuilder.dialogWrapper.close(OK_EXIT_CODE) }
  }

  fun onUpdate(count: Int, size: Long) {
    invokeLater { status.text = "Processed ${formatFileSize(size)} bytes in $count chunks" }
  }
}
