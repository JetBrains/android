/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.profilers.perfetto.traceconv

import com.android.tools.nativeSymbolizer.getLlvmSymbolizerPath
import com.google.common.annotations.VisibleForTesting
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.io.FileUtil
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object TraceconvBundler {
  private val LOGGER = Logger.getInstance(TraceconvBundler::class.java)
  private val inProgressTasks = ConcurrentHashMap<String, CompletableFuture<File?>>()

  private const val TRACECONV_TIMEOUT_MS = 120_000

  @JvmStatic
  @JvmOverloads
  fun bundle(
    traceFile: File,
    symbolDirs: List<String> = emptyList(),
    proguardMaps: Map<String, String> = emptyMap(),
  ): File? {
    val validProguardMaps = proguardMaps.filter { File(it.value).exists() }
    if ((symbolDirs.isEmpty() && validProguardMaps.isEmpty()) || !traceFile.exists()) {
      return null
    }

    val canonicalPath =
      try {
        traceFile.canonicalPath
      } catch (e: Exception) {
        LOGGER.warn("Failed to resolve canonical path for trace ${traceFile.name}", e)
        return null
      }

    val bundleTask = CompletableFuture<File?>()
    val inProgressTask = inProgressTasks.putIfAbsent(canonicalPath, bundleTask)
    if (inProgressTask != null) {
      LOGGER.debug("traceconv bundle already in progress for trace: ${traceFile.name}, waiting for completion")
      return try {
        inProgressTask.get(TRACECONV_TIMEOUT_MS.toLong() + 1000, TimeUnit.MILLISECONDS)
      } catch (e: Exception) {
        LOGGER.warn("Error or timeout while waiting for concurrent traceconv bundle for trace: ${traceFile.name}", e)
        null
      }
    }

    try {
      val bundledFile = executeBundle(traceFile, symbolDirs, validProguardMaps)
      bundleTask.complete(bundledFile)
      return bundledFile
    } finally {
      bundleTask.complete(null)
      inProgressTasks.remove(canonicalPath)
    }
  }

  private fun executeBundle(
    traceFile: File,
    symbolDirs: List<String>,
    proguardMaps: Map<String, String>,
  ): File? {
    var tempOut: File? = null
    var success = false
    return try {
      val execPath = TraceconvManager.getExecutablePath()
      val execFile = File(execPath)
      if (!execFile.exists() || (!execFile.canExecute() && !execFile.setExecutable(true))) {
        LOGGER.warn("traceconv executable not found or cannot be executed at $execPath")
        return null
      }

      LOGGER.debug("Starting traceconv bundle for trace: ${traceFile.name}")
      tempOut = File(FileUtil.getTempDirectory(), "bundled_${java.util.UUID.randomUUID()}.temp")

      val command = buildBundleCommand(traceFile, tempOut, symbolDirs, proguardMaps)
      LOGGER.info("Running traceconv command: $command")

      val commandLine = GeneralCommandLine(command)
      configureEnvironment(commandLine.environment)

      val processHandler = CapturingProcessHandler(commandLine)
      val processOutput = processHandler.runProcess(TRACECONV_TIMEOUT_MS)

      if (processOutput.isTimeout) {
        LOGGER.warn("traceconv bundle timed out for trace: ${traceFile.name}")
        processHandler.destroyProcess()
        return null
      }

      if (processOutput.exitCode != 0) {
        val error = processOutput.stderr.trim().ifEmpty { processOutput.stdout.trim() }
        LOGGER.warn("traceconv bundle failed for trace ${traceFile.name} (exit code: ${processOutput.exitCode}): $error")
        return null
      }

      LOGGER.debug("Successfully bundled trace ${traceFile.name}")
      success = true
      tempOut
    } catch (e: Exception) {
      LOGGER.warn("Exception during traceconv bundle for trace ${traceFile.name}", e)
      null
    } finally {
      if (!success) {
        tempOut?.let { FileUtil.delete(it) }
      }
    }
  }

  @VisibleForTesting
  internal fun configureEnvironment(
    environment: MutableMap<String, String>,
    symbolizerPathProvider: () -> String = { getLlvmSymbolizerPath() },
  ) {
    try {
      val symbolizerPath = symbolizerPathProvider()
      val symbolizerFile = File(symbolizerPath)
      if (!symbolizerFile.exists()) {
        LOGGER.warn("llvm-symbolizer not found at path: $symbolizerPath")
        return
      }

      environment["PERFETTO_LLVM_SYMBOLIZER_PATH"] = symbolizerPath

      val symbolizerDir = symbolizerFile.parentFile?.absolutePath
      if (!symbolizerDir.isNullOrEmpty()) {
        val pathKey = environment.keys.firstOrNull { it.equals("PATH", ignoreCase = true) } ?: "PATH"
        val existingPath = environment[pathKey] ?: System.getenv("PATH")
        environment[pathKey] = if (existingPath.isNullOrEmpty()) symbolizerDir else "$symbolizerDir${File.pathSeparator}$existingPath"
      }
    } catch (e: Exception) {
      LOGGER.warn("Failed to configure llvm-symbolizer path for traceconv", e)
    }
  }

  @VisibleForTesting
  internal fun buildBundleCommand(
    traceFile: File,
    outputFile: File,
    symbolDirs: List<String> = emptyList(),
    proguardMaps: Map<String, String> = emptyMap(),
    verbose: Boolean = LOGGER.isDebugEnabled,
  ): List<String> {
    val command = mutableListOf<String>()
    command.add(TraceconvManager.getExecutablePath())
    command.add("bundle")
    if (symbolDirs.isNotEmpty()) {
      command.add("--symbol-paths")
      command.add(symbolDirs.joinToString(","))
    }

    for ((pkg, mapPath) in proguardMaps) {
      command.add("--proguard-map")
      command.add(if (pkg.isNotEmpty()) "$pkg=$mapPath" else mapPath)
    }

    if (verbose) {
      command.add("--verbose")
    }
    command.add(traceFile.absolutePath)
    command.add(outputFile.absolutePath)
    return command
  }
}
