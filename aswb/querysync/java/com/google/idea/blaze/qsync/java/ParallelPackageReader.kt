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

import com.google.idea.blaze.common.Context
import com.google.idea.blaze.qsync.dispatchers.QuerySyncDispatchers
import java.nio.file.Path
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/** A [PackageReader] that parallelizes package reads of another [PackageReader]. */
class ParallelPackageReader : PackageReader.ParallelReader {

  override fun readPackages(
    context: Context<*>,
    reader: PackageReader,
    paths: List<Path>,
  ): Map<Path, String> = runBlocking(QuerySyncDispatchers.IO) {
    readPackagesSuspending(context, reader, paths)
  }

  suspend fun readPackagesSuspending(
    context: Context<*>,
    reader: PackageReader,
    paths: List<Path>,
  ): Map<Path, String> = coroutineScope {
    withContext(QuerySyncDispatchers.IO) {
      paths
        .map { file ->
          async { reader.readPackage(context, file)?.let { file to it } }
        }
        .awaitAll()
        .filterNotNull()
        .toMap()
    }
  }
}