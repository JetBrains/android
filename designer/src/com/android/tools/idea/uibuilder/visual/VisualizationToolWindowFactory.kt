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
package com.android.tools.idea.uibuilder.visual

import com.android.resources.ResourceFolderType
import com.android.tools.idea.res.getFolderType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update

/**
 * [ToolWindowFactory] for the Layout Validation Tool. The tool is registered in designer.xml and the initialization is controlled by IJ's
 * framework.
 */
class VisualizationToolWindowFactory : ToolWindowFactory {

  companion object {
    // Must be same as the tool window id in designer.xml
    const val TOOL_WINDOW_ID = "Layout Validation"
  }

  /**
   * [isApplicable] is called first before other functions.
   */
  private lateinit var project: Project

  override fun isApplicable(project: Project): Boolean {
    this.project = project
    return true
  }

  override fun init(toolWindow: ToolWindow) {
    project.messageBus.connect(toolWindow.disposable).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER,
      object : FileEditorManagerListener {
        override fun fileOpened(source: FileEditorManager, file: VirtualFile) = updateAvailable(toolWindow, file)

        override fun fileClosed(source: FileEditorManager, file: VirtualFile) = updateAvailable(toolWindow, null)

        override fun selectionChanged(event: FileEditorManagerEvent) = updateAvailable(toolWindow, event.newFile)
      }
    )

    // Set up initial availability.
    toolWindow.isAvailable = FileEditorManager.getInstance(project).selectedEditors
      .mapNotNull { it.file }
      .any { getFolderType(it) == ResourceFolderType.LAYOUT }
  }

  /**
   * Show Layout Validation Tool Tab when current editor is Layout editor, or hide otherwise.
   */
  private fun updateAvailable(toolWindow: ToolWindow, file: VirtualFile?) {
    toolWindow.isAvailable = file?.let { getFolderType(it) == ResourceFolderType.LAYOUT } ?: false
  }

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    val handler = AsyncVisualizationEditorChangeHandler(toolWindow.disposable,
                                                        SyncVisualizationEditorChangeHandler(VisualizationFormProvider))

    toolWindow.isAutoHide = false
    project.messageBus.connect(toolWindow.disposable).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER,
                                                                MyFileEditorManagerListener(project, toolWindow, handler))
    // Process editor change task to have initial status.
    handler.onFileEditorChange(FileEditorManager.getInstance(project).selectedEditor, project, toolWindow)
  }
}

/**
 * Wrapped a [VisualizationEditorChangeHandler] with [MergingUpdateQueue] to make it run asynchronously.
 */
private class AsyncVisualizationEditorChangeHandler(parentDisposable: Disposable, private val delegator: VisualizationEditorChangeHandler)
  : VisualizationEditorChangeHandler by delegator {

  private val toolWindowUpdateQueue: MergingUpdateQueue by lazy {
    MergingUpdateQueue("android.layout.visual", 100, true, null, parentDisposable)
  }

  override fun onFileEditorChange(newEditor: FileEditor?, project: Project, toolWindow: ToolWindow) {
    toolWindowUpdateQueue.cancelAllUpdates()
    toolWindowUpdateQueue.queue(object : Update("update") {
      override fun run() {
        delegator.onFileEditorChange(newEditor, project, toolWindow)
      }
    })
  }
}

private class MyFileEditorManagerListener(private val project: Project,
                                          private val toolWindow: ToolWindow,
                                          private val visualizationEditorChangeHandler: VisualizationEditorChangeHandler)
  : FileEditorManagerListener {
  override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
    if (!file.isValid) {
      return
    }
    val psiFile = PsiManager.getInstance(project).findFile(file)
    val fileEditor = getActiveLayoutEditor(psiFile)
    if (fileEditor != null) {
      visualizationEditorChangeHandler.onFileEditorChange(fileEditor, project, toolWindow)
    }
  }

  override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
    visualizationEditorChangeHandler.onFileClose(source, toolWindow, file)
  }

  override fun selectionChanged(event: FileEditorManagerEvent) {
    var editorForLayout: FileEditor? = null
    val newEditor = event.newEditor
    if (newEditor != null) {
      val newVirtualFile = newEditor.file
      if (newVirtualFile != null) {
        val psiFile = PsiManager.getInstance(project).findFile(newVirtualFile)
        if (getFolderType(psiFile) == ResourceFolderType.LAYOUT) {
          // Visualization tool only works for layout files.
          editorForLayout = newEditor
        }
      }
    }
    visualizationEditorChangeHandler.onFileEditorChange(editorForLayout, project, toolWindow)
  }

  /**
   * Find an active editor for the specified file, or just the first active editor if file is null.
   */
  private fun getActiveLayoutEditor(file: PsiFile?): FileEditor? {
    return ApplicationManager.getApplication().runReadAction(Computable {
      FileEditorManager.getInstance(project).selectedEditors.firstOrNull { editor: FileEditor ->
        editor.file?.let { editorFile -> editorFile == file } ?: false
      }
    })
  }
}
