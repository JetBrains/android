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
package com.android.screenshottest

import com.android.tools.idea.testartifacts.screenshottest.ScreenshotTestSuite
import com.intellij.openapi.Disposable
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project

/**
 * Manages state of screenshot test result details view.
 */
object ScreenshotTestDetailsViewManager : Disposable {
  private val inMemoryTestResultFile = ScreenshotTestResultVirtualFile("Screenshot Test Results Detail")

  /**
   * Update screenshot test details view with the latest result and focus on it
   */
  fun showTestResultInEditorTab(project: Project, testSuite: ScreenshotTestSuite) {
    val editorProvider = FileEditorProvider.EP_FILE_EDITOR_PROVIDER.extensionList.first { it.javaClass == ScreenshotTestResultEditorProvider::class.java }
    (editorProvider as ScreenshotTestResultEditorProvider).inMemoryTestResultVirtualFile = inMemoryTestResultFile
    val fileEditorManager = FileEditorManager.getInstance(project)
    if (fileEditorManager.isFileOpen(inMemoryTestResultFile)) {
      // If test result is already opened in editor, do not open the file again. Update file content and focus on the tab
      ScreenshotTestResultEditorSingleton.updateTestResult(testSuite)
      val fd = OpenFileDescriptor(project, inMemoryTestResultFile)
      FileEditorManager.getInstance(project).openEditor(fd, true)
    }
    else {
      ScreenshotTestResultEditorSingleton.updateTestResult(testSuite)
      fileEditorManager.openFile(inMemoryTestResultFile).first()
    }
  }

  /**
   * Update screenshot test details view with the latest result without focusing on it
   */
  fun updateTestResultWithoutFocus(testSuite: ScreenshotTestSuite) =
    ScreenshotTestResultEditorSingleton.updateTestResult(testSuite)

  override fun dispose() = Unit
}