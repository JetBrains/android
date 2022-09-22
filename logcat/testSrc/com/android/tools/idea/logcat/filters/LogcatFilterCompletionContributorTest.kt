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
import com.android.tools.idea.logcat.PACKAGE_NAMES_PROVIDER_KEY
import com.android.tools.idea.logcat.PackageNamesProvider
import com.android.tools.idea.logcat.TAGS_PROVIDER_KEY
import com.android.tools.idea.logcat.TagsProvider
import com.android.tools.idea.logcat.filters.parser.LogcatFilterFileType
import com.android.tools.idea.logcat.message.LogLevel
import com.android.tools.idea.logcat.settings.AndroidLogcatSettings
import com.android.tools.idea.logcat.util.AndroidProjectDetector
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.caret
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.replaceService
import org.junit.Before
import org.junit.Rule
import org.junit.Test


private val STRING_KEYS = listOf(
  "line",
  "message",
  "package",
  "tag",
)

private val ALL_STRING_KEYS = STRING_KEYS.map(String::getKeyVariants).flatten()

private val IS_VALUES = listOf("crash ", "stacktrace ")

/**
 * Tests for [LogcatFilterCompletionContributor]
 */
class LogcatFilterCompletionContributorTest {
  private val projectRule = AndroidProjectRule.inMemory()

  @get:Rule
  val chain: RuleChain = RuleChain(
    projectRule,
    EdtRule(),
    RestoreFlagRule(StudioFlags.LOGCAT_IS_FILTER),
  )

  private val fixture: CodeInsightTestFixture by lazy(projectRule::fixture)
  private val history by lazy { AndroidLogcatFilterHistory() }
  private val settings = AndroidLogcatSettings()

  @Before
  fun setUp() {
    StudioFlags.LOGCAT_IS_FILTER.override(true)
    val application = ApplicationManager.getApplication()
    application.replaceService(AndroidLogcatFilterHistory::class.java, history, projectRule.fixture.testRootDisposable)
    application.replaceService(AndroidLogcatSettings::class.java, settings, projectRule.fixture.testRootDisposable)
  }

  @Test
  fun complete_keys() {
    fixture.configure("")

    fixture.completeBasic()

    assertThat(fixture.lookupElementStrings).containsExactly(
      "age:",
      "is:",
      "level:",
      "message:",
      "name:",
      "package:",
      "package:mine ",
      "process:",
      "tag:")
  }

  @Test
  fun complete_keys_withHistory() {
    settings.filterHistoryAutocomplete = true
    history.favorites.add("favorite item")
    history.nonFavorites.add("history item")
    fixture.configure("")

    fixture.completeBasic()

    assertThat(fixture.lookupElementStrings).containsExactly(
      "age:",
      "is:",
      "level:",
      "message:",
      "name:",
      "package:",
      "package:mine ",
      "process:",
      "tag:",
      "favorite item",
      "history item",
    )
  }

  /**
   * This test uses the message key, but it represents the behavior of the other keys as well.
   *
   * This is not ideal but having a pair of tests for each key seems like overkill and a generic test will be unreadable.
   */
  @Test
  fun complete_message() {
    fixture.configure("mes$caret")

    fixture.completeBasic()

    assertThat(fixture.lookupElementStrings).containsExactly(
      "message:",
      "message~:",
      "-message:",
      "-message~:",
      "message=:",
      "-message=:",
    )
  }

  /**
   * This test uses the message key, but it represents the behavior of the other keys as well.
   *
   * This is not ideal but having a pair of tests for each key seems like overkill and a generic test will be unreadable.
   */
  @Test
  fun complete_message_withHistory() {
    settings.filterHistoryAutocomplete = true
    history.favorites.add("favorite item")
    history.favorites.add("message:favorite")
    history.nonFavorites.add("history item")
    history.nonFavorites.add("message:history")
    fixture.configure("mes$caret")

    fixture.completeBasic()

    assertThat(fixture.lookupElementStrings).containsExactly(
      "message:",
      "message~:",
      "-message:",
      "-message~:",
      "message=:",
      "-message=:",
      "message:favorite",
      "message:history",
    )
  }

  @Test
  fun complete_afterKey_withoutWhitespace() {
    for (key in ALL_STRING_KEYS) {
      fixture.configure("$key$caret")

      fixture.completeBasic()

      if (key.isPackageKey()) {
        // Package always has a "mine" item even if not apps are present.
        assertThat(fixture.lookupElementStrings).named("$key with whitespace").containsExactly("mine ")
      }
      else {
        assertThat(fixture.lookupElementStrings).named("$key with whitespace").isEmpty()
      }
    }
  }

  @Test
  fun complete_afterKey_withWhitespace() {
    for (key in ALL_STRING_KEYS) {
      fixture.configure("$key  $caret")

      fixture.completeBasic()

      if (key.isPackageKey()) {
        // Package always has a "mine" item even if not apps are present.
        assertThat(fixture.lookupElementStrings).named("$key with whitespace").containsExactly("mine ")
      }
      else {
        assertThat(fixture.lookupElementStrings).named("$key with whitespace").isEmpty()
      }
    }
  }

  @Test
  fun complete_afterAge_withoutWhitespace() {
    fixture.configure("age:$caret")

    fixture.completeBasic()

    assertThat(fixture.lookupElementStrings).containsExactly("1d ", "3h ", "5m ", "30s ")
  }

  @Test
  fun complete_afterAge_withWhitespace() {
    fixture.configure("age: $caret")

    fixture.completeBasic()

    assertThat(fixture.lookupElementStrings).containsExactly("1d ", "3h ", "5m ", "30s ")
  }

  @Test
  fun complete_levels_withoutWhitespace() {
    val levels = LogLevel.values().map { "${it.name.lowercase()} " }
    fixture.configure("level:$caret")

    fixture.completeBasic()

    assertThat(fixture.lookupElementStrings).containsExactlyElementsIn(levels)
  }

  @Test
  fun complete_levels_withWhitespace() {
    val levels = LogLevel.values().map { "${it.name.lowercase()} " }
    fixture.configure("level:  $caret")

    fixture.completeBasic()

    assertThat(fixture.lookupElementStrings).containsExactlyElementsIn(levels)
  }

  @Test
  fun complete_levels_lowercase() {
    LogLevel.values().map { it.name.lowercase() }.forEach {
      //Use a prefix of 3 letters so all levels get a single completion and insert it rather than some showing a list
      fixture.configure("level:${it.substring(0, 3)}$caret")
      fixture.completeBasic()
      assertThat(fixture.editor.document.text).named(it).isEqualTo("level:$it ")
    }
  }

  @Test
  fun complete_levels_uppercase() {
    LogLevel.values().map { it.name.uppercase() }.forEach {
      //Use a prefix of 3 letters so all levels get a single completion and insert it rather than some showing a list
      fixture.configure("level:${it.substring(0, 3)}$caret")
      fixture.completeBasic()
      assertThat(fixture.editor.document.text).named(it).isEqualTo("level:$it ")
    }
  }

  @Test
  fun complete_levels_withoutColon() {
    fixture.configure("le$caret")

    fixture.completeBasic()

    assertThat(fixture.lookupElementStrings).containsExactly(
      "level:verbose ",
      "level:debug ",
      "level:info ",
      "level:warn ",
      "level:error ",
      "level:assert ",
    )
  }

  @Test
  fun complete_is_withoutWhitespace() {
    fixture.configure("is:$caret")

    fixture.completeBasic()

    assertThat(fixture.lookupElementStrings).named("is with no whitespace").containsExactlyElementsIn(IS_VALUES)
  }

  @Test
  fun complete_is_withWhitespace() {
    fixture.configure("is:   $caret")

    fixture.completeBasic()

    assertThat(fixture.lookupElementStrings).named("is with no whitespace").containsExactlyElementsIn(IS_VALUES)
  }

  @Test
  fun complete_is_withoutColon() {
    fixture.configure("is$caret")

    fixture.completeBasic()

    assertThat(fixture.lookupElementStrings).containsExactly(
      "is:crash ",
      "is:stacktrace ",
    )
  }

  @Test
  fun complete_tags_withoutWhiteSpace() {
    for (key in "tag".getKeyVariants()) {
      fixture.configure("$key$caret", tags = setOf("Tag1", "Tag2"))

      fixture.completeBasic()

      assertThat(fixture.lookupElementStrings).named("$key without whitespace").containsExactlyElementsIn(setOf("Tag1 ", "Tag2 "))
    }
  }

  @Test
  fun complete_tags_withWhiteSpace() {
    for (key in "tag".getKeyVariants()) {
      fixture.configure("$key $caret", tags = setOf("Tag1", "Tag2"))

      fixture.completeBasic()

      assertThat(fixture.lookupElementStrings).named("$key with whitespace").containsExactlyElementsIn(setOf("Tag1 ", "Tag2 "))
    }
  }

  @Test
  fun complete_tags_removesBankTags() {
    for (key in "tag".getKeyVariants()) {
      fixture.configure("$key $caret", tags = setOf("Tag1", "Tag2", "  "))

      fixture.completeBasic()

      assertThat(fixture.lookupElementStrings).named("$key with whitespace").containsExactlyElementsIn(setOf("Tag1 ", "Tag2 "))
    }
  }

  @Test
  fun complete_packages_withoutWhiteSpace() {
    for (key in "package".getKeyVariants()) {
      fixture.configure("$key$caret", packages = setOf("package1", "package2"))

      fixture.completeBasic()

      if (key.isPackageKey()) {
        assertThat(fixture.lookupElementStrings).named("$key with whitespace")
          .containsExactlyElementsIn(setOf("mine ", "package1 ", "package2 "))
      }
      else {
        assertThat(fixture.lookupElementStrings).named("$key with whitespace")
          .containsExactlyElementsIn(setOf("package1 ", "package2 "))
      }
    }
  }

  @Test
  fun complete_packages_withWhiteSpace() {
    for (key in "package".getKeyVariants()) {
      fixture.configure("$key $caret", packages = setOf("package1", "package2"))

      fixture.completeBasic()

      if (key.isPackageKey()) {
        assertThat(fixture.lookupElementStrings).named("$key with whitespace")
          .containsExactlyElementsIn(setOf("mine ", "package1 ", "package2 "))
      }
      else {
        assertThat(fixture.lookupElementStrings).named("$key with whitespace")
          .containsExactlyElementsIn(setOf("package1 ", "package2 "))
      }
    }
  }

  @Test
  fun complete_insideQuotes() {
    listOf("'foo $caret'", "\"foo $caret\"").forEach {
      fixture.configure(it)

      fixture.completeBasic()

      assertThat(fixture.lookupElementStrings).named(it).isEmpty()
    }
  }

  @Test
  fun complete_insideUnterminatedQuotes() {
    listOf("'foo $caret", "\"foo $caret").forEach {
      fixture.configure(it)

      fixture.completeBasic()

      assertThat(fixture.lookupElementStrings).named(it).isEmpty()
    }
  }

  @Test
  fun complete_afterClosingQuoteOrParen() {
    listOf("'foo'$caret", "\"foo\"$caret", "(foo)$caret").forEach {
      fixture.configure(it)

      fixture.completeBasic()

      assertThat(fixture.lookupElementStrings).named(it).isEmpty()
    }
  }

  @Test
  fun complete_afterClosingQuoteOrParenFollowedBySpace() {
    listOf("'foo' $caret", "\"foo\" $caret", "(foo) $caret").forEach {
      fixture.configure(it)

      fixture.completeBasic()

      assertThat(fixture.lookupElementStrings).named(it).containsExactly(
        "age:",
        "is:",
        "level:",
        "message:",
        "name:",
        "package:",
        "package:mine ",
        "process:",
        "tag:")
    }
  }

  @Test
  fun nonAndroidProject_doesNotProvideProjectPackageKey() {
    fixture.configure("package$caret", androidProjectDetector = FakeAndroidProjectDetector(false))

    fixture.completeBasic()

    assertThat(fixture.lookupElementStrings).containsExactly(
      "package:",
      "package~:",
      "-package:",
      "-package~:",
      "package=:",
      "-package=:",
    )
  }

  @Test
  fun nonAndroidProject_doesNotProvideProjectPackageValue() {
    fixture.configure("package:$caret", packages = setOf("foo"), androidProjectDetector = FakeAndroidProjectDetector(false))

    fixture.completeBasic()

    assertThat(fixture.lookupElementStrings).containsExactly("foo ")
  }

  @Test
  fun caseInsensitivity() {
    listOf("tag:t$caret", "tag:T$caret").forEach {
      fixture.configure(it, tags = setOf("Tag", "tag"))

      fixture.completeBasic()

      assertThat(fixture.lookupElementStrings).named("it").containsExactlyElementsIn(setOf("Tag ", "tag "))
    }
  }
}

private fun String.isPackageKey() = equals("package:")

/**
 * Configure fixture with given text and set up its editor.
 */
private fun CodeInsightTestFixture.configure(
  text: String,
  tags: Set<String> = emptySet(),
  packages: Set<String> = emptySet(),
  androidProjectDetector: AndroidProjectDetector = FakeAndroidProjectDetector(true)
) {
  configureByText(LogcatFilterFileType, text)
  // This can't be done in the setUp() method because the editor is only created when the fixture is configured.
  editor.apply {
    putUserData(TAGS_PROVIDER_KEY, object : TagsProvider {
      override fun getTags(): Set<String> = tags
    })
    putUserData(PACKAGE_NAMES_PROVIDER_KEY, object : PackageNamesProvider {
      override fun getPackageNames(): Set<String> = packages
    })
    putUserData(AndroidProjectDetector.KEY, androidProjectDetector)
  }
}
