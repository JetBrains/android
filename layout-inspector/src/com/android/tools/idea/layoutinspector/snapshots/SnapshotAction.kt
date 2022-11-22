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

import com.android.tools.adtui.actions.DropDownAction
import com.android.tools.idea.layoutinspector.LAYOUT_INSPECTOR_DATA_KEY
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.TooltipDescriptionProvider
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileChooser.FileChooserDialog
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.fileChooser.FileSaverDialog
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.VfsUtil
import icons.StudioIcons
import org.jetbrains.kotlin.idea.util.application.invokeLater
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SnapshotAction : DropDownAction(null, "Snapshot Export/Import", StudioIcons.LayoutInspector.SNAPSHOT), TooltipDescriptionProvider {
  init {
    add(ExportSnapshotAction)
    add(ImportSnapshotAction)
  }
}

object ExportSnapshotAction : AnAction(
  "Export Snapshot",
  "Export a snapshot of Layout inspector to share, inspect, and use offline.",
  AllIcons.ToolbarDecorator.Export
), TooltipDescriptionProvider {
  override fun update(event: AnActionEvent) {
    super.update(event)
    event.presentation.isEnabled = event.getData(LAYOUT_INSPECTOR_DATA_KEY)?.currentClient?.isConnected ?: false
  }

  override fun actionPerformed(event: AnActionEvent) {
    val inspector = event.getData(LAYOUT_INSPECTOR_DATA_KEY) ?: return
    val project = inspector.layoutInspectorModel.project
    val outputDir = VfsUtil.getUserHomeDir()

    // Configure title, description and extension
    val descriptor = FileSaverDescriptor("Save Layout Snapshot", "Save layout inspector snapshot", EXT_LAYOUT_INSPECTOR)

    // Open the Dialog which returns a VirtualFileWrapper when closed
    val saveFileDialog: FileSaverDialog =
      FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project)

    var fileName: String = inspector.currentClient.process.name + "_" +
                           SimpleDateFormat("yyyy.MM.dd_HH.mm", Locale.US).format(Date())
    fileName = fileName.replace(Regex("[^._A-Za-z0-9]"), "")
    // Append extension manually to file name on MacOS because FileSaverDialog does not do it automatically.
    fileName += if (SystemInfo.isMac) DOT_EXT_LAYOUT_INSPECTOR else ""
    val result = saveFileDialog.save(outputDir, fileName) ?: return
    val vFile = result.getVirtualFile(true)
    val path = vFile?.toNioPath() ?: return
    val saveAndOpenSnapshot = Runnable {
      inspector.currentClient.saveSnapshot(path)
      invokeLater { FileEditorManager.getInstance(project).openEditor(OpenFileDescriptor(project, vFile), false) }
    }
    ProgressManager.getInstance().runProcessWithProgressSynchronously(saveAndOpenSnapshot, "Saving snapshot", true, project)
  }
}

object ImportSnapshotAction : AnAction(
  "Import Snapshot",
  "Import a snapshot, open into an editor.",
  AllIcons.ToolbarDecorator.Import
), TooltipDescriptionProvider {
  override fun actionPerformed(event: AnActionEvent) {
    val inspector = event.getData(LAYOUT_INSPECTOR_DATA_KEY) ?: return
    val project = inspector.layoutInspectorModel.project

    // Configure title, description and extension
    val descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor(EXT_LAYOUT_INSPECTOR)
      .withTitle("Load LayoutSnapshot")
      .withDescription("Load layout inspector snapshot")

    // Open the Dialog which returns a VirtualFileWrapper when closed
    val openFileDialog: FileChooserDialog = FileChooserFactory.getInstance().createFileChooser(descriptor, project, null)

    val vFiles = openFileDialog.choose(project, VfsUtil.getUserHomeDir())
    if (vFiles.isEmpty()) return
    val vFile = vFiles[0]
    val saveAndOpenSnapshot = Runnable {
      invokeLater { FileEditorManager.getInstance(project).openEditor(OpenFileDescriptor(project, vFile), true) }
    }
    ProgressManager.getInstance().runProcessWithProgressSynchronously(saveAndOpenSnapshot, "Opening snapshot", true, project)
  }
}
