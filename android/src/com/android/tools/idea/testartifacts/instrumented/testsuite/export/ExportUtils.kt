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
package com.android.tools.idea.testartifacts.instrumented.testsuite.export

import com.android.annotations.concurrency.AnyThread
import com.android.annotations.concurrency.UiThread
import com.android.annotations.concurrency.WorkerThread
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.AndroidTestResultsTreeNode
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDevice
import com.intellij.execution.ExecutionBundle
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.testframework.export.ExportTestResultsAction
import com.intellij.execution.testframework.export.ExportTestResultsConfiguration
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.PerformInBackgroundOption
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.io.URLUtil
import java.io.File
import java.io.StringWriter
import java.time.Duration
import javax.swing.event.HyperlinkEvent
import javax.swing.event.HyperlinkListener
import javax.xml.transform.OutputKeys
import javax.xml.transform.sax.SAXTransformerFactory
import javax.xml.transform.sax.TransformerHandler
import javax.xml.transform.stream.StreamResult
import javax.xml.transform.stream.StreamSource

/**
 * Exports a given [rootResultsNode] into a AndroidTestMatrix XML file.
 */
@AnyThread
fun exportAndroidTestMatrixResultXmlFile(project: Project,
                                         toolWindowId: String?,
                                         exportConfig: ExportTestResultsConfiguration,
                                         exportFile: File,
                                         executionDuration: Duration,
                                         rootResultsNode: AndroidTestResultsTreeNode,
                                         runConfiguration: RunConfiguration,
                                         devices: List<AndroidDevice>,
                                         onFinishedFunc: () -> Unit = {}) {
  ProgressManager.getInstance().run(
    object : Task.Backgroundable(
      project,
      ExecutionBundle.message("export.test.results.task.name"),
      false,
      PerformInBackgroundOption.ALWAYS_BACKGROUND) {
      override fun run(indicator: ProgressIndicator) {
        indicator.isIndeterminate = true
        val outputText = createOutputText(exportConfig, executionDuration, rootResultsNode, runConfiguration, devices, toolWindowId)
                         ?: return
        val (resultFile, errorMessage) = invokeAndWaitIfNeeded {
          runWriteAction {
            exportFile.parentFile.mkdirs()

            val parent = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(exportFile.parentFile)
            if (parent?.isValid != true) {
              return@runWriteAction Pair(
                null, ExecutionBundle.message("failed.to.create.output.file", exportFile.path))
            }

            val resultFile = parent.findChild(exportFile.name) ?: parent.createChildData(this, exportFile.name)
            VfsUtil.saveText(resultFile, outputText)

            Pair(resultFile, null)
          }
        }

        if (!errorMessage.isNullOrBlank()) {
          showBalloon(project, toolWindowId, MessageType.ERROR,
                      ExecutionBundle.message("export.test.results.failed", errorMessage),
                      null)
          return
        }
        if (resultFile == null) {
          return
        }

        if (exportConfig.isOpenResults) {
          openEditorOrBrowser(
            resultFile, project,
            exportConfig.exportFormat == ExportTestResultsConfiguration.ExportFormat.Xml)
        }
        else {
          val listener = HyperlinkListener { e ->
            if (e.eventType == HyperlinkEvent.EventType.ACTIVATED) {
              openEditorOrBrowser(
                resultFile, project,
                exportConfig.exportFormat == ExportTestResultsConfiguration.ExportFormat.Xml)
            }
          }
          showBalloon(project, toolWindowId, MessageType.INFO,
                      ExecutionBundle.message("export.test.results.succeeded", exportFile.name),
                      listener)
        }
      }

      override fun onFinished() {
        onFinishedFunc()
      }
    }
  )
}

@WorkerThread
private fun createOutputText(exportConfig: ExportTestResultsConfiguration,
                             executionDuration: Duration,
                             rootResultsNode: AndroidTestResultsTreeNode,
                             runConfiguration: RunConfiguration,
                             devices: List<AndroidDevice>,
                             toolWindowId: String?): String? {
  val transformerHandler = createTransformerHandler(exportConfig, runConfiguration, toolWindowId) ?: return null
  val writer = StringWriter()
  transformerHandler.setResult(StreamResult(writer))
  AndroidTestResultsXmlFormatter(executionDuration, rootResultsNode, devices, runConfiguration, transformerHandler).execute()
  return writer.toString()
}

@WorkerThread
private fun createTransformerHandler(exportConfig: ExportTestResultsConfiguration,
                                     runConfiguration: RunConfiguration,
                                     toolWindowId: String?): TransformerHandler? {
  val transformerFactory = SAXTransformerFactory.newDefaultInstance() as SAXTransformerFactory
  return when(exportConfig.exportFormat) {
    ExportTestResultsConfiguration.ExportFormat.Xml -> {
      transformerFactory.newTransformerHandler().apply {
        transformer.apply {
          setOutputProperty(OutputKeys.INDENT, "yes")
          setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4")
        }
      }
    }
    ExportTestResultsConfiguration.ExportFormat.BundledTemplate -> {
      val xslSource = StreamSource(URLUtil.openStream(
        ExportTestResultsAction::class.java.getResource("intellij-export.xsl")))
      transformerFactory.newTransformerHandler(xslSource).apply {
        transformer.apply {
          setParameter("TITLE",
                       ExecutionBundle.message("export.test.results.filename",
                                               runConfiguration.name,
                                               runConfiguration.type.displayName))
        }
      }
    }
    else -> {
      val xslFile = File(exportConfig.userTemplatePath)
      if (!xslFile.isFile) {
        showBalloon(runConfiguration.project, toolWindowId, MessageType.ERROR,
                    ExecutionBundle.message("export.test.results.custom.template.not.found", xslFile.path), null)
        return null
      }
      transformerFactory.newTransformerHandler(StreamSource(xslFile)).apply {
        transformer.apply {
          setParameter("TITLE",
                       ExecutionBundle.message("export.test.results.filename",
                                               runConfiguration.name,
                                               runConfiguration.type.displayName))
        }
      }
    }
  }
}

@AnyThread
private fun showBalloon(project: Project, toolWindowId: String?, type: MessageType, text: String, listener: HyperlinkListener?) {
  val toolWindowId = toolWindowId ?: return
  ApplicationManager.getApplication().invokeLater {
    if (project.isDisposed) {
      return@invokeLater
    }
    if (ToolWindowManager.getInstance(project).getToolWindow(toolWindowId) != null) {
      ToolWindowManager.getInstance(project).notifyByBalloon(toolWindowId, type, text, null, listener)
    }
  }
}

@UiThread
private fun openEditorOrBrowser(result: VirtualFile, project: Project, editor: Boolean) {
  ApplicationManager.getApplication().invokeLater {
    if (editor) {
      FileEditorManager.getInstance(project).openFile(result, true)
    }
    else {
      BrowserUtil.browse(result)
    }
  }
}