/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.resourceExplorer

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.resourceExplorer.editor.ResourceExplorer
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Condition
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import org.jetbrains.android.facet.AndroidFacet

/**
 * Provides the tool explorer panel
 */
class ResourceExplorerToolFactory : ToolWindowFactory, DumbAware, Condition<Any> {

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    val connection = project.messageBus.connect(project)
    connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, MyFileEditorListener(project, toolWindow))
  }


  class MyFileEditorListener(val project: Project, val toolWindow: ToolWindow) : FileEditorManagerListener {
    private var currentModule: Module? = null

    override fun selectionChanged(event: FileEditorManagerEvent) {
      val editor = event.newEditor ?: return
      editorFocused(editor, toolWindow, project)
    }

    override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
      editorFocused(source.getSelectedEditor(file) ?: return, toolWindow, project)
    }

    override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
      toolWindow.contentManager.removeAllContents(true)
      currentModule = null
    }


    private fun editorFocused(
      editor: FileEditor,
      toolWindow: ToolWindow,
      project: Project
    ) {
      val module = ModuleUtilCore.findModuleForFile(editor.file ?: return, project)

      if (module == null || module == currentModule) {
        return
      }
      currentModule = module
      AndroidFacet.getInstance(module)
        ?.let { ResourceExplorer.createForToolWindow(editor, it) }
        ?.let {
          val contentManager = toolWindow.contentManager
          contentManager.removeAllContents(true)
          val content = contentManager.factory.createContent(it, "Resource Explorer", false)
          contentManager.addContent(content)
        }
    }
  }

  override fun shouldBeAvailable(project: Project) = StudioFlags.RESOURCE_MANAGER_ENABLED.get()

  override fun value(o: Any) = StudioFlags.RESOURCE_MANAGER_ENABLED.get()
}
