/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.profilers.capture.unified

import com.android.tools.idea.profilers.AndroidProfilerToolWindowFactory
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowManager
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import org.jetbrains.annotations.Nls

/**
 * A [com.intellij.openapi.fileEditor.FileEditor] for displaying profiler captures in a main editor tab.
 */
class UnifiedProfilerFileEditor(private val project: Project, private val file: VirtualFile) : UserDataHolderBase(), FileEditor {

  private val component: JComponent = JLabel("Unified Profiler Capture View for ${file.name}", SwingConstants.CENTER)

  init {
    importFileIntoAndroidProfiler(project, file)

    // When a user opens a profiler file via "File -> Open", the IDE opens the source file in this editor.
    // However, the profiler imports this file into its own session storage (temp directory) and opens that copy.
    // To avoid having two tabs (source file + imported session), we close this source file editor immediately,
    // leaving only the imported session visible.
    // TODO(b/472667234): Investigate and implement a alternative approach to directly open imported file.
    if (!FileUtil.isAncestor(FileUtil.getTempDirectory(), file.path, true)) {
      SwingUtilities.invokeLater {
        FileEditorManager.getInstance(project).closeFile(file)
      }
    }
  }

  override fun getComponent() = component

  override fun getPreferredFocusedComponent() = component

  @Nls(capitalization = Nls.Capitalization.Title)
  override fun getName() = "Profiler Capture"

  override fun setState(state: FileEditorState) {}

  override fun isModified() = false

  override fun isValid() = file.isValid

  override fun getFile() = file

  override fun addPropertyChangeListener(listener: PropertyChangeListener) {}

  override fun removePropertyChangeListener(listener: PropertyChangeListener) {}

  override fun getCurrentLocation(): FileEditorLocation? = null

  /**
   * Handles the import of an external profiler file into the Android Profiler tool window.
   * @param project The current project context.
   * @param file The [VirtualFile] representing the profiler data to be imported.
   */
  private fun importFileIntoAndroidProfiler(project: Project, file: VirtualFile) {
    val window = ToolWindowManager.getInstance(project).getToolWindow(AndroidProfilerToolWindowFactory.ID)
    if (window != null) {
      window.isShowStripeButton = true
      // Makes sure the window is visible because opening a file is an explicit indication that the user wants to view the file,
      // and for that we need the profiler window to be open.
      if (!window.isVisible) {
        window.show(null)
      }
      val profilerToolWindow = AndroidProfilerToolWindowFactory.getProfilerToolWindow(project)
      if (profilerToolWindow != null) {
        val fileIo = VfsUtilCore.virtualToIoFile(file)
        // If the file is already in the temp directory, it's an internal re-open;
        // return early to prevent an infinite import loop.
        if (FileUtil.isAncestor(FileUtil.getTempDirectory(), fileIo.path, true)) {
          return
        }
        // Check if the file is already tracked in sessions to avoid duplicate imports.
        //TODO(b/472667234): File will be handled by external
        val isAlreadyImported = profilerToolWindow.profilers.sessionsManager.sessionArtifacts.any { it.name == file.name }
        if (!isAlreadyImported) {
          profilerToolWindow.openFile(file)
        }
      }
    }
  }

  override fun dispose() {
  }
}
