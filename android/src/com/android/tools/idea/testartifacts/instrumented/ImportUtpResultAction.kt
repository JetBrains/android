/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.testartifacts.instrumented

import com.android.tools.idea.concurrency.coroutineScope
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testartifacts.instrumented.testsuite.adapter.UtpTestResultAdapter
import com.android.tools.idea.testartifacts.instrumented.testsuite.view.AndroidTestSuiteView
import com.android.tools.idea.util.toIoFile
import com.google.common.annotations.VisibleForTesting
import com.intellij.execution.ui.RunContentManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileChooser.chooseFile
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream

/**
 * An action to import Unified Test Platform (UTP) results, and display them in the test result panel.
 */
class ImportUtpResultAction : AnAction() {
  companion object {
    const val IMPORTED_TEST_WINDOW_ID = "Imported Tests"
  }

  /**
   * Get the tool window to display the imported results.
   *
   * @param project an Android Studio project.
   * @return a tool window to display the results.
   */
  @VisibleForTesting
  fun getToolWindow(project: Project): ToolWindow {
    val toolWindowManager = ToolWindowManager.getInstance(project)
    val toolWindow = toolWindowManager.getToolWindow(IMPORTED_TEST_WINDOW_ID)
    if (toolWindow != null) {
      return toolWindow
    }
    return toolWindowManager.registerToolWindow(RegisterToolWindowTask(id = IMPORTED_TEST_WINDOW_ID))
  }

  /**
   * Import test results and display them in the test result panel.
   *
   * @param inputStream contains a binary protobuf of the test suite result
   * @param project the Android Studio project.
   **/
  @VisibleForTesting
  fun parseResultsAndDisplay(file: File, disposable: Disposable, project: Project) {
    RunContentManager.getInstance(project)
    val toolWindow = getToolWindow(project)
    val testSuiteView = AndroidTestSuiteView(disposable, project, null)
    val contentManager = toolWindow.contentManager
    val content = contentManager.factory.createContent(testSuiteView.component, "Imported Android Test Results", true)
    contentManager.addContent(content)
    val testAdapter = UtpTestResultAdapter(testSuiteView)
    // TODO: error handling
    project.coroutineScope.launch {
      testAdapter.importResult(file)
    }
    toolWindow.activate(null)
  }

  /**
   * The action. It will pop up a file select dialogue to select the test result protobuf.
   *
   * @param e an action event with environment settings.
   */
  override fun actionPerformed(e: AnActionEvent) {
    chooseFile(FileChooserDescriptor(true, false, false, false, false, false),
               e.project,
               null
    ) { file: VirtualFile ->
      parseResultsAndDisplay(file.toIoFile(), requireNotNull(e.project), requireNotNull(e.project))
    }
  }

  /**
   * Check if this action is enabled.
   *
   * @param e an action event
   */
  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabledAndVisible = (e.project != null
                                          && StudioFlags.UTP_TEST_RESULT_SUPPORT.get())
  }
}