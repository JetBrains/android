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
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowManager
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.SwingConstants
import org.jetbrains.annotations.Nls

/** A [com.intellij.openapi.fileEditor.FileEditor] for displaying profiler captures in a main editor tab. */
class UnifiedProfilerFileEditor(private val project: Project, private val file: VirtualFile) : UserDataHolderBase(), FileEditor {

  private val component: JComponent = JLabel("Unified Profiler Capture View for ${file.name}", SwingConstants.CENTER)

  init {
    importFileIntoAndroidProfiler(project, file)
  }

  override fun getComponent() = component

  override fun getPreferredFocusedComponent() = component

  @Nls(capitalization = Nls.Capitalization.Title) override fun getName() = "Profiler Capture"

  override fun setState(state: FileEditorState) {}

  override fun isModified() = false

  override fun isValid() = file.isValid

  override fun getFile() = file

  override fun addPropertyChangeListener(listener: PropertyChangeListener) {}

  override fun removePropertyChangeListener(listener: PropertyChangeListener) {}

  override fun getCurrentLocation(): FileEditorLocation? = null

  /**
   * There are three ways to open a trace file:
   * - UI Import: Session -> Editor (Standard flow)
   * - File Action: Editor, Device Explorer -> Session (Lazy registration)
   * - Live Capture: We filter out artifacts to delegate session creation to the Editor flow.
   * This prevents duplicate entries in 'Past Recordings', specifically for System Traces.
   * for detailed explanation please check the comment https://b.corp.google.com/issues/472667234#comment3
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
      profilerToolWindow?.openFile(file)
    }
  }

  override fun dispose() {}
}