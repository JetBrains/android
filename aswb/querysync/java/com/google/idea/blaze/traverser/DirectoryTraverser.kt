/*
 * Copyright 2025 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.traverser

import com.google.idea.blaze.qsync.dispatchers.QuerySyncDispatchers
import com.google.idea.common.experiments.IntExperiment
import com.intellij.openapi.diagnostic.thisLogger
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private val WORKER_COUNT = IntExperiment("aswb.query.sync.structure.worker.count", 50)

/**
 * Holds a regular file path and its last modified timestamp in milliseconds extracted during directory traversal.
 *
 * @property path Path to the file.
 * @property lastModifiedTimeMs Last modified timestamp in milliseconds of the file.
 */
data class FileEntry(val path: Path, val lastModifiedTimeMs: Long)

/**
 * Holds the results of processing a single directory, separating files and subdirectories.
 *
 * @property files A list of regular files found within the directory.
 * @property subDirectories A list of subdirectories found within the directory.
 */
data class DirectoryContents(val files: List<FileEntry>, val subDirectories: List<Path>)

/** Defines the contract for processing a directory during traversal. */
fun interface DirectoryProcessor {
  /**
   * Processes a directory.
   *
   * @param rootDir The closest matching root directory from the initial include list that this directory belongs to.
   * @param currentDir The directory currently being processed.
   */
  fun processDirectory(rootDir: Path, currentDir: Path): DirectoryContents?
}

/** A single scan task indicating the root directory and the current directory to be processed. */
data class ScanTask(val rootDir: Path, val currentDir: Path)

/**
 * Traverses the specified directories concurrently and processes them.
 *
 * @param initialTasks The list of initial tasks to start the traversal from.
 * @param directoryProcessor The processor to apply to each directory.
 */
suspend fun traverseIncludedDirectories(initialTasks: List<ScanTask>, directoryProcessor: DirectoryProcessor) {
  coroutineScope {
    val directoryChannel = Channel<ScanTask>(Channel.UNLIMITED)
    val activeDirCount = AtomicInteger(0)
    val visitedDirs = ConcurrentHashMap.newKeySet<Path>()

    suspend fun offerDir(rootDir: Path, dir: Path) {
      if (visitedDirs.add(dir)) {
        activeDirCount.incrementAndGet()
        directoryChannel.send(ScanTask(rootDir, dir))
      }
    }

    // Seed initial directories first
    initialTasks.forEach { task -> offerDir(task.rootDir, task.currentDir) }

    // If no directories to start with, close channel
    if (activeDirCount.get() == 0) {
      directoryChannel.close()
    }

    repeat(WORKER_COUNT.value) {
      launch(QuerySyncDispatchers.IO) {
        for (task in directoryChannel) {
          runCatching {
              val contents = directoryProcessor.processDirectory(task.rootDir, task.currentDir)
              contents?.subDirectories?.forEach { subDir -> offerDir(task.rootDir, subDir) }
            }
            .getOrElse { t -> thisLogger().error("Failed processing ${task.currentDir}", t) }
          if (activeDirCount.decrementAndGet() == 0) {
            directoryChannel.close()
          }
        }
      }
    }
  }
}
