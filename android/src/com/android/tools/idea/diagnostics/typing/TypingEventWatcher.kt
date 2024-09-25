/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.diagnostics.typing

import com.android.tools.analytics.crash.GoogleCrashReporter
import com.android.tools.idea.diagnostics.crash.StudioCrashReporter
import com.android.tools.idea.diagnostics.report.DiagnosticCrashReport
import com.android.tools.idea.diagnostics.report.DiagnosticReportProperties
import com.android.tools.idea.diagnostics.util.ThreadCallTree
import com.android.tools.idea.serverflags.ServerFlagService
import com.android.tools.idea.serverflags.protos.TypingLatencyReportConfig
import com.google.common.collect.Maps
import com.intellij.diagnostic.EventWatcher
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.ui.EDT
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.apache.commons.io.FileUtils
import org.apache.http.entity.mime.MultipartEntityBuilder
import java.awt.AWTEvent
import java.awt.event.KeyEvent
import java.io.IOException
import java.lang.management.ManagementFactory
import java.lang.management.ThreadInfo
import java.lang.management.ThreadMXBean
import java.nio.file.Files
import java.nio.file.Path
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.io.path.name
import kotlin.time.Duration.Companion.hours

@Suppress("UnstableApiUsage")
class TypingEventWatcher(private val coroutineScope: CoroutineScope) : EventWatcher {

  private val isActive: Boolean = !ApplicationManager.getApplication().isHeadlessEnvironment
  private var processKeyEvents: Boolean = false
  private var mergedThreadSnapshots: HashMap<Long, ThreadCallTree>? = null
  private var mergedSnapshotsCount = 0
  private var slowTypingEventsCount = 0
  private val myThreadMXBean: ThreadMXBean = ManagementFactory.getThreadMXBean();

  private val taskFlow = MutableSharedFlow<TypingLatencyCheckerTask?>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  private var currentTypingLatencyCheckerTask: TypingLatencyCheckerTask? = null

  private val typingLatencyReportConfig by lazy {
    ServerFlagService.Companion.instance.getProtoOrNull(TYPING_LATENCY_SERVER_FLAG_NAME, TypingLatencyReportConfig.getDefaultInstance())
  }

  init {
    if (isActive) {
      coroutineScope.launch {
        taskFlow.collectLatest { task ->
          if (task == null) {
            return@collectLatest
          }

          delay(typingLatencyReportConfig!!.typingEventReportThresholdMillis)
          task.processSlowTypingEvent()
        }
      }

      coroutineScope.launch(Dispatchers.Default) {
        while (true) {
          delay(1.hours)

          if (slowTypingEventsCount > 0) {
            collectTypingLatencyDumpsAndSendReport()

            reset()
          }
        }
      }
    }
  }

  private fun buildName(): String = ApplicationInfo.getInstance().build.asString()

  fun collectTypingLatencyDumpsAndSendReport() {
    val typingLatencyFolder = "${TYPING_LATENCY_DUMP_PREFIX}-${buildName()}"
    val reportDir = PathManager.getLogDir().resolve(typingLatencyFolder)
    if (!reportDir.toFile().exists()) {
      return
    }
    val reportFile = reportDir.toFile().listFiles()?.filter { e ->
      e.name.startsWith(TYPING_LATENCY_DUMP_PREFIX)
    }?.sortedWith { o1, o2 -> o1.name.compareTo(o2.name) }?.lastOrNull()
    if (reportFile == null) {
      return
    }
    StudioCrashReporter.getInstance().submit(TypingLatencyCrashReport(Files.readString(reportFile.toPath()), slowTypingEventsCount))
    FileUtils.deleteDirectory(reportDir.toFile())
  }

  private inner class TypingLatencyCheckerTask {
    private val logDir = PathManager.getLogDir()

    @Suppress("SpellCheckingInspection")
    private val dateFormat = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

    private fun formatTime(time: ZonedDateTime): String = dateFormat.format(time)

    fun processSlowTypingEvent() {
      slowTypingEventsCount++
      if (mergedSnapshotsCount >= typingLatencyReportConfig!!.maxNumberOfSnapshotsPerReport) {
        processKeyEvents = false
        return
      }
      val allThreads = myThreadMXBean.dumpAllThreads(false, false);
      allThreads.filter { t -> t.threadId == EDT.getEventDispatchThread().id }.forEach { e ->
        addThreadSnapshot(e)
      }
      if (ApplicationManagerEx.getApplicationEx().isWriteActionPending) {
        allThreads.filter { t -> t.threadId != EDT.getEventDispatchThread().id }.forEach { e ->
          // only readAction holding threads
          addThreadSnapshot(e)
        }
      }

      val typingLatencyFolder = "${TYPING_LATENCY_DUMP_PREFIX}-${buildName()}"
      val reportDir = logDir.resolve(typingLatencyFolder)
      Files.createDirectories(reportDir)

      val now = ZonedDateTime.now()
      val reportFileName = "$TYPING_LATENCY_DUMP_PREFIX-${formatTime(now)}-${now.toInstant().toEpochMilli()}.txt"
      val reportFilePath = reportDir.resolve(reportFileName)

      try {
        serializeThreadsReport(mergedThreadSnapshots, reportFilePath)
        reportDir.toFile().listFiles()?.filter { f -> !f.name.equals(reportFilePath.name) }?.forEach { f -> Files.delete(f.toPath()) }
        mergedSnapshotsCount++
      }
      catch (e: IOException) {
        LOG.warn("Failed to write the thread dump file", e)
      }
    }

    private fun serializeThreadsReport(mergedThreadSnapshots: Map<Long, ThreadCallTree>?, path: Path) {
      if (mergedThreadSnapshots == null) {
        return
      }
      val sb = StringBuilder()
      serializeThread(mergedThreadSnapshots[EDT.getEventDispatchThread().id], sb)
      for (threadId in mergedThreadSnapshots.keys) {
        if (threadId != EDT.getEventDispatchThread().id) {
          serializeThread(mergedThreadSnapshots[threadId], sb)
        }
      }
      Files.writeString(path, sb)
    }

    private fun serializeThread(threadCallTree: ThreadCallTree?, stringBuilder: java.lang.StringBuilder) {
      if (threadCallTree == null) {
        return
      }
      stringBuilder.append(threadCallTree.getReportString(0))
    }

    private fun addThreadSnapshot(threadInfo: ThreadInfo) {
      mergedThreadSnapshots?.getOrPut(threadInfo.threadId) { ThreadCallTree(threadInfo.threadId, threadInfo.threadName) }?.addThreadInfo(
        threadInfo, typingLatencyReportConfig!!.typingEventReportThresholdMillis)
    }
  }

  class TypingLatencyCrashReport(private val uiThreadSnapshot: String, private val slowTypingEventsCount: Int) : DiagnosticCrashReport(
    "TypingLatency", DiagnosticReportProperties()
  ) {
    override fun serialize(builder: MultipartEntityBuilder) {
      super.serialize(builder)
      GoogleCrashReporter.addBodyToBuilder(builder, "numberOfSlowTypingEvents", slowTypingEventsCount.toString())
      GoogleCrashReporter.addBodyToBuilder(builder, "mergedUIThreadStackTraces", uiThreadSnapshot)
    }
  }

  override fun runnableTaskFinished(
    runnable: Runnable, waitedInQueueNs: Long, queueSize: Int, executionDurationNs: Long, wasInSkippedItems: Boolean
  ) {
  }

  private fun shouldProcessEvent(event: AWTEvent): Boolean {
    return isActive && processKeyEvents && typingLatencyReportConfig != null && event is KeyEvent && event.id == KeyEvent.KEY_TYPED
  }

  override fun edtEventStarted(event: AWTEvent, startedAtMs: Long) {
    if (shouldProcessEvent(event)) {
      stopCurrentTaskAndReEmit(TypingLatencyCheckerTask())
    }
  }

  private fun stopCurrentTaskAndReEmit(task: TypingLatencyCheckerTask?) {
    currentTypingLatencyCheckerTask = task
    check(taskFlow.tryEmit(task))
  }

  override fun edtEventFinished(event: AWTEvent, finishedAtMs: Long) {
    if (shouldProcessEvent(event)) {
      stopCurrentTaskAndReEmit(null)
    }
  }

  override fun reset() {
    processKeyEvents = true
    mergedSnapshotsCount = 0
    mergedThreadSnapshots = Maps.newHashMap()
    slowTypingEventsCount = 0
  }

  override fun logTimeMillis(processId: String, startedAtMs: Long, runnableClass: Class<out Runnable>) {
  }

  companion object {
    private val LOG: Logger
      get() = logger<TypingEventWatcher>()

    const val TYPING_LATENCY_SERVER_FLAG_NAME = "diagnostics/typing_latency_report"
    const val TYPING_LATENCY_DUMP_PREFIX = "slowTypingLatency"
  }
}