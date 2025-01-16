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

import com.android.resources.ResourceFolderType
import com.android.resources.ResourceFolderType.ANIMATOR
import com.android.resources.ResourceFolderType.COLOR
import com.android.resources.ResourceFolderType.DRAWABLE
import com.android.resources.ResourceFolderType.FONT
import com.android.resources.ResourceFolderType.INTERPOLATOR
import com.android.resources.ResourceFolderType.LAYOUT
import com.android.resources.ResourceFolderType.MENU
import com.android.resources.ResourceFolderType.MIPMAP
import com.android.resources.ResourceFolderType.NAVIGATION
import com.android.resources.ResourceFolderType.RAW
import com.android.resources.ResourceFolderType.TRANSITION
import com.android.resources.ResourceFolderType.VALUES
import com.android.tools.analytics.UsageTracker
import com.android.tools.analytics.withProjectId
import com.android.tools.idea.concurrency.AndroidDispatchers.workerThread
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind.DEBUGGER_EVENT
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind.EDITING_METRICS_EVENT
import com.google.wireless.android.sdk.stats.DebuggerEvent
import com.google.wireless.android.sdk.stats.DebuggerEvent.FramesViewUpdated
import com.google.wireless.android.sdk.stats.DebuggerEvent.FramesViewUpdated.FileTypeInfo
import com.google.wireless.android.sdk.stats.DebuggerEvent.Type.FRAMES_VIEW_UPDATED
import com.google.wireless.android.sdk.stats.EditorFileType
import com.google.wireless.android.sdk.stats.EditorFileType.XML_RES_ANIM
import com.google.wireless.android.sdk.stats.FileType
import com.google.wireless.android.sdk.stats.FileUsage
import com.google.wireless.android.sdk.stats.IntelliJNewUISwitch
import com.google.wireless.android.sdk.stats.KotlinGradlePerformance
import com.google.wireless.android.sdk.stats.KotlinGradlePerformance.FirUsage
import com.google.wireless.android.sdk.stats.KotlinProjectConfiguration
import com.google.wireless.android.sdk.stats.RunFinishData
import com.google.wireless.android.sdk.stats.RunStartData
import com.google.wireless.android.sdk.stats.StartupEvent
import com.google.wireless.android.sdk.stats.StartupPerformanceCodeLoadedAndVisibleInEditor
import com.google.wireless.android.sdk.stats.StartupPerformanceFirstUiShownEvent
import com.google.wireless.android.sdk.stats.StartupPerformanceFrameBecameInteractiveEvent
import com.google.wireless.android.sdk.stats.StartupPerformanceFrameBecameVisibleEvent
import com.google.wireless.android.sdk.stats.StartupPerformanceFrameBecameVisibleEvent.ProjectType
import com.google.wireless.android.sdk.stats.VfsRefresh
import com.intellij.ide.ui.experimental.ExperimentalUiCollector
import com.intellij.internal.statistic.eventLog.EmptyEventLogFilesProvider
import com.intellij.internal.statistic.eventLog.EventLogConfiguration
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.StatisticsEventLogger
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.getProjectCacheFileName
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.xdebugger.impl.XDebuggerActionsCollector
import kotlinx.coroutines.withContext
import org.jetbrains.kotlin.statistics.metrics.BooleanMetrics
import org.jetbrains.kotlin.statistics.metrics.StringMetrics
import java.util.Locale
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

object AndroidStudioEventLogger : StatisticsEventLogger {

  private val logExecutor = AppExecutorUtil.createBoundedApplicationPoolExecutor("AndroidStudioEventLogger", 1)

  override fun cleanup() {}
  override fun getActiveLogFile(): Nothing? = null
  override fun getLogFilesProvider() = EmptyEventLogFilesProvider
  override fun logAsync(group: EventLogGroup, eventId: String, data: Map<String, Any>, isState: Boolean): CompletableFuture<Void> {
    val callbacks = mapOf("debugger.breakpoints.usage" to ::logDebuggerBreakpointsUsage,
                          "documentation" to ::logQuickDocEvent,
                          "experimental.ui.interactions" to ::logNewUIStateChange,
                          "file.types" to ::logFileType,
                          "file.types.usage" to ::logFileTypeUsage,
                          "kotlin.gradle.performance" to ::logKotlinGradlePerformance,
                          "kotlin.project.configuration" to ::logKotlinProjectConfiguration,
                          "reopen.project.startup.performance" to ::logReopenProjectStartupPerformance,
                          "run.configuration.exec" to ::logRunConfigurationExec,
                          "startup" to ::logStartupEvent,
                          "vfs" to ::logVfsEvent,
                          "xdebugger.actions" to ::logDebuggerEvent)
    val builder = callbacks[group.id]?.invoke(eventId, data) ?: return CompletableFuture.completedFuture(null)
    return CompletableFuture.runAsync({ UsageTracker.log(builder) }, logExecutor)
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

  private fun logNewUIStateChange(eventId: String, data: Map<String, Any>): AndroidStudioEvent.Builder? {
    if (eventId != "switch.ui") {
      return null
    }

    return AndroidStudioEvent.newBuilder().apply {
      kind = AndroidStudioEvent.EventKind.INTELLIJ_NEW_UI_SWITCH
      intellijNewUiSwitch = IntelliJNewUISwitch.newBuilder().apply {
        switchSource = getSwitchSource(data)
        (data["exp_ui"] as? Boolean)?.let { newUi = it }
      }.build()
    }
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

  private fun logFileType(eventId: String, data: Map<String, Any>): AndroidStudioEvent.Builder? {
    // filter out events that Jetbrains does not require
    if (eventId != "file.in.project") {
      return null
    }

    return AndroidStudioEvent.newBuilder().apply {
      kind = AndroidStudioEvent.EventKind.FILE_TYPE
      fileType = FileType.newBuilder().apply {
        (data["file_type"] as? String)?.let { fileType = it }
        (data["plugin_type"] as? String)?.let { pluginType = it }
        (data["count"] as? String)?.toIntOrNull()?.let { numberOfFiles = it }
      }.build()
    }.withProjectId(data)
  }

  private fun logFileTypeUsage(eventId: String, data: Map<String, Any>): AndroidStudioEvent.Builder? {
    // filter out events that Jetbrains does not require
    if (eventId == "registered") {
      return null
    }

    return AndroidStudioEvent.newBuilder().apply {
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
    }.withProjectId(data)
  }

  private fun logKotlinGradlePerformance(eventId: String, data: Map<String, Any>): AndroidStudioEvent.Builder? {
    if (eventId != "All") {
      return null
    }

    return AndroidStudioEvent.newBuilder().apply {
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
    }.withProjectId(data, "project_path")
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

  private fun logKotlinProjectConfiguration(eventId: String, data: Map<String, Any>): AndroidStudioEvent.Builder? {
    // filter out events that Jetbrains does not require
    if (eventId == "invoked") {
      return null
    }

    return AndroidStudioEvent.newBuilder().apply {
      kind = AndroidStudioEvent.EventKind.KOTLIN_PROJECT_CONFIGURATION
      kotlinProjectConfiguration = KotlinProjectConfiguration.newBuilder().apply {
        (data["system"] as? String?)?.let { system = it }
        (data["plugin_version"] as? String?)?.let { pluginVersion = it }
        (data["plugin"] as? String?)?.let { plugin = it }
        (data["plugin_type"] as? String?)?.let { pluginType = it }
        (data["platform"] as? String?)?.let { platform = it }
        (data["isMPP"] as? Boolean?)?.let { isMultiplatform = it }
        (data["eventFlags"] as? Long?)?.let { eventFlags = it }
        eventType = when (eventId) {
          "Build" -> KotlinProjectConfiguration.EventType.BUILD
          else -> KotlinProjectConfiguration.EventType.TYPE_UNKNOWN
        }
      }.build()
    }.withProjectId(data)
  }

  private fun logRunConfigurationExec(eventId: String, data: Map<String, Any>): AndroidStudioEvent.Builder? {
    return when (eventId) {
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

      else -> return null
    }.withProjectId(data)
  }

  private fun logVfsEvent(eventId: String, data: Map<String, Any>): AndroidStudioEvent.Builder? {
    if (!eventId.equals("refreshed")) { // eventId as declared in com.intellij.openapi.vfs.newvfs.RefreshProgress
      return null
    }

    (data["duration_ms"] as? Long)?.let { durationMs ->
      return AndroidStudioEvent.newBuilder()
        .setKind(AndroidStudioEvent.EventKind.VFS_REFRESH)
        .setVfsRefresh(VfsRefresh.newBuilder().setDurationMs(durationMs))
    }
    return null
  }

  private fun logStartupEvent(eventId: String, data: Map<String, Any>) : AndroidStudioEvent.Builder? {
    val type = when (eventId) {
      "totalDuration" -> StartupEvent.Type.TOTAL_DURATION

      "splash" -> StartupEvent.Type.SPLASH

      "bootstrap" -> StartupEvent.Type.BOOTSTRAP

      "appInit" -> StartupEvent.Type.APP_INIT

      "splashShown" -> StartupEvent.Type.SPLASH_SHOWN

      "splashHidden" -> StartupEvent.Type.SPLASH_HIDDEN

      "projectFrameVisible" -> StartupEvent.Type.PROJECT_FRAME_VISIBLE

      else -> return null
    }
    return AndroidStudioEvent.newBuilder().setKind(AndroidStudioEvent.EventKind.STARTUP_EVENT).setStartupEvent(
      StartupEvent.newBuilder().setType(type).setDurationMs(data["duration"] as Int))
  }

  private fun logReopenProjectStartupPerformance(eventId: String, data: Map<String, Any>) : AndroidStudioEvent.Builder? {
    return when (eventId) {
      "first.ui.shown" -> AndroidStudioEvent.newBuilder().apply {
        kind = AndroidStudioEvent.EventKind.STARTUP_PERFORMANCE_FIRST_UI_SHOWN
        startupPerformanceFirstUiShownEvent = StartupPerformanceFirstUiShownEvent.newBuilder().apply {
          (data["duration_ms"] as? Int?)?.let { durationMs = it }
          (data["type"] as? String?)?.let {
            type = when (it) {
              "Splash" -> StartupPerformanceFirstUiShownEvent.UiResponseType.SPLASH
              "Frame" -> StartupPerformanceFirstUiShownEvent.UiResponseType.FRAME
              else -> StartupPerformanceFirstUiShownEvent.UiResponseType.UNKNOWN_UI_RESPONSE_TYPE
            }
          }
        }.build()
      }

      "frame.became.visible" -> AndroidStudioEvent.newBuilder().apply {
        kind = AndroidStudioEvent.EventKind.STARTUP_PERFORMANCE_FRAME_BECAME_VISIBLE
        startupPerformanceFrameBecameVisibleEvent = StartupPerformanceFrameBecameVisibleEvent.newBuilder().apply {
          (data["duration_ms"] as? Int?)?.let { durationMs = it }
          (data["has_settings"] as? Boolean?)?.let { hasSettings = it }
          (data["projects_type"] as? String?)?.let {
            projectType = when (it) {
              "Reopened" -> ProjectType.REOPENED
              "FromFilesToLoad" -> ProjectType.FROM_FILES_TO_LOAD
              "FromArgs" -> ProjectType.FROM_ARGS
              else -> ProjectType.UNKNOWN_PROJECT_TYPE
            }
          }
        }.build()
      }

      "frame.became.interactive" -> AndroidStudioEvent.newBuilder().apply {
        kind = AndroidStudioEvent.EventKind.STARTUP_PERFORMANCE_FRAME_BECAME_INTERACTIVE
        startupPerformanceFrameBecameInteractiveEvent = StartupPerformanceFrameBecameInteractiveEvent.newBuilder().apply {
          (data["duration_ms"] as? Int?)?.let { durationMs = it }
        }.build()
      }

      "code.loaded.and.visible.in.editor" -> AndroidStudioEvent.newBuilder().apply {
        kind = AndroidStudioEvent.EventKind.STARTUP_PERFORMANCE_CODE_LOADED_AND_VISIBLE_IN_EDITOR
        startupPerformanceCodeLoadedAndVisibleInEditor = StartupPerformanceCodeLoadedAndVisibleInEditor.newBuilder().apply {
          (data["duration_ms"] as? Int?)?.let { durationMs = it }
          (data["file_type"] as? String?)?.let { fileType = it }
          (data["has_settings"] as? Boolean?)?.let { hasSettings = it }
          (data["loaded_cached_markup"] as? Boolean?)?.let { loadedCachedMarkup = it }
          (data["no_editors_to_open"] as? Boolean?)?.let { noEditorsToOpen = it }
          (data["source_of_selected_editor"] as? String?)?.let { sourceOfSelectedEditor = when(it) {
            "TextEditor" -> StartupPerformanceCodeLoadedAndVisibleInEditor.SourceOfSelectedEditor.TEXT_EDITOR
            "FoundReadmeFile" -> StartupPerformanceCodeLoadedAndVisibleInEditor.SourceOfSelectedEditor.FOUND_README_FILE
            else -> StartupPerformanceCodeLoadedAndVisibleInEditor.SourceOfSelectedEditor.UNKNOWN_SOURCE_OF_SELECTED_EDITOR
          } }
        }.build()
      }

      else -> null
    }
  }

  private fun logDebuggerBreakpointsUsage(eventId: String, data: Map<String, Any>): AndroidStudioEvent.Builder? {
    when (eventId) {
      "breakpoint.added" -> {
        val type = data["type"] as? String ?: return null
        val pluginType = data["plugin_type"] as? String ?: return null
        val withinSession = data["within_session"] as? Boolean ?: return null

        return AndroidStudioEvent.newBuilder()
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
      }
    }
    return null
  }


  private fun logDebuggerEvent(eventId: String, data: Map<String, Any>) : AndroidStudioEvent.Builder? {
    val event = when (eventId) {
      XDebuggerActionsCollector.EVENT_FRAMES_UPDATED -> {
        @Suppress("UnstableApiUsage")
        val durationMs = data[EventFields.DurationMs.name] as? Long ?: return null
        val totalFrames = data[XDebuggerActionsCollector.TOTAL_FRAMES] as? Int ?: return null

        @Suppress("UNCHECKED_CAST")
        val fileTypes = data[XDebuggerActionsCollector.FILE_TYPES] as? List<String> ?: return null

        @Suppress("UNCHECKED_CAST")
        val framesPerFileType = data[XDebuggerActionsCollector.FRAMES_PER_TYPE] as? List<Int> ?: return null

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

      else -> return null
    }
    return AndroidStudioEvent.newBuilder().setKind(DEBUGGER_EVENT).setDebuggerEvent(event)
  }

  private fun logQuickDocEvent(eventId: String, data: Map<String, Any>): AndroidStudioEvent.Builder? {
    if (eventId != "quick.doc.closed") return null
    val fileTypeString = data["file_type"] as? String
    val durationMsLong = data["duration_ms"] as? Long
    if (fileTypeString == null) {
      thisLogger().error("file_type was not a String, instead was $fileTypeString")
      return null
    }
    if (durationMsLong == null) {
      thisLogger().error("duration_ms was not a Long, instead was $durationMsLong")
      return null
    }
    return AndroidStudioEvent.newBuilder().setKind(EDITING_METRICS_EVENT).apply {
      editingMetricsEventBuilder.apply {
        quickDocEventBuilder.apply {
          fileType = fileTypeString.toEditorFileType()
          shownDurationMs = durationMsLong
        }
      }
    }
  }

  private fun String.toEditorFileType() = when(this) {
    "JAVA" -> EditorFileType.JAVA
    "Kotlin" -> EditorFileType.KOTLIN
    "Groovy" -> EditorFileType.GROOVY
    "Properties" -> EditorFileType.PROPERTIES
    "JSON" -> EditorFileType.JSON
    "ObjectiveC" -> EditorFileType.NATIVE // Derived from OCLanguage constructor.
    "XML" -> EditorFileType.XML
    "protobuf" -> EditorFileType.PROTO
    "TOML" -> EditorFileType.TOML
    "Dart" -> EditorFileType.DART // https://github.com/JetBrains/intellij-plugins/blob/master/Dart/src/com/jetbrains/lang/dart/DartFileType.java
    else -> EditorFileType.UNKNOWN
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