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
import com.android.tools.idea.emulator.DummyStreamObserver
import com.android.tools.idea.emulator.EmulatorId
import com.android.tools.idea.emulator.EmulatorToolWindowPanel
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.TextRange
import com.intellij.util.containers.ContainerUtil.createConcurrentList
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Creates and saves a snapshot of the Emulator state.
 */
class EmulatorCreateSnapshotAction : AbstractEmulatorAction() {

  override fun actionPerformed(event: AnActionEvent) {
    val project: Project = event.getRequiredData(CommonDataKeys.PROJECT)
    val emulatorController = getEmulatorController(event) ?: return
    val emulatorPanel = getEmulatorToolWindowPanel(event) ?: return
    val snapshotId = getSnapshotName(project) ?: return
    emulatorController.saveSnapshot(snapshotId, CompletionTracker(emulatorController.emulatorId, emulatorPanel))
  }

  private fun getSnapshotName(project: Project): String? {
    val suffix = TIMESTAMP_FORMAT.format(Date())
    val defaultName = "snap_${suffix}"
    val selection = TextRange(0, defaultName.length)
    return Messages.showInputDialog(project, "Snapshot Name:", "Create Emulator Snapshot", null, defaultName, null, selection)
  }

  override fun isEnabled(event: AnActionEvent): Boolean {
    return super.isEnabled(event) && !inProgress.contains(getEmulatorController(event)?.emulatorId)
  }

  private class CompletionTracker(
    val emulatorId: EmulatorId,
    val emulatorPanel: EmulatorToolWindowPanel
  ) : DummyStreamObserver<SnapshotPackage>() {

    init {
      inProgress.add(emulatorId)
      emulatorPanel.showLongRunningOperationIndicator("Saving state...")
    }

    override fun onCompleted() {
      emulatorPanel.hideLongRunningOperationIndicator()
      inProgress.remove(emulatorId)
    }
  }

  companion object {
    @JvmStatic
    private val TIMESTAMP_FORMAT = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)

    @JvmStatic
    private val inProgress = createConcurrentList<EmulatorId>()
  }
}