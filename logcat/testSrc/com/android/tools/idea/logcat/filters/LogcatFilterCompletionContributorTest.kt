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
import com.android.tools.idea.logcat.PACKAGE_NAMES_PROVIDER_KEY
import com.android.tools.idea.logcat.PackageNamesProvider
import com.android.tools.idea.logcat.TAGS_PROVIDER_KEY
import com.android.tools.idea.logcat.TagsProvider
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
).map(String::getKeyVariants).flatten()

private val KEYS = STRING_KEYS + "level:" + "age:" + "package:mine "

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
    fixture.configure("")

    fixture.completeBasic()

    assertThat(fixture.lookupElementStrings).containsExactlyElementsIn(KEYS)
  }

  @Test
  fun complete_afterKey_withoutWhitespace() {
    for (key in STRING_KEYS + "age:") {
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
    for (key in STRING_KEYS + "age:") {
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
  fun complete_levels_withoutWhitespace() {
    val levels = LogLevel.values().map { "${it.name} " }
    fixture.configure("level:$caret")

    fixture.completeBasic()

    assertThat(fixture.lookupElementStrings).named("level with no whitespace").containsExactlyElementsIn(levels)
  }

  @Test
  fun complete_levels_withWhitespace() {
    val levels = LogLevel.values().map { "${it.name} " }
    fixture.configure("level:  $caret")

    fixture.completeBasic()

    assertThat(fixture.lookupElementStrings).named("level with whitespace").containsExactlyElementsIn(levels)
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

      assertThat(fixture.lookupElementStrings).named(it).containsExactlyElementsIn(KEYS)
    }
  }
}

private fun String.isPackageKey() = equals("package:")

/**
 * Configure fixture with given text and set up its editor.
 */
private fun CodeInsightTestFixture.configure(text: String, tags: Set<String> = emptySet(), packages: Set<String> = emptySet()) {
  configureByText(LogcatFilterFileType, text)
  // This can't be done in the setUp() method because the editor is only created when the fixture is configured.
  editor.putUserData(TAGS_PROVIDER_KEY, object : TagsProvider {
    override fun getTags(): Set<String> = tags
  })
  editor.putUserData(PACKAGE_NAMES_PROVIDER_KEY, object : PackageNamesProvider {
    override fun getPackageNames(): Set<String> = packages
  })
}
