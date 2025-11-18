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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

private val WORKER_COUNT = IntExperiment("aswb.query.sync.structure.worker.count", 50)

fun interface DirectoryProcessor {
  suspend fun processDirectory(currentDir: Path): List<Path>
}

suspend fun traverseIncludedDirectories(includeAbsolute: List<Path>, directoryProcessor: DirectoryProcessor) {
  coroutineScope {
    val directoryChannel = Channel<Path>(Channel.UNLIMITED)
    val activeDirCount = AtomicInteger(0)
    val visitedDirs = ConcurrentHashMap.newKeySet<Path>()

    suspend fun offerDir(dir: Path) {
      if (visitedDirs.add(dir)) {
        activeDirCount.incrementAndGet()
        directoryChannel.send(dir)
      }
    }

    repeat(WORKER_COUNT.value) {
      launch(QuerySyncDispatchers.IO) {
        for (dir in directoryChannel) {
          runCatching {
            val subDirs = directoryProcessor.processDirectory(dir)
            subDirs.forEach { subDir ->
              offerDir(subDir)
            }
          }
            .getOrElse { t -> thisLogger().error("Failed processing $dir", t) }
          if (activeDirCount.decrementAndGet() == 0) {
            directoryChannel.close()
          }
        }
      }
    }
    // Seed initial directories
    includeAbsolute.forEach { rootDir ->
      offerDir(rootDir)
    }
    // If no directories to start with, close channel
    if (activeDirCount.get() == 0) {
      directoryChannel.close()
    }
  }
}
