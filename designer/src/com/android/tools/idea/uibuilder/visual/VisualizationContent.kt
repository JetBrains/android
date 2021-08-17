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

import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow

/**
 * The content which is added into ToolWindow of Visualization.
 */
interface VisualizationContent : Disposable {
  /**
   * Specifies the next editor the preview should be shown for.
   * The update of the preview may be delayed.
   * Return True on success, or False if the preview update is not possible (e.g. the file for the editor cannot be found).
   */
  fun setNextEditor(editor: FileEditor): Boolean

  /**
   * Called when a file editor was closed.
   */
  fun fileClosed(editorManager: FileEditorManager, file: VirtualFile)

  /**
   * Enables updates for this content.
   */
  fun activate()

  /**
   * Disables the updates for this content. Any changes to resources or the layout won't update this content until [activate] is called.
   */
  fun deactivate()
}

interface VisualizationContentProvider {
  fun createVisualizationForm(project: Project, toolWindow: ToolWindow): VisualizationContent
}

object VisualizationFormProvider : VisualizationContentProvider {
  override fun createVisualizationForm(project: Project, toolWindow: ToolWindow): VisualizationForm {
    val visualizationForm = VisualizationForm(project, toolWindow.disposable)
    val contentPanel = visualizationForm.component
    val contentManager = toolWindow.contentManager
    contentManager.addDataProvider { dataId: String? ->
      if (LangDataKeys.MODULE.`is`(dataId) || LangDataKeys.IDE_VIEW.`is`(dataId) || CommonDataKeys.VIRTUAL_FILE.`is`(dataId)) {
        val fileEditor = visualizationForm.editor
        if (fileEditor != null) {
          val component = fileEditor.component
          val context = DataManager.getInstance().getDataContext(component)
          return@addDataProvider context.getData(dataId!!)
        }
      }
      null
    }
    val content = contentManager.factory.createContent(contentPanel, null, false)
    content.setDisposer(visualizationForm)
    content.isCloseable = false
    content.preferredFocusableComponent = contentPanel
    contentManager.addContent(content)
    contentManager.setSelectedContent(content, true)
    if (toolWindow.isVisible) {
      visualizationForm.activate()
    }
    return visualizationForm
  }
}
