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

import com.android.tools.idea.concurrency.executeOnPooledThread
import com.android.tools.idea.emulator.actions.dialogs.BootMode
import com.android.tools.idea.emulator.actions.dialogs.BootOptionsDialog
import com.android.tools.idea.emulator.actions.dialogs.SnapshotInfo
import com.android.tools.idea.emulator.actions.dialogs.SnapshotManager
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.Project

/**
 * Opens the Boot Options dialog.
 */
class EmulatorBootOptionsAction : AbstractEmulatorAction() {

  override fun actionPerformed(event: AnActionEvent) {
    val project: Project = event.getRequiredData(CommonDataKeys.PROJECT)
    val emulatorController = getEmulatorController(event) ?: return
    val snapshotManager = SnapshotManager(emulatorController)
    executeOnPooledThread {
      val bootMode = snapshotManager.readBootMode() ?: return@executeOnPooledThread
      val snapshotsFuture: SettableFuture<List<SnapshotInfo>> = SettableFuture.create()
      invokeLater {
        showDialogAndSave(project, bootMode, snapshotsFuture, snapshotManager)
      }
      snapshotsFuture.set(snapshotManager.fetchSnapshotList(excludeQuickBoot = true))
    }
  }

  private fun showDialogAndSave(project: Project,
                                bootMode: BootMode,
                                snapshotsFuture: ListenableFuture<List<SnapshotInfo>>,
                                snapshotManager: SnapshotManager) {
    val dialog = BootOptionsDialog(bootMode, snapshotsFuture, snapshotManager)
    val wrapper = dialog.createWrapper(project)
    if (!wrapper.showAndGet()) {
      return
    }
    val newBootMode = BootMode(dialog.bootType, dialog.bootSnapshot)
    if (newBootMode != bootMode) {
      executeOnPooledThread {
        snapshotManager.saveBootMode(newBootMode)
      }
    }
  }

  override fun isEnabled(event: AnActionEvent): Boolean =
    getEmulatorController(event) != null
}