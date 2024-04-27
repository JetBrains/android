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

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.protobuf.InvalidProtocolBufferException
import com.android.tools.idea.testartifacts.instrumented.testsuite.adapter.UtpTestResultAdapter
import com.android.tools.idea.testartifacts.instrumented.testsuite.view.AndroidTestSuiteView
import com.android.tools.idea.util.toIoFile
import com.google.common.annotations.VisibleForTesting
import com.google.testing.platform.proto.api.core.TestSuiteResultProto
import com.intellij.execution.ui.RunContentManager
import com.intellij.notification.NotificationDisplayType
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.ComponentManagerEx
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
import java.io.File
import java.io.IOException
import java.util.Date
import javax.swing.Icon

/**
 * A file name which UTP outputs test results.
 */
private const val TEST_RESULT_PB_FILE_NAME = "test-result.pb"
/**
 * A file name which UTP outputs device info
 */
private const val DEVICE_INFO_PB_FILE_NAME = "device-info.pb"
/**
 * Top level "flavors" directory
 */
private const val FLAVOR_DIRECTORY_NAME = "flavors"

// Type of device that tests run on
enum class DeviceType(val deviceType: String) {
  CONNECTED("connected"),
  MANAGED("managed"),
  EMPTY("")
}

/**
 * An action to import Unified Test Platform (UTP) results, and display them in the test result panel.
 *
 * @param toolWindowDisplayName a name that is displayed as a tool-window title
 * @param importFile a UTP result protobuf file to open, or null to open file chooser dialog
 */
class ImportUtpResultAction(icon: Icon? = null,
                            text: String = "Import Android Test Results...",
                            val toolWindowDisplayName: String = "Imported Android Test Results",
                            val importFile: VirtualFile? = null) : AnAction(text, text, icon) {
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
   * @param file contains a binary protobuf of the test suite result
   * @param project the Android Studio project.
   **/
  @VisibleForTesting
  fun parseResultsAndDisplay(file: File, disposable: Disposable, project: Project) {
    RunContentManager.getInstance(project)
    try {
      val testAdapter = UtpTestResultAdapter(file)
      val packageName = testAdapter.packageName
      val module = if (packageName != null) {
        ModuleManager.getInstance(project).modules.find {
          it.getModuleSystem().getPackageName() == packageName
        }
      } else {
        null
      }
      if (module == null) {
        NOTIFICATION_GROUP.createNotification("Cannot find corresponding module. Some features might not be available. Did you "
                                              + "import the test results from a different project?", NotificationType.WARNING)
          .notify(project)
      }
      val testSuiteView = AndroidTestSuiteView(disposable, project, module, IMPORTED_TEST_WINDOW_ID)
      val toolWindow = getToolWindow(project)
      val contentManager = toolWindow.contentManager
      val content = contentManager.factory.createContent(testSuiteView.component, toolWindowDisplayName, true)
      contentManager.addContent(content)
      contentManager.setSelectedContent(content)

      (project as ComponentManagerEx).getCoroutineScope().launch {
        testAdapter.forwardResults(testSuiteView)
      }
      toolWindow.activate(null)
    }
    catch (exception: InvalidProtocolBufferException) {
      NOTIFICATION_GROUP.createNotification("Failed to import protobuf with exception: $exception",
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
      findTestResultProto(it, DeviceType.EMPTY).firstOrNull()
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
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

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
  return findTestResultProtoAndCreateImportActions(testDirectory, deviceType = DeviceType.CONNECTED)
}

fun createImportGradleManagedDeviceUtpResults(project: Project?): List<ImportUtpResultActionFromFile> {
  val deviceFolder = getDefaultAndroidGradlePluginDevicesTestDirectory(project) ?: return listOf()
  return findTestResultProtoAndCreateImportActions(deviceFolder, deviceType = DeviceType.MANAGED)
}

private fun VirtualFile.parent(level: Int): VirtualFile {
  var targetDir = this
  repeat(level) { targetDir = targetDir.parent ?: return targetDir }
  return targetDir
}

private fun findTestResultProtoAndCreateImportActions(dir: VirtualFile,
                                                      deviceType: DeviceType): List<ImportUtpResultActionFromFile> {
  return findTestResultProto(dir, deviceType)
    .map {
      val currentFlavor = getFlavorName(it, 3)
      createImportUtpResultsFromProto(it, currentFlavor, deviceType) }
    .filterNotNull().toList()
}

/**
 * Obtains flavor based on test-result.pb location, we either get its parent name or its skip parent name
 * flavors folder is structured as below
 * flavors
 *    |-> flavor1
 *           |-> test-result.pb
 *           |-> testDevice1
 *                  |-> test-result.pb
 */
private fun getFlavorName(dir: VirtualFile, layer: Int): String? {
  if (layer < 2) {
    return null
  }
  return if (dir.parent(layer).name == FLAVOR_DIRECTORY_NAME) {
    dir.parent(layer - 1).name
  } else {
    getFlavorName(dir, layer - 1)
  }
}

private fun findTestResultProto(dir: VirtualFile, deviceType: DeviceType): Sequence<VirtualFile> {
  val resultPbFile = dir.findChild(TEST_RESULT_PB_FILE_NAME)
  // TODO(b/294439844) We skip the aggregated test result file for GMDs until this issue is resolved
  if (resultPbFile != null &&
      (deviceType != DeviceType.MANAGED || dir.findChild(DEVICE_INFO_PB_FILE_NAME) != null)) {
    return sequenceOf(resultPbFile)
  }
  return dir.children.asSequence()
    .filter(VirtualFile::isDirectory)
    .flatMap { findTestResultProto(it, deviceType) }
}

private fun createImportUtpResultsFromProto(file: VirtualFile,
                                            flavorName: String?,
                                            deviceType: DeviceType): ImportUtpResultActionFromFile? {
  val resultProto = try {
    file.inputStream.use {
      TestSuiteResultProto.TestSuiteResult.parseFrom(it)
    }
  } catch (e: IOException) {
    null
  } ?: return null

  val (startTimeMillis, testName) = resultProto.testResultList.asSequence().map {
    val startTimeMillis = it.testCase.startTime.seconds * 1000 + it.testCase.startTime.nanos / 1000000
    Pair(startTimeMillis, it.testCase.testClass)
  }.filter {
    it.first > 0 && it.second.isNotBlank()
  }.firstOrNull() ?: return null

  val actionText = StringBuilder(testName).apply {
    flavorName?.let {
      append(" - $it")
    }
    append(" - ${deviceType.deviceType}")
    append(" (${DateFormatUtil.formatDateTime(Date(startTimeMillis))})")
  }.toString()
  return ImportUtpResultActionFromFile(
    startTimeMillis,
    ImportUtpResultAction(text = actionText, toolWindowDisplayName = actionText, importFile = file))
}

private fun getDefaultAndroidGradlePluginTestDirectory(project: Project?): VirtualFile? {
  return findFileByRelativePathToContentRoot(project, "build/outputs/androidTest-results/connected")
}

private fun getDefaultAndroidGradlePluginDevicesTestDirectory(project: Project?): VirtualFile? {
  return findFileByRelativePathToContentRoot(project, "build/outputs/androidTest-results/managedDevice")
}

private fun findFileByRelativePathToContentRoot(project: Project?, relativePath: String): VirtualFile? {
  if (project == null) {
    return null
  }
  return ModuleManager.getInstance(project).modules.asSequence().map { module ->
    ModuleRootManager.getInstance(module).contentRoots.asSequence().map {
      it.findFileByRelativePath(relativePath)
    }.filterNotNull().firstOrNull()
  }.filterNotNull().firstOrNull()
}