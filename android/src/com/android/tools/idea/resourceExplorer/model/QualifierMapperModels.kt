/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.resourceExplorer.model

import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.ide.common.resources.configuration.ResourceQualifier
import com.android.tools.idea.resourceExplorer.importer.QualifierMatcher
import java.util.regex.MatchResult
import java.util.regex.Pattern

/**
 * Data class that represents the mapping from a string to a [ResourceQualifier].
 *
 * This is used for readability to avoid using a Pair
 */
data class MatcherEntry(val matchingString: String, val matchedQualifier: ResourceQualifier)

/**
 * A Mapper returns a [ResourceQualifier] given a provided value that was
 * matched using the regex [pattern].
 * It can also optionally provide a default qualifier that will be used if
 * the [pattern] was not matched to ensure that a qualifier is always supplied.
 * For example if we want files with no Density qualifier defined by their path, we can
 * apply the medium density qualifier by default.
 */
interface Mapper<out T : ResourceQualifier> {

  /**
   * The implementing class should return a [ResourceQualifier] and can use
   * [value] to customize the return [ResourceQualifier]
   */
  fun getQualifier(value: String?): T?

  /**
   * The implementing class can optionally return a default qualifier if the [pattern] was not matched
   */
  val defaultQualifier: T?

  /**
   * If the implementing class is using capturing group with [pattern] or need to customize
   * the value used to called [getQualifier], it can override this class
   */
  fun getValue(matcher: MatchResult): String? = matcher.group()

  /**
   * A [Pattern] that will try to be matched with the string provided in [QualifierMatcher.parsePath]
   */
  val pattern: Pattern
}

/**
 * A [Mapper] that maps a string, typically defined by the user, to a qualifiers.
 *
 * It will compile a regex in the form `(string1|string2...)` where `stringN` is a key in
 * [matchers] and return the captured string when calling [getValue]. Each value in the capturing group
 * is mapped with a [ResourceQualifier] using [matchers].
 *
 * For example if we have the following files: file@2x.png, file@3x.png, we can define an [StaticStringMapper] with
 *  matchers {@2x -> DensityQualifier(XHDPI), @3x -> DensityQualifier(XXHDPI)}
 *
 * If [defaultParam] is not defined, it will try to find an empty string key in [matchers] to use as the
 * default parameter when calling [getQualifier].
 */
class StaticStringMapper(
  val matchers: Map<String, ResourceQualifier>,
  private val defaultParam: ResourceQualifier? = matchers[""]
) : Mapper<ResourceQualifier> {

  override val defaultQualifier = defaultParam?.let { getQualifierFromFolderName(defaultParam.folderSegment) }

  private val regexp: String = createRegexp(matchers.keys)

  constructor(matcherEntries: List<MatcherEntry>) :
      this(matchers = matcherEntries.associate { it.matchingString to it.matchedQualifier })

  companion object {
    private fun createRegexp(strings: Set<String>) = "(${strings.filter(String::isNotEmpty).joinToString("|")})"
  }

  override fun getValue(matcher: MatchResult): String = matcher.group(1)

  override val pattern: Pattern = Pattern.compile(regexp)

  override fun getQualifier(value: String?): ResourceQualifier? {
    val qualifierParameter = matchers[value] ?: return null
    return getQualifierFromFolderName(qualifierParameter.folderSegment)
  }

  private fun getQualifierFromFolderName(folderName: String): ResourceQualifier? {
    val folderConfiguration = FolderConfiguration.getConfigFromQualifiers(listOf(folderName))
    return folderConfiguration?.qualifiers?.firstOrNull()
  }
}
