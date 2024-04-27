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
package com.android.tools.idea.stats

import com.android.tools.analytics.UsageTracker
import com.android.tools.analytics.withProjectId
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind.DEBUGGER_EVENT
import com.google.wireless.android.sdk.stats.DebuggerEvent
import com.google.wireless.android.sdk.stats.DebuggerEvent.FramesViewUpdated
import com.google.wireless.android.sdk.stats.DebuggerEvent.FramesViewUpdated.FileTypeInfo
import com.google.wireless.android.sdk.stats.DebuggerEvent.Type.FRAMES_VIEW_UPDATED
import com.google.wireless.android.sdk.stats.FileType
import com.google.wireless.android.sdk.stats.FileUsage
import com.google.wireless.android.sdk.stats.IntelliJNewUISwitch
import com.google.wireless.android.sdk.stats.KotlinGradlePerformance
import com.google.wireless.android.sdk.stats.KotlinGradlePerformance.FirUsage
import com.google.wireless.android.sdk.stats.KotlinProjectConfiguration
import com.google.wireless.android.sdk.stats.RunFinishData
import com.google.wireless.android.sdk.stats.RunStartData
import com.google.wireless.android.sdk.stats.VfsRefresh
import com.intellij.ide.ui.experimental.ExperimentalUiCollector
import com.intellij.internal.statistic.eventLog.EmptyEventLogFilesProvider
import com.intellij.internal.statistic.eventLog.EventLogConfiguration
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.StatisticsEventLogger
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.getProjectCacheFileName
import com.intellij.xdebugger.impl.XDebuggerActionsCollector
import org.jetbrains.kotlin.statistics.metrics.BooleanMetrics
import org.jetbrains.kotlin.statistics.metrics.StringMetrics
import java.util.Locale
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

object AndroidStudioEventLogger : StatisticsEventLogger {
  override fun cleanup() {}
  override fun getActiveLogFile(): Nothing? = null
  override fun getLogFilesProvider() = EmptyEventLogFilesProvider
  override fun logAsync(group: EventLogGroup, eventId: String, data: Map<String, Any>, isState: Boolean): CompletableFuture<Void> {
    when (group.id) {
      "debugger.breakpoints.usage" -> logDebuggerBreakpointsUsage(eventId, data)
      "experimental.ui.interactions" -> logNewUIStateChange(eventId, data)
      "file.types" -> logFileType(eventId, data)
      "file.types.usage" -> logFileTypeUsage(eventId, data)
      "kotlin.gradle.performance" -> logKotlinGradlePerformance(eventId, data)
      "kotlin.project.configuration" -> logKotlinProjectConfiguration(eventId, data)
      "run.configuration.exec" -> logRunConfigurationExec(eventId, data)
      "vfs" -> logVfsEvent(eventId, data)
      "xdebugger.actions" -> logDebuggerEvent(eventId, data)
    }
    return CompletableFuture.completedFuture(null)
  }

  override fun logAsync(group: EventLogGroup,
                        eventId: String,
                        dataProvider: () -> Map<String, Any>?,
                        isState: Boolean): CompletableFuture<Void> {
    val data = dataProvider() ?: return CompletableFuture.completedFuture(null)
    return logAsync(group, eventId, data, isState)
  }

  override fun computeAsync(computation: (backgroundThreadExecutor: Executor) -> Unit) {
  }

  override fun rollOver() {}

  private fun logNewUIStateChange(eventId: String, data: Map<String, Any>) {
    if (eventId != "switch.ui") {
      return
    }

    UsageTracker.log(AndroidStudioEvent.newBuilder().apply {
      kind = AndroidStudioEvent.EventKind.INTELLIJ_NEW_UI_SWITCH
      intellijNewUiSwitch = IntelliJNewUISwitch.newBuilder().apply {
        switchSource = getSwitchSource(data)
        (data["exp_ui"] as? Boolean)?.let { newUi = it }
      }.build()
    })
  }

  private fun getSwitchSource(data: Map<String, Any>): IntelliJNewUISwitch.SwitchSource {
    val s = data["switch_source"] as? String ?: return IntelliJNewUISwitch.SwitchSource.SOURCE_UNKNOWN
    val source = try {
      ExperimentalUiCollector.SwitchSource.valueOf(s)
    }
    catch (_: IllegalArgumentException) {
      return IntelliJNewUISwitch.SwitchSource.SOURCE_UNKNOWN
    }

    return when (source) {
      ExperimentalUiCollector.SwitchSource.ENABLE_NEW_UI_ACTION -> IntelliJNewUISwitch.SwitchSource.ENABLE_NEW_UI_ACTION
      ExperimentalUiCollector.SwitchSource.DISABLE_NEW_UI_ACTION -> IntelliJNewUISwitch.SwitchSource.DISABLE_NEW_UI_ACTION
      ExperimentalUiCollector.SwitchSource.WELCOME_PROMO -> IntelliJNewUISwitch.SwitchSource.WELCOME_PROMO
      ExperimentalUiCollector.SwitchSource.WHATS_NEW_PAGE -> IntelliJNewUISwitch.SwitchSource.WHATS_NEW_PAGE
      else -> IntelliJNewUISwitch.SwitchSource.SOURCE_UNKNOWN
    }
  }

  private fun logFileType(eventId: String, data: Map<String, Any>) {
    // filter out events that Jetbrains does not require
    if (eventId != "file.in.project") {
      return
    }

    UsageTracker.log(AndroidStudioEvent.newBuilder().apply {
      kind = AndroidStudioEvent.EventKind.FILE_TYPE
      fileType = FileType.newBuilder().apply {
        (data["file_type"] as? String)?.let { fileType = it }
        (data["plugin_type"] as? String)?.let { pluginType = it }
        (data["count"] as? String)?.toIntOrNull()?.let { numberOfFiles = it }
      }.build()
    }.withProjectId(data))
  }

  private fun logFileTypeUsage(eventId: String, data: Map<String, Any>) {
    // filter out events that Jetbrains does not require
    if (eventId == "registered") {
      return
    }

    UsageTracker.log(AndroidStudioEvent.newBuilder().apply {
      kind = AndroidStudioEvent.EventKind.FILE_USAGE
      fileUsage = FileUsage.newBuilder().apply {
        (data["file_path"] as? String)?.let { filePath = it }
        (data["file_type"] as? String)?.let { fileType = it }
        (data["plugin_type"] as? String)?.let { pluginType = it }
        (data["plugin_version"] as? String)?.let { pluginVersion = it }
        eventType = when (eventId) {
          "select" -> FileUsage.EventType.SELECT
          "edit" -> FileUsage.EventType.EDIT
          "open" -> FileUsage.EventType.OPEN
          "close" -> FileUsage.EventType.CLOSE
          else -> FileUsage.EventType.UNKNOWN_TYPE
        }
      }.build()
    }.withProjectId(data))
  }

  private fun logKotlinGradlePerformance(eventId: String, data: Map<String, Any>) {
    if (eventId != "All") {
      return
    }

    UsageTracker.log(AndroidStudioEvent.newBuilder().apply {
      kind = AndroidStudioEvent.EventKind.KOTLIN_GRADLE_PERFORMANCE_EVENT
      kotlinGradlePerformanceEvent = KotlinGradlePerformance.newBuilder().apply {
        data.getString(StringMetrics.USE_FIR)?.let { useFir = firUsage(it) }
        data.getString(StringMetrics.KOTLIN_API_VERSION)?.let { kotlinApiVersion = it }
        data.getString(StringMetrics.KOTLIN_COMPILER_VERSION)?.let { kotlinCompilerVersion = it }
        data.getString(StringMetrics.KOTLIN_LANGUAGE_VERSION)?.let { kotlinLanguageVersion = it }
        data.getString(StringMetrics.KOTLIN_STDLIB_VERSION)?.let { kotlinStdlibVersion = it }
        (data["plugin_version"] as? String)?.let { pluginVersion = it }
        data.getBoolean(BooleanMetrics.ENABLED_COMPILER_PLUGIN_ALL_OPEN)?.let { enabledCompilerPluginAllOpen = it }
        data.getBoolean(BooleanMetrics.ENABLED_COMPILER_PLUGIN_ATOMICFU)?.let { enabledCompilerPluginAtomicfu = it }
        (data["enabled_compiler_plugin_jpasupport"] as? Boolean)?.let { enabledCompilerPluginJpaSupport = it }
        data.getBoolean(BooleanMetrics.ENABLED_COMPILER_PLUGIN_LOMBOK)?.let { enabledCompilerPluginLombok = it }
        data.getBoolean(BooleanMetrics.ENABLED_COMPILER_PLUGIN_NO_ARG)?.let { enabledCompilerPluginNoArg = it }
        data.getBoolean(BooleanMetrics.ENABLED_COMPILER_PLUGIN_PARSELIZE)?.let { enabledCompilerPluginParcelize = it }
        data.getBoolean(BooleanMetrics.ENABLED_COMPILER_PLUGIN_SAM_WITH_RECEIVER)?.let { enabledCompilerPluginSamWithReceiver = it }
        data.getBoolean(BooleanMetrics.KOTLIN_KTS_USED)?.let { ktsUsed = it }
      }.build()
    }.withProjectId(data, "project_path"))
  }

  private fun Map<String, Any>.getBoolean(metric: BooleanMetrics): Boolean? {
    return this[metric.toString().lowercase(Locale.getDefault())] as? Boolean
  }

  private fun Map<String, Any>.getString(metric: StringMetrics): String? {
    return this[metric.toString().lowercase(Locale.getDefault())] as? String
  }

  private fun firUsage(s: String): FirUsage {
    val tokens = s.split(';')
    val hasTrue = tokens.contains("true")
    val hasFalse = tokens.contains("false")

    return when {
      hasTrue && !hasFalse -> FirUsage.TOTAL
      hasFalse && !hasTrue -> FirUsage.NONE
      hasTrue && hasFalse -> FirUsage.PARTIAL
      else -> FirUsage.UNSPECIFIED
    }
  }

  private fun logKotlinProjectConfiguration(eventId: String, data: Map<String, Any>) {
    // filter out events that Jetbrains does not require
    if (eventId == "invoked") {
      return
    }

    UsageTracker.log(AndroidStudioEvent.newBuilder().apply {
      kind = AndroidStudioEvent.EventKind.KOTLIN_PROJECT_CONFIGURATION
      kotlinProjectConfiguration = KotlinProjectConfiguration.newBuilder().apply {
        (data["system"] as? String?)?.let { system = it }
        (data["plugin_version"] as? String?)?.let { pluginVersion = it }
        (data["plugin"] as? String?)?.let { plugin = it }
        (data["plugin_type"] as? String?)?.let { pluginType = it }
        (data["platform"] as? String?)?.let { platform = it }
        (data["isMPP"] as? String?)?.toBoolean()?.let { isMultiplatform = it }
        (data["eventFlags"] as? Long?)?.let { eventFlags = it }
        eventType = when (eventId) {
          "Build" -> KotlinProjectConfiguration.EventType.BUILD
          else -> KotlinProjectConfiguration.EventType.TYPE_UNKNOWN
        }
      }.build()
    }.withProjectId(data))
  }

  private fun logRunConfigurationExec(eventId: String, data: Map<String, Any>) {
    val builder = when (eventId) {
      "started" -> AndroidStudioEvent.newBuilder().apply {
        kind = AndroidStudioEvent.EventKind.RUN_START_DATA
        runStartData = RunStartData.newBuilder().apply {
          (data["ide_activity_id"] as? Int?)?.let { ideActivityId = it }
          (data["executor"] as? String?)?.let { executor = it }
          (data["id"] as? String?)?.let { runConfiguration = it }
        }.build()
      }

      "finished" -> AndroidStudioEvent.newBuilder().apply {
        kind = AndroidStudioEvent.EventKind.RUN_FINISH_DATA
        runFinishData = RunFinishData.newBuilder().apply {
          (data["duration_ms"] as? String?)?.toLongOrNull()?.let { durationMs = it }
          (data["ide_activity_id"] as? String?)?.toIntOrNull()?.let { ideActivity = it }
        }.build()
      }

      else -> return
    }

    UsageTracker.log(builder.withProjectId(data))
  }

  private fun logVfsEvent(eventId: String, data: Map<String, Any>) {
    if (!eventId.equals("refreshed")) { // eventId as declared in com.intellij.openapi.vfs.newvfs.RefreshProgress
      return
    }

    (data["duration_ms"] as? Long)?.let { durationMs ->
      UsageTracker.log(AndroidStudioEvent.newBuilder()
                         .setKind(AndroidStudioEvent.EventKind.VFS_REFRESH)
                         .setVfsRefresh(VfsRefresh.newBuilder().setDurationMs(durationMs)))
    }
  }

  private fun logDebuggerBreakpointsUsage(eventId: String, data: Map<String, Any>) {
    when (eventId) {
      "breakpoint.added" -> {
        val type = data["type"] as? String ?: return
        val pluginType = data["plugin_type"] as? String ?: return
        val withinSession = data["within_session"] as? Boolean ?: return

        val studioEvent = AndroidStudioEvent.newBuilder()
          .setKind(AndroidStudioEvent.EventKind.DEBUGGER_EVENT)
          .setDebuggerEvent(
            DebuggerEvent.newBuilder()
              .setType(DebuggerEvent.Type.BREAKPOINT_ADDED_EVENT)
              .setBreakpointAdded(
                DebuggerEvent.BreakpointAdded.newBuilder()
                  .setType(type)
                  .setPluginType(pluginType)
                  .setInSession(withinSession)
              )
          )

        UsageTracker.log(studioEvent)
      }
    }
  }


  private fun logDebuggerEvent(eventId: String, data: Map<String, Any>) {
    val event = when (eventId) {
      XDebuggerActionsCollector.EVENT_FRAMES_UPDATED -> {
        @Suppress("UnstableApiUsage")
        val durationMs = data[EventFields.DurationMs.name] as? Long ?: return
        val totalFrames = data[XDebuggerActionsCollector.TOTAL_FRAMES] as? Int ?: return

        @Suppress("UNCHECKED_CAST")
        val fileTypes = data[XDebuggerActionsCollector.FILE_TYPES] as? List<String> ?: return

        @Suppress("UNCHECKED_CAST")
        val framesPerFileType = data[XDebuggerActionsCollector.FRAMES_PER_TYPE] as? List<Int> ?: return

        val fileTypeInfos = fileTypes.zip(framesPerFileType).map { (type, frames) ->
          FileTypeInfo.newBuilder()
            .setFileType(type)
            .setNumFrames(frames)
            .build()
        }
        DebuggerEvent.newBuilder()
          .setType(FRAMES_VIEW_UPDATED)
          .setFramesViewUpdated(
            FramesViewUpdated.newBuilder()
              .setDurationMs(durationMs)
              .setTotalFrames(totalFrames)
              .addAllFileTypeInfos(fileTypeInfos)
          )
      }

      else -> return
    }
    UsageTracker.log(
      AndroidStudioEvent.newBuilder()
        .setKind(DEBUGGER_EVENT)
        .setDebuggerEvent(event))
  }

  /**
   * Adds the associated project from the IntelliJ anonymization project id to the builder
   */
  private fun AndroidStudioEvent.Builder.withProjectId(data: Map<String, Any>, key: String = "project"): AndroidStudioEvent.Builder {
    val id = data[key] as? String? ?: return this
    val eventLogConfiguration = EventLogConfiguration.getInstance().getOrCreate("FUS")
    val project = ProjectManager.getInstance().openProjects
      .firstOrNull { eventLogConfiguration.anonymize(it.getProjectCacheFileName()) == id }
    return this.withProjectId(project)
  }
}