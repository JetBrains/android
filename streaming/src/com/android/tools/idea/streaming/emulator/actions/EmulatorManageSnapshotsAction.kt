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
package com.android.tools.idea.streaming.emulator.actions

import com.android.tools.idea.streaming.emulator.EMULATOR_CONTROLLER_KEY
import com.android.tools.idea.streaming.emulator.EmulatorController
import com.android.tools.idea.streaming.emulator.EmulatorView
import com.android.tools.idea.streaming.emulator.dialogs.ManageSnapshotsDialog
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import org.jetbrains.annotations.VisibleForTesting

/**
 * Opens the manage Snapshots dialog.
 */
class EmulatorManageSnapshotsAction : AbstractEmulatorAction() {

  override fun actionPerformed(event: AnActionEvent) {
    val emulatorView = getEmulatorView(event) ?: return
    showManageSnapshotsDialog(emulatorView, getProject(event))
  }

  protected fun getProject(event: AnActionEvent) = event.getRequiredData(CommonDataKeys.PROJECT)
}

/**
 * Shows a "Manage Snapshots" dialog associated with [emulatorView].
 */
fun showManageSnapshotsDialog(emulatorView: EmulatorView, project: Project): DialogWrapper {
  var dialogWrapper = openDialogs[emulatorView]
  if (dialogWrapper == null) {
    val emulator = emulatorView.emulator
    closeDuplicateDialogs(emulator, project)
    dialogWrapper = ManageSnapshotsDialog(emulator, emulatorView).createWrapper(project)
    dialogWrapper.show()
    Disposer.register(dialogWrapper.disposable) {
      openDialogs.entries.removeIf { it.value == dialogWrapper }
    }
    openDialogs[emulatorView] = dialogWrapper
  }
  return dialogWrapper
}

/**
 * Returns the "Manage Snapshots" dialog associated with [emulatorView], or null if no such dialog
 * is shown.
 */
fun findManageSnapshotDialog(emulatorView: EmulatorView): DialogWrapper? {
  return openDialogs[emulatorView]
}

/**
 * Closes all "Manage Snapshots" dialogs for the given [emulator] belonging to other projects.
 * This is done to avoid displaying multiple dialogs for the same emulator.
 */
private fun closeDuplicateDialogs(emulator: EmulatorController, project: Project) {
  val conflictingDialogs = openDialogs.entries
    .filter {
      val emulatorView = it.key
      val dataContext = DataManager.getInstance().getDataContext(emulatorView)
      dataContext.getData(EMULATOR_CONTROLLER_KEY) == emulator && dataContext.getData(CommonDataKeys.PROJECT) != project
    }
    .map { it.value }
  for (dialogWrapper in conflictingDialogs) {
    dialogWrapper.close(DialogWrapper.CLOSE_EXIT_CODE)
  }
}

@VisibleForTesting
fun getOpenManageSnapshotsDialogs(): Map<EmulatorView, DialogWrapper> = openDialogs

private val openDialogs = mutableMapOf<EmulatorView, DialogWrapper>()