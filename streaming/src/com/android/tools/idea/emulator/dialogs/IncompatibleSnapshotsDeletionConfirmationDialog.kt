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
package com.android.tools.idea.emulator.dialogs

import com.android.tools.adtui.util.getHumanizedSize
import com.android.tools.idea.emulator.CloseDialogAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.dialog
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import java.awt.Component

/**
 * Dialog asking for confirmation before deletion of incompatible emulator snapshots.
 */
internal class IncompatibleSnapshotsDeletionConfirmationDialog(
  private val incompatibleSnapshotsCount: Int,
  private val incompatibleSnapshotsSize: Long,
) {

  var doNotAskAgain: Boolean = false

  /**
   * Creates contents of the dialog.
   */
  private fun createPanel(): DialogPanel {
    return panel {
      row {
        val snapshotsClause = if (incompatibleSnapshotsCount == 1) "There is 1 snapshot"
                              else "There are $incompatibleSnapshotsCount snapshots"
        val pronoun = if (incompatibleSnapshotsCount == 1) "it" else "them"
        label("""$snapshotsClause incompatible with the current configuration occupying
|                ${getHumanizedSize(incompatibleSnapshotsSize)} of disk space. Do you want to permanently delete $pronoun?
|             """.trimMargin())
      }
      row {
        checkBox("Do this from now on without asking")
          .bindSelected(::doNotAskAgain)
      }
    }
  }

  /**
   * Creates the dialog wrapper.
   */
  fun createWrapper(project: Project? = null, parent: Component? = null): DialogWrapper {
    val dialogPanel = createPanel()
    return dialog(
      title = "Incompatible Snapshots Detected",
      resizable = true,
      panel = dialogPanel,
      project = project,
      parent = parent,
      createActions = {
        listOf(
          CloseDialogAction(dialogPanel, "Delete", DELETE_EXIT_CODE, isDefault = true),
          CloseDialogAction(dialogPanel, "Keep", KEEP_EXIT_CODE)
        )
      })
  }

  companion object {
    const val DELETE_EXIT_CODE = DialogWrapper.OK_EXIT_CODE
    const val KEEP_EXIT_CODE = DialogWrapper.NEXT_USER_EXIT_CODE
  }
}
