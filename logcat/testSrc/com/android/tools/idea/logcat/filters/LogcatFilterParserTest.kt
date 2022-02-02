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

import com.android.ddmlib.Log
import com.android.ddmlib.Log.LogLevel.INFO
import com.android.ddmlib.Log.LogLevel.WARN
import com.android.tools.idea.logcat.FakePackageNamesProvider
import com.android.tools.idea.logcat.filters.LogcatFilterField.APP
import com.android.tools.idea.logcat.filters.LogcatFilterField.IMPLICIT_LINE
import com.android.tools.idea.logcat.filters.LogcatFilterField.LINE
import com.android.tools.idea.logcat.filters.LogcatFilterField.MESSAGE
import com.android.tools.idea.logcat.filters.LogcatFilterField.TAG
import com.android.tools.idea.logcat.filters.LogcatFilterParser.CombineWith
import com.android.tools.idea.logcat.filters.LogcatFilterParser.CombineWith.AND
import com.android.tools.idea.logcat.filters.LogcatFilterParser.CombineWith.OR
import com.android.tools.idea.logcat.util.LogcatFilterLanguageRule
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.util.concurrent.TimeUnit

private val KEYS = mapOf(
  "tag" to TAG,
  "package" to APP,
  "message" to MESSAGE,
  "line" to LINE,
)

private val AGE_VALUES = mapOf(
  "10s" to Duration.ofSeconds(10),
  "10m" to Duration.ofSeconds(TimeUnit.MINUTES.toSeconds(10)),
  "10h" to Duration.ofSeconds(TimeUnit.HOURS.toSeconds(10)),
  "10d" to Duration.ofSeconds(TimeUnit.DAYS.toSeconds(10)),
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
  val rule = RuleChain(projectRule, EdtRule(), LogcatFilterLanguageRule())

  @Test
  fun parse_stringKey() {
    for ((key, field) in KEYS) {
      assertThat(logcatFilterParser().parse("$key: Foo")).isEqualTo(StringFilter("Foo", field))
      assertThat(logcatFilterParser().parse("$key:Foo")).isEqualTo(StringFilter("Foo", field))
    }
  }

  @Test
  fun parse_negatedStringKey() {
    for ((key, field) in KEYS) {
      assertThat(logcatFilterParser().parse("-$key: Foo")).isEqualTo(NegatedStringFilter("Foo", field))
      assertThat(logcatFilterParser().parse("-$key:Foo")).isEqualTo(NegatedStringFilter("Foo", field))
    }
  }

  @Test
  fun parse_regexKey() {
    for ((key, field) in KEYS) {
      assertThat(logcatFilterParser().parse("$key~: Foo")).isEqualTo(RegexFilter("Foo", field))
      assertThat(logcatFilterParser().parse("$key~:Foo")).isEqualTo(RegexFilter("Foo", field))
    }
  }

  @Test
  fun parse_negatedRegexKey() {
    for ((key, field) in KEYS) {
      assertThat(logcatFilterParser().parse("-$key~: Foo")).isEqualTo(NegatedRegexFilter("Foo", field))
      assertThat(logcatFilterParser().parse("-$key~:Foo")).isEqualTo(NegatedRegexFilter("Foo", field))
    }
  }

  @Test
  fun parse_invalidRegex() {
    assertThat(logcatFilterParser().parse("""tag~:\""")).isEqualTo(StringFilter("""tag~:\""", IMPLICIT_LINE))
  }

  @Test
  fun parse_invalidNegatedRegex() {
    assertThat(logcatFilterParser().parse("""-tag~:\""")).isEqualTo(StringFilter("""-tag~:\""", IMPLICIT_LINE))
  }

  @Test
  fun parse_levelKeys() {
    for (logLevel in Log.LogLevel.values()) {
      assertThat(logcatFilterParser().parse("level: $logLevel")).isEqualTo(LevelFilter(logLevel))
      assertThat(logcatFilterParser().parse("level:$logLevel")).isEqualTo(LevelFilter(logLevel))
    }
  }

  @Test
  fun parse_levelKeys_invalidLevel() {
    val query = "level: Invalid"

    assertThat(logcatFilterParser().parse(query) as StringFilter).isEqualTo(StringFilter(query, IMPLICIT_LINE))
  }

  @Test
  fun parse_age() {
    for ((key, duration) in AGE_VALUES) {
      val clock = Clock.systemUTC()
      assertThat(logcatFilterParser(clock = clock).parse("age: $key")).isEqualTo(AgeFilter(duration, clock))
      assertThat(logcatFilterParser(clock = clock).parse("age:$key")).isEqualTo(AgeFilter(duration, clock))
    }
  }

  @Test
  fun parse_age_invalid() {
    for (age in INVALID_AGES) {
      val query = "age: $age"

      assertThat(logcatFilterParser().parse(query)).isEqualTo(StringFilter(query, IMPLICIT_LINE))
    }
  }

  @Test
  fun parse_topLevelExpressions_joinConsecutiveTopLevelValue_true() {

    assertThat(logcatFilterParser(joinConsecutiveTopLevelValue = true).parse("level:I foo    bar   tag:bar foo  package:foobar")).isEqualTo(
      AndLogcatFilter(
        LevelFilter(INFO),
        StringFilter("foo    bar", IMPLICIT_LINE),
        StringFilter("bar", TAG),
        StringFilter("foo", IMPLICIT_LINE),
        StringFilter("foobar", APP),
      )
    )
  }

  @Test
  fun parse_topLevelExpressions_joinConsecutiveTopLevelValue_false() {

    assertThat(logcatFilterParser(joinConsecutiveTopLevelValue = false).parse("level:I foo    bar   tag:bar foo  package:foobar"))
      .isEqualTo(
        AndLogcatFilter(
          LevelFilter(INFO),
          StringFilter("foo", IMPLICIT_LINE),
          StringFilter("bar", IMPLICIT_LINE),
          StringFilter("bar", TAG),
          StringFilter("foo", IMPLICIT_LINE),
          StringFilter("foobar", APP),
        )
      )
  }

  @Test
  fun parse_topLevelExpressions_sameKey_or() {
    val parser = logcatFilterParser(topLevelSameKeyTreatment = OR)

    assertThat(parser.parse("-tag:ignore1 foo tag:tag1 -tag~:ignore2 bar level:W tag~:tag2")).isEqualTo(
      AndLogcatFilter(
        NegatedStringFilter("ignore1", TAG),
        StringFilter("foo", IMPLICIT_LINE),
        OrLogcatFilter(
          StringFilter("tag1", TAG),
          RegexFilter("tag2", TAG),
        ),
        NegatedRegexFilter("ignore2", TAG),
        StringFilter("bar", IMPLICIT_LINE),
        LevelFilter(WARN),
      )
    )
  }

  @Test
  fun parse_and() {
    assertThat(logcatFilterParser().parse("tag: bar & foo & package: foobar")).isEqualTo(
      AndLogcatFilter(
        StringFilter("bar", TAG),
        StringFilter("foo", IMPLICIT_LINE),
        StringFilter("foobar", APP),
      )
    )
  }

  @Test
  fun parse_or() {
    assertThat(logcatFilterParser().parse("tag: bar | foo | package: foobar")).isEqualTo(
      OrLogcatFilter(
        StringFilter("bar", TAG),
        StringFilter("foo", IMPLICIT_LINE),
        StringFilter("foobar", APP),
      )
    )
  }

  @Test
  fun parse_operatorPrecedence() {
    assertThat(logcatFilterParser().parse("f1 & f2 | f3 & f4")).isEqualTo(
      OrLogcatFilter(
        AndLogcatFilter(
          StringFilter("f1", IMPLICIT_LINE),
          StringFilter("f2", IMPLICIT_LINE),
        ),
        AndLogcatFilter(
          StringFilter("f3", IMPLICIT_LINE),
          StringFilter("f4", IMPLICIT_LINE),
        ),
      )
    )
  }

  @Test
  fun parse_parens() {
    assertThat(logcatFilterParser().parse("f1 & (tag: foo | tag: 'bar') & f4")).isEqualTo(
      AndLogcatFilter(
        StringFilter("f1", IMPLICIT_LINE),
        OrLogcatFilter(
          StringFilter("foo", TAG),
          StringFilter("bar", TAG),
        ),
        StringFilter("f4", IMPLICIT_LINE),
      )
    )
  }

  @Test
  fun parse_appFilter() {
    assertThat(logcatFilterParser().parse("package:mine")).isEqualTo(ProjectAppFilter(fakePackageNamesProvider))
  }

  @Test
  fun parse_psiError() {
    val query = "key: 'foo"
    assertThat(logcatFilterParser().parse(query)).isEqualTo(StringFilter(query, IMPLICIT_LINE))
  }

  private fun logcatFilterParser(
    joinConsecutiveTopLevelValue: Boolean = true,
    topLevelSameKeyTreatment: CombineWith = AND,
    clock: Clock = Clock.systemUTC(),
  ) = LogcatFilterParser(project, fakePackageNamesProvider, joinConsecutiveTopLevelValue, topLevelSameKeyTreatment, clock)
}
