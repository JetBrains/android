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

package com.android.tools.idea.logcat.messages

import com.android.annotations.concurrency.GuardedBy
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.logcat.LogcatR8MappingsToken
import com.android.tools.idea.logcat.messages.AutoProguardMessageRewriter.Result.Error
import com.android.tools.idea.logcat.messages.AutoProguardMessageRewriter.Result.Mapping
import com.android.tools.idea.logcat.messages.AutoProguardMessageRewriter.Result.PartitionedMapping
import com.android.tools.idea.logcat.messages.AutoProguardMessageRewriter.Result.Reason.MAPPINGS_HAVE_NO_MAP_ID
import com.android.tools.idea.logcat.messages.AutoProguardMessageRewriter.Result.Reason.MATCHING_MAPPING_NOT_FOUND
import com.android.tools.idea.logcat.messages.AutoProguardMessageRewriter.Result.Reason.NO_MAPPING_IN_PROJECT
import com.android.tools.idea.logcat.messages.AutoProguardMessageRewriter.Result.TextMapping
import com.android.tools.idea.logcat.util.LOGGER
import com.android.tools.idea.logcat.util.LogcatUsageTracker
import com.android.tools.r8.retrace.RetraceCommand
import com.android.utils.associateByNotNull
import com.android.utils.text.dropPrefix
import com.google.wireless.android.sdk.stats.LogcatUsageEvent.StackRetraceEvent.MappingType
import com.google.wireless.android.sdk.stats.LogcatUsageEvent.StackRetraceEvent.MappingType.PARTITIONED
import com.google.wireless.android.sdk.stats.LogcatUsageEvent.StackRetraceEvent.MappingType.TEXT
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level.PROJECT
import com.intellij.openapi.project.Project
import com.intellij.util.Alarm
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.io.path.useLines
import kotlin.time.measureTimedValue
import org.jetbrains.annotations.VisibleForTesting

private val exceptionLinePattern = Regex("\n\\s*at .+\\(r8-map-id-(?<mapId>.+):\\d+\\)\n")

private const val MAP_ID_PREFIX = "# pg_map_id: "

/**
 * Rewrites an obfuscated stack trace automatically
 *
 * Detects a mapping file from a stack trace by extracting a `r8-map-id` from the frame and looks
 * for a corresponding `mapping.txt` file in the project build directory.
 */
@Service(PROJECT)
internal class AutoProguardMessageRewriter(private val project: Project) : Disposable {

  private val lock = Any()
  private val alarm = Alarm(this)

  // We keep a single instance of a retracer for the latest "r8-map-id" we detect. The assumption
  // is that there's usually only going to be a single one at a time.
  @VisibleForTesting @GuardedBy("lock") var autoRetracer: AutoRetracer? = null

  fun getMapping(): Path? {
    synchronized(lock) {
      return autoRetracer?.mapping
    }
  }

  fun rewrite(message: String): String {
    if (StudioFlags.LOGCAT_AUTO_DEOBFUSCATE.get()) {
      try {
        val match = exceptionLinePattern.find(message) ?: return message
        val id = match.groups["mapId"]?.value ?: return message
        val retracer = getAutoRetracer(id) ?: return message // tracked by getAutoRetracer

        val (retraced, duration) =
          try {
            measureTimedValue { retracer.builder.rewrite(message) }
          } catch (e: Throwable) {
            LogcatUsageTracker.logRetraceException(
              e,
              retracer.mappingSize,
              retracer.isCached,
              retracer.mappingType,
            )
            LOGGER.warn(
              "Error while retracing. mappingSize=${retracer.mappingSize} isCached=${retracer.isCached}"
            )
            return message
          }
        val result = if (retraced != message) "SUCCESS" else "NOOP"
        LogcatUsageTracker.logRetrace(
          result,
          duration,
          retracer.mappingSize,
          retracer.isCached,
          retracer.mappingType,
        )
        return retraced
      } catch (e: Throwable) {
        LogcatUsageTracker.logRetraceException(e)
        LOGGER.warn("Error while rewriting message.")
        return message
      }
    }
    return message
  }

  /**
   * If the current auto retraces matches the stacktrace id, we use it. Otherwise, we try to find a
   * matching mapping.txt based on the default project structure:
   * ```
   *    <module-dir>/build/outputs/mapping/<variant>/mapping.txt
   * ```
   */
  private fun getAutoRetracer(id: String): AutoRetracer? {
    synchronized(lock) {
      val retracer = autoRetracer
      if (retracer?.id == id) {
        rescheduleCachePurge()
        return retracer.copy(isCached = true)
      }
      return when (val result = findMapping(id)) {
        is Error -> {
          LogcatUsageTracker.logRetrace(result.reason.toString())
          null
        }
        is Mapping -> {
          val mappings = result.path
          val builder = result.createRetracer()
          autoRetracer = AutoRetracer(id, builder, mappings, false, result.mappingType)
          rescheduleCachePurge()
          autoRetracer
        }
      }
    }
  }

  private fun rescheduleCachePurge() {
    alarm.cancelAllRequests()
    alarm.addRequest(
      { synchronized(lock) { autoRetracer = null } },
      StudioFlags.LOGCAT_AUTO_DEOBFUSCATE_CACHE_TIME_MS.get(),
    )
  }

  private fun findMapping(mapId: String): Result {
    val mappingsFiles = LogcatR8MappingsToken.getR8Mappings(project)
    if (mappingsFiles.isEmpty()) {
      return Error(NO_MAPPING_IN_PROJECT)
    }
    val mappingsById =
      mappingsFiles.filter { it.text.exists() }.associateByNotNull { it.text.getMapId() }
    if (mappingsById.isEmpty()) {
      return Error(MAPPINGS_HAVE_NO_MAP_ID)
    }
    val mapping = mappingsById[mapId] ?: return Error(MATCHING_MAPPING_NOT_FOUND)
    return when (mapping.partitioned?.exists() == true) {
      true -> PartitionedMapping(mapping.partitioned)
      false -> TextMapping(mapping.text)
    }
  }

  override fun dispose() {}

  @VisibleForTesting
  data class AutoRetracer(
    val id: String,
    val builder: RetraceCommand.Builder,
    val mapping: Path,
    // Mapping file might have changed, but we still have a valid cached retracer
    val isCached: Boolean,
    val mappingType: MappingType,
    val mappingSize: Long = mapping.fileSize(),
  )

  private sealed class Result {
    enum class Reason {
      MAPPINGS_HAVE_NO_MAP_ID,
      MATCHING_MAPPING_NOT_FOUND,
      NO_MAPPING_IN_PROJECT,
    }

    sealed class Mapping(val path: Path, val mappingType: MappingType) : Result() {
      abstract fun createRetracer(): RetraceCommand.Builder
    }

    class TextMapping(path: Path) : Mapping(path, TEXT) {
      override fun createRetracer() = createTextRetracer(path)
    }

    class PartitionedMapping(path: Path) : Mapping(path, PARTITIONED) {
      override fun createRetracer() = createPartitionedRetracer(path)
    }

    class Error(val reason: Reason) : Result()
  }
}

private fun Path.getMapId(): String? {
  useLines { lines ->
    lines.forEach {
      if (!it.startsWith('#')) {
        return@forEach
      }
      if (it.startsWith(MAP_ID_PREFIX)) {
        return it.dropPrefix(MAP_ID_PREFIX)
      }
    }
  }
  return null
}
