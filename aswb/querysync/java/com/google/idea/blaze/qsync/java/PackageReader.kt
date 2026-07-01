/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
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
import java.nio.file.Path
import org.jetbrains.annotations.TestOnly

/** Calculates the package for a java source file. */
fun interface PackageReader {
  /** Calculates packages for java source files. */
  interface ParallelReader {
    fun readPackages(context: Context<*>, reader: PackageReader, paths: List<Sequence<Path>>): Map<Path, String>

    @TestOnly
    class SingleThreadedForTests : ParallelReader {
      override fun readPackages(context: Context<*>, reader: PackageReader, paths: List<Sequence<Path>>): Map<Path, String> {
        val pathToPkgMap = LinkedHashMap<Path, String>()
        for (pathGroup in paths) {
          for (path in pathGroup) {
            val pkg = reader.readPackage(context, path)
            if (pkg != null) {
              pathToPkgMap[path] = pkg
              break
            }
          }
        }
        return pathToPkgMap
      }
    }
  }

  fun readPackage(context: Context<*>, path: Path): String?
}
