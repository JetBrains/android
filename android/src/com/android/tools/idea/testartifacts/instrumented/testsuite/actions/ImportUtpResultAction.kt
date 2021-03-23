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
package com.android.tools.idea.testartifacts.instrumented.testsuite.actions

import com.android.tools.idea.concurrency.coroutineScope
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testartifacts.instrumented.testsuite.adapter.UtpTestResultAdapter
import com.android.tools.idea.testartifacts.instrumented.testsuite.view.AndroidTestSuiteView
import com.android.tools.idea.util.toIoFile
import com.google.common.annotations.VisibleForTesting
import com.google.protobuf.InvalidProtocolBufferException
import com.google.testing.platform.proto.api.core.TestSuiteResultProto
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
import com.intellij.util.text.DateFormatUtil
import kotlinx.coroutines.launch
import org.jetbrains.android.dom.manifest.getPackageName
import java.io.File
import java.io.IOException
import java.nio.file.Paths
import java.util.Date
import javax.swing.Icon

/**
 * A file name which UTP outputs test results.
 */
private const val TEST_RESULT_PB_FILE_NAME = "test-result.pb"

/**
 * An action to import Unified Test Platform (UTP) results, and display them in the test result panel.
 *
 * @param importFile a UTP result protobuf file to open, or null to open file chooser dialog
 */
class ImportUtpResultAction(icon: Icon? = null,
                            text: String = "Import Android Test Results...",
                            private val importFile: VirtualFile? = null) : AnAction(text, text, icon) {
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
      contentManager.setSelectedContent(content)

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
    val project = e.project ?: return

    if (importFile != null) {
      parseResultsAndDisplay(importFile.toIoFile(), project, project)
      return
    }

    val defaultPath = getDefaultAndroidGradlePluginTestDirectory(project)?.let {
      findTestResultProto(it).firstOrNull()
    }
    chooseFile(
      FileChooserDescriptor(true, false, false, false, false, false)
        .withFileFilter { it.name == TEST_RESULT_PB_FILE_NAME },
      e.project,
      defaultPath
    ) { file: VirtualFile ->
      parseResultsAndDisplay(file.toIoFile(), project, project)
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

/**
 * A pair of timestamp and ImportUtpResultAction.
 *
 * @param timestamp a timestamp in millis when the test execution started
 * @param action an action to import the test
 */
data class ImportUtpResultActionFromFile(val timestamp: Long, val action: ImportUtpResultAction)

/**
 * Creates an ImportUtpResultAction from the default output directory of Android Gradle plugin.
 */
fun createImportUtpResultActionFromAndroidGradlePluginOutput(project: Project?): List<ImportUtpResultActionFromFile> {
  val testDirectory = getDefaultAndroidGradlePluginTestDirectory(project) ?: return listOf()
  return findTestResultProtoAndCreateImportActions(testDirectory)
}

fun createImportGradleManagedDeviceUtpResults(project: Project?): List<ImportUtpResultActionFromFile> {
  val deviceFolder = getDefaultAndroidGradlePluginDevicesTestDirectory(project) ?: return listOf()
  return findTestResultProtoAndCreateImportActions(deviceFolder)
}

private fun findTestResultProtoAndCreateImportActions(dir: VirtualFile): List<ImportUtpResultActionFromFile> {
  return findTestResultProto(dir)
    .map { createImportUtpResultsFromProto(it) }
    .filterNotNull()
    .toList()
}

private fun findTestResultProto(dir: VirtualFile): Sequence<VirtualFile> {
  return dir.children.asSequence()
    .filter(VirtualFile::isDirectory)
    .map { it.findChild(TEST_RESULT_PB_FILE_NAME) }
    .filterNotNull()
}

private fun createImportUtpResultsFromProto(file: VirtualFile): ImportUtpResultActionFromFile? {
  val resultProto = try {
    TestSuiteResultProto.TestSuiteResult.parseFrom(file.inputStream)
  } catch (e: IOException) {
    null
  } ?: return null

  val (startTimeMillis, testName) = resultProto.testResultList.asSequence().map {
    val startTimeMillis = it.testCase.startTime.seconds * 1000 + it.testCase.startTime.nanos / 1000000
    Pair(startTimeMillis, it.testCase.testClass)
  }.filter {
    it.first > 0 && it.second.isNotBlank()
  }.firstOrNull() ?: return null

  val actionText = "${testName} (${DateFormatUtil.formatDateTime(Date(startTimeMillis))})"
  return ImportUtpResultActionFromFile(startTimeMillis, ImportUtpResultAction(text = actionText, importFile = file))
}

private fun getDefaultAndroidGradlePluginTestDirectory(project: Project?): VirtualFile? {
  if (project == null) {
    return null
  }
  val relativePath = Paths.get("build", "outputs", "androidTest-results", "connected")
  return ModuleManager.getInstance(project).modules.asSequence().map { module ->
    ModuleRootManager.getInstance(module).contentRoots.asSequence().map {
      it.findFileByRelativePath(relativePath.toString())
    }.filterNotNull().firstOrNull()
  }.filterNotNull().firstOrNull()
}

private fun getDefaultAndroidGradlePluginDevicesTestDirectory(project: Project?): VirtualFile? {
  if (project == null) {
    return null
  }
  val relativePath = Paths.get("build", "outputs", "androidTest-results", "managedDevice")
  return ModuleManager.getInstance(project).modules.asSequence().map { module ->
    ModuleRootManager.getInstance(module).contentRoots.asSequence().map {
      it.findFileByRelativePath(relativePath.toString())
    }.filterNotNull().firstOrNull()
  }.filterNotNull().firstOrNull()
}