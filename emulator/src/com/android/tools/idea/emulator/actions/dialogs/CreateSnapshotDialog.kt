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
package com.android.tools.idea.emulator.actions.dialogs

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.dialog
import com.intellij.ui.layout.applyToComponent
import com.intellij.ui.layout.panel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dialog for specifying snapshot name and use at boot time.
 */
internal class CreateSnapshotDialog {

  var snapshotName: String = getDefaultSnapshotName()
    private set
  var useToBoot: Boolean = false
    private set

  /**
   * Creates contents of the dialog.
   */
  private fun createPanel(): DialogPanel {
    return panel {
      row {
        label("Snapshot Name:")
      }
      row {
        textField(::snapshotName)
          .applyToComponent { growX }
          .focused()
          .withValidationOnInput { validateSnapshotName() }
      }
      row {
        checkBox("Boot from this snapshot", ::useToBoot,
                 comment = "The virtual device will boot from the saved snapshot until changed in boot settings")
      }
    }
  }

  /**
   * Creates the dialog wrapper.
   */
  fun createWrapper(project: Project): DialogWrapper {
    return dialog(
        title = "Boot Options",
        resizable = true,
        panel = createPanel(),
        project = project)
  }

  private fun validateSnapshotName(): ValidationInfo? {
    val name = snapshotName.trim()
    // Use Windows filename rules because they are stricter than Linux ones.
    if (name.contains(Regex("""[<>:"/\\|?*]""")) || name.endsWith('.') || name.isEmpty()) {
      return ValidationInfo("Invalid snapshot name")
    }
    return null
  }

  private fun getDefaultSnapshotName(): String {
    val suffix = TIMESTAMP_FORMAT.format(Date())
    return "snap_${suffix}"
  }
}

private val TIMESTAMP_FORMAT = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
