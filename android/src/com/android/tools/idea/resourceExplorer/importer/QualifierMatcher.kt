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
import com.android.resources.ResourceEnum
import com.google.common.base.Joiner
import com.intellij.openapi.util.io.FileUtil
import java.util.regex.MatchResult
import java.util.regex.Pattern

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
        .mapNotNull { it.getDefaultQualifier() }
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
  fun getDefaultQualifier(): T? = null

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
 * A [Mapper] that will compile the regex with the following form:
 *
 * `prefix(valueRegExp)suffix`
 *
 * And return the captured group when calling [getValue]
 */
abstract class BaseMapper<out T : ResourceQualifier>(
    valueRegExp: String,
    prefix: String = "",
    suffix: String = ""
) : Mapper<T> {

  protected val regexp = "$prefix($valueRegExp)$suffix"

  override fun getValue(matcher: MatchResult): String = matcher.group(1)

  override val pattern: Pattern = Pattern.compile(regexp)

  abstract override fun getQualifier(value: String?): T?
}

/**
 * A [Mapper] that will compile a regex in the form `prefix(string1|string2...)suffix` and return
 * the captured string when calling [getValue]. Each value in the capturing group is mapped with a
 * [ResourceEnum] using [stringToParam].
 *
 * For example if we have the following files: file@2x.png, file@3x.png, we can define an [EnumBasedMapper] with
 * prefix @, suffix x, QualifierClass DensityQualifier, stringToParam {2 -> XHDPI, 3 -> XXHDPI}
 *
 * If [defaultParam] is not defined, it will try to find an empty string key in [stringToParam] to use as the
 * default parameter when calling [getQualifier].
 */
class EnumBasedMapper<out T : ResourceQualifier, E : ResourceEnum>(
    prefix: String = "",
    suffix: String = "",
    private val qualifierClass: Class<T>,
    private val stringToParam: Map<String, E>,
    private val defaultParam: E? = stringToParam[""]
) : BaseMapper<T>(createRegexp(stringToParam.keys), prefix, suffix) {

  companion object {
    inline fun <reified T : ResourceQualifier, E : ResourceEnum> builder() = Builder<T, E>(T::class.java)

    private fun createRegexp(strings: Set<String>) = Joiner.on('|').join(strings.filter(String::isNotEmpty))
  }

  class Builder<T : ResourceQualifier, E : ResourceEnum>(private val qualifierClass: Class<T>) {
    private var prefix: String = ""
    private var suffix: String = ""
    private var default: E? = null
    private val matchers = mutableListOf<Pair<String, E>>()

    fun withPrefix(prefix: String): Builder<T, E> {
      this.prefix = prefix
      return this
    }

    fun withSuffix(suffix: String): Builder<T, E> {
      this.suffix = suffix
      return this
    }

    fun map(stingToParam: Pair<String, E>): Builder<T, E> {
      matchers.add(stingToParam)
      return this
    }

    fun withDefault(default: E): Builder<T, E> {
      this.default = default
      return this
    }

    fun build() = EnumBasedMapper(prefix, suffix, qualifierClass, matchers.toMap(), default)

  }

  override fun getQualifier(value: String?): T? {
    val qualifierParameter = stringToParam[value] ?: return null
    return getQualifier(qualifierParameter)
  }

  private fun getQualifier(qualifierParameter: E) =
      qualifierClass.getConstructor(qualifierParameter.javaClass).newInstance(qualifierParameter)

  override fun getDefaultQualifier(): T? = defaultParam?.let { getQualifier(defaultParam) }
}
