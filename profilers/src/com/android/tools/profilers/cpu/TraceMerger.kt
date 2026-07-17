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
package com.android.tools.profilers.cpu

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.io.FileUtil
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Utility to merge a native OS Perfetto trace with one or more App Sandbox Perfetto traces. Android Studio's TraceProcessor natively
 * supports parsing `.zip` archives containing Perfetto streams. By packaging the OS trace and the Compose app traces into a single ZIP
 * archive, we allow the trace processor to seamlessly parse them together as a unified trace.
 */
object TraceMerger {
  private val log = Logger.getInstance(TraceMerger::class.java)
  private const val ADB_PULL_TIMEOUT_SECONDS = 15L

  // Cache to map a process ID (PID) to its pending Compose trace files.
  // The value is a CompletableFuture that completes when the ADB pull finishes.
  @JvmField val appTracePathCache = ConcurrentHashMap<Long, CompletableFuture<File?>>()
  val activeV2Sessions = ConcurrentHashMap<Long, String>()

  @JvmStatic
  fun markAsTracingV2(pid: Long, appName: String) {
    activeV2Sessions[pid] = appName
  }

  @JvmStatic
  fun getAppName(pid: Long): String? {
    return activeV2Sessions[pid]
  }

  /** Zips the OS trace and all app traces from the appTraceFiles into a single trace archive at outputFile. */
  @Throws(IOException::class)
  fun mergeTraces(osTraceFile: File, appTraceFiles: List<File>, outputFile: File) {
    ZipOutputStream(FileOutputStream(outputFile).buffered()).use { zipOut ->
      // Add the OS trace
      if (osTraceFile.exists()) {
        zipOut.putNextEntry(ZipEntry(osTraceFile.name))
        FileInputStream(osTraceFile).use { it.copyTo(zipOut) }
        zipOut.closeEntry()
      } else {
        log.warn("OS trace file does not exist: ${osTraceFile.absolutePath}")
      }

      // Add all app traces
      appTraceFiles.forEach { appTrace ->
        if (appTrace.exists()) {
          zipOut.putNextEntry(ZipEntry(appTrace.name))
          FileInputStream(appTrace).use { it.copyTo(zipOut) }
          zipOut.closeEntry()
        } else {
          log.warn("App trace file does not exist: ${appTrace.absolutePath}")
        }
      }
    }
  }

  /**
   * Retrieves cached Compose traces for a PID, merges them with the OS trace, overwrites the OS trace inline, and explicitly cleans up the
   * host temp directories.
   */
  @JvmStatic
  fun mergeAndCleanUp(pid: Long, osTraceFile: File) {
    activeV2Sessions.remove(pid)
    val future = appTracePathCache.remove(pid)
    if (future == null) {
      return
    }

    val appTraceDir =
      try {
        future.get(ADB_PULL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
      } catch (e: Exception) {
        log.warn("Timeout or error waiting for Compose traces for PID $pid", e)
        // Ensure that if the future completes later, the directory is deleted to avoid a disk leak
        future.thenAccept { dir -> dir?.let { FileUtil.delete(it) } }
        null
      }

    if (appTraceDir == null || !appTraceDir.exists()) {
      return
    }

    try {
      val files = appTraceDir.listFiles()
      if (files != null && files.isNotEmpty()) {
        val tempOut = FileUtil.createTempFile("merged_trace", ".trace")
        try {
          mergeTraces(osTraceFile, files.toList(), tempOut)
          FileUtil.copy(tempOut, osTraceFile)
          log.info("Successfully replaced original OS trace with merged Zip at ${osTraceFile.absolutePath}")
        } finally {
          FileUtil.delete(tempOut)
        }
      }
    } catch (e: Exception) {
      log.warn("Failed to merge Tracing 2.0 app traces with OS trace.", e)
    } finally {
      // Cleanup: Delete the raw, unmerged trace files to prevent memory leaks
      FileUtil.delete(appTraceDir)
    }
  }
}
