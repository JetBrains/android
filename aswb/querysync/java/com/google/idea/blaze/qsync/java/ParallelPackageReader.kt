/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.qsync.java

import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.Uninterruptibles
import com.google.idea.blaze.common.Context
import java.nio.file.Path
import java.util.concurrent.ExecutionException

/** A [PackageReader] that parallelizes package reads of another [PackageReader].  */
class ParallelPackageReader(
  private val executor: ListeningExecutorService
) : PackageReader.ParallelReader {
  override fun readPackages(
    context: Context<*>,
    reader: PackageReader,
    paths: List<Path>
  ): Map<Path, String> {
    val futures = paths.map { file ->
      executor.submit<Pair<Path, String>?> {
        reader.readPackage(context, file)?.let { file to it }
      }
    }
    return try {
      Uninterruptibles
        .getUninterruptibly(Futures.allAsList(futures))
        .filterNotNull()
        .toMap()
    } catch (e: ExecutionException) {
      throw IllegalStateException(e)
    }
  }
}
