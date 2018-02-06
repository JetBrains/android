/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.resourceExplorer.importer

import com.android.ide.common.resources.configuration.ResourceQualifier
import com.android.tools.idea.resourceExplorer.model.Mapper
import com.intellij.openapi.util.io.FileUtil

/**
 * Lexer that parses a file path and matches tokens to a list of [ResourceQualifier].
 *
 * The token are defined using a set of [Mapper] that map a string to a [ResourceQualifier]
 */
class QualifierMatcher(private val mappers: Set<Mapper<ResourceQualifier>> = emptySet()) {

  constructor(vararg mapper: Mapper<ResourceQualifier>) : this(setOf(*mapper))

  fun parsePath(path: String): Result {
    if (mappers.isEmpty()) {
      return Result(getResourceName(path), emptySet())
    }
    val qualifiers = mutableSetOf<ResourceQualifier>()

    // We save a list of unused mapper in case they provide a default qualifier to add anyway.
    val unusedMappers = mappers.toMutableSet()
    val finalFileName = StringBuffer()

    // We use the same matcher for each mapper so we don't restart the
    // search from the start of the string. This also means that the order of the
    // mappers is important since once a mappers matched the string, we won't go
    // backtrack.
    val matcher = mappers.first().pattern.matcher(path)
    mappers
      .forEach {
        // Apply the current mapper's pattern
        matcher.usePattern(it.pattern)
        if (matcher.find()) {
          val qualifier = it.getQualifier(it.getValue(matcher.toMatchResult()))
          if (qualifier != null) {
            qualifiers.add(qualifier)
            unusedMappers.remove(it)
          }

          // We add the part of the path currently matched and remove the part
          // of it that has been matched with a qualifier. We'll end up with a
          // base name of the path that will be used to group the files with
          // the same base name
          matcher.appendReplacement(finalFileName, "")
        }
      }

    // Add the default qualifiers from the unused mappers
    unusedMappers
      .mapNotNull { it.defaultQualifier }
      .toCollection(qualifiers)

    // Append the rest of the path that has not been matched
    matcher.appendTail(finalFileName)
    val resourceName = getResourceName(finalFileName.toString())
    return Result(resourceName, qualifiers)
  }

  private fun getResourceName(path: String) = FileUtil.sanitizeFileName(FileUtil.getNameWithoutExtension(path))

  /**
   * Result of a parsing with the lexer.
   */
  data class Result(
    /**
     * The the name of the resource without the part that have been matched by the lexer
     */
    val resourceName: String,

    /**
     * The qualifiers that have been matched with the path.
     */
    val qualifiers: Set<ResourceQualifier>
  )
}
