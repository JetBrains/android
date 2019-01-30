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

import com.android.tools.idea.diagnostics.AndroidStudioSystemHealthMonitor
import com.android.tools.idea.diagnostics.hprof.util.HeapDumpAnalysisNotificationGroup
import com.android.tools.idea.ui.GuiTestingService
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.util.ProgressWindow
import com.intellij.openapi.ui.Messages
import com.intellij.util.MemoryDumpHelper
import com.intellij.util.io.createDirectories
import com.intellij.util.io.exists
import org.jetbrains.android.util.AndroidBundle
import org.jetbrains.kotlin.daemon.common.usedMemory
import java.nio.file.Files
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities

class HeapDumpSnapshotRunnable(
  private val invokedByUser: Boolean,
  private val analysisOption: AnalysisOption) : Runnable {

  companion object {
    const val MINIMUM_USED_MEMORY_TO_CAPTURE_HEAP_DUMP_IN_MB = 800
    const val NEXT_CHECK_TIMESTAMP_KEY = "heap.dump.snapshot.next.check.timestamp"
    private val LOG = Logger.getInstance(HeapDumpSnapshotRunnable::class.java)
  }

  enum class AnalysisOption {
    NO_ANALYSIS,
    SCHEDULE_ON_NEXT_START,
    IMMEDIATE
  }

  override fun run() {
    LOG.info("HeapDumpSnapshotRunnable started: invokedByUser=$invokedByUser, analysisOption=$analysisOption")

    if (!invokedByUser) {

      if (ApplicationManager.getApplication().isUnitTestMode || GuiTestingService.getInstance().isGuiTestingMode) {
        LOG.info("Disabled for tests.")
        return
      }

      if (!ApplicationManager.getApplication().isEAP) {
        LOG.info("Heap dump analysis is enabled only on EAP builds.")
        return
      }

      // Analysis uses memory-mapped files to analyze heap dumps. This may require larger virtual memory address
      // space, so limiting the capture/analyze to 64-bit platforms only.
      if (System.getProperty("sun.arch.data.model") != "64") {
        LOG.info("Heap dump analysis supported only on 64-bit platforms.")
        return
      }

      // capture heap dumps that are larger then a threshold.
      val usedMemoryMB = usedMemory(false) / 1_000_000

      // Capture only large memory heaps, unless explicitly requested by the user
      if (usedMemoryMB < MINIMUM_USED_MEMORY_TO_CAPTURE_HEAP_DUMP_IN_MB) {
        LOG.info("Heap dump too small: $usedMemoryMB < $MINIMUM_USED_MEMORY_TO_CAPTURE_HEAP_DUMP_IN_MB (in MB)")
        return
      }
    }


    val hprofPath = AndroidStudioSystemHealthMonitor.ourHProfDatabase.createHprofTemporaryFilePath()

    val spaceInMB = Files.getFileStore(hprofPath.parent).usableSpace / 1_000_000
    val estimatedRequiredMB = estimateRequiredFreeSpaceInMB()

    // Check if there is enough space
    if (spaceInMB < estimatedRequiredMB) {
      LOG.info("Not enough space for heap dump: $spaceInMB < $estimatedRequiredMB (in MB)")
      // If invoked by the user action, show a message why a heap dump cannot be captured.
      if (invokedByUser) {
        val message = AndroidBundle.message("heap.dump.snapshot.no.space", hprofPath.parent.toString(),
                                            estimatedRequiredMB, spaceInMB)
        Messages.showErrorDialog(message, AndroidBundle.message("heap.dump.snapshot.title"))
      }
      return
    }

    var offerRestart = true
    if (!invokedByUser) {
      val nextCheckPropertyMs = PropertiesComponent.getInstance().getOrInitLong(NEXT_CHECK_TIMESTAMP_KEY, 0)
      val currentTimestampMs = System.currentTimeMillis()

      if (nextCheckPropertyMs > currentTimestampMs) {
        val nextCheckDateString = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z").format(Date(nextCheckPropertyMs))
        LOG.info("Don't ask for snapshot until $nextCheckDateString.")
        return
      }

      // Ask user for permission to capture a heap dump
      val productName = ApplicationNamesInfo.getInstance().fullProductName
      val result = Messages.showYesNoCancelDialog(
        AndroidBundle.message("heap.dump.snapshot.dialog.text", productName),
        AndroidBundle.message("heap.dump.snapshot.title"),
        AndroidBundle.message("heap.dump.snapshot.capture.restart"),
        AndroidBundle.message("heap.dump.snapshot.capture"),
        AndroidBundle.message("heap.dump.snapshot.skip"),
        Messages.getWarningIcon())
      if (result == Messages.CANCEL) {
        // Don't capture a heap dump for a week if user selected 'skip'
        val nextCheckMs = currentTimestampMs + TimeUnit.DAYS.toMillis(7)
        PropertiesComponent.getInstance().getOrInitLong(NEXT_CHECK_TIMESTAMP_KEY, nextCheckMs)
        LOG.info("Aborted by the user.")
        return
      }
      offerRestart = result == Messages.YES
    }

    LOG.info("Capturing heap dump.")

    // Start heap capture as a modal task
    CaptureHeapDumpTask(hprofPath, analysisOption, offerRestart).queue()
  }

  private fun estimateRequiredFreeSpaceInMB(): Long {
    return Math.max(100, (usedMemory(false) * 1.5).toLong() / 1_000_000)
  }

  class CaptureHeapDumpTask(private val hprofPath: Path,
                            private val analysisOption: AnalysisOption,
                            private val restart: Boolean)
    : Task.Modal(null,
                 AndroidBundle.message("heap.dump.snapshot.task.title"),
                 false) {

    override fun onSuccess() {
      if (analysisOption == AnalysisOption.SCHEDULE_ON_NEXT_START && restart) {
        SwingUtilities.invokeLater {
          ApplicationManager.getApplication().restart()
        }
      }
    }

    override fun onThrowable(error: Throwable) {
      LOG.error(error)
      val notification = HeapDumpAnalysisNotificationGroup.GROUP.createNotification(
        AndroidBundle.message("heap.dump.snapshot.exception"), NotificationType.ERROR)
      notification.notify(null)
    }

    override fun run(indicator: ProgressIndicator) {
      indicator.isIndeterminate = true
      indicator.text = AndroidBundle.message("heap.dump.snapshot.indicator.text")

      // TODO: Rewrite to remove this delay. Task.queue() shows progress dialog with 300ms delay currently without
      //   an API to lower this or get notified the window is shown. Creating a heap dump is a long-running operation
      //   that freezes JVM, therefore it is important to freeze UI with a progress shown on the screen.
      Thread.sleep(500)

      // Freezes JVM (and whole UI) while heap dump is created.
      captureSnapshot()

      when (analysisOption) {
        AnalysisOption.SCHEDULE_ON_NEXT_START -> {
          AndroidStudioSystemHealthMonitor.addHProfToDatabase(hprofPath)
          SwingUtilities.invokeLater {
            val productName = ApplicationNamesInfo.getInstance().fullProductName
            val notification = HeapDumpAnalysisNotificationGroup.GROUP.createNotification(
              AndroidBundle.message("heap.dump.snapshot.created", hprofPath.toString(), productName),
              NotificationType.INFORMATION)
            notification.notify(null)
          }
        }
        AnalysisOption.IMMEDIATE -> {
          SwingUtilities.invokeLater(AnalysisRunnable(hprofPath, true))
        }
        AnalysisOption.NO_ANALYSIS -> {
          val notification = HeapDumpAnalysisNotificationGroup.GROUP.createNotification(
            AndroidBundle.message("heap.dump.snapshot.created.no.analysis", hprofPath.toString()),
            NotificationType.INFORMATION)
          notification.notify(null)
        }
      }
    }

    private fun captureSnapshot() {
      if (hprofPath.exists()) {
        // While unlikely, don't overwrite existing files.
        throw FileAlreadyExistsException(hprofPath.toFile())
      }
      try {
        MemoryDumpHelper.captureMemoryDump(hprofPath.toString())
      }
      catch (t: Throwable) {
        // Delete the hprof file if exception was raised.
        Files.deleteIfExists(hprofPath)
        throw t
      }
    }
  }
}