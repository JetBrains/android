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

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ToolWindowType
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.openapi.wm.ex.ToolWindowManagerListener

/** The default width for first time open. */
private const val DEFAULT_WINDOW_WIDTH = 500

/** Handle the editor change event and response it to the content. */
interface VisualizationEditorChangeHandler {

  val visualizationContent: VisualizationContent?

  fun onFileEditorChange(newEditor: FileEditor?, project: Project, toolWindow: ToolWindow)

  fun onFileClose(source: FileEditorManager, toolWindow: ToolWindow, file: VirtualFile)
}

/** Handle the editor change and file close event synchronously. */
class SyncVisualizationEditorChangeHandler(
  private val contentProvider: VisualizationContentProvider
) : VisualizationEditorChangeHandler {

  private var toolWindowContent: VisualizationContent? = null
  override val visualizationContent: VisualizationContent?
    get() = toolWindowContent

  override fun onFileEditorChange(
    newEditor: FileEditor?,
    project: Project,
    toolWindow: ToolWindow
  ) {
    if (toolWindow.isDisposed) {
      return
    }
    if (toolWindowContent == null) {
      val form = contentProvider.createVisualizationForm(project, toolWindow)
      toolWindowContent = form
      project.messageBus
        .connect()
        .subscribe(
          ToolWindowManagerListener.TOPIC,
          object : ToolWindowManagerListener {
            override fun stateChanged(manager: ToolWindowManager) {
              if (project.isDisposed) {
                return
              }
              if (
                VisualizationToolSettings.getInstance().globalState.isFirstTimeOpen &&
                  toolWindow is ToolWindowEx
              ) {
                val width = toolWindow.getComponent().width
                toolWindow.stretchWidth(DEFAULT_WINDOW_WIDTH - width)
              }
              VisualizationToolSettings.getInstance().globalState.isFirstTimeOpen = false
              val visible = toolWindow.isVisible
              if (toolWindow.isAvailable) {
                // The tool window may become unavailable by tool window manager, for example,
                // switching from a layout editor to a text editor.
                // Here we want to trace the user-changed visibility, which only happens when tool
                // window is available.
                VisualizationToolSettings.getInstance().globalState.isVisible = visible
              }
              if (!Disposer.isDisposed(form)) {
                if (visible) {
                  form.activate()
                } else {
                  form.deactivate()
                }
              }
            }
          }
        )
    }
    if (Disposer.isDisposed(toolWindowContent!!)) {
      return
    }
    if (newEditor == null || !toolWindowContent!!.setNextEditor(newEditor)) {
      toolWindow.isAvailable = false
      return
    }
    toolWindow.isAvailable = true
    if (VisualizationToolSettings.getInstance().globalState.isVisible && !toolWindow.isVisible) {
      var restoreFocus: Runnable? = null
      if (toolWindow.type == ToolWindowType.WINDOWED) {
        // Ugly hack: Fix for b/68148499
        // We never want the preview to take focus when the content of the preview changes because
        // of a file change.
        // Even when the preview is restored after being closed (move from Java file to an XML
        // file).
        // There is no way to show the tool window without also taking the focus.
        // This hack is a workaround that sets the focus back to editor.
        // Note, that this may be wrong in certain circumstances, but should be OK for most
        // scenarios.
        restoreFocus = Runnable {
          IdeFocusManager.getInstance(project).doWhenFocusSettlesDown {
            restoreFocusToEditor(newEditor)
          }
        }
      }
      toolWindow.activate(restoreFocus, false, false)
    }
  }

  override fun onFileClose(source: FileEditorManager, toolWindow: ToolWindow, file: VirtualFile) {
    if (toolWindowContent != null && !Disposer.isDisposed(toolWindowContent!!)) {
      // Remove stale references from the preview form. See b/80084773
      toolWindowContent!!.fileClosed(source, file)
    }
    // When using "Close All" action, the selectionChanged event is not triggered.
    // Thus we have to handle this case here.
    // In other cases, do not respond to fileClosed events since this has led to problems
    // with the preview window in the past. See b/64199946 and b/64288544
    if (source.openFiles.isEmpty()) {
      onFileEditorChange(null, source.project, toolWindow)
    }
  }
}

private fun restoreFocusToEditor(newEditor: FileEditor) {
  ApplicationManager.getApplication().invokeLater { newEditor.component.requestFocus() }
}
