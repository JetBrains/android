/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.diagnostics.hprof.action

import com.android.tools.idea.diagnostics.crash.StudioCrashReporter
import com.android.tools.idea.diagnostics.hprof.analysis.HProfAnalysis
import com.android.tools.idea.diagnostics.hprof.util.HeapDumpAnalysisNotificationGroup
import com.android.tools.idea.diagnostics.report.AnalyzedHeapReport
import com.android.tools.idea.diagnostics.report.HeapReport
import com.android.tools.idea.diagnostics.report.UnanalyzedHeapReport
import com.intellij.ide.BrowserUtil
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.SwingHelper
import com.intellij.util.ui.UIUtil
import org.jetbrains.android.util.AndroidBundle
import org.jetbrains.ide.PooledThreadExecutor
import java.awt.BorderLayout
import java.io.File
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.OpenOption
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.function.BiConsumer
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.event.HyperlinkEvent

const val HEAP_REPORTS_DIR = "heapReports"

class AnalysisRunnable(val report: UnanalyzedHeapReport,
                       private val deleteAfterAnalysis: Boolean) : Runnable {

  companion object {
    private val LOG = Logger.getInstance(AnalysisRunnable::class.java)

    private fun updateNextCheckTimeIfNeeded(report: HeapReport) {
      // Silence report collection for next 7 days if not invoked by the user
      if (!report.heapProperties.reason.isUserInvoked()) {
        val nextCheckMs = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(7)
        PropertiesComponent.getInstance().setValue(HeapDumpSnapshotRunnable.NEXT_CHECK_TIMESTAMP_KEY, nextCheckMs.toString())
      }
    }
  }

  override fun run() {
    AnalysisTask().queue()
  }

  inner class AnalysisTask : Task.Backgroundable(null, AndroidBundle.message("heap.dump.analysis.task.title"), false) {

    override fun onThrowable(error: Throwable) {
      LOG.error(error)

      updateNextCheckTimeIfNeeded(report)

      val notification = HeapDumpAnalysisNotificationGroup.GROUP.createNotification(AndroidBundle.message("heap.dump.analysis.exception"),
                                                                                    NotificationType.INFORMATION)
      notification.notify(null)
      if (deleteAfterAnalysis) {
        deleteHprofFileAsync()
      }
    }

    private fun deleteHprofFileAsync() {
      CompletableFuture.runAsync { Files.deleteIfExists(report.hprofPath) }
    }

    override fun run(indicator: ProgressIndicator) {
      indicator.isIndeterminate = false
      indicator.text = "Analyze Heap"
      indicator.fraction = 0.0

      val openOptions: Set<OpenOption>
      if (deleteAfterAnalysis) {
        openOptions = setOf(StandardOpenOption.READ, StandardOpenOption.DELETE_ON_CLOSE)
      }
      else {
        openOptions = setOf(StandardOpenOption.READ)
      }
      val reportString = FileChannel.open(report.hprofPath, openOptions).use { channel ->
        HProfAnalysis(channel, SystemTempFilenameSupplier()).analyze(indicator)
      }
      if (deleteAfterAnalysis) {
        deleteHprofFileAsync()
      }

      val analyzedReport = AnalyzedHeapReport(
        reportString,
        report.heapProperties,
        report.properties
      )

      val heapReportDir = Paths.get(PathManager.getLogPath()).resolve(HEAP_REPORTS_DIR)
      heapReportDir.toFile().mkdirs()
      val datetime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
      val heapReportFile = heapReportDir.resolve(Paths.get("heapReport$datetime.txt"))
      heapReportFile.toFile().writeText(reportString)

      val notification = HeapDumpAnalysisNotificationGroup.GROUP.createNotification(
        AndroidBundle.message("heap.dump.analysis.notification.title"),
        AndroidBundle.message("heap.dump.analysis.notification.ready.content"),
        NotificationType.INFORMATION)
      notification.isImportant = true
      notification.addAction(ReviewReportAction(analyzedReport))

      notification.notify(null)
    }
  }

  class ReviewReportAction(private val report: AnalyzedHeapReport) :
    NotificationAction(AndroidBundle.message("heap.dump.analysis.notification.action.title")) {
    private var reportShown = false

    override fun actionPerformed(e: AnActionEvent, notification: Notification) {
      if (reportShown) return

      reportShown = true
      UIUtil.invokeLaterIfNeeded {
        notification.expire()

        val reportDialog = ShowReportDialog(report)
        val userAgreedToSendReport = reportDialog.showAndGet()

        updateNextCheckTimeIfNeeded(report)

        if (userAgreedToSendReport) {
          uploadReport()
        }
      }
    }

    private fun uploadReport() {
      // No need to check for AnalyticsSettings.hasOptedIn() as user agreed to the privacy policy by
      // clicking "Send" in ShowReportDialog.
      StudioCrashReporter.getInstance().submit(report.asCrashReport(), true)
        .whenCompleteAsync(BiConsumer<String, Throwable?> { _, throwable ->
          if (throwable == null) {
            HeapDumpAnalysisNotificationGroup.GROUP.createNotification(
              AndroidBundle.message("heap.dump.analysis.notification.title"),
              AndroidBundle.message("heap.dump.analysis.notification.submitted.content"),
              NotificationType.INFORMATION
            ).setImportant(false).notify(null)
          }
          else {
            LOG.error(throwable)
            HeapDumpAnalysisNotificationGroup.GROUP.createNotification(
              AndroidBundle.message("heap.dump.analysis.notification.title"),
              AndroidBundle.message("heap.dump.analysis.notification.submit.error.content"),
              NotificationType.ERROR
            ).setImportant(false).notify(null)
          }
        }, PooledThreadExecutor.INSTANCE)
    }
  }
}

class ShowReportDialog(report: AnalyzedHeapReport) : DialogWrapper(false) {
  private val textArea: JTextArea = JTextArea(30, 130)

  init {
    textArea.text = getReportText(report)
    textArea.isEditable = false
    textArea.caretPosition = 0
    init()
    title = AndroidBundle.message("heap.dump.analysis.report.dialog.title")
    isModal = true
  }

  private fun getReportText(report: AnalyzedHeapReport): String {
    return "${report.text}$SECTION_SEPARATOR\n${report.heapProperties.liveStats}"
  }

  override fun createCenterPanel(): JComponent? {
    val pane = JPanel(BorderLayout(0, 5))
    val productName = ApplicationNamesInfo.getInstance().fullProductName

    val header = JLabel(AndroidBundle.message("heap.dump.analysis.report.dialog.header", productName))

    pane.add(header, BorderLayout.PAGE_START)
    pane.add(JBScrollPane(textArea), BorderLayout.CENTER)
    with(SwingHelper.createHtmlViewer(true, null, JBColor.WHITE, JBColor.BLACK)) {
      isOpaque = false
      isFocusable = false
      addHyperlinkListener {
        if (it.eventType == HyperlinkEvent.EventType.ACTIVATED) {
          it.url?.let(BrowserUtil::browse)
        }
      }
      text = AndroidBundle.message("heap.dump.analysis.report.dialog.footer")
      pane.add(this, BorderLayout.PAGE_END)
    }

    return pane
  }

  override fun createActions(): Array<Action> {
    return arrayOf(okAction, cancelAction)
  }

  override fun createDefaultActions() {
    super.createDefaultActions()
    okAction.putValue(Action.NAME, AndroidBundle.message("heap.dump.analysis.report.dialog.action.send"))
    cancelAction.putValue(Action.NAME, AndroidBundle.message("heap.dump.analysis.report.dialog.action.dont.send"))
  }

  companion object {
    private const val SECTION_SEPARATOR = "================"
  }
}

class SystemTempFilenameSupplier : HProfAnalysis.TempFilenameSupplier {
  override fun getTempFilePath(type: String): Path {
    return FileUtil.createTempFile("studio-analysis-", "-$type.tmp").toPath()
  }
}
