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

import com.android.flags.junit.FlagRule
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.logcat.FakeAndroidProjectDetector
import com.android.tools.idea.logcat.FakeProjectApplicationIdsProvider
import com.android.tools.idea.logcat.filters.LogcatFilterField.APP
import com.android.tools.idea.logcat.filters.LogcatFilterField.IMPLICIT_LINE
import com.android.tools.idea.logcat.filters.LogcatFilterField.LINE
import com.android.tools.idea.logcat.filters.LogcatFilterField.MESSAGE
import com.android.tools.idea.logcat.filters.LogcatFilterField.TAG
import com.android.tools.idea.logcat.filters.LogcatFilterParser.CombineWith
import com.android.tools.idea.logcat.filters.LogcatFilterParser.CombineWith.AND
import com.android.tools.idea.logcat.filters.LogcatFilterParser.CombineWith.OR
import com.android.tools.idea.logcat.message.LogLevel
import com.android.tools.idea.logcat.message.LogLevel.INFO
import com.android.tools.idea.logcat.message.LogLevel.WARN
import com.android.tools.idea.logcat.util.AndroidProjectDetector
import com.android.tools.idea.logcat.util.LogcatFilterLanguageRule
import com.android.tools.idea.logcat.util.logcatMessage
import com.android.tools.idea.testing.WaitForIndexRule
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.LogcatUsageEvent.LogcatFilterEvent
import com.google.wireless.android.sdk.stats.LogcatUsageEvent.LogcatFilterEvent.TermVariants
import com.intellij.openapi.util.TextRange
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import java.time.Clock
import kotlin.test.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

private val keys = mapOf("tag" to TAG, "package" to APP, "message" to MESSAGE, "line" to LINE)

private val ageValues = listOf("10s", "10m", "10h", "10d")

private val invalidAges =
  listOf(
    "10",
    "10f",
    "broom",
    "99999999999999999999999999999999999999999999999s", // Triggers a NumberFormatException
  )

/** Tests for [LogcatFilterParser] */
@RunsInEdt
@RunWith(Parameterized::class)
class LogcatFilterParserTest(private val matchCase: Boolean) {
  companion object {

    @JvmStatic @Parameterized.Parameters fun getMatchCase() = listOf(true, false)
  }

  private val projectRule = ProjectRule()
  private val project
    get() = projectRule.project

  @get:Rule
  val rule =
    RuleChain(
      projectRule,
      WaitForIndexRule(projectRule),
      EdtRule(),
      LogcatFilterLanguageRule(),
      FlagRule(StudioFlags.LOGCAT_IS_FILTER),
    )

  private val fakeProjectApplicationIdsProvider by lazy {
    FakeProjectApplicationIdsProvider(project)
  }

  @Test
  fun parse_emptyFilter() {
    assertThat(logcatFilterParser().parse("", matchCase)).isNull()
  }

  @Test
  fun parse_blankFilter() {
    val filter = " \t"
    assertThat(logcatFilterParser().parse(filter, matchCase))
      .isEqualTo(StringFilter(" \t", IMPLICIT_LINE, matchCase, filter.asRange()))
  }

  @Test
  fun parse_stringKey() {
    for ((key, field) in keys) {
      assertThat(logcatFilterParser().parse("$key: Foo", matchCase))
        .isEqualTo(StringFilter("Foo", field, matchCase, "$key: Foo".asRange()))
      assertThat(logcatFilterParser().parse("$key:Foo", matchCase))
        .isEqualTo(StringFilter("Foo", field, matchCase, "$key:Foo".asRange()))
    }
  }

  @Test
  fun parse_stringKey_escapeChars() {
    for ((key, field) in keys) {
      val filterString =
        """
        $key:foo\ bar
        $key:'foobar'
        $key:\'foobar\'
        $key:"foobar"
        $key:\"foobar\"
        $key:foo\\bar
      """
          .trimIndent()
      val filter = logcatFilterParser().parse(filterString, matchCase)

      assertThat(filter)
        .isEqualTo(
          AndLogcatFilter(
            StringFilter("foo bar", field, matchCase, filterString.rangeOf("$key:foo\\ bar")),
            StringFilter("foobar", field, matchCase, filterString.rangeOf("$key:'foobar'")),
            StringFilter("'foobar'", field, matchCase, filterString.rangeOf("$key:\\'foobar\\'")),
            StringFilter("foobar", field, matchCase, filterString.rangeOf("$key:\"foobar\"")),
            StringFilter(
              """"foobar"""",
              field,
              matchCase,
              filterString.rangeOf("$key:\\\"foobar\\\""),
            ),
            StringFilter("""foo\bar""", field, matchCase, filterString.rangeOf("$key:foo\\\\bar")),
          )
        )
    }
  }

  @Test
  fun parse_negatedStringKey() {
    for ((key, field) in keys) {
      assertThat(logcatFilterParser().parse("-$key: Foo", matchCase))
        .isEqualTo(NegatedStringFilter("Foo", field, matchCase, "-$key: Foo".asRange()))
      assertThat(logcatFilterParser().parse("-$key:Foo", matchCase))
        .isEqualTo(NegatedStringFilter("Foo", field, matchCase, "-$key:Foo".asRange()))
    }
  }

  @Test
  fun parse_regexKey() {
    for ((key, field) in keys) {
      assertThat(logcatFilterParser().parse("$key~: Foo", matchCase))
        .isEqualTo(RegexFilter("Foo", field, matchCase, "$key~: Foo".asRange()))
      assertThat(logcatFilterParser().parse("$key~:Foo", matchCase))
        .isEqualTo(RegexFilter("Foo", field, matchCase, "$key~:Foo".asRange()))
    }
  }

  @Test
  fun parse_negatedRegexKey() {
    for ((key, field) in keys) {
      assertThat(logcatFilterParser().parse("-$key~: Foo", matchCase))
        .isEqualTo(NegatedRegexFilter("Foo", field, matchCase, "-$key~: Foo".asRange()))
      assertThat(logcatFilterParser().parse("-$key~:Foo", matchCase))
        .isEqualTo(NegatedRegexFilter("Foo", field, matchCase, "-$key~:Foo".asRange()))
    }
  }

  @Test
  fun parse_invalidRegex() {
    assertThat(logcatFilterParser().parse("""tag~:\""", matchCase))
      .isEqualTo(StringFilter("""tag~:\""", IMPLICIT_LINE, matchCase, """tag~:\""".asRange()))
  }

  @Test
  fun parse_invalidNegatedRegex() {
    assertThat(logcatFilterParser().parse("""-tag~:\""", matchCase))
      .isEqualTo(StringFilter("""-tag~:\""", IMPLICIT_LINE, matchCase, """-tag~:\""".asRange()))
  }

  @Test
  fun parse_exactKey() {
    for ((key, field) in keys) {
      assertThat(logcatFilterParser().parse("$key=: Foo", matchCase))
        .isEqualTo(ExactStringFilter("Foo", field, matchCase, "$key=: Foo".asRange()))
      assertThat(logcatFilterParser().parse("$key=:Foo", matchCase))
        .isEqualTo(ExactStringFilter("Foo", field, matchCase, "$key=:Foo".asRange()))
    }
  }

  @Test
  fun parse_negatedExactKey() {
    for ((key, field) in keys) {
      assertThat(logcatFilterParser().parse("-$key=: Foo", matchCase))
        .isEqualTo(NegatedExactStringFilter("Foo", field, matchCase, "-$key=: Foo".asRange()))
      assertThat(logcatFilterParser().parse("-$key=:Foo", matchCase))
        .isEqualTo(NegatedExactStringFilter("Foo", field, matchCase, "-$key=:Foo".asRange()))
    }
  }

  @Test
  fun parse_levelKeys() {
    for (logLevel in LogLevel.entries) {
      assertThat(logcatFilterParser().parse("level: $logLevel", matchCase))
        .isEqualTo(LevelFilter(logLevel, "level: $logLevel".asRange()))
      assertThat(logcatFilterParser().parse("level:$logLevel", matchCase))
        .isEqualTo(LevelFilter(logLevel, "level:$logLevel".asRange()))
    }
  }

  @Test
  fun parse_levelKeys_invalidLevel() {
    val query = "level: Invalid"

    assertThat(logcatFilterParser().parse(query, matchCase) as StringFilter)
      .isEqualTo(StringFilter(query, IMPLICIT_LINE, matchCase, query.asRange()))
  }

  @Test
  fun parse_age() {
    for (key in ageValues) {
      val clock = Clock.systemUTC()
      assertThat(logcatFilterParser(clock = clock).parse("age: $key", matchCase))
        .isEqualTo(AgeFilter(key, clock, "age: $key".asRange()))
      assertThat(logcatFilterParser(clock = clock).parse("age:$key", matchCase))
        .isEqualTo(AgeFilter(key, clock, "age:$key".asRange()))
    }
  }

  @Test
  fun parse_age_invalid() {
    for (age in invalidAges) {
      val query = "age: $age"

      assertThat(logcatFilterParser().parse(query, matchCase))
        .isEqualTo(StringFilter(query, IMPLICIT_LINE, matchCase, query.asRange()))
    }
  }

  @Test
  fun isValidLogAge() {
    for (age in ageValues) {
      assertThat(age.isValidLogAge()).named(age).isTrue()
    }
    for (age in invalidAges) {
      assertThat(age.isValidLogAge()).named(age).isFalse()
    }
  }

  @Test
  fun isValidLogLevel() {
    for (logLevel in LogLevel.entries) {
      assertThat(logLevel.name.isValidLogLevel()).named(logLevel.name).isTrue()
    }
    assertThat("foo".isValidLogLevel()).isFalse()
  }

  @Test
  fun parse_isCrash() {
    StudioFlags.LOGCAT_IS_FILTER.override(true)
    assertThat(logcatFilterParser().parse("is:crash", matchCase))
      .isEqualTo(CrashFilter("is:crash".asRange()))
  }

  @Test
  fun parse_isFirebase() {
    StudioFlags.LOGCAT_IS_FILTER.override(true)
    val filter =
      logcatFilterParser().parse("is:firebase", matchCase) as? RegexFilter
        ?: fail("Expected a RegexFilter")

    assertThat(filter.field).isEqualTo(TAG)
    // No need to test all the tags, just test a single tag and make sure we don't match substrings
    assertThat(filter.matches(LogcatMessageWrapper(logcatMessage(tag = "FA")))).isTrue()
    assertThat(filter.matches(LogcatMessageWrapper(logcatMessage(tag = "FAQ")))).isFalse()
  }

  @Test
  fun parse_isStacktrace() {
    StudioFlags.LOGCAT_IS_FILTER.override(true)
    assertThat(logcatFilterParser().parse("is:stacktrace", matchCase))
      .isEqualTo(StackTraceFilter("is:stacktrace".asRange()))
  }

  @Test
  fun parse_isLevel() {
    StudioFlags.LOGCAT_IS_FILTER.override(true)
    LogLevel.entries.forEach {
      assertThat(logcatFilterParser().parse("is:${it.stringValue}", matchCase))
        .isEqualTo(ExactLevelFilter(it, "is:${it.stringValue}".asRange()))
    }
  }

  @Test
  fun parse_is_invalid() {
    val query = "if:foo"

    assertThat(logcatFilterParser().parse(query, matchCase))
      .isEqualTo(StringFilter(query, IMPLICIT_LINE, matchCase, query.rangeOf("if:foo")))
  }

  @Test
  fun parse_topLevelExpressions_joinConsecutiveTopLevelValue_true() {

    val query = "level:INFO foo1    bar1   tag:bar2 foo2  package:foobar"
    assertThat(logcatFilterParser(joinConsecutiveTopLevelValue = true).parse(query, matchCase))
      .isEqualTo(
        AndLogcatFilter(
          LevelFilter(INFO, query.rangeOf("level:INFO")),
          StringFilter("foo1    bar1", IMPLICIT_LINE, matchCase, query.rangeOf("foo1    bar1")),
          StringFilter("bar2", TAG, matchCase, query.rangeOf("tag:bar2")),
          StringFilter("foo2", IMPLICIT_LINE, matchCase, query.rangeOf("foo2")),
          StringFilter("foobar", APP, matchCase, query.rangeOf("package:foobar")),
        )
      )
  }

  @Test
  fun parse_topLevelExpressions_joinConsecutiveTopLevelValue_false() {

    val query = "level:INFO foo1    bar1   tag:bar2 foo2  package:foobar"
    assertThat(logcatFilterParser(joinConsecutiveTopLevelValue = false).parse(query, matchCase))
      .isEqualTo(
        AndLogcatFilter(
          LevelFilter(INFO, query.rangeOf("level:INFO")),
          StringFilter("foo1", IMPLICIT_LINE, matchCase, query.rangeOf("foo1")),
          StringFilter("bar1", IMPLICIT_LINE, matchCase, query.rangeOf("bar1")),
          StringFilter("bar2", TAG, matchCase, query.rangeOf("tag:bar2")),
          StringFilter("foo2", IMPLICIT_LINE, matchCase, query.rangeOf("foo2")),
          StringFilter("foobar", APP, matchCase, query.rangeOf("package:foobar")),
        )
      )
  }

  @Test
  fun parse_topLevelExpressions_sameKey_or() {
    val parser = logcatFilterParser(topLevelSameKeyTreatment = OR)

    val query =
      "-tag:ignore1 foo tag:tag1 -tag~:ignore2 bar level:WARN tag~:tag2 tag=:tag3 -tag=:ignore3"
    assertThat(parser.parse(query, matchCase))
      .isEqualTo(
        AndLogcatFilter(
          NegatedStringFilter("ignore1", TAG, matchCase, query.rangeOf("-tag:ignore1")),
          StringFilter("foo", IMPLICIT_LINE, matchCase, query.rangeOf("foo")),
          OrLogcatFilter(
            StringFilter("tag1", TAG, matchCase, query.rangeOf("tag:tag1")),
            RegexFilter("tag2", TAG, matchCase, query.rangeOf("tag~:tag2")),
            ExactStringFilter("tag3", TAG, matchCase, query.rangeOf("tag=:tag3")),
          ),
          NegatedRegexFilter("ignore2", TAG, matchCase, query.rangeOf("-tag~:ignore2")),
          StringFilter("bar", IMPLICIT_LINE, matchCase, query.rangeOf("bar")),
          LevelFilter(WARN, query.rangeOf("level:WARN")),
          NegatedExactStringFilter("ignore3", TAG, matchCase, query.rangeOf("-tag=:ignore3")),
        )
      )
  }

  @Test
  fun parse_and() {
    val query = "tag: bar & foo & package: foobar"
    assertThat(logcatFilterParser().parse(query, matchCase))
      .isEqualTo(
        AndLogcatFilter(
          StringFilter("bar", TAG, matchCase, query.rangeOf("tag: bar")),
          StringFilter("foo", IMPLICIT_LINE, matchCase, query.rangeOf("foo")),
          StringFilter("foobar", APP, matchCase, query.rangeOf("package: foobar")),
        )
      )
  }

  @Test
  fun parse_or() {
    val query = "tag: bar | foo | package: foobar"
    assertThat(logcatFilterParser().parse(query, matchCase))
      .isEqualTo(
        OrLogcatFilter(
          StringFilter("bar", TAG, matchCase, query.rangeOf("tag: bar")),
          StringFilter("foo", IMPLICIT_LINE, matchCase, query.rangeOf("foo")),
          StringFilter("foobar", APP, matchCase, query.rangeOf("package: foobar")),
        )
      )
  }

  @Test
  fun parse_operatorPrecedence() {
    val query = "f1 & f2 | f3 & f4"
    assertThat(logcatFilterParser().parse(query, matchCase))
      .isEqualTo(
        OrLogcatFilter(
          AndLogcatFilter(
            StringFilter("f1", IMPLICIT_LINE, matchCase, query.rangeOf("f1")),
            StringFilter("f2", IMPLICIT_LINE, matchCase, query.rangeOf("f2")),
          ),
          AndLogcatFilter(
            StringFilter("f3", IMPLICIT_LINE, matchCase, query.rangeOf("f3")),
            StringFilter("f4", IMPLICIT_LINE, matchCase, query.rangeOf("f4")),
          ),
        )
      )
  }

  @Test
  fun parse_parens() {
    val query = "f1 & (tag: foo | tag: 'bar') & f4"
    assertThat(logcatFilterParser().parse(query, matchCase))
      .isEqualTo(
        AndLogcatFilter(
          StringFilter("f1", IMPLICIT_LINE, matchCase, query.rangeOf("f1")),
          OrLogcatFilter(
            StringFilter("foo", TAG, matchCase, query.rangeOf("tag: foo")),
            StringFilter("bar", TAG, matchCase, query.rangeOf("tag: 'bar'")),
          ),
          StringFilter("f4", IMPLICIT_LINE, matchCase, query.rangeOf("f4")),
        )
      )
  }

  @Test
  fun parse_emptyParens() {
    val query = "f1 & () & f4"
    assertThat(logcatFilterParser().parse(query, matchCase))
      .isEqualTo(
        AndLogcatFilter(
          StringFilter("f1", IMPLICIT_LINE, matchCase, query.rangeOf("f1")),
          EmptyFilter,
          StringFilter("f4", IMPLICIT_LINE, matchCase, query.rangeOf("f4")),
        )
      )
  }

  @Test
  fun parse_appFilter() {
    assertThat(logcatFilterParser().parse("package:mine", matchCase))
      .isEqualTo(ProjectAppFilter(fakeProjectApplicationIdsProvider, "package:mine".asRange()))
  }

  @Test
  fun parse_appFilter_nonAndroidProject() {
    assertThat(
        logcatFilterParser(androidProjectDetector = FakeAndroidProjectDetector(false))
          .parse("package:mine", matchCase)
      )
      .isEqualTo(StringFilter("mine", APP, matchCase, "package:mine".asRange()))
  }

  @Test
  fun parse_psiError() {
    val query = "key: 'foo"
    assertThat(logcatFilterParser().parse(query, matchCase))
      .isEqualTo(StringFilter(query, IMPLICIT_LINE, matchCase, query.asRange()))
  }

  @Test
  fun getUsageTrackingEvent_terms() {
    StudioFlags.LOGCAT_IS_FILTER.override(true)
    val query =
      "tag:foo tag:bar -package:foo line~:foo -message~:bar age:2m level:INFO package:mine foo is:crash is:stacktrace"

    assertThat(logcatFilterParser().getUsageTrackingEvent(query, matchCase)?.build())
      .isEqualTo(
        LogcatFilterEvent.newBuilder()
          .setTagTerms(TermVariants.newBuilder().setCount(2))
          .setPackageTerms(TermVariants.newBuilder().setCountNegated(1))
          .setMessageTerms(TermVariants.newBuilder().setCountNegatedRegex(1))
          .setLineTerms(TermVariants.newBuilder().setCountRegex(1))
          .setImplicitLineTerms(1)
          .setLevelTerms(1)
          .setAgeTerms(1)
          .setCrashTerms(1)
          .setStacktraceTerms(1)
          .setPackageProjectTerms(1)
          .build()
      )
  }

  @Test
  fun getUsageTrackingEvent_operators() {
    assertThat(
        logcatFilterParser().getUsageTrackingEvent("(foo | bar) & (for | boo)", matchCase)?.build()
      )
      .isEqualTo(
        LogcatFilterEvent.newBuilder()
          .setImplicitLineTerms(4)
          .setAndOperators(1)
          .setOrOperators(2)
          .setParentheses(2)
          .build()
      )
  }

  @Test
  fun getUsageTrackingEvent_error() {
    assertThat(logcatFilterParser().getUsageTrackingEvent("level:foo", matchCase)?.build())
      .isEqualTo(LogcatFilterEvent.newBuilder().setContainsErrors(true).build())
  }

  @Test
  fun getUsageTrackingEvent_emptyFilter() {
    assertThat(logcatFilterParser().getUsageTrackingEvent("", matchCase)?.build())
      .isEqualTo(LogcatFilterEvent.getDefaultInstance())
  }

  @Test
  fun parse_name() {
    val query = "level:INFO tag:bar package:foo name:Name"
    val filter = logcatFilterParser().parse(query, matchCase)

    assertThat(filter)
      .isEqualTo(
        AndLogcatFilter(
          LevelFilter(INFO, query.rangeOf("level:INFO")),
          StringFilter("bar", TAG, matchCase, query.rangeOf("tag:bar")),
          StringFilter("foo", APP, matchCase, query.rangeOf("package:foo")),
          NameFilter("Name", query.rangeOf("name:Name")),
        )
      )
  }

  @Test
  fun parse_name_quoted() {
    val query = """name:'Name1' name:"Name2""""
    val filter = logcatFilterParser().parse(query, matchCase)

    assertThat(filter)
      .isEqualTo(
        AndLogcatFilter(
          NameFilter("Name1", query.rangeOf("name:'Name1'")),
          NameFilter("Name2", query.rangeOf("name:\"Name2\"")),
        )
      )
  }

  @Test
  fun removeFilterNames_beginning() {
    assertThat(logcatFilterParser().removeFilterNames("name:foo package:app"))
      .isEqualTo("package:app")
  }

  @Test
  fun removeFilterNames_end() {
    assertThat(logcatFilterParser().removeFilterNames("package:app name:foo"))
      .isEqualTo("package:app")
  }

  @Test
  fun removeFilterNames_withWhitespace() {
    assertThat(logcatFilterParser().removeFilterNames("package:app name:  foo"))
      .isEqualTo("package:app")
  }

  @Test
  fun removeFilterNames_withSingleQuotes() {
    assertThat(logcatFilterParser().removeFilterNames("name:'foo' package:app"))
      .isEqualTo("package:app")
  }

  @Test
  fun removeFilterNames_withDoubleQuotes() {
    assertThat(logcatFilterParser().removeFilterNames("""name:"foo"'" package:app"""))
      .isEqualTo("package:app")
  }

  @Test
  fun removeFilterNames_multiple() {
    assertThat(logcatFilterParser().removeFilterNames("name:foo package:app name:foo"))
      .isEqualTo("package:app")
  }

  @Test
  fun findFilterForOffset() {
    val query = "(f1 | f2) & (f3 | f4)"

    val filter = logcatFilterParser().parse(query, matchCase)

    assertThat(filter?.findFilterForOffset(0)).isEqualTo(null)
    assertThat(filter?.findFilterForOffset(1))
      .isEqualTo(StringFilter("f1", IMPLICIT_LINE, matchCase, query.rangeOf("f1")))
    assertThat(filter?.findFilterForOffset(2))
      .isEqualTo(StringFilter("f1", IMPLICIT_LINE, matchCase, query.rangeOf("f1")))
    assertThat(filter?.findFilterForOffset(3)).isEqualTo(null)
    assertThat(filter?.findFilterForOffset(4)).isEqualTo(null)
    assertThat(filter?.findFilterForOffset(5)).isEqualTo(null)
    assertThat(filter?.findFilterForOffset(6))
      .isEqualTo(StringFilter("f2", IMPLICIT_LINE, matchCase, query.rangeOf("f2")))
    assertThat(filter?.findFilterForOffset(7))
      .isEqualTo(StringFilter("f2", IMPLICIT_LINE, matchCase, query.rangeOf("f2")))
    assertThat(filter?.findFilterForOffset(8)).isEqualTo(null)
    assertThat(filter?.findFilterForOffset(9)).isEqualTo(null)
    assertThat(filter?.findFilterForOffset(10)).isEqualTo(null)
    assertThat(filter?.findFilterForOffset(11)).isEqualTo(null)
    assertThat(filter?.findFilterForOffset(12)).isEqualTo(null)
    assertThat(filter?.findFilterForOffset(13))
      .isEqualTo(StringFilter("f3", IMPLICIT_LINE, matchCase, query.rangeOf("f3")))
    assertThat(filter?.findFilterForOffset(14))
      .isEqualTo(StringFilter("f3", IMPLICIT_LINE, matchCase, query.rangeOf("f3")))
    assertThat(filter?.findFilterForOffset(15)).isEqualTo(null)
    assertThat(filter?.findFilterForOffset(16)).isEqualTo(null)
    assertThat(filter?.findFilterForOffset(17)).isEqualTo(null)
    assertThat(filter?.findFilterForOffset(18))
      .isEqualTo(StringFilter("f4", IMPLICIT_LINE, matchCase, query.rangeOf("f4")))
    assertThat(filter?.findFilterForOffset(19))
      .isEqualTo(StringFilter("f4", IMPLICIT_LINE, matchCase, query.rangeOf("f4")))
    assertThat(filter?.findFilterForOffset(200)).isEqualTo(null)
    assertThat(filter?.findFilterForOffset(21)).isEqualTo(null)
    assertThat(filter?.findFilterForOffset(22)).isEqualTo(null)
    assertThat(filter?.findFilterForOffset(23)).isEqualTo(null)
    assertThat(filter?.findFilterForOffset(100)).isEqualTo(null)
  }

  private fun logcatFilterParser(
    androidProjectDetector: AndroidProjectDetector = FakeAndroidProjectDetector(true),
    joinConsecutiveTopLevelValue: Boolean = true,
    topLevelSameKeyTreatment: CombineWith = AND,
    clock: Clock = Clock.systemUTC(),
  ) =
    LogcatFilterParser(
      project,
      fakeProjectApplicationIdsProvider,
      androidProjectDetector,
      joinConsecutiveTopLevelValue,
      topLevelSameKeyTreatment,
      clock,
    )
}

private fun String.rangeOf(substring: String): TextRange {
  val i = indexOf(substring)
  return if (i < 0) TextRange.EMPTY_RANGE else TextRange(i, i + substring.length)
}

private fun String.asRange(): TextRange = TextRange(0, length)
