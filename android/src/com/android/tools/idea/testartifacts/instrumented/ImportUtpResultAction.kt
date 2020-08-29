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
import com.google.protobuf.InvalidProtocolBufferException
import com.intellij.execution.ui.RunContentManager
import com.intellij.notification.NotificationDisplayType
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileChooser.chooseFile
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import kotlinx.coroutines.launch
import org.jetbrains.android.dom.manifest.getPackageName
import java.io.File
import java.nio.file.Paths

/**
 * An action to import Unified Test Platform (UTP) results, and display them in the test result panel.
 */
class ImportUtpResultAction : AnAction() {
  companion object {
    const val IMPORTED_TEST_WINDOW_ID = "Imported Tests"
    private val NOTIFICATION_GROUP = NotificationGroup("Import Android Test Results", NotificationDisplayType.BALLOON)
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
    try {
      val testAdapter = UtpTestResultAdapter(file)
      val packageName = testAdapter.getPackageName()
      val module = ModuleManager.getInstance(project).modules.find {
        getPackageName(it) == packageName
      }
      if (module == null) {
        NOTIFICATION_GROUP.createNotification("Cannot find corresponding module. Some features might not be available. Did you "
                                              + "import the test results from a different project?", NotificationType.WARNING)
          .notify(project)
      }
      val testSuiteView = AndroidTestSuiteView(disposable, project, module, IMPORTED_TEST_WINDOW_ID)
      val toolWindow = getToolWindow(project)
      val contentManager = toolWindow.contentManager
      val content = contentManager.factory.createContent(testSuiteView.component, "Imported Android Test Results", true)
      contentManager.addContent(content)

      project.coroutineScope.launch {
        testAdapter.forwardResults(testSuiteView)
      }
      toolWindow.activate(null)
    }
    catch (exception: InvalidProtocolBufferException) {
      NOTIFICATION_GROUP.createNotification("Failed to import protobuf with exception: " + exception.toString(),
                                            NotificationType.ERROR)
        .notify(project)
      throw exception
    }
  }

  /**
   * The action. It will pop up a file select dialogue to select the test result protobuf.
   *
   * @param e an action event with environment settings.
   */
  override fun actionPerformed(e: AnActionEvent) {
    val relativePath = Paths.get("build", "outputs", "androidTest-results", "connected", "test-result.pb")
    val defaultPath = e.project?.let { project ->
      ModuleManager.getInstance(project).modules.asSequence().map { module ->
        ModuleRootManager.getInstance(module).contentRoots.asSequence().map {
          it.findFileByRelativePath(relativePath.toString())
        }.filterNotNull().firstOrNull()
      }.filterNotNull().firstOrNull()
    }
    chooseFile(
      FileChooserDescriptor(true, false, false, false, false, false)
                 .withFileFilter { it.extension == "pb" },
      e.project,
      defaultPath
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