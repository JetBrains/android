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
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.FileType
import com.google.wireless.android.sdk.stats.FileUsage
import com.google.wireless.android.sdk.stats.KotlinGradlePerformance
import com.google.wireless.android.sdk.stats.KotlinGradlePerformance.FirUsage
import com.google.wireless.android.sdk.stats.KotlinProjectConfiguration
import com.google.wireless.android.sdk.stats.RunFinishData
import com.google.wireless.android.sdk.stats.RunStartData
import com.google.wireless.android.sdk.stats.VfsRefresh
import com.intellij.internal.statistic.eventLog.EmptyEventLogFilesProvider
import com.intellij.internal.statistic.eventLog.EventLogConfiguration
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.StatisticsEventLogger
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.getProjectCacheFileName
import org.jetbrains.kotlin.statistics.metrics.BooleanMetrics
import org.jetbrains.kotlin.statistics.metrics.StringMetrics
import java.util.Locale
import java.util.concurrent.CompletableFuture

object AndroidStudioEventLogger : StatisticsEventLogger {
  override fun cleanup() {}
  override fun getActiveLogFile(): Nothing? = null
  override fun getLogFilesProvider() = EmptyEventLogFilesProvider
  override fun logAsync(group: EventLogGroup, eventId: String, data: Map<String, Any>, isState: Boolean): CompletableFuture<Void> {
    when (group.id) {
      "file.types" -> logFileType(eventId, data)
      "file.types.usage" -> logFileTypeUsage(eventId, data)
      "kotlin.gradle.performance" -> logKotlinGradlePerformance(eventId, data)
      "kotlin.project.configuration" -> logKotlinProjectConfiguration(eventId, data)
      "run.configuration.exec" -> logRunConfigurationExec(eventId, data)
      "vfs" -> logVfsEvent(eventId, data)
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

  override fun rollOver() {}

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
        (data[StringMetrics.USE_FIR.key()] as? String?)?.let { useFir = firUsage(it) }
        (data[StringMetrics.KOTLIN_API_VERSION.key()] as? String?)?.let { kotlinApiVersion = it }
        (data[StringMetrics.KOTLIN_COMPILER_VERSION.key()] as? String?)?.let { kotlinCompilerVersion = it }
        (data[StringMetrics.KOTLIN_LANGUAGE_VERSION.key()] as? String?)?.let { kotlinLanguageVersion = it }
        (data[StringMetrics.KOTLIN_STDLIB_VERSION.key()] as? String?)?.let { kotlinStdlibVersion = it }
        (data["plugin_version"] as? String?)?.let { pluginVersion = it }
        (data[BooleanMetrics.ENABLED_COMPILER_PLUGIN_ALL_OPEN.key()] as? String?)?.toBoolean()?.let { enabledCompilerPluginAllOpen = it }
        (data[BooleanMetrics.ENABLED_COMPILER_PLUGIN_ATOMICFU.key()] as? String?)?.toBoolean()?.let { enabledCompilerPluginAtomicfu = it }
        (data["enabled_compiler_plugin_jpasupport"] as? String?)?.toBoolean()?.let { enabledCompilerPluginJpaSupport = it }
        (data[BooleanMetrics.ENABLED_COMPILER_PLUGIN_LOMBOK.key()] as? String?)?.toBoolean()?.let { enabledCompilerPluginLombok = it }
        (data[BooleanMetrics.ENABLED_COMPILER_PLUGIN_NO_ARG.key()] as? String?)?.toBoolean()?.let { enabledCompilerPluginNoArg = it }
        (data[BooleanMetrics.ENABLED_COMPILER_PLUGIN_PARSELIZE.key()] as? String?)?.toBoolean()?.let { enabledCompilerPluginParcelize = it }
        (data[BooleanMetrics.ENABLED_COMPILER_PLUGIN_SAM_WITH_RECEIVER.key()] as? String?)?.toBoolean()?.let { enabledCompilerPluginSamWithReceiver = it }
        (data[BooleanMetrics.KOTLIN_KTS_USED.key()] as? String?)?.toBoolean()?.let { ktsUsed = it }
      }.build()
    }.withProjectId(data))
  }

  private fun BooleanMetrics.key() = this.toString().lowercase(Locale.getDefault())
  private fun StringMetrics.key() = this.toString().lowercase(Locale.getDefault())

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

  /**
   * Adds the associated project from the IntelliJ anonymization project id to the builder
   */
  private fun AndroidStudioEvent.Builder.withProjectId(data: Map<String, Any>): AndroidStudioEvent.Builder {
    val id = data["project"] as? String? ?: return this
    val eventLogConfiguration = EventLogConfiguration.getInstance().getOrCreate("FUS")
    val project = ProjectManager.getInstance().openProjects
      .firstOrNull { eventLogConfiguration.anonymize(it.getProjectCacheFileName()) == id }
    return this.withProjectId(project)
  }
}