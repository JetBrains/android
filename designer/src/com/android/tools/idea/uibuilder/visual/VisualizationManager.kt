/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.intellij.util.ui.update.MergingUpdateQueue
import com.android.tools.idea.uibuilder.visual.VisualizationForm
import javax.swing.JComponent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.ide.DataManager
import com.android.tools.idea.uibuilder.visual.VisualizationManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.openapi.wm.ToolWindowManager
import com.android.tools.idea.uibuilder.visual.VisualizationToolSettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.wm.ex.ToolWindowEx
import java.lang.Runnable
import com.intellij.openapi.wm.ToolWindowType
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.util.ui.update.Update

/**
 * Manages a shared visualization window on the right side of the source editor which shows a preview
 * of the focused Android layout file. When user is not interacting with Android layout file then
 * the window is gone.
 *
 *
 * The visualization tool use [NlDesignSurface] for rendering previews.
 */
class VisualizationManager {
  private var myToolWindowUpdateQueue: MergingUpdateQueue? = null
  private var myToolWindowForm: VisualizationForm? = null

  private fun initToolWindowContent(project: Project, toolWindow: ToolWindow): VisualizationForm {
    // TODO(b/180927397): move tool initialization to VisualizationToolFactory if possible?
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

  fun processFileEditorChange(newEditor: FileEditor?, project: Project, toolWindow: ToolWindow) {
    if (myToolWindowUpdateQueue == null) {
      // TODO(b/180927397): Consider to move Queue into VisualizationWindowFactory.
      myToolWindowUpdateQueue = createUpdateQueue(toolWindow.disposable)
    }
    myToolWindowUpdateQueue!!.cancelAllUpdates()
    myToolWindowUpdateQueue!!.queue(object : Update("update") {
      override fun run() {
        if (toolWindow.isDisposed) {
          return
        }
        if (myToolWindowForm == null) {
          val form = initToolWindowContent(project, toolWindow)
          myToolWindowForm = form
          project.messageBus.connect().subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
            override fun stateChanged(manager: ToolWindowManager) {
              if (project.isDisposed) {
                return
              }
              if (VisualizationToolSettings.getInstance().globalState.isFirstTimeOpen && toolWindow is ToolWindowEx) {
                val width = toolWindow.getComponent().width
                toolWindow.stretchWidth(DEFAULT_WINDOW_WIDTH - width)
              }
              VisualizationToolSettings.getInstance().globalState.isFirstTimeOpen = false
              if (toolWindow.isAvailable) {
                val visible = toolWindow.isVisible
                VisualizationToolSettings.getInstance().globalState.isVisible = visible
                if (!Disposer.isDisposed(form)) {
                  if (visible) {
                    form.activate()
                  }
                  else {
                    form.deactivate()
                  }
                }
              }
            }
          })
        }
        if (Disposer.isDisposed(myToolWindowForm!!)) {
          return
        }
        if (newEditor == null || !myToolWindowForm!!.setNextEditor(newEditor)) {
          toolWindow.isAvailable = false
          return
        }
        toolWindow.isAvailable = true
        if (VisualizationToolSettings.getInstance().globalState.isVisible && !toolWindow.isVisible) {
          var restoreFocus: Runnable? = null
          if (toolWindow.type == ToolWindowType.WINDOWED) {
            // Ugly hack: Fix for b/68148499
            // We never want the preview to take focus when the content of the preview changes because of a file change.
            // Even when the preview is restored after being closed (move from Java file to an XML file).
            // There is no way to show the tool window without also taking the focus.
            // This hack is a workaround that sets the focus back to editor.
            // Note, that this may be wrong in certain circumstances, but should be OK for most scenarios.
            restoreFocus = Runnable { IdeFocusManager.getInstance(project).doWhenFocusSettlesDown { restoreFocusToEditor(newEditor) } }
          }
          toolWindow.activate(restoreFocus, false, false)
        }
      }
    })
  }

  fun processFileClose(source: FileEditorManager, file: VirtualFile) {
    if (myToolWindowForm != null && !Disposer.isDisposed(myToolWindowForm!!)) {
      // Remove stale references from the preview form. See b/80084773
      myToolWindowForm!!.fileClosed(source, file)
    }
  }

  companion object {
    // Must be same as the tool window id in designer.xml
    const val TOOL_WINDOW_ID = "Layout Validation"

    /**
     * The default width for first time open.
     */
    private const val DEFAULT_WINDOW_WIDTH = 500

    private fun createUpdateQueue(parentDisposable: Disposable): MergingUpdateQueue {
      return MergingUpdateQueue("android.layout.visual", 100, true, null, parentDisposable)
    }

    private fun restoreFocusToEditor(newEditor: FileEditor) {
      ApplicationManager.getApplication().invokeLater { newEditor.component.requestFocus() }
    }
  }
}