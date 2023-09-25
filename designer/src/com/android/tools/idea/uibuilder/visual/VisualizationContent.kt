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

import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.android.tools.idea.startup.ClearResourceCacheAfterFirstBuild
import com.android.tools.idea.util.listenUntilNextSync
import com.android.tools.idea.util.runWhenSmartAndSyncedOnEdt
import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow

/** The content which is added into ToolWindow of Visualization. */
interface VisualizationContent : Disposable {

  companion object {
    @JvmField
    val VISUALIZATION_CONTENT =
      DataKey.create<VisualizationContent>(VisualizationContent::class.java.name)
  }

  /**
   * Specifies the next editor the preview should be shown for. The update of the preview may be
   * delayed. Return True on success, or False if the preview update is not possible (e.g. the file
   * for the editor cannot be found).
   */
  fun setNextEditor(editor: FileEditor): Boolean

  /** Called when a file editor was closed. */
  fun fileClosed(editorManager: FileEditorManager, file: VirtualFile)

  /** Get the current selected [ConfigurationSet] */
  fun getConfigurationSet(): ConfigurationSet

  /** Change the displayed [ConfigurationSet] */
  fun setConfigurationSet(configurationSet: ConfigurationSet)

  /** Enables updates for this content. */
  fun activate()

  /**
   * Disables the updates for this content. Any changes to resources or the layout won't update this
   * content until [activate] is called.
   */
  fun deactivate()
}

interface VisualizationContentProvider {
  fun createVisualizationForm(project: Project, toolWindow: ToolWindow): VisualizationContent
}

object VisualizationFormProvider : VisualizationContentProvider {
  override fun createVisualizationForm(
    project: Project,
    toolWindow: ToolWindow
  ): VisualizationForm {
    val visualizationForm =
      VisualizationForm(project, toolWindow.disposable, AsyncContentInitializer)
    val contentPanel = visualizationForm.component
    val contentManager = toolWindow.contentManager
    contentManager.addDataProvider { dataId: String? ->
      if (
        PlatformCoreDataKeys.MODULE.`is`(dataId) ||
          LangDataKeys.IDE_VIEW.`is`(dataId) ||
          CommonDataKeys.VIRTUAL_FILE.`is`(dataId)
      ) {
        val fileEditor = visualizationForm.editor
        if (fileEditor != null) {
          return@addDataProvider DataManager.getDataProvider(fileEditor.component)
            ?.getData(dataId!!)
        }
      }
      if (VisualizationContent.VISUALIZATION_CONTENT.`is`(dataId)) {
        return@addDataProvider visualizationForm
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

private object AsyncContentInitializer : VisualizationForm.ContentInitializer {

  override fun initContent(project: Project, form: VisualizationForm, onComplete: () -> Unit) {
    val task = Runnable {
      form.showLoadingMessage()
      initPreviewFormAfterInitialBuild(project, form, onComplete)
    }
    val onError = Runnable { form.showErrorMessage() }
    ClearResourceCacheAfterFirstBuild.getInstance(project).runWhenResourceCacheClean(task, onError)
  }

  private fun initPreviewFormAfterInitialBuild(
    project: Project,
    form: VisualizationForm,
    onComplete: () -> Unit
  ) {
    project.runWhenSmartAndSyncedOnEdt(
      form,
      { result: ProjectSystemSyncManager.SyncResult ->
        if (result.isSuccessful) {
          form.createContentPanel()
          onComplete()
        } else {
          form.showErrorMessage()
          project.listenUntilNextSync(
            form,
            object : ProjectSystemSyncManager.SyncResultListener {
              override fun syncEnded(result: ProjectSystemSyncManager.SyncResult) {
                form.createContentPanel()
                onComplete()
              }
            }
          )
        }
      }
    )
  }
}
