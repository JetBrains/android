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
package com.android.screenshottest.ui

import com.android.screenshottest.ScreenshotTestSuite
import com.android.screenshottest.ui.composables.DiffViewUi
import com.android.tools.adtui.compose.StudioComposePanel
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.getPreferredFocusedComponent
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.NotNull
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.enableNewSwingCompositing
import java.beans.PropertyChangeListener
import javax.swing.JComponent

/**
 * Singleton FileEditor for screenshot test result in memory virtual file. We want to display
 * only 1 test result details view in the editor tab to avoid confusion.
 */
object ScreenshotTestResultEditorSingleton : UserDataHolderBase(), FileEditor {
  lateinit var inMemoryTestResultVirtualFile: ScreenshotTestResultVirtualFile
  private lateinit var mProject: Project
  private lateinit var mTestSuite: ScreenshotTestSuite

  private fun readResolve(): Any = ScreenshotTestResultEditorSingleton

  fun updateTestResult(project: Project, testSuite: ScreenshotTestSuite): ScreenshotTestResultEditorSingleton {
    mProject = project
    mTestSuite = testSuite
    return this
  }

  override fun dispose() = Unit

  override fun getComponent(): JComponent {
    @OptIn(ExperimentalJewelApi::class) (enableNewSwingCompositing())
    return StudioComposePanel {
      DiffViewUi(mProject, mTestSuite.testResult)
    }
  }

  @NotNull
  @Throws(RuntimeException::class)
  override fun getFile(): VirtualFile {
    return if (ScreenshotTestResultEditorSingleton::inMemoryTestResultVirtualFile.isInitialized) {
      inMemoryTestResultVirtualFile
    }
    else {
      throw RuntimeException("Should give this a non-null virtual file")
    }
  }

  override fun getPreferredFocusedComponent(): JComponent? = component.getPreferredFocusedComponent()

  override fun getName(): String = SCREENSHOT_TEST_RESULT_EDITOR_NAME

  override fun setState(state: FileEditorState) = Unit

  override fun isModified(): Boolean = false

  override fun isValid(): Boolean = true

  override fun addPropertyChangeListener(listener: PropertyChangeListener) = Unit

  override fun removePropertyChangeListener(listener: PropertyChangeListener) = Unit
}

const val SCREENSHOT_TEST_RESULT_EDITOR_NAME = "Screenshot Test Result Viewer"
