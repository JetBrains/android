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

import com.android.emulator.control.PaneEntry
import com.android.emulator.control.PaneEntry.PaneIndex
import com.android.emulator.control.WindowPosition
import com.android.tools.idea.protobuf.Empty
import com.android.tools.idea.streaming.emulator.EmptyStreamObserver
import com.android.tools.idea.streaming.emulator.EmulatorController
import com.android.tools.idea.streaming.emulator.getEmulatorUiTheme
import com.intellij.ide.ui.LafManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.WindowManager

/**
 * Shows the emulator extended controls.
 */
class EmulatorShowExtendedControlsAction : AbstractEmulatorAction() {

  override fun actionPerformed(event: AnActionEvent) {
    val emulatorController = getEmulatorController(event) ?: return
    showExtendedControls(emulatorController, getProject(event))
  }
}

internal fun showExtendedControls(emulatorController: EmulatorController, project: Project, paneIndex: PaneIndex = PaneIndex.KEEP_CURRENT) {
  emulatorController.setUiTheme(getEmulatorUiTheme(LafManager.getInstance()), object : EmptyStreamObserver<Empty>() {
    override fun onCompleted() {
      val pane = PaneEntry.newBuilder().setIndex(paneIndex)
      val frame = WindowManager.getInstance().getFrame(project)
      if (frame != null) {
        // Position the extended controls window at the center of the project window.
        pane.positionBuilder
          .setHorizontalAnchor(WindowPosition.HorizontalAnchor.HCENTER)
          .setVerticalAnchor(WindowPosition.VerticalAnchor.VCENTER)
          .setX(frame.x + frame.width / 2)
          .setY(frame.y + frame.height / 2)
      }
      emulatorController.showExtendedControls(pane.build())
    }
  })
}