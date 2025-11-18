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
package com.android.tools.analytics

import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.EditorFileType
import com.google.wireless.android.sdk.stats.EditorFileType.CMAKE
import com.google.wireless.android.sdk.stats.EditorFileType.GROOVY
import com.google.wireless.android.sdk.stats.EditorFileType.JAVA
import com.google.wireless.android.sdk.stats.EditorFileType.JSON
import com.google.wireless.android.sdk.stats.EditorFileType.KOTLIN
import com.google.wireless.android.sdk.stats.EditorFileType.KOTLIN_SCRIPT
import com.google.wireless.android.sdk.stats.EditorFileType.NATIVE
import com.google.wireless.android.sdk.stats.EditorFileType.PROPERTIES
import com.google.wireless.android.sdk.stats.EditorFileType.UNKNOWN
import com.google.wireless.android.sdk.stats.EditorFileType.XML
import com.google.wireless.android.sdk.stats.EditorHighlightingStats
import com.intellij.codeInsight.daemon.impl.GeneralHighlightingPass
import com.intellij.concurrency.JobScheduler
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ReflectionUtil
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit.HOURS
import java.util.function.BiConsumer
import org.HdrHistogram.Recorder

/**
 * Tracks highlighting latency across file types.
 * To log an [AndroidStudioEvent] with the collected data, call [reportHighlightingStats].
 */
@Service
class HighlightingStats : Disposable {
  companion object {
    private const val MAX_LATENCY_MS = 10 * 60 * 1000 // Limit latencies to 10 minutes to ensure reasonable histogram size.

    @JvmStatic
    fun getInstance(): HighlightingStats {
      return ApplicationManager.getApplication().getService(HighlightingStats::class.java)
    }
  }

  fun startRecording() {
    // Our fork of IntelliJ adds a custom callback hook in GeneralHighlightingPass to allow
    // measuring highlighting latency. We set the callback using reflection to avoid compilation
    // issues in JetBrains/android. We may upstream a better solution later (b/461569054).
    val callbackInstalled = ReflectionUtil.setField(
      @Suppress("UnstableApiUsage")
      GeneralHighlightingPass::class.java,
      null,
      BiConsumer::class.java,
      "latencyCallbackForAndroidStudio",
      BiConsumer<Document, Long>(::recordHighlightingLatency),
    )
    if (callbackInstalled) {
      // Send reports hourly.
      JobScheduler.getScheduler().scheduleWithFixedDelay(::reportHighlightingStats, 1, 1, HOURS)
    } else {
      // This might happen if we lost the platform patch for some reason,
      // e.g. if we're running with upstream intellij-community sources.
      thisLogger().warn("Failed to install hook for measuring highlighting latency")
    }
  }

  override fun dispose() {
    // Send reports on application close.
    reportHighlightingStats()
  }

  /**
   * Maps file types to latency recorders.
   * We use [Recorder] to allow thread-safe read access from background threads.
   */
  private val latencyRecorders = ConcurrentHashMap<EditorFileType, Recorder>()

  fun recordHighlightingLatency(document: Document, latencyMs: Long) {
    if (latencyMs < 0 || latencyMs > MAX_LATENCY_MS) return
    val file = FileDocumentManager.getInstance().getFile(document) ?: return
    val fileType = convertFileType(file)
    val recorder = latencyRecorders.computeIfAbsent(fileType) { Recorder(1) }
    recorder.recordValue(latencyMs)
  }

  /**
   * Logs an [AndroidStudioEvent] with editor highlighting stats.
   * Resets statistics so that counts are not double-counted in the next report.
   */
  fun reportHighlightingStats() {
    val allStats = EditorHighlightingStats.newBuilder()
    for ((fileType, recorder) in latencyRecorders) {
      val histogram = recorder.intervalHistogram // Automatically resets statistics for this recorder.
      if (histogram.totalCount == 0L) {
        continue
      }
      val record = EditorHighlightingStats.Stats.newBuilder().also {
        it.fileType = fileType
        it.histogram = histogram.toProto()
      }
      allStats.addByFileType(record.build())
    }

    if (allStats.byFileTypeCount == 0) {
      return
    }

    UsageTracker.log(
      AndroidStudioEvent.newBuilder().apply {
        kind = AndroidStudioEvent.EventKind.EDITOR_HIGHLIGHTING_STATS
        editorHighlightingStats = allStats.build()
      }
    )
  }

  /** Converts from file type name to proto enum value. */
  private fun convertFileType(file: VirtualFile): EditorFileType {
    if (file.fileType.name.isCMakeFileByName()) return CMAKE
    return when (file.fileType.name) {
      // We use string literals here (rather than, e.g., JsonFileType.INSTANCE.name) to avoid unnecessary
      // dependencies on other plugins. Fortunately, these values are extremely unlikely to change.
      "JAVA" -> JAVA
      "Kotlin" -> if (file.extension == "kts") KOTLIN_SCRIPT else KOTLIN
      "XML" -> XML
      "Groovy" -> GROOVY
      "Properties" -> PROPERTIES
      "JSON" -> JSON
      "ObjectiveC" -> NATIVE
      else -> UNKNOWN
    }
  }

  private fun String.isCMakeFileByName(): Boolean = this == "CMakeLists.txt" || StringUtil.endsWithIgnoreCase(this, ".cmake")

}
