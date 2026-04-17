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

import com.google.idea.blaze.qsync.project.FileExtensions
import java.nio.file.Path

/**
 * Chooses a candidate file from a collection of files to represent the package of the directory.
 *
 * It selects the lexicographically smallest JVM source file that exists.
 */
fun choosePackageCandidate(files: Collection<Path>, fileExtensions: FileExtensions, exists: (Path) -> Boolean): Path? {
  return files
    .sortedBy { it.fileName.toString() }
    .asSequence()
    .filter { fileExtensions.jvmExtensions.contains(it.toFile().extension) }
    .filter { exists(it) }
    .firstOrNull()
}
