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

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.r8.retrace.RetraceCommand
import com.android.utils.text.dropPrefix
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level.PROJECT
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessModuleDir
import com.intellij.util.getValue
import com.intellij.util.io.directoryStreamIfExists
import com.intellij.util.setValue
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.isDirectory
import kotlin.io.path.notExists
import kotlin.io.path.useLines

private val exceptionLinePattern = Regex("\n\\s*at .+\\((?<filename>.+)\\)\n")

private const val R8MAP_ID_PREFIX = "r8-map-id-"

private const val MAPPINGS_DIR = "build/outputs/mapping"

/**
 * Rewrites an obfuscated stack trace automatically
 *
 * Detects a mapping file from a stack trace by extracting a `r8-map-id` from the frame and looks
 * for a corresponding `mapping.txt` file in the project build directory.
 */
@Service(PROJECT)
internal class AutoProguardMessageRewriter(private val project: Project) {

  // We keep a single instance of a retracer for the latest "r8-map-id" we detect. The assumption
  // is that there's usually only going to be a single one at a time.
  private var autoRetracer by AtomicReference<AutoRetracer?>(null)

  fun rewrite(message: String, applicationId: String): String {
    val match = exceptionLinePattern.find(message) ?: return message
    val filename = match.groups["filename"]?.value ?: return message
    if (StudioFlags.LOGCAT_AUTO_DEOBFUSCATE.get()) {
      if (filename.startsWith(R8MAP_ID_PREFIX)) {
        val id = filename.dropPrefix(R8MAP_ID_PREFIX).substringBefore(':')
        val retracer = getAutoRetracer(id, applicationId)
        val result = retracer?.rewrite(message) ?: message
        if (result != message) {
          return result
        }
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
  private fun getAutoRetracer(id: String, applicationId: String): RetraceCommand.Builder? {
    val retracer = autoRetracer
    if (retracer?.id == id) {
      return retracer.builder
    }
    val mapping = findMapping(applicationId, id) ?: return null
    val builder = createRetracer(mapping)
    autoRetracer = AutoRetracer(id, builder)
    return builder
  }

  private fun findMapping(applicationId: String, mapId: String): Path? {
    val modules = project.getProjectSystem().findModulesWithApplicationId(applicationId)
    val moduleDirs =
      modules.mapNotNull { it.getModuleSystem().getHolderModule().guessModuleDir()?.toNioPath() }

    return moduleDirs.firstNotNullOfOrNull {
      val mappingDir = it.resolve(MAPPINGS_DIR)
      mappingDir.findMapping(mapId)
    }
  }

  private class AutoRetracer(val id: String, val builder: RetraceCommand.Builder)
}

/**
 * Find a mapping file with a matching `pg_map_id`
 *
 * Scans a mapping file and looks for a header line that looks like `# pg_map_id: <map-id>`
 *
 * Abandons the search as soon as the first non-comment line is encountered.
 */
private fun Path.findMapping(mapId: String): Path? {
  if (notExists() || !isDirectory()) {
    return null
  }
  val line = "# pg_map_id: $mapId"
  directoryStreamIfExists {
    it.forEach variant@{ variant ->
      if (!variant.isDirectory()) {
        return@variant
      }
      val mapping = variant.resolve("mapping.txt")
      if (mapping.notExists()) {
        return@variant
      }
      mapping.useLines { lines ->
        lines.forEach { it ->
          if (!it.startsWith('#')) {
            // Skip this file
            return@variant
          }
          if (it == line) {
            return mapping
          }
        }
      }
    }
  }
  iterator().forEach variant@{ variant -> }

  return null
}
