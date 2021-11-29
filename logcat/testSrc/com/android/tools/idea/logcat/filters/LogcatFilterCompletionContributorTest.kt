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

import com.android.ddmlib.Log.LogLevel
import com.android.tools.idea.logcat.filters.parser.LogcatFilterFileType
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.caret
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

private val STRING_KEYS = listOf(
  "line",
  "message",
  "package",
  "tag",
).map { listOf("$it:", "-$it:", "$it~:", "-$it~:") }.flatten()

private val LEVEL_KEYS = listOf(
  "fromLevel:",
  "level:",
  "toLevel:",
)

private const val AGE_KEY = "age:"

private const val PROJECT_APP = "app! "

private val KEYS = STRING_KEYS + LEVEL_KEYS + AGE_KEY + PROJECT_APP

/**
 * Tests for [LogcatFilterCompletionContributor]
 */
class LogcatFilterCompletionContributorTest {
  private val projectRule = AndroidProjectRule.inMemory()

  @get:Rule
  val chain: RuleChain = RuleChain.outerRule(projectRule).around(EdtRule())

  private val fixture: CodeInsightTestFixture by lazy(projectRule::fixture)

  @Test
  fun complete_keys() {
    fixture.configureByText(LogcatFilterFileType, "")

    fixture.completeBasic()

    assertThat(fixture.lookupElementStrings).containsExactlyElementsIn(KEYS)
  }

  @Test
  fun complete_afterKey_withoutWhitespace() {
    for (key in STRING_KEYS + AGE_KEY) {
      fixture.configureByText(LogcatFilterFileType, "$key$caret")

      fixture.completeBasic()

      assertThat(fixture.lookupElementStrings).named("$key with no whitespace").isEmpty()
    }
  }

  @Test
  fun complete_afterKey_withWhitespace() {
    for (key in STRING_KEYS + AGE_KEY) {
      fixture.configureByText(LogcatFilterFileType, "$key  $caret")

      fixture.completeBasic()

      assertThat(fixture.lookupElementStrings).named("$key with whitespace").isEmpty()
    }
  }

  @Test
  fun complete_afterLevelKey_withoutWhitespace() {
    val levels = LogLevel.values().map { "${it.name} " }
    for (key in LEVEL_KEYS) {
      fixture.configureByText(LogcatFilterFileType, "$key$caret")

      fixture.completeBasic()

      assertThat(fixture.lookupElementStrings).named("$key with no whitespace").containsExactlyElementsIn(levels)
    }
  }

  @Test
  fun complete_afterLevelKey_withWhitespace() {
    val levels = LogLevel.values().map { "${it.name} " }
    for (key in LEVEL_KEYS) {
      fixture.configureByText(LogcatFilterFileType, "$key  $caret")

      fixture.completeBasic()

      assertThat(fixture.lookupElementStrings).named("$key with whitespace").containsExactlyElementsIn(levels)
    }
  }
}
