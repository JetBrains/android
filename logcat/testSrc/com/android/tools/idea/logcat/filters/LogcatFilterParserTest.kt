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

import com.android.flags.junit.RestoreFlagRule
import com.android.tools.idea.FakeAndroidProjectDetector
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.logcat.FakePackageNamesProvider
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
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.LogcatUsageEvent.LogcatFilterEvent
import com.google.wireless.android.sdk.stats.LogcatUsageEvent.LogcatFilterEvent.TermVariants
import com.intellij.openapi.util.TextRange
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test
import java.time.Clock

private val KEYS = mapOf(
  "tag" to TAG,
  "package" to APP,
  "message" to MESSAGE,
  "line" to LINE,
)

private val AGE_VALUES = listOf(
  "10s",
  "10m",
  "10h",
  "10d",
)

private val INVALID_AGES = listOf(
  "10",
  "10f",
  "broom",
  "99999999999999999999999999999999999999999999999s", // Triggers a NumberFormatException
)

private val fakePackageNamesProvider = FakePackageNamesProvider()

/**
 * Tests for [LogcatFilterParser]
 */
@RunsInEdt
class LogcatFilterParserTest {
  private val projectRule = ProjectRule()
  private val project by lazy(projectRule::project)

  @get:Rule
  val rule = RuleChain(projectRule, EdtRule(), LogcatFilterLanguageRule(), RestoreFlagRule(StudioFlags.LOGCAT_IS_FILTER))

  @Test
  fun parse_emptyFilter() {
    assertThat(logcatFilterParser().parse("")).isNull()
  }

  @Test
  fun parse_blankFilter() {
    val filter = " \t"
    assertThat(logcatFilterParser().parse(filter)).isEqualTo(StringFilter(" \t", IMPLICIT_LINE, filter.asRange()))
  }

  @Test
  fun parse_stringKey() {
    for ((key, field) in KEYS) {
      assertThat(logcatFilterParser().parse("$key: Foo")).isEqualTo(StringFilter("Foo", field, "$key: Foo".asRange()))
      assertThat(logcatFilterParser().parse("$key:Foo")).isEqualTo(StringFilter("Foo", field, "$key:Foo".asRange()))
    }
  }

  @Test
  fun parse_stringKey_escapeChars() {
    for ((key, field) in KEYS) {
      val filterString = """
        $key:foo\ bar
        $key:'foobar'
        $key:\'foobar\'
        $key:"foobar"
        $key:\"foobar\"
        $key:foo\\bar
      """.trimIndent()
      val filter = logcatFilterParser().parse(filterString)

      assertThat(filter).isEqualTo(
        AndLogcatFilter(
          StringFilter("foo bar", field, filterString.rangeOf("$key:foo\\ bar")),
          StringFilter("foobar", field, filterString.rangeOf("$key:'foobar'")),
          StringFilter("'foobar'", field, filterString.rangeOf("$key:\\'foobar\\'")),
          StringFilter("foobar", field, filterString.rangeOf("$key:\"foobar\"")),
          StringFilter(""""foobar"""", field, filterString.rangeOf("$key:\\\"foobar\\\"")),
          StringFilter("""foo\bar""", field, filterString.rangeOf("$key:foo\\\\bar")),
        )
      )
    }
  }

  @Test
  fun parse_negatedStringKey() {
    for ((key, field) in KEYS) {
      assertThat(logcatFilterParser().parse("-$key: Foo")).isEqualTo(NegatedStringFilter("Foo", field, "-$key: Foo".asRange()))
      assertThat(logcatFilterParser().parse("-$key:Foo")).isEqualTo(NegatedStringFilter("Foo", field, "-$key:Foo".asRange()))
    }
  }

  @Test
  fun parse_regexKey() {
    for ((key, field) in KEYS) {
      assertThat(logcatFilterParser().parse("$key~: Foo")).isEqualTo(RegexFilter("Foo", field, "$key~: Foo".asRange()))
      assertThat(logcatFilterParser().parse("$key~:Foo")).isEqualTo(RegexFilter("Foo", field, "$key~:Foo".asRange()))
    }
  }

  @Test
  fun parse_negatedRegexKey() {
    for ((key, field) in KEYS) {
      assertThat(logcatFilterParser().parse("-$key~: Foo")).isEqualTo(NegatedRegexFilter("Foo", field, "-$key~: Foo".asRange()))
      assertThat(logcatFilterParser().parse("-$key~:Foo")).isEqualTo(NegatedRegexFilter("Foo", field, "-$key~:Foo".asRange()))
    }
  }

  @Test
  fun parse_invalidRegex() {
    assertThat(logcatFilterParser().parse("""tag~:\""")).isEqualTo(StringFilter("""tag~:\""", IMPLICIT_LINE, """tag~:\""".asRange()))
  }

  @Test
  fun parse_invalidNegatedRegex() {
    assertThat(logcatFilterParser().parse("""-tag~:\""")).isEqualTo(StringFilter("""-tag~:\""", IMPLICIT_LINE, """-tag~:\""".asRange()))
  }

  @Test
  fun parse_exactKey() {
    for ((key, field) in KEYS) {
      assertThat(logcatFilterParser().parse("$key=: Foo")).isEqualTo(ExactStringFilter("Foo", field, "$key=: Foo".asRange()))
      assertThat(logcatFilterParser().parse("$key=:Foo")).isEqualTo(ExactStringFilter("Foo", field, "$key=:Foo".asRange()))
    }
  }

  @Test
  fun parse_negatedExactKey() {
    for ((key, field) in KEYS) {
      assertThat(logcatFilterParser().parse("-$key=: Foo")).isEqualTo(NegatedExactStringFilter("Foo", field, "-$key=: Foo".asRange()))
      assertThat(logcatFilterParser().parse("-$key=:Foo")).isEqualTo(NegatedExactStringFilter("Foo", field, "-$key=:Foo".asRange()))
    }
  }

  @Test
  fun parse_levelKeys() {
    for (logLevel in LogLevel.values()) {
      assertThat(logcatFilterParser().parse("level: $logLevel")).isEqualTo(LevelFilter(logLevel, "level: $logLevel".asRange()))
      assertThat(logcatFilterParser().parse("level:$logLevel")).isEqualTo(LevelFilter(logLevel, "level:$logLevel".asRange()))
    }
  }

  @Test
  fun parse_levelKeys_invalidLevel() {
    val query = "level: Invalid"

    assertThat(logcatFilterParser().parse(query) as StringFilter).isEqualTo(StringFilter(query, IMPLICIT_LINE, query.asRange()))
  }

  @Test
  fun parse_age() {
    for (key in AGE_VALUES) {
      val clock = Clock.systemUTC()
      assertThat(logcatFilterParser(clock = clock).parse("age: $key")).isEqualTo(AgeFilter(key, clock, "age: $key".asRange()))
      assertThat(logcatFilterParser(clock = clock).parse("age:$key")).isEqualTo(AgeFilter(key, clock, "age:$key".asRange()))
    }
  }

  @Test
  fun parse_age_invalid() {
    for (age in INVALID_AGES) {
      val query = "age: $age"

      assertThat(logcatFilterParser().parse(query)).isEqualTo(StringFilter(query, IMPLICIT_LINE, query.asRange()))
    }
  }

  @Test
  fun isValidLogAge() {
    for (age in AGE_VALUES) {
      assertThat(age.isValidLogAge()).named(age).isTrue()
    }
    for (age in INVALID_AGES) {
      assertThat(age.isValidLogAge()).named(age).isFalse()
    }
  }

  @Test
  fun isValidLogLevel() {
    for (logLevel in LogLevel.values()) {
      assertThat(logLevel.name.isValidLogLevel()).named(logLevel.name).isTrue()
    }
    assertThat("foo".isValidLogLevel()).isFalse()
  }

  @Test
  fun parse_isCrash() {
    StudioFlags.LOGCAT_IS_FILTER.override(true)
    assertThat(logcatFilterParser().parse("is:crash")).isEqualTo(CrashFilter("is:crash".asRange()))
  }

  @Test
  fun parse_isStacktrace() {
    StudioFlags.LOGCAT_IS_FILTER.override(true)
    assertThat(logcatFilterParser().parse("is:stacktrace")).isEqualTo(StackTraceFilter("is:stacktrace".asRange()))
  }

  @Test
  fun parse_is_invalid() {
    val query = "if:foo"

    assertThat(logcatFilterParser().parse(query)).isEqualTo(
      StringFilter(query, IMPLICIT_LINE, query.rangeOf("if:foo"))
    )
  }

  @Test
  fun parse_topLevelExpressions_joinConsecutiveTopLevelValue_true() {

    val query = "level:INFO foo1    bar1   tag:bar2 foo2  package:foobar"
    assertThat(
      logcatFilterParser(joinConsecutiveTopLevelValue = true).parse(query)).isEqualTo(
      AndLogcatFilter(
        LevelFilter(INFO, query.rangeOf("level:INFO")),
        StringFilter("foo1    bar1", IMPLICIT_LINE, query.rangeOf("foo1    bar1")),
        StringFilter("bar2", TAG, query.rangeOf("tag:bar2")),
        StringFilter("foo2", IMPLICIT_LINE, query.rangeOf("foo2")),
        StringFilter("foobar", APP, query.rangeOf("package:foobar")),
      )
    )
  }

  @Test
  fun parse_topLevelExpressions_joinConsecutiveTopLevelValue_false() {

    val query = "level:INFO foo1    bar1   tag:bar2 foo2  package:foobar"
    assertThat(logcatFilterParser(joinConsecutiveTopLevelValue = false).parse(query))
      .isEqualTo(
        AndLogcatFilter(
          LevelFilter(INFO, query.rangeOf("level:INFO")),
          StringFilter("foo1", IMPLICIT_LINE, query.rangeOf("foo1")),
          StringFilter("bar1", IMPLICIT_LINE, query.rangeOf("bar1")),
          StringFilter("bar2", TAG, query.rangeOf("tag:bar2")),
          StringFilter("foo2", IMPLICIT_LINE, query.rangeOf("foo2")),
          StringFilter("foobar", APP, query.rangeOf("package:foobar")),
        )
      )
  }

  @Test
  fun parse_topLevelExpressions_sameKey_or() {
    val parser = logcatFilterParser(topLevelSameKeyTreatment = OR)

    val query = "-tag:ignore1 foo tag:tag1 -tag~:ignore2 bar level:WARN tag~:tag2 tag=:tag3 -tag=:ignore3"
    assertThat(parser.parse(query)).isEqualTo(
      AndLogcatFilter(
        NegatedStringFilter("ignore1", TAG, query.rangeOf("-tag:ignore1")),
        StringFilter("foo", IMPLICIT_LINE, query.rangeOf("foo")),
        OrLogcatFilter(
          StringFilter("tag1", TAG, query.rangeOf("tag:tag1")),
          RegexFilter("tag2", TAG, query.rangeOf("tag~:tag2")),
          ExactStringFilter("tag3", TAG,query.rangeOf("tag=:tag3")),
        ),
        NegatedRegexFilter("ignore2", TAG, query.rangeOf("-tag~:ignore2")),
        StringFilter("bar", IMPLICIT_LINE, query.rangeOf("bar")),
        LevelFilter(WARN, query.rangeOf("level:WARN")),
        NegatedExactStringFilter("ignore3", TAG, query.rangeOf("-tag=:ignore3")),
      )
    )
  }

  @Test
  fun parse_and() {
    val query = "tag: bar & foo & package: foobar"
    assertThat(logcatFilterParser().parse(query)).isEqualTo(
      AndLogcatFilter(
        StringFilter("bar", TAG, query.rangeOf("tag: bar")),
        StringFilter("foo", IMPLICIT_LINE, query.rangeOf("foo")),
        StringFilter("foobar", APP, query.rangeOf("package: foobar")),
      )
    )
  }

  @Test
  fun parse_or() {
    val query = "tag: bar | foo | package: foobar"
    assertThat(logcatFilterParser().parse(query)).isEqualTo(
      OrLogcatFilter(
        StringFilter("bar", TAG, query.rangeOf("tag: bar")),
        StringFilter("foo", IMPLICIT_LINE, query.rangeOf("foo")),
        StringFilter("foobar", APP, query.rangeOf("package: foobar")),
      )
    )
  }

  @Test
  fun parse_operatorPrecedence() {
    val query = "f1 & f2 | f3 & f4"
    assertThat(logcatFilterParser().parse(query)).isEqualTo(
      OrLogcatFilter(
        AndLogcatFilter(
          StringFilter("f1", IMPLICIT_LINE, query.rangeOf("f1")),
          StringFilter("f2", IMPLICIT_LINE, query.rangeOf("f2")),
        ),
        AndLogcatFilter(
          StringFilter("f3", IMPLICIT_LINE, query.rangeOf("f3")),
          StringFilter("f4", IMPLICIT_LINE, query.rangeOf("f4")),
        ),
      )
    )
  }

  @Test
  fun parse_parens() {
    val query = "f1 & (tag: foo | tag: 'bar') & f4"
    assertThat(logcatFilterParser().parse(query)).isEqualTo(
      AndLogcatFilter(
        StringFilter("f1", IMPLICIT_LINE, query.rangeOf("f1")),
        OrLogcatFilter(
          StringFilter("foo", TAG, query.rangeOf("tag: foo")),
          StringFilter("bar", TAG, query.rangeOf("tag: 'bar'")),
        ),
        StringFilter("f4", IMPLICIT_LINE, query.rangeOf("f4")),
      )
    )
  }

  @Test
  fun parse_appFilter() {
    assertThat(logcatFilterParser().parse("package:mine")).isEqualTo(ProjectAppFilter(fakePackageNamesProvider, "package:mine".asRange()))
  }

  @Test
  fun parse_appFilter_nonAndroidProject() {
    assertThat(logcatFilterParser(androidProjectDetector = FakeAndroidProjectDetector(false)).parse("package:mine"))
      .isEqualTo(StringFilter("mine", APP, "package:mine".asRange()))
  }

  @Test
  fun parse_psiError() {
    val query = "key: 'foo"
    assertThat(logcatFilterParser().parse(query)).isEqualTo(StringFilter(query, IMPLICIT_LINE, query.asRange()))
  }

  @Test
  fun getUsageTrackingEvent_terms() {
    StudioFlags.LOGCAT_IS_FILTER.override(true)
    val query = "tag:foo tag:bar -package:foo line~:foo -message~:bar age:2m level:INFO package:mine foo is:crash is:stacktrace"

    assertThat(logcatFilterParser().getUsageTrackingEvent(query)?.build()).isEqualTo(
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
        .build())
  }

  @Test
  fun getUsageTrackingEvent_operators() {
    assertThat(logcatFilterParser().getUsageTrackingEvent("(foo | bar) & (for | boo)")?.build()).isEqualTo(
      LogcatFilterEvent.newBuilder()
        .setImplicitLineTerms(4)
        .setAndOperators(1)
        .setOrOperators(2)
        .setParentheses(2)
        .build())
  }

  @Test
  fun getUsageTrackingEvent_error() {
    assertThat(logcatFilterParser().getUsageTrackingEvent("level:foo")?.build()).isEqualTo(
      LogcatFilterEvent.newBuilder()
        .setContainsErrors(true)
        .build())
  }

  @Test
  fun getUsageTrackingEvent_emptyFilter() {
    assertThat(logcatFilterParser().getUsageTrackingEvent("")?.build()).isEqualTo(LogcatFilterEvent.getDefaultInstance())
  }

  @Test
  fun parse_name() {
    val query = "level:INFO tag:bar package:foo name:Name"
    val filter = logcatFilterParser().parse(query)

    assertThat(filter).isEqualTo(
      AndLogcatFilter(
        LevelFilter(INFO, query.rangeOf("level:INFO")),
        StringFilter("bar", TAG, query.rangeOf("tag:bar")),
        StringFilter("foo", APP, query.rangeOf("package:foo")),
        NameFilter("Name", query.rangeOf("name:Name")),
      )
    )
  }

  @Test
  fun parse_name_quoted() {
    val query = """name:'Name1' name:"Name2""""
    val filter = logcatFilterParser().parse(query)

    assertThat(filter).isEqualTo(
      AndLogcatFilter(
        NameFilter("Name1", query.rangeOf("name:'Name1'")),
        NameFilter("Name2",  query.rangeOf("name:\"Name2\"")),
      )
    )
  }

  @Test
  fun removeFilterNames_beginning() {
    assertThat(logcatFilterParser().removeFilterNames("name:foo package:app")).isEqualTo("package:app")
  }

  @Test
  fun removeFilterNames_end() {
    assertThat(logcatFilterParser().removeFilterNames("package:app name:foo")).isEqualTo("package:app")
  }

  @Test
  fun removeFilterNames_withWhitespace() {
    assertThat(logcatFilterParser().removeFilterNames("package:app name:  foo")).isEqualTo("package:app")
  }

  @Test
  fun removeFilterNames_withSingleQuotes() {
    assertThat(logcatFilterParser().removeFilterNames("name:'foo' package:app")).isEqualTo("package:app")
  }

  @Test
  fun removeFilterNames_withDoubleQuotes() {
    assertThat(logcatFilterParser().removeFilterNames("""name:"foo"'" package:app""")).isEqualTo("package:app")
  }

  @Test
  fun removeFilterNames_multiple() {
    assertThat(logcatFilterParser().removeFilterNames("name:foo package:app name:foo")).isEqualTo("package:app")
  }

  @Test
  fun findFilterForOffset() {
    val query = "(f1 | f2) & (f3 | f4)"

    val filter = logcatFilterParser().parse(query)

    assertThat(filter?.findFilterForOffset(0)).isEqualTo(null)
    assertThat(filter?.findFilterForOffset(1)).isEqualTo(StringFilter("f1", IMPLICIT_LINE, query.rangeOf("f1")))
    assertThat(filter?.findFilterForOffset(2)).isEqualTo(StringFilter("f1", IMPLICIT_LINE, query.rangeOf("f1")))
    assertThat(filter?.findFilterForOffset(3)).isEqualTo(null)
    assertThat(filter?.findFilterForOffset(4)).isEqualTo(null)
    assertThat(filter?.findFilterForOffset(5)).isEqualTo(null)
    assertThat(filter?.findFilterForOffset(6)).isEqualTo(StringFilter("f2", IMPLICIT_LINE, query.rangeOf("f2")))
    assertThat(filter?.findFilterForOffset(7)).isEqualTo(StringFilter("f2", IMPLICIT_LINE, query.rangeOf("f2")))
    assertThat(filter?.findFilterForOffset(8)).isEqualTo(null)
    assertThat(filter?.findFilterForOffset(9)).isEqualTo(null)
    assertThat(filter?.findFilterForOffset(10)).isEqualTo(null)
    assertThat(filter?.findFilterForOffset(11)).isEqualTo(null)
    assertThat(filter?.findFilterForOffset(12)).isEqualTo(null)
    assertThat(filter?.findFilterForOffset(13)).isEqualTo(StringFilter("f3", IMPLICIT_LINE, query.rangeOf("f3")))
    assertThat(filter?.findFilterForOffset(14)).isEqualTo(StringFilter("f3", IMPLICIT_LINE, query.rangeOf("f3")))
    assertThat(filter?.findFilterForOffset(15)).isEqualTo(null)
    assertThat(filter?.findFilterForOffset(16)).isEqualTo(null)
    assertThat(filter?.findFilterForOffset(17)).isEqualTo(null)
    assertThat(filter?.findFilterForOffset(18)).isEqualTo(StringFilter("f4", IMPLICIT_LINE, query.rangeOf("f4")))
    assertThat(filter?.findFilterForOffset(19)).isEqualTo(StringFilter("f4", IMPLICIT_LINE, query.rangeOf("f4")))
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
  ) = LogcatFilterParser(
    project,
    fakePackageNamesProvider,
    androidProjectDetector,
    joinConsecutiveTopLevelValue,
    topLevelSameKeyTreatment,
    clock)
}


private fun String.rangeOf(substring: String): TextRange {
  val i = indexOf(substring)
  return if (i < 0) TextRange.EMPTY_RANGE else TextRange(i, i + substring.length)
}

private fun String.asRange(): TextRange = TextRange(0, length)
