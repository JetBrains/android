/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.snapshots

import com.android.tools.idea.editors.layoutInspector.LayoutInspectorFileType
import com.android.tools.idea.layoutinspector.LAYOUT_INSPECTOR_DATA_KEY
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.fileChooser.FileSaverDialog
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.VfsUtil

object CaptureSnapshotAction: AnAction(AllIcons.ToolbarDecorator.Export) {
  override fun update(event: AnActionEvent) {
    super.update(event)
    event.presentation.isEnabled = event.getData(LAYOUT_INSPECTOR_DATA_KEY)?.currentClient?.isConnected ?: false
    // TODO: tooltip
  }

  override fun actionPerformed(event: AnActionEvent) {
    val inspector = event.getData(LAYOUT_INSPECTOR_DATA_KEY) ?: return
    val outputDir = VfsUtil.getUserHomeDir()

    // Configure title, description and extension
    val descriptor = FileSaverDescriptor("Save Layout Snapshot", "Save layout inspector snapshot",
                                         LayoutInspectorFileType.EXT_LAYOUT_INSPECTOR)

    // Open the Dialog which returns a VirtualFileWrapper when closed
    val saveFileDialog: FileSaverDialog =
      FileChooserFactory.getInstance().createSaveFileDialog(descriptor, inspector.layoutInspectorModel.project)

    // Append extension manually to file name on MacOS because FileSaverDialog does not do it automatically.
    // TODO: good file name
    val fileName: String = "capture" + if (SystemInfo.isMac) LayoutInspectorFileType.DOT_EXT_LAYOUT_INSPECTOR else ""
    val result = saveFileDialog.save(outputDir, fileName) ?: return

    val path = result.getVirtualFile(true)?.toNioPath() ?: return
    ProgressManager.getInstance().runProcessWithProgressSynchronously(
      { inspector.currentClient.saveSnapshot(path) }, "Saving snapshot", true, event.project)
  }
}