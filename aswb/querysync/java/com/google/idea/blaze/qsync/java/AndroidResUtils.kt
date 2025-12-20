/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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

import java.nio.file.Path

/**
 * Utility for identifying Android resource directories.
 */
object AndroidResUtils {
  /**
   * Standard Android resource directory prefixes.
   * See https://developer.android.com/guide/topics/resources/providing-resources#ResourceTypes
   */
  private val RESOURCE_TYPES = setOf(
    "anim",
    "animator",
    "color",
    "drawable",
    "font",
    "layout",
    "menu",
    "mipmap",
    "navigation",
    "raw",
    "transition",
    "values",
    "xml"
  )

  /**
   * Identifies Android resource roots from a collection of file paths.
   *
   * A file is identified as a resource if its immediate parent directory matches
   * a standard Android resource type (e.g., `values`, `layout`).
   *
   * Following AAPT2 structure: `path/resource-type[-config]/file`
   */
  fun computeAndroidResourceDirectories(sourceFiles: Iterable<Path>): Set<Path> {
    val directories = mutableSetOf<Path>()
    for (file in sourceFiles) {
      val typeDir = file.parent ?: continue
      val typeName = typeDir.fileName.toString()
      if (RESOURCE_TYPES.any { typeName == it || typeName.startsWith("$it-") }) {
        typeDir.parent?.let { directories.add(it) }
      }
    }
    return directories
  }
}
