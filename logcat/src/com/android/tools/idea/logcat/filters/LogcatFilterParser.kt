/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.logcat.filters

import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.logcat.filters.LogcatFilterField.APP
import com.android.tools.idea.logcat.filters.LogcatFilterField.IMPLICIT_LINE
import com.android.tools.idea.logcat.filters.LogcatFilterField.LINE
import com.android.tools.idea.logcat.filters.LogcatFilterField.MESSAGE
import com.android.tools.idea.logcat.filters.LogcatFilterField.PROCESS
import com.android.tools.idea.logcat.filters.LogcatFilterField.TAG
import com.android.tools.idea.logcat.filters.LogcatFilterParser.CombineWith.AND
import com.android.tools.idea.logcat.filters.LogcatFilterParser.CombineWith.OR
import com.android.tools.idea.logcat.filters.parser.LogcatFilterAndExpression
import com.android.tools.idea.logcat.filters.parser.LogcatFilterExpression
import com.android.tools.idea.logcat.filters.parser.LogcatFilterFileType
import com.android.tools.idea.logcat.filters.parser.LogcatFilterLiteralExpression
import com.android.tools.idea.logcat.filters.parser.LogcatFilterOrExpression
import com.android.tools.idea.logcat.filters.parser.LogcatFilterParenExpression
import com.android.tools.idea.logcat.filters.parser.LogcatFilterTypes.KEY
import com.android.tools.idea.logcat.filters.parser.LogcatFilterTypes.REGEX_KEY
import com.android.tools.idea.logcat.filters.parser.LogcatFilterTypes.STRING_KEY
import com.android.tools.idea.logcat.filters.parser.LogcatFilterTypes.VALUE
import com.android.tools.idea.logcat.filters.parser.isTopLevelValue
import com.android.tools.idea.logcat.filters.parser.toText
import com.android.tools.idea.logcat.message.LogLevel
import com.android.tools.idea.logcat.util.AndroidProjectDetector
import com.android.tools.idea.logcat.util.AndroidProjectDetectorImpl
import com.android.tools.idea.projectsystem.ProjectApplicationIdsProvider
import com.google.wireless.android.sdk.stats.LogcatUsageEvent.LogcatFilterEvent
import com.google.wireless.android.sdk.stats.LogcatUsageEvent.LogcatFilterEvent.TermVariants
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.impl.source.tree.PsiErrorElementImpl
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.psi.util.startOffset
import com.intellij.refactoring.suggested.endOffset
import java.text.ParseException
import java.time.Clock

private val IS_FILTERS =
  mapOf<String, (TextRange) -> LogcatFilter>(
    "crash" to { CrashFilter(it) },
    "firebase" to { RegexFilter(firebaseRegex, TAG, matchCase = true, it) },
    "stacktrace" to { StackTraceFilter(it) },
  ) + LogLevel.entries.associate { level -> level.stringValue to { ExactLevelFilter(level, it) } }

/** Parses a Logcat Filter expression into a [LogcatFilter] */
internal class LogcatFilterParser(
  project: Project,
  private val projectApplicationIdsProvider: ProjectApplicationIdsProvider,
  private val androidProjectDetector: AndroidProjectDetector = AndroidProjectDetectorImpl(),
  private val joinConsecutiveTopLevelValue: Boolean = false,
  private val topLevelSameKeyTreatment: CombineWith = OR,
  private val clock: Clock = Clock.systemDefaultZone(),
) {
  enum class CombineWith {
    AND,
    OR,
  }

  private val psiFileFactory = PsiFileFactory.getInstance(project)

  /**
   * Parse a filter provided by a string in the
   * [com.android.tools.idea.logcat.filters.parser.LogcatFilterLanguage]
   *
   * @param filterString a string in the Logcat filter language
   * @return A [LogcatFilter] representing the provided string or null if the filter is empty.
   */
  @UiThread
  fun parse(filterString: String, matchCase: Boolean): LogcatFilter? {
    return try {
      parseInternal(filterString, matchCase)
    } catch (e: LogcatFilterParseException) {
      // Any error in parsing results in a filter that matches the raw string with the entire line.
      StringFilter(filterString, IMPLICIT_LINE, matchCase, TextRange(0, filterString.length))
    }
  }

  fun isValid(filterString: String): Boolean {
    return try {
      parseInternal(filterString, true)
      true
    } catch (e: LogcatFilterParseException) {
      false
    }
  }

  /**
   * Removes `name:` terms from a filter
   *
   * This method does a best-effort to remove `name:` terms from a filter. The resulting filter
   * string may not be a valid filter under extreme circumstances. Therefore, this method should
   * only be used for display purposes. The resulting filter should never actually be used for
   * filtering.
   *
   * An example of a filter that will be broken by this method: `tag:Foo | name:Name`
   *
   * It's possible to improve this method to be able to handle these cases, but it's non-trivial.
   *
   * TODO(b/236844042): Improve to Handle Complex Expressions ￼
   */
  fun removeFilterNames(filterString: String): String {
    return try {
      val psi = psiFileFactory.createFileFromText("temp.lcf", LogcatFilterFileType, filterString)
      val offsets =
        PsiTreeUtil.findChildrenOfType(psi, LogcatFilterLiteralExpression::class.java)
          .filter { it.firstChild.text == "name:" }
          .map { Pair(it.startOffset, it.endOffset) }
          .sortedBy { it.first }

      val sb = StringBuilder(filterString)
      offsets.reversed().forEach { (start, end) ->
        val endWithSpace = if (end < sb.length && sb[end] in arrayOf(' ', '\t')) end + 1 else end
        sb.replace(start, endWithSpace, "")
      }
      sb.toString().trim()
    } catch (e: LogcatFilterParseException) {
      filterString
    }
  }

  private fun parseInternal(filterString: String, matchCase: Boolean): LogcatFilter? {
    return when {
      filterString.isEmpty() -> null
      filterString.isBlank() ->
        StringFilter(filterString, IMPLICIT_LINE, matchCase, TextRange(0, filterString.length))
      else -> {
        val psi = psiFileFactory.createFileFromText("temp.lcf", LogcatFilterFileType, filterString)
        if (PsiTreeUtil.hasErrorElements(psi)) {
          val errorElement =
            PsiTreeUtil.findChildOfType(psi, PsiErrorElement::class.java) as PsiErrorElement
          throw LogcatFilterParseException(errorElement)
        }
        psi.toFilter(matchCase)
      }
    }
  }

  /**
   * Parse a filter provided by a string in the Logcat filter language
   * ([com.android.tools.idea.logcat.filters.parser.LogcatFilterLanguage]) and return a usage
   * tracking event representing it.
   *
   * @param filterString a string in the Logcat filter language
   * @return A [LogcatFilterEvent] representing the provided string.
   */
  fun getUsageTrackingEvent(filterString: String, matchCase: Boolean): LogcatFilterEvent.Builder? {
    val builder = LogcatFilterEvent.newBuilder()
    try {
      val psi = psiFileFactory.createFileFromText("temp.lcf", LogcatFilterFileType, filterString)
      if (PsiTreeUtil.hasErrorElements(psi)) {
        builder.containsErrors = true
      } else {
        // We should not be getting a null here because we don't call this method if filterString is
        // empty
        // TODO(aalbert): Add matchCase to event tracking
        val logcatFilter = psi.toFilter(matchCase) ?: return builder
        processFilters(logcatFilter) { filter ->
          when {
            filter is StringFilter && filter.field == IMPLICIT_LINE -> builder.implicitLineTerms++
            filter is StringFilter -> builder.updateTermVariants(filter.field) { it.count++ }
            filter is NegatedStringFilter ->
              builder.updateTermVariants(filter.field) { it.countNegated++ }
            filter is RegexFilter -> builder.updateTermVariants(filter.field) { it.countRegex++ }
            filter is NegatedRegexFilter ->
              builder.updateTermVariants(filter.field) { it.countNegatedRegex++ }
            filter is ProjectAppFilter -> builder.packageProjectTerms++
            filter is LevelFilter -> builder.levelTerms++
            filter is AgeFilter -> builder.ageTerms++
            filter is CrashFilter -> builder.crashTerms++
            filter is StackTraceFilter -> builder.stacktraceTerms++
          }
        }

        PsiTreeUtil.processElements(psi) {
          when (it) {
            is LogcatFilterParenExpression -> builder.parentheses++
            is LogcatFilterAndExpression -> builder.andOperators++
            is LogcatFilterOrExpression -> builder.orOperators++
          }
          true
        }
      }
    } catch (e: LogcatFilterParseException) {
      builder.containsErrors = true
    }
    return builder
  }

  private fun processFilters(filter: LogcatFilter, process: (LogcatFilter) -> Unit) {
    when (filter) {
      is AndLogcatFilter -> filter.filters.forEach { processFilters(it, process) }
      is OrLogcatFilter -> filter.filters.forEach { processFilters(it, process) }
      else -> process(filter)
    }
  }

  private fun LogcatFilterEvent.Builder.updateTermVariants(
    field: LogcatFilterField,
    updater: (TermVariants.Builder) -> Unit,
  ) {
    val terms =
      when (field) {
        TAG -> tagTermsBuilder
        APP -> packageTermsBuilder
        MESSAGE -> messageTermsBuilder
        LINE -> lineTermsBuilder
        IMPLICIT_LINE -> return
        PROCESS -> return // TODO(238877175): Add processTermsBuilder
      }
    updater(terms)
  }

  private fun PsiFile.toFilter(matchCase: Boolean): LogcatFilter? {
    val expressions = PsiTreeUtil.getChildrenOfType(this, LogcatFilterExpression::class.java)

    return when {
      expressions == null -> null
      expressions.size == 1 -> expressions[0].toFilter(matchCase)
      else -> createTopLevelFilter(expressions, matchCase)
    }
  }

  private fun createTopLevelFilter(
    expressions: Array<LogcatFilterExpression>,
    matchCase: Boolean,
  ): LogcatFilter {
    val filters =
      when {
        joinConsecutiveTopLevelValue -> combineConsecutiveValues(expressions, matchCase)
        else -> expressions.map { it.toFilter(matchCase) }
      }

    return when {
      filters.size == 1 -> filters[0]
      topLevelSameKeyTreatment == AND -> AndLogcatFilter(filters)
      else -> createComplexTopLevelFilter(filters)
    }
  }

  private fun createComplexTopLevelFilter(filters: List<LogcatFilter>): LogcatFilter {
    @Suppress(
      "ConvertLambdaToReference"
    ) // IJ wants to convert "it.value" to "IndexedValue<LogcatFilter>::value"
    val groups =
      filters.withIndex().groupBy({ it.value.getFieldForImplicitOr(it.index) }, { it.value }).values
    return AndLogcatFilter(groups.map { if (it.size == 1) it[0] else OrLogcatFilter(it.toList()) })
  }

  private fun combineConsecutiveValues(
    expressions: Array<LogcatFilterExpression>,
    matchCase: Boolean,
  ): List<LogcatFilter> {
    // treat consecutive top level values as concatenations rather than an 'and'.
    // First, group consecutive top level value expressions.
    val grouped =
      expressions.fold(mutableListOf<MutableList<LogcatFilterExpression>>()) {
        accumulator,
        expression ->
        if (
          expression.isTopLevelValue() &&
            accumulator.isNotEmpty() &&
            accumulator.last().last().isTopLevelValue()
        ) {
          accumulator.last().add(expression)
        } else {
          accumulator.add(mutableListOf(expression))
        }
        accumulator
      }

    // Then, combine in an AndFilter while creating a single top level filter for consecutive
    // top-level expressions.
    return grouped.map {
      if (it.size == 1) it[0].toFilter(matchCase) else combineLiterals(it, matchCase)
    }
  }

  private fun combineLiterals(
    expressions: List<LogcatFilterExpression>,
    matchCase: Boolean,
  ): StringFilter {
    val text =
      expressions.joinToString("") {
        val expression = it as LogcatFilterLiteralExpression
        expression.firstChild.toText() +
          if (expression.nextSibling is PsiWhiteSpace) expression.nextSibling.text else ""
      }
    return StringFilter(
      text.trim(),
      IMPLICIT_LINE,
      matchCase,
      TextRange(expressions.first().startOffset, expressions.last().endOffset),
    )
  }

  private fun LogcatFilterExpression.toFilter(matchCase: Boolean): LogcatFilter {
    return when (this) {
      is LogcatFilterLiteralExpression -> this.literalToFilter(matchCase)
      is LogcatFilterParenExpression -> expression?.toFilter(matchCase) ?: EmptyFilter
      is LogcatFilterAndExpression ->
        AndLogcatFilter(flattenAndExpression(this).map { it.toFilter(matchCase) })
      is LogcatFilterOrExpression ->
        OrLogcatFilter(flattenOrExpression(this).map { it.toFilter(matchCase) })
      else ->
        throw ParseException(
          "Unexpected element: ${this::class.simpleName}",
          -1,
        ) // Should not happen
    }
  }

  private fun LogcatFilterLiteralExpression.literalToFilter(matchCase: Boolean) =
    when (firstChild.elementType) {
      VALUE ->
        StringFilter(
          firstChild.toText(),
          IMPLICIT_LINE,
          matchCase,
          TextRange(startOffset, endOffset),
        )
      KEY,
      STRING_KEY,
      REGEX_KEY ->
        toKeyFilter(matchCase, clock, projectApplicationIdsProvider, androidProjectDetector)
      else ->
        throw ParseException(
          "Unexpected elementType: $firstChild.elementType",
          -1,
        ) // Should not happen
    }
}

private fun LogcatFilterLiteralExpression.toKeyFilter(
  matchCase: Boolean,
  clock: Clock,
  projectApplicationIdsProvider: ProjectApplicationIdsProvider,
  androidProjectDetector: AndroidProjectDetector,
): LogcatFilter {
  val textRange = TextRange(startOffset, endOffset)
  return when (val key = firstChild.text.trim(':', '-', '~', '=')) {
    "level" -> LevelFilter(lastChild.asLogLevel(), textRange)
    "age" -> createAgeFilter(lastChild.text, clock)
    "is" -> createIsFilter(lastChild.text)
    "name" -> createNameFilter(lastChild.toText())
    else -> {
      val value = lastChild.toText()
      val isNegated = firstChild.text.startsWith('-')
      val isRegex = firstChild.text.endsWith("~:")
      val isExact = firstChild.text.endsWith("=:")
      val field: LogcatFilterField =
        when (key) {
          "tag" -> TAG
          "package" -> APP
          "process" -> PROCESS
          "message" -> MESSAGE
          "line" -> LINE
          else -> {
            throw LogcatFilterParseException(
              PsiErrorElementImpl("Invalid key: $key")
            ) // Should not happen
          }
        }

      fun isAndroidProject() = androidProjectDetector.isAndroidProject(project)
      when {
        isNegated && isRegex -> NegatedRegexFilter(value, field, matchCase, textRange)
        isNegated && isExact -> NegatedExactStringFilter(value, field, matchCase, textRange)
        isNegated -> NegatedStringFilter(value, field, matchCase, textRange)
        isRegex -> RegexFilter(value, field, matchCase, textRange)
        isExact -> ExactStringFilter(value, field, matchCase, textRange)
        key == "package" && value == "mine" && isAndroidProject() ->
          ProjectAppFilter(projectApplicationIdsProvider, textRange)
        else -> StringFilter(value, field, matchCase, textRange)
      }
    }
  }
}

private fun LogcatFilterLiteralExpression.createAgeFilter(
  text: String,
  clock: Clock,
): LogcatFilter {
  return try {
    AgeFilter(text, clock, TextRange(startOffset, endOffset))
  } catch (e: IllegalArgumentException) {
    throw LogcatFilterParseException(PsiErrorElementImpl(e.message ?: "Parse error"))
  }
}

private fun LogcatFilterLiteralExpression.createIsFilter(text: String): LogcatFilter {
  if (!StudioFlags.LOGCAT_IS_FILTER.get()) {
    throw LogcatFilterParseException(PsiErrorElementImpl("Invalid key: is"))
  }
  val createFilter =
    IS_FILTERS[text]
      ?: throw LogcatFilterParseException(PsiErrorElementImpl("Invalid filter: is:$text"))
  return createFilter(TextRange(startOffset, endOffset))
}

private fun LogcatFilterLiteralExpression.createNameFilter(text: String): LogcatFilter =
  NameFilter(text, TextRange(startOffset, endOffset))

private fun PsiElement.asLogLevel(): LogLevel =
  LogLevel.getByString(text.lowercase())
    ?: throw LogcatFilterParseException(PsiErrorElementImpl("Invalid Log Level: $text"))

internal fun String.isValidLogLevel(): Boolean = LogLevel.getByString(lowercase()) != null

internal fun String.isValidIsFilter(): Boolean = IS_FILTERS.contains(this)

internal fun String.isValidLogAge(): Boolean {
  durationRegex.matchEntire(this) ?: return false
  try {
    substring(0, length - 1).toLong()
  } catch (e: NumberFormatException) {
    return false
  }
  return true
}

private fun flattenOrExpression(expression: LogcatFilterExpression): List<LogcatFilterExpression> =
  if (expression is LogcatFilterOrExpression) {
    flattenOrExpression(expression.expressionList[0]) +
      flattenOrExpression(expression.expressionList[1])
  } else {
    listOf(expression)
  }

private fun flattenAndExpression(expression: LogcatFilterExpression): List<LogcatFilterExpression> =
  if (expression is LogcatFilterAndExpression) {
    flattenAndExpression(expression.expressionList[0]) +
      flattenAndExpression(expression.expressionList[1])
  } else {
    listOf(expression)
  }

private data class FilterType(private val obj: Any)

/**
 * Returns a [FilterType] to be used for grouping.
 *
 * The grouping determines if they will be combined with an 'OR' or an 'AND'. The filters inside
 * each group will be combined with an 'OR' while the groups will be combined with an 'AND'.
 *
 * Grouping rules:
 * 1. StringFilter & RegexFilter are grouped by their `field`.
 * 2. All level filters are grouped together.
 * 3. AgeFilter's are grouped together.
 * 2. Any other filter (including NegatedStringFilter & NegatedRegexFilter) are not grouped and get
 *    a unique FilterType which is established using its index.
 */
private fun LogcatFilter.getFieldForImplicitOr(index: Int): FilterType {
  return when {
    this is StringFilter && field != IMPLICIT_LINE -> FilterType(field)
    this is RegexFilter -> FilterType(field)
    this is ExactStringFilter -> FilterType(field)
    this is LevelFilter -> FilterType("level")
    this is ExactLevelFilter -> FilterType("level")
    this is AgeFilter -> FilterType("age")
    this is CrashFilter -> FilterType("is")
    this is StackTraceFilter -> FilterType("is")
    else -> FilterType(index)
  }
}

// Avoid clutter at the top of the file
private val durationRegex = "\\d+[smhd]".toRegex()

private val firebaseTags =
  listOf(
    "AppInstallOperation",
    "AppInviteActivity",
    "AppInviteAgent",
    "AppInviteAnalytics",
    "AppInviteLogger",
    "BackgroundTask",
    "ClassMapper",
    "Connection",
    "DataOperation",
    "EventRaiser",
    "FA",
    "FirebaseAppIndex",
    "FirebaseDatabase",
    "FirebaseInstanceId",
    "FirebaseMessaging",
    "FirebaseRemoteConfig",
    "NetworkRequest",
    "Persistence",
    "PersistentConnection",
    "RepoOperation",
    "RunLoop",
    "StorageTask",
    "SyncTree",
    "Transaction",
    "WebSocket",
  )
private val firebaseRegex = "^(${firebaseTags.joinToString("|")})$"
