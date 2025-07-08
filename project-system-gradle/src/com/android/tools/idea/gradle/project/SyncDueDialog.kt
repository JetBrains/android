/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.gradle.project

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.DialogWrapper.CANCEL_EXIT_CODE
import com.intellij.openapi.ui.DialogWrapper.NEXT_USER_EXIT_CODE
import com.intellij.ui.components.JBOptionButton
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import org.jetbrains.android.util.AndroidBundle
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

enum class SyncDueDialogSelection {
  SyncOnce,
  SyncAlways,
  Close,
  CloseAndSnoozeTodayForAllProjects,
  CloseAndSnoozeIndefinitelyForProject;

  /**
   * Use this instead of [ordinal] to avoid collision with default exit codes of dialog.
   */
  val dialogExitCode: Int
    get() = NEXT_USER_EXIT_CODE + ordinal

  companion object {
    /**
     * Interprets exit code of dialog
     */
    fun of(dialog: SyncDueDialog): SyncDueDialogSelection {
      return if (dialog.exitCode == CANCEL_EXIT_CODE) {
        Close
      } else {
        entries.first { it.dialogExitCode == dialog.exitCode }
      }
    }
  }
}

class SyncDueDialog : DialogWrapper(true) {

  init {
    title = AndroidBundle.message("gradle.settings.autoSync.dialog.title")
    isModal = true
    isResizable = false
    init()
  }

  override fun createCenterPanel(): JComponent {
    return panel {
      row {
        icon(AllIcons.General.WarningDialog)
        text(AndroidBundle.message("gradle.settings.autoSync.dialog.message", ApplicationNamesInfo.getInstance().fullProductName))
      }
    }
  }

  override fun createSouthPanel(): JComponent {
    val syncAutomaticallyAction = object : DialogWrapperAction(AndroidBundle.message("gradle.settings.autoSync.dialog.enable")) {
      override fun doAction(e: ActionEvent?) {
        close(SyncDueDialogSelection.SyncAlways.dialogExitCode)
      }
    }

    val syncOnceAction = object : DialogWrapperAction(AndroidBundle.message("gradle.settings.autoSync.dialog.sync")) {
      override fun doAction(e: ActionEvent?) {
        close(SyncDueDialogSelection.SyncOnce.dialogExitCode)
      }
    }

    val closeAction = object : DialogWrapperAction(AndroidBundle.message("gradle.settings.autoSync.dialog.continue")) {
      init {
        putValue(DEFAULT_ACTION, true)
      }

      override fun doAction(e: ActionEvent?) {
        close(SyncDueDialogSelection.Close.dialogExitCode)
      }
    }

    val snoozeTomorrowAction = object : AbstractAction(AndroidBundle.message("gradle.settings.autoSync.dialog.snooze")) {
      override fun actionPerformed(e: ActionEvent?) {
        close(SyncDueDialogSelection.CloseAndSnoozeTodayForAllProjects.dialogExitCode)
      }
    }

    val snoozeForThisProject = object : AbstractAction(AndroidBundle.message("gradle.settings.autoSync.dialog.snooze.long")) {
      override fun actionPerformed(e: ActionEvent?) {
        close(SyncDueDialogSelection.CloseAndSnoozeIndefinitelyForProject.dialogExitCode)
      }
    }

    val syncAutomaticallyButton = JButton(syncAutomaticallyAction)
    val syncOnceButton = JButton(syncOnceAction)
    val closeSplitButton = JBOptionButton(closeAction, arrayOf(snoozeTomorrowAction, snoozeForThisProject))

    val buttonsPanel = JPanel(FlowLayout(FlowLayout.RIGHT))
    buttonsPanel.add(syncAutomaticallyButton)
    buttonsPanel.add(syncOnceButton)
    buttonsPanel.add(closeSplitButton)

    val mainSouthPanel = JPanel(BorderLayout())
    mainSouthPanel.border = JBUI.Borders.emptyTop(8)

    mainSouthPanel.add(buttonsPanel, BorderLayout.EAST)

    return mainSouthPanel
  }

  /**
   * By overriding createSouthPanel, we must return an empty array here
   * to prevent the default button creation mechanism from interfering.
   */
  override fun createActions(): Array<Action> {
    return emptyArray()
  }
}