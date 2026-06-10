/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync

import com.android.tools.adtui.HtmlLabel
import com.android.tools.analytics.UsageTracker
import com.android.tools.analytics.withProjectId
import com.android.tools.idea.gradle.project.GradleExperimentalSettings
import com.android.tools.idea.gradle.project.PropertyBasedDoNotAskOption
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.OptionAction
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.event.ActionEvent
import javax.swing.*
import org.jetbrains.android.util.AndroidBundle

// JetBrains patch: DialogWrapper is frontend API in a shared module. The Android plugin is not split into frontend and backend modules, so there is no frontend module to move this into.
@Suppress("SplitModeApiUsage")
class ParallelSyncMigrationDialog(private val project: Project) : DialogWrapper(project) {
  private val suppressionOption = PropertyBasedDoNotAskOption(project, ParallelSyncMigrationActivity.SHOW_MIGRATION_DIALOG_KEY)
  private val ignoreCheckbox = JCheckBox(AndroidBundle.message("gradle.sync.parallel.migration.notification.ignore"))

  init {
    title = AndroidBundle.message("gradle.sync.parallel.migration.notification.title")
    init()
  }

  private fun logUsage(kind: AndroidStudioEvent.EventKind) {
    UsageTracker.log(
      AndroidStudioEvent.newBuilder()
        .setCategory(AndroidStudioEvent.EventCategory.PARALLEL_GRADLE_SYNC_MODAL_DIALOG)
        .setKind(kind)
        .withProjectId(project)
    )
  }

  override fun createCenterPanel(): JComponent {
    val mainPanel = JPanel(BorderLayout(0, JBUI.scale(20)))
    val messageLabel =
      HtmlLabel().apply {
        isEditable = false
        text = AndroidBundle.message("gradle.sync.parallel.migration.notification.message")
      }
    mainPanel.add(messageLabel, BorderLayout.NORTH)
    // Place the checkbox in the south of the center panel so it has its own line above the buttons.
    mainPanel.add(ignoreCheckbox, BorderLayout.SOUTH)
    return mainPanel
  }

  override fun createActions(): Array<Action> {
    return arrayOf(EnableAction(), cancelAction)
  }

  private fun saveChoice(exitCode: Int) {
    // Save the suppression state for this project
    suppressionOption.setToBeShown(!ignoreCheckbox.isSelected, exitCode)
    if (ignoreCheckbox.isSelected) {
      logUsage(AndroidStudioEvent.EventKind.PARALLEL_GRADLE_SYNC_MODAL_DIALOG_IGNORE_SETTING)
    }
  }

  override fun doCancelAction() {
    saveChoice(CANCEL_EXIT_CODE)
    logUsage(AndroidStudioEvent.EventKind.PARALLEL_GRADLE_SYNC_MODAL_DIALOG_CANCEL)
    super.doCancelAction()
  }

  private inner class EnableAction :
    AbstractAction(AndroidBundle.message("gradle.sync.parallel.migration.notification.enable")), OptionAction {
    init {
      // Mark this as default action and would also be selected with ENTER button.
      putValue(DEFAULT_ACTION, true)
    }

    override fun actionPerformed(e: ActionEvent) {
      saveChoice(OK_EXIT_CODE)
      logUsage(AndroidStudioEvent.EventKind.PARALLEL_GRADLE_SYNC_MODAL_DIALOG_APPLY_SETTING)
      ApplicationManager.getApplication().executeOnPooledThread { ParallelSyncMigrationActivity.applyMigration(project) }
      close(OK_EXIT_CODE)
    }

    override fun getOptions(): Array<Action> {
      return arrayOf(
        object : AbstractAction(AndroidBundle.message("gradle.sync.parallel.migration.notification.always.enable")) {
          override fun actionPerformed(e: ActionEvent) {
            saveChoice(OK_EXIT_CODE)
            logUsage(AndroidStudioEvent.EventKind.PARALLEL_GRADLE_SYNC_MODAL_DIALOG_ALWAYS_APPLY_SETTING)
            // Save the "always enable" action across all the projects.
            GradleExperimentalSettings.getInstance().ALWAYS_ENABLE_MIGRATION_TO_PARALLEL_SYNC = true
            ApplicationManager.getApplication().executeOnPooledThread { ParallelSyncMigrationActivity.applyMigration(project) }
            close(OK_EXIT_CODE)
          }
        }
      )
    }
  }
}
