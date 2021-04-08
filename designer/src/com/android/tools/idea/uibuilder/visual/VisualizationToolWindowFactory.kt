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
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import org.jetbrains.android.facet.AndroidFacet

/**
 * [ToolWindowFactory] for the Layout Validation Tool. The tool is registered in designer.xml and the initialization is controlled by IJ's
 * framework.
 */
class VisualizationToolWindowFactory : ToolWindowFactory {

  /**
   * [isApplicable] is called first before other functions.
   */
  private lateinit var project: Project

  override fun isApplicable(project: Project): Boolean {
    this.project = project
    return ModuleManager.getInstance(project).modules.any { AndroidFacet.getInstance(it) != null }
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
    val manager = VisualizationManager()
    toolWindow.isAutoHide = false
    // TODO(b/180927397): Consider to move content initialization from VisualizationManager to here?

    val editorChangeTask: (FileEditor?) -> Unit = { editor -> manager.processFileEditorChange(editor, project, toolWindow) }
    val fileCloseTask: (FileEditorManager, VirtualFile) -> Unit = { source, virtualFile -> manager.processFileClose(source, virtualFile) }
    project.messageBus.connect(toolWindow.disposable).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER,
                                                                MyFileEditorManagerListener(project, editorChangeTask, fileCloseTask))
    // Process editor change task to have initial status.
    editorChangeTask(FileEditorManager.getInstance(project).selectedEditor)
  }
}

private class MyFileEditorManagerListener(private val project: Project,
                                          private val editorChangeTask: (FileEditor?) -> Unit,
                                          private val fileCloseTask: (FileEditorManager, VirtualFile) -> Unit) : FileEditorManagerListener {
  override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
    if (!file.isValid) {
      return
    }
    val psiFile = PsiManager.getInstance(project).findFile(file)
    val fileEditor = getActiveLayoutEditor(psiFile)
    if (fileEditor != null) {
      editorChangeTask(fileEditor)
    }
  }

  override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
    fileCloseTask(source, file)
    // When using "Close All" action, the selectionChanged event is not triggered.
    // Thus we have to handle this case here.
    // In other cases, do not respond to fileClosed events since this has led to problems
    // with the preview window in the past. See b/64199946 and b/64288544
    if (source.openFiles.isEmpty()) {
      editorChangeTask(null)
    }
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
    editorChangeTask(editorForLayout)
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
