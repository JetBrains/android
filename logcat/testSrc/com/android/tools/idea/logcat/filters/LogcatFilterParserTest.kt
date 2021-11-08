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
import com.android.tools.idea.logcat.filters.LogcatFilterField.APP
import com.android.tools.idea.logcat.filters.LogcatFilterField.LINE
import com.android.tools.idea.logcat.filters.LogcatFilterField.MESSAGE
import com.android.tools.idea.logcat.filters.LogcatFilterField.TAG
import com.android.tools.idea.logcat.filters.parser.LogcatFilterLanguage
import com.android.tools.idea.logcat.filters.parser.LogcatFilterParserDefinition
import com.google.common.truth.Truth.assertThat
import com.intellij.lang.LanguageParserDefinitions
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.util.concurrent.TimeUnit

private val parserDefinition = LogcatFilterParserDefinition()

private val KEYS = mapOf(
  "tag" to TAG,
  "app" to APP,
  "package" to APP,
  "msg" to MESSAGE,
  "message" to MESSAGE,
  "line" to LINE,
)

private val LEVEL_KEYS = mapOf<String, (Log.LogLevel) -> LogcatFilter>(
  "level" to ::LevelFilter,
  "fromLevel" to ::FromLevelFilter,
  "toLevel" to ::ToLevelFilter)

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

/**
 * Tests for [LogcatFilterParser]
 */
@RunsInEdt
class LogcatFilterParserTest {
  private val projectRule = ProjectRule()
  private val project by lazy(projectRule::project)

  @get:Rule
  val rule = RuleChain(projectRule, EdtRule())

  @Before
  fun setUp() {
    LanguageParserDefinitions.INSTANCE.addExplicitExtension(LogcatFilterLanguage, parserDefinition)
  }

  @After
  fun tearDown() {
    LanguageParserDefinitions.INSTANCE.removeExplicitExtension(LogcatFilterLanguage, parserDefinition)
  }

  @Test
  fun parse_stringKey() {
    for ((key, field) in KEYS) {
      assertThat(logcatFilterParser().parse("$key: Foo")).isEqualTo(StringFilter("Foo", field))
    }
  }

  @Test
  fun parse_negatedStringKey() {
    for ((key, field) in KEYS) {
      assertThat(logcatFilterParser().parse("-$key: Foo")).isEqualTo(NegatedStringFilter("Foo", field))
    }
  }

  @Test
  fun parse_regexKey() {
    for ((key, field) in KEYS) {
      assertThat(logcatFilterParser().parse("$key~: Foo")).isEqualTo(RegexFilter("Foo", field))
    }
  }

  @Test
  fun parse_negatedRegexKey() {
    for ((key, field) in KEYS) {
      assertThat(logcatFilterParser().parse("-$key~: Foo")).isEqualTo(NegatedRegexFilter("Foo", field))
    }
  }

  @Test
  fun parse_levelKeys() {
    for ((key, expectedFilter) in LEVEL_KEYS) {
      for (logLevel in Log.LogLevel.values()) {
        val filter = logcatFilterParser().parse("${key}: $logLevel")

        assertThat(filter).isEqualTo(expectedFilter(logLevel))
      }
    }
  }

  @Test
  fun parse_levelKeys_invalidLevel() {
    for ((key, _) in LEVEL_KEYS) {
      for (logLevel in Log.LogLevel.values()) {
        val query = "${key}: Invalid"

        assertThat(logcatFilterParser().parse(query) as StringFilter).isEqualTo(StringFilter(query, LINE))
      }
    }
  }

  @Test
  fun parse_age() {
    for ((key, duration) in AGE_VALUES) {
      val clock = Clock.systemUTC()
      val filter = logcatFilterParser(clock).parse("age: $key")

      assertThat(filter).isEqualTo(AgeFilter(duration, clock))
    }
  }

  @Test
  fun parse_age_invalid() {
    for (age in INVALID_AGES) {
      val query = "age: $age"

      assertThat(logcatFilterParser().parse(query)).isEqualTo(StringFilter(query, LINE))
    }
  }

  @Test
  fun parse_topLevelExpressions() {

    assertThat(logcatFilterParser().parse("level: I foo    bar   tag: bar   app: foobar")).isEqualTo(
      AndLogcatFilter(
        LevelFilter(INFO),
        StringFilter("foo    bar", LINE),
        StringFilter("bar", TAG),
        StringFilter("foobar", APP),
      )
    )
  }

  @Test
  fun parse_and() {
    assertThat(logcatFilterParser().parse("tag: bar & foo & app: foobar")).isEqualTo(
      AndLogcatFilter(
        StringFilter("bar", TAG),
        StringFilter("foo", LINE),
        StringFilter("foobar", APP),
      )
    )
  }

  @Test
  fun parse_or() {
    assertThat(logcatFilterParser().parse("tag: bar | foo | app: foobar")).isEqualTo(
      OrLogcatFilter(
        StringFilter("bar", TAG),
        StringFilter("foo", LINE),
        StringFilter("foobar", APP),
      )
    )
  }

  @Test
  fun parse_operatorPrecedence() {
    assertThat(logcatFilterParser().parse("f1 & f2 | f3 & f4")).isEqualTo(
      OrLogcatFilter(
        AndLogcatFilter(
          StringFilter("f1", LINE),
          StringFilter("f2", LINE),
        ),
        AndLogcatFilter(
          StringFilter("f3", LINE),
          StringFilter("f4", LINE),
        ),
      )
    )
  }

  @Test
  fun parse_parens() {
    assertThat(logcatFilterParser().parse("f1 & (tag: foo | tag: 'bar') & f4")).isEqualTo(
      AndLogcatFilter(
        StringFilter("f1", LINE),
        OrLogcatFilter(
          StringFilter("foo", TAG),
          StringFilter("bar", TAG),
        ),
        StringFilter("f4", LINE),
      )
    )
  }

  @Test
  fun parse_psiError() {
    val query = "key: 'foo"
    assertThat(logcatFilterParser().parse(query)).isEqualTo(StringFilter(query, LINE))
  }

  private fun logcatFilterParser(clock: Clock = Clock.systemUTC()) = LogcatFilterParser(project, clock)
}
