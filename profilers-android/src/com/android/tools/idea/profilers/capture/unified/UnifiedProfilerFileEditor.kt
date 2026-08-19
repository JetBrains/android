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
import com.android.tools.idea.profilers.IntellijProfilerComponents
import com.android.tools.profilers.StageView
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.StudioProfilersView
// JetBrains patch: the sherlock.common module is not part of the monorepo and Google publishes no artifact for it,
// so com.android.tools.sherlock.common.system.editor.PerfettoFileEditor cannot be imported here.
// import com.android.tools.sherlock.common.system.editor.PerfettoFileEditor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.concurrency.AppExecutorUtil
import java.awt.BorderLayout
import java.beans.PropertyChangeListener
import java.io.File
import java.util.concurrent.TimeUnit
import javax.swing.JPanel
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.VisibleForTesting

/** A [com.intellij.openapi.fileEditor.FileEditor] for displaying profiler captures in a main editor tab. */
class UnifiedProfilerFileEditor(private val project: Project, private val file: VirtualFile) : UserDataHolderBase(), FileEditor {
  private val delegate: FileEditor? =
    if (UnifiedProfilerEditorProvider.isSupportedByPerfettoEditor(file)) {
      // JetBrains patch: PerfettoFileEditor lives in the sherlock.common module, which the monorepo does not carry
      // and for which no artifact is published, so the Sherlock trace viewer cannot be embedded in
      // this tree. The delegate stays null and the placeholder label below is shown instead.
      // PerfettoFileEditor(project, file)
      null
    } else {
      null
    }

  private var offlineProfilers: StudioProfilers? = null
  private val component: JPanel = JPanel(BorderLayout())

  // Strong references to prevent garbage collection of AspectObservers
  private var profilersView: StudioProfilersView? = null
  private var profilerStageView: StageView<*>? = null

  init {
    when {
      delegate != null -> component.add(delegate.component, BorderLayout.CENTER)
      file is ProfilerVirtualFile -> initializeLiveTaskEditor()
      else -> initializeOfflineSessionEditor()
    }

    if (file !is ProfilerVirtualFile) {
      ApplicationManager.getApplication().executeOnPooledThread {
        val localFile = File(file.path)
        if (localFile.exists() && localFile.length() > 0L) {
          ApplicationManager.getApplication().invokeLater { importFileIntoAndroidProfiler(project, file) }
        }
      }
    }
  }

  /**
   * Initializes the editor for Live Tasks (e.g. Live Telemetry, Java/Kotlin Allocations).
   *
   * Because live profiling sessions share a single centralized UI component ([AndroidProfilerToolWindow.profilersPanel]) across the IDE,
   * this method embeds that panel into the editor tab. To ensure layout consistency and prevent UI cloning across split editor panes, this
   * method enforces a strict singleton policy by closing any older editor tabs for this file after layout transitions settle.
   */
  private fun initializeLiveTaskEditor() {
    val profilerToolWindow = AndroidProfilerToolWindowFactory.getProfilerToolWindow(project) ?: return
    component.add(profilerToolWindow.profilersPanel, BorderLayout.CENTER)

    // Enforce singleton tab: close any other editors for this file across all windows.
    // We delay this check to allow native IDE actions (like "Move to New Window")
    // to finish their layout shifts, avoiding 'Already disposed' exceptions.
    AppExecutorUtil.getAppScheduledExecutorService()
      .schedule(
        {
          ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater
            val fileEditorManager = FileEditorManagerEx.getInstanceEx(project)

            // Find all windows containing this file
            val openWindows = fileEditorManager.windows.filter { it.isFileOpen(file) }
            if (openWindows.size <= 1) return@invokeLater

            // If there are multiple windows with this file open, it was a Split or a Drag & Drop.
            // We keep the newly created window (which is always appended to the end of the windows array)
            // and close the file in all older windows. This avoids the `currentWindow` race condition.
            val windowToKeep = openWindows.last()
            openWindows.forEach { window ->
              if (window != windowToKeep) {
                window.closeFile(file)
              }
            }
          }
        },
        TAB_TRANSITION_DEBOUNCE_DELAY_MS,
        TimeUnit.MILLISECONDS,
      )

    component.revalidate()
    component.repaint()
  }

  /**
   * Initializes the editor for offline sessions (e.g. Heap Dumps, traditional traces) by building a standard StudioProfilers session
   * asynchronously.
   */
  private fun initializeOfflineSessionEditor() {
    OfflineProfilerSessionFactory.buildSessionAsync(
      project,
      file,
      component,
      this,
      { ideServices -> IntellijProfilerComponents(project, this, ideServices.featureTracker) },
    ) { session ->
      offlineProfilers = session.profilers
      profilersView = session.profilersView
      profilerStageView = session.stageView
      session.stageView?.let { component.add(session.profilersView.component, BorderLayout.CENTER) }
      component.revalidate()
      component.repaint()
    }
  }

  override fun getComponent() = component

  override fun getPreferredFocusedComponent() = delegate?.preferredFocusedComponent ?: component

  @Nls(capitalization = Nls.Capitalization.Title)
  override fun getName() = if (file is ProfilerVirtualFile) file.name else "Profiler Capture"

  override fun setState(state: FileEditorState) {
    delegate?.setState(state)
  }

  override fun isModified() = delegate?.isModified ?: false

  override fun isValid() = delegate?.isValid ?: file.isValid

  override fun getFile() = file

  override fun addPropertyChangeListener(listener: PropertyChangeListener) {
    delegate?.addPropertyChangeListener(listener)
  }

  override fun removePropertyChangeListener(listener: PropertyChangeListener) {
    delegate?.removePropertyChangeListener(listener)
  }

  override fun getCurrentLocation(): FileEditorLocation? = delegate?.currentLocation

  override fun getState(level: FileEditorStateLevel): FileEditorState {
    return delegate?.getState(level) ?: FileEditorState.INSTANCE
  }

  /**
   * There are three ways to open a trace file:
   * - UI Import: Session -> Editor (Standard flow)
   * - File Action: Editor, Device Explorer -> Session (Lazy registration)
   * - Live Capture: We filter out artifacts to delegate session creation to the Editor flow. This prevents duplicate entries in 'Past
   *   Recordings', specifically for System Traces. for detailed explanation please check the comment
   *   https://b.corp.google.com/issues/472667234#comment3
   */
  private fun importFileIntoAndroidProfiler(project: Project, file: VirtualFile) {
    val window = ToolWindowManager.getInstance(project).getToolWindow(AndroidProfilerToolWindowFactory.ID)
    if (window != null) {
      window.isAvailable = true

      val profilerToolWindow = AndroidProfilerToolWindowFactory.getProfilerToolWindow(project)
      profilerToolWindow?.openFile(file)
    }
  }

  override fun dispose() {
    delegate?.let { Disposer.dispose(it) }
    if (file is ProfilerVirtualFile) {
      val sessionId = file.sessionId

      // When tabs are split or moved, IntelliJ may dispose the old editor before creating the new one.
      // We delay the teardown check to allow a new editor to be instantiated and claim the UI panel
      // if this is a "Move Tab" operation.
      AppExecutorUtil.getAppScheduledExecutorService()
        .schedule(
          {
            ApplicationManager.getApplication().invokeLater {
              if (project.isDisposed) return@invokeLater

              val profilerToolWindow = AndroidProfilerToolWindowFactory.getProfilerToolWindow(project)
              val fileEditorManager = FileEditorManager.getInstance(project)

              // Get all currently open editors for this file
              val openEditors = fileEditorManager.getAllEditors(file).filterIsInstance<UnifiedProfilerFileEditor>()

              // If no editor remains open, the user explicitly closed the task
              if (openEditors.isEmpty()) {
                profilerToolWindow?.notifyEditorTabClosed(sessionId)
              }
            }
          },
          TAB_TRANSITION_DEBOUNCE_DELAY_MS,
          TimeUnit.MILLISECONDS,
        )
    }
  }

  companion object {
    /**
     * Delay in milliseconds to allow native IDE window/tab transitions (such as Drag & Drop, Split Tab, Move to New Window) to complete
     * before disposing or tearing down the singleton live task panel.
     */
    @VisibleForTesting const val TAB_TRANSITION_DEBOUNCE_DELAY_MS = 500L
  }
}
