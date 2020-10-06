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
package com.android.tools.idea.emulator.actions

import com.android.emulator.control.SnapshotPackage
import com.android.tools.idea.concurrency.executeOnPooledThread
import com.android.tools.idea.emulator.EMULATOR_TOOL_WINDOW_NOTIFICATION_GROUP
import com.android.tools.idea.emulator.EmptyStreamObserver
import com.android.tools.idea.emulator.EmulatorId
import com.android.tools.idea.emulator.EmulatorView
import com.android.tools.idea.emulator.actions.dialogs.BootMode
import com.android.tools.idea.emulator.actions.dialogs.BootType
import com.android.tools.idea.emulator.actions.dialogs.CreateSnapshotDialog
import com.android.tools.idea.emulator.actions.dialogs.SnapshotManager
import com.android.tools.idea.emulator.invokeLaterInAnyModalityState
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project

/**
 * Creates an Emulator snapshot and optionally designates it as the boot snapshot.
 */
class EmulatorCreateSnapshotAction : AbstractEmulatorAction() {

  override fun actionPerformed(event: AnActionEvent) {
    val project: Project = event.getRequiredData(CommonDataKeys.PROJECT)
    val emulatorController = getEmulatorController(event) ?: return
    val emulatorView = getEmulatorView(event) ?: return

    val dialog = CreateSnapshotDialog()
    if (!dialog.createWrapper(project).showAndGet()) {
      return
    }

    val emulatorId = emulatorController.emulatorId
    val snapshotName = dialog.snapshotName.trim()
    val bootSnapshotUpdater = {
      if (dialog.useToBoot) {
        SnapshotManager(emulatorController).saveBootMode(BootMode(BootType.SNAPSHOT, snapshotName))
      }
    }
    emulatorController.saveSnapshot(snapshotName, CompletionTracker(emulatorId, emulatorView, bootSnapshotUpdater))
  }

  override fun isEnabled(event: AnActionEvent): Boolean {
    return super.isEnabled(event) && !inProgress.contains(getEmulatorController(event)?.emulatorId)
  }

  private class CompletionTracker(
    val emulatorId: EmulatorId,
    val emulatorView: EmulatorView,
    val bootSnapshotUpdater: () -> Unit
  ) : EmptyStreamObserver<SnapshotPackage>() {

    init {
      inProgress.add(emulatorId)
      emulatorView.showLongRunningOperationIndicator("Saving state...")
    }

    override fun onCompleted() {
      executeOnPooledThread {
        bootSnapshotUpdater()
        finished()
      }
    }

    override fun onError(t: Throwable) {
      val notification = EMULATOR_TOOL_WINDOW_NOTIFICATION_GROUP.createNotification("Unable to create a snapshot", NotificationType.ERROR)
      Notifications.Bus.notify(notification)
      finished()
    }

    private fun finished() {
      invokeLaterInAnyModalityState {
        emulatorView.hideLongRunningOperationIndicator()
        inProgress.remove(emulatorId)
      }
    }
  }
}

private val inProgress = mutableListOf<EmulatorId>()
