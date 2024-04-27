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

import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.AndroidTestResultsTreeNode
import com.android.tools.idea.testartifacts.instrumented.testsuite.export.exportAndroidTestMatrixResultXmlFile
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDevice
import com.intellij.CommonBundle
import com.intellij.execution.ExecutionBundle
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.testframework.TestRunnerBundle
import com.intellij.execution.testframework.export.ExportTestResultsConfiguration
import com.intellij.execution.testframework.export.ExportTestResultsDialog
import com.intellij.icons.AllIcons
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.PathUtil
import java.io.File
import java.time.Duration

/**
 * Exports Android instrumentation test results into XML or HTML.
 *
 * This class is an alternative implementation of [com.intellij.execution.testframework.export.ExportTestResultsAction].
 * This class exports XML file which is compatible with the original ExportTestResultsAction
 * with additional information for Android Test Matrix view.
 */
class ExportAndroidTestResultsAction :
  DumbAwareAction(ActionsBundle.message("action.ExportTestResults.text"),
                  ActionsBundle.message("action.ExportTestResults.description"),
                  AllIcons.ToolbarDecorator.Export) {

  var executionDuration: Duration? = null
  var devices: List<AndroidDevice>? = null
  var rootResultsNode: AndroidTestResultsTreeNode? = null
  var runConfiguration: RunConfiguration? = null
  var toolWindowId: String? = null

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  @UiThread
  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = e.project != null &&
                               devices?.isEmpty() == false &&
                               rootResultsNode?.results?.getTestResultSummary()?.isTerminalState == true &&
                               runConfiguration != null &&
                               executionDuration != null
  }

  @UiThread
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val rootResultsNode = rootResultsNode ?: return
    val runConfiguration = runConfiguration ?: return
    val executionDuration = executionDuration ?: return
    val devices = devices ?: return
    val exportConfig = ExportTestResultsConfiguration.getInstance(project)
    val defaultFileName = ExecutionBundle.message(
      "export.test.results.filename",
      PathUtil.suggestFileName(runConfiguration.name)
    ) + "." + exportConfig.exportFormat.defaultExtension
    val exportFile = showExportTestResultsDialog(exportConfig, project, defaultFileName) ?: return
    exportAndroidTestMatrixResultXmlFile(
      project, toolWindowId, exportConfig, exportFile, executionDuration, rootResultsNode, runConfiguration, devices)
  }

  @UiThread
  private fun showExportTestResultsDialog(exportConfig: ExportTestResultsConfiguration,
                                          project: Project,
                                          defaultFileName: String): File? {
    do {
      val dialog = ExportTestResultsDialog(project, exportConfig, defaultFileName)
      if (!dialog.showAndGet()) {
        return null
      }
      val file = getOutputFile(exportConfig, project, dialog.fileName)
      if (!file.exists()) {
        return file
      }
      if (Messages.showOkCancelDialog(
          project,
          ExecutionBundle.message("export.test.results.file.exists.message", file.name),
          ExecutionBundle.message("export.test.results.file.exists.title"),
          TestRunnerBundle.message("export.test.results.overwrite.button.text"),
          CommonBundle.getCancelButtonText(), Messages.getQuestionIcon()
        ) == Messages.OK) {
        return file
      }
    } while(true)
  }

  @UiThread
  private fun getOutputFile(exportConfig: ExportTestResultsConfiguration,
                            project: Project,
                            filename: String): File {
    val outputFolder: File
    val outputFolderPath = exportConfig.outputFolder
    outputFolder = if (!StringUtil.isEmptyOrSpaces(outputFolderPath)) {
      if (FileUtil.isAbsolute(outputFolderPath)) {
        File(outputFolderPath)
      } else {
        File(File(project.basePath), exportConfig.outputFolder)
      }
    } else {
      File(project.basePath)
    }
    return File(outputFolder, filename)
  }
}