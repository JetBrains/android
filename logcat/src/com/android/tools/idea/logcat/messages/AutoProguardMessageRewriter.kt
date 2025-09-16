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
import com.android.tools.idea.logcat.messages.AutoProguardMessageRewriter.Result.Error
import com.android.tools.idea.logcat.messages.AutoProguardMessageRewriter.Result.Reason.BUILD_DIR_NOT_FOUND
import com.android.tools.idea.logcat.messages.AutoProguardMessageRewriter.Result.Reason.MAPPINGS_DIR_NOT_FOUND
import com.android.tools.idea.logcat.messages.AutoProguardMessageRewriter.Result.Reason.MAPPINGS_FILE_NOT_FOUND
import com.android.tools.idea.logcat.messages.AutoProguardMessageRewriter.Result.Reason.MAPPINGS_HAVE_NO_MAP_ID
import com.android.tools.idea.logcat.messages.AutoProguardMessageRewriter.Result.Reason.MATCHING_MAPPING_NOT_FOUND
import com.android.tools.idea.logcat.messages.AutoProguardMessageRewriter.Result.Reason.MODULES_NOT_FOUND
import com.android.tools.idea.logcat.messages.AutoProguardMessageRewriter.Result.Reason.MODULE_DIR_NOT_FOUND
import com.android.tools.idea.logcat.messages.AutoProguardMessageRewriter.Result.Success
import com.android.tools.idea.logcat.util.LogcatUsageTracker
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.r8.retrace.RetraceCommand
import com.android.utils.associateByNotNull
import com.android.utils.text.dropPrefix
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level.PROJECT
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessModuleDir
import com.intellij.openapi.project.modules
import com.intellij.util.Alarm
import com.intellij.util.io.directoryStreamIfExists
import java.nio.file.Path
import kotlin.io.path.fileSize
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.useLines
import kotlin.time.measureTimedValue
import org.jetbrains.annotations.VisibleForTesting

private val exceptionLinePattern = Regex("\n\\s*at .+\\(r8-map-id-(?<mapId>.+):\\d+\\)\n")

private const val BUILD_DIR = "build"
private const val MAPPINGS_DIR = "outputs/mapping"
private const val MAPPINGS_FILE = "mapping.txt"
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

  fun rewrite(message: String, applicationId: String): String {
    if (StudioFlags.LOGCAT_AUTO_DEOBFUSCATE.get()) {
      try {
        val match = exceptionLinePattern.find(message) ?: return message
        val id = match.groups["mapId"]?.value ?: return message
        val modules = getModuleCandidates(applicationId)
        if (modules.isEmpty()) {
          LogcatUsageTracker.logRetrace(MODULES_NOT_FOUND.toString())
          return message
        }
        val retracer = getAutoRetracer(id, modules) ?: return message // tracked by getAutoRetracer

        val (retraced, duration) = measureTimedValue { retracer.builder.rewrite(message) }
        val result = if (retraced != message) "SUCCESS" else "NOOP"
        LogcatUsageTracker.logRetrace(result, duration, retracer.mappingSize, retracer.isCached)
        return retraced
      } catch (e: Throwable) {
        LogcatUsageTracker.logRetraceException(e)
        throw e
      }
    }
    return message
  }

  /**
   * If we were able to determine the application id, restrict candidates to matching modules.
   * Otherwise, check all modules.
   */
  private fun getModuleCandidates(applicationId: String): Collection<Module> {
    return when (applicationId.startsWith("pid-")) {
      true -> project.modules.asList()
      false -> project.getProjectSystem().findModulesWithApplicationId(applicationId)
    }
  }

  /**
   * If the current auto retraces matches the stacktrace id, we use it. Otherwise, we try to find a
   * matching mapping.txt based on the default project structure:
   * ```
   *    <module-dir>/build/outputs/mapping/<variant>/mapping.txt
   * ```
   */
  private fun getAutoRetracer(id: String, modules: Collection<Module>): AutoRetracer? {
    synchronized(lock) {
      val retracer = autoRetracer
      if (retracer?.id == id) {
        rescheduleCachePurge()
        return retracer.copy(isCached = true)
      }
      val result = findMapping(modules, id)
      if (result is Error) {
        LogcatUsageTracker.logRetrace(result.reason.toString())
        return null
      }
      val mappings = (result as Success).path
      val builder = createRetracer(mappings)
      autoRetracer = AutoRetracer(id, builder, mappings, false)
      rescheduleCachePurge()
      return autoRetracer
    }
  }

  private fun rescheduleCachePurge() {
    alarm.cancelAllRequests()
    alarm.addRequest(
      { synchronized(lock) { autoRetracer = null } },
      StudioFlags.LOGCAT_AUTO_DEOBFUSCATE_CACHE_TIME_MS.get(),
    )
  }

  private fun findMapping(modules: Collection<Module>, mapId: String): Result {
    val moduleDirs =
      modules.mapNotNull { it.getModuleSystem().getHolderModule().guessModuleDir()?.toNioPath() }
    if (moduleDirs.isEmpty()) {
      return Error(MODULE_DIR_NOT_FOUND)
    }
    val buildDirs =
      moduleDirs.mapNotNull { it.resolve(BUILD_DIR).takeIf { path -> path.isDirectory() } }
    if (buildDirs.isEmpty()) {
      return Error(BUILD_DIR_NOT_FOUND)
    }
    val mappingsDirs =
      buildDirs.mapNotNull { it.resolve(MAPPINGS_DIR).takeIf { path -> path.isDirectory() } }
    if (mappingsDirs.isEmpty()) {
      return Error(MAPPINGS_DIR_NOT_FOUND)
    }
    val mappingsFiles = mappingsDirs.flatMap { it.findMappingFiles() }
    if (mappingsFiles.isEmpty()) {
      return Error(MAPPINGS_FILE_NOT_FOUND)
    }

    val mappingsById = mappingsFiles.associateByNotNull { it.getMapId() }
    if (mappingsById.isEmpty()) {
      return Error(MAPPINGS_HAVE_NO_MAP_ID)
    }
    val mapping = mappingsById[mapId] ?: return Error(MATCHING_MAPPING_NOT_FOUND)
    return Success(mapping)
  }

  override fun dispose() {}

  @VisibleForTesting
  data class AutoRetracer(
    val id: String,
    val builder: RetraceCommand.Builder,
    val mapping: Path,
    // Mapping file might have changed, but we still have a valid cached retracer
    val isCached: Boolean,
    val mappingSize: Long = mapping.fileSize(),
  )

  private sealed class Result {
    enum class Reason {
      MODULES_NOT_FOUND,
      MODULE_DIR_NOT_FOUND,
      BUILD_DIR_NOT_FOUND,
      MAPPINGS_DIR_NOT_FOUND,
      MAPPINGS_FILE_NOT_FOUND,
      MAPPINGS_HAVE_NO_MAP_ID,
      MATCHING_MAPPING_NOT_FOUND,
    }

    class Success(val path: Path) : Result()

    class Error(val reason: Reason) : Result()
  }
}

private fun Path.findMappingFiles(): List<Path> {
  return buildList {
    directoryStreamIfExists { paths ->
      paths.forEach variant@{ variant ->
        if (!variant.isDirectory()) {
          return@variant
        }
        val mapping = variant.resolve(MAPPINGS_FILE)
        if (mapping.isRegularFile()) {
          add(mapping)
        }
      }
    }
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
