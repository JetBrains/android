/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.diagnostics.jfr

import com.android.annotations.concurrency.GuardedBy
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.diagnostics.jfr.reports.JfrFreezeReports
import com.android.tools.idea.diagnostics.jfr.reports.JfrManifestMergerReports
import com.android.tools.idea.diagnostics.jfr.reports.JfrTypingLatencyReports
import com.android.tools.idea.diagnostics.report.DiagnosticReport
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.serverflags.ServerFlagService
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnActionResult
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.LowMemoryWatcher
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import jdk.jfr.consumer.RecordingFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.IdentityHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.seconds

private const val JFR_SERVER_FLAG_NAME = "diagnostics/jfr"
internal val JFR_RECORDING_DURATION = 30.seconds

typealias ReportCallback = (DiagnosticReport) -> Boolean

@Service
class RecordingManager : Disposable {
  private val logger: Logger = thisLogger()
  private val initDone = AtomicBoolean()
  private val mutex = Mutex()
  @GuardedBy("mutex")
  private val pendingCaptures: MutableList<JfrReportGenerator.Capture> = mutableListOf();
  @GuardedBy("mutex")
  private val recordings = RecordingBuffer()
  private lateinit var reportCallback: ReportCallback
  private lateinit var coroutineScope: CoroutineScope
  private var lowMemoryWatcher: LowMemoryWatcher? = null

  private var previousRecordingEnd: Instant = Instant.MIN

  @JvmOverloads
  fun init(
    reportCallback: ReportCallback,
    coroutineScope: CoroutineScope = AndroidCoroutineScope(this),
  ) {
    // We should fail loudly if anyone tries to init this a second time, but we don't know what that will do now, so just warn.
    if (!initDone.compareAndSet(false, true)) {
      logger.warn("Multiple init() calls attempted on RecordingManager!")
      return
    }

    this.reportCallback = reportCallback
    this.coroutineScope = coroutineScope

    // TODO(b/257594096): disabled on Mac due to crashes in the JVM during sampling
    if (SystemInfo.isMac || !ServerFlagService.instance.getBoolean(JFR_SERVER_FLAG_NAME, false)) return

    setupActionEvents()
    lowMemoryWatcher = LowMemoryWatcher.register { LowMemory().commit() }
    scheduleRecording()
    createReportManagers()
  }

  override fun dispose() { }

  fun startCapture(capture: JfrReportGenerator.Capture) {
    coroutineScope.launch {
      mutex.withLock { pendingCaptures.add(capture) }
    }
  }

  class DumpJfrAction: AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
      getInstance().dumpJfrTo(File(PathManager.getLogPath()).toPath())
    }
  }

  private fun dumpJfrTo(directory: Path): Path? = runBlocking(coroutineScope.coroutineContext) {
    mutex.withLock { recordings.dumpJfrTo(directory) }
  }

  private fun createReportManagers() {
    JfrFreezeReports.createFreezeReportManager()
    if (StudioFlags.JFR_MANIFEST_MERGE_ENABLED.get()) JfrManifestMergerReports.createReportManager()
    if (StudioFlags.JFR_TYPING_LATENCY_ENABLED.get()) JfrTypingLatencyReports.createReportManager(ServerFlagService.instance)
  }

  private fun setupActionEvents() {
    ApplicationManager.getApplication().messageBus.connect(this).subscribe(AnActionListener.TOPIC, object : AnActionListener {
      val jfrEventMap = IdentityHashMap<AnActionEvent, Action>()
      override fun beforeActionPerformed(action: AnAction, event: AnActionEvent) {
        jfrEventMap[event] = Action().apply {
          actionId = ActionManager.getInstance().getId(action)
          begin()
        }
      }

      override fun afterActionPerformed(action: AnAction, event: AnActionEvent, result: AnActionResult) {
        jfrEventMap.remove(event)?.commit()
      }
    })
  }

  private fun scheduleRecording() {
    coroutineScope.launch {
      try {
        while (isActive) {
          // If we are canceled, we need to record one last time before shutdown.
          try {
            delay(JFR_RECORDING_DURATION)
          }
          finally {
            withContext(NonCancellable) {
              doRecording()
            }
          }
        }
      }
      finally {
        // One more time to get the last bit of data.
        withContext(NonCancellable) {
          doRecording()
        }
      }
    }
  }

  private suspend fun doRecording() {
    val recordingEnd = Instant.now()
    mutex.withLock {
      recordings.swapBuffers()?.let { recording ->
        // Don't need to check if the capture's end is before the start of the previous recording,
        // since it would have been deleted by the previous call to purgeCompletedCaptures.
        if (pendingCaptures.any { it.start.isBefore(previousRecordingEnd) }) {
          val recPath = File(FileUtil.getTempDirectory(), "recording.jfr").toPath()
          try {
            recording.dump(recPath)
            recording.close()
            readAndDispatchRecordingEventsLocked(recPath)
            Files.deleteIfExists(recPath)
          }
          catch (e: IOException) {
            logger.warn(e)
          }
        }
      }
      pendingCaptures.removeIf { it.completeAndGenerateReport(previousRecordingEnd, reportCallback) }
      previousRecordingEnd = recordingEnd;
    }
  }

  @GuardedBy("mutex")
  private fun readAndDispatchRecordingEventsLocked(recPath: Path) {
    RecordingFile(recPath).use { recordingFile ->
      while (recordingFile.hasMoreEvents()) {
        val e = recordingFile.readEvent()
        pendingCaptures.forEach { it.maybeAccept(e) }
      }
    }
  }

  companion object {
    @JvmStatic
    fun getInstance(): RecordingManager = service()
  }
}
