/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.tools.idea.logcat.FakePackageNamesProvider
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test

/**
 * Tests for [AndroidLogcatFilterHistory]
 */
@RunsInEdt
class AndroidLogcatFilterHistoryTest {
  private val projectRule = ProjectRule()

  @get:Rule
  val rule = RuleChain(projectRule, EdtRule())

  private val logcatFilterParser by lazy { LogcatFilterParser(projectRule.project, FakePackageNamesProvider()) }

  @Test
  fun add_maxNonFavoriteItems() {
    val history = AndroidLogcatFilterHistory(maxNonFavoriteItems = 3)

    for (filter in listOf("foo1", "foo2", "foo3", "foo4")) {
      history.add(logcatFilterParser, filter, isFavorite = false)
    }
    assertThat(history.nonFavorites).containsExactly(
      "foo4",
      "foo3",
      "foo2",
    ).inOrder()
  }

  @Test
  fun add_maxNonFavoriteItems_doesNotAffectFavorites() {
    val history = AndroidLogcatFilterHistory(maxNonFavoriteItems = 3)

    for (filter in listOf("foo1", "foo2", "foo3", "foo4")) {
      history.add(logcatFilterParser, filter, isFavorite = true)
    }
    assertThat(history.favorites).containsExactly(
      "foo4",
      "foo3",
      "foo2",
      "foo1",
    ).inOrder()
  }

  @Test
  fun add_maxNonFavoriteItems_doesNotAffectNamed() {
    val history = AndroidLogcatFilterHistory(maxNonFavoriteItems = 3)

    for (filter in listOf("name:foo1", "name:foo2", "name:foo3", "name:foo4")) {
      history.add(logcatFilterParser, filter, isFavorite = false)
    }
    assertThat(history.named).containsExactly(
      "name:foo4",
      "name:foo3",
      "name:foo2",
      "name:foo1",
    ).inOrder()
  }

  @Test
  fun add_nonFavoriteItems_bubbleUp() {
    val history = AndroidLogcatFilterHistory()

    for (filter in listOf("foo1", "foo2", "foo3", "foo1")) {
      history.add(logcatFilterParser, filter, isFavorite = false)
    }
    assertThat(history.nonFavorites).containsExactly(
      "foo1",
      "foo3",
      "foo2",
    ).inOrder()
  }

  @Test
  fun add_favoriteItems_bubbleUp() {
    val history = AndroidLogcatFilterHistory()

    for (filter in listOf("foo1", "foo2", "foo3", "foo1")) {
      history.add(logcatFilterParser, filter, isFavorite = true)
    }
    assertThat(history.favorites).containsExactly(
      "foo1",
      "foo3",
      "foo2",
    ).inOrder()
  }

  @Test
  fun add_namedItems_bubbleUp() {
    val history = AndroidLogcatFilterHistory()

    for (filter in listOf("name:foo1", "name:foo2", "name:foo3", "name:foo1")) {
      history.add(logcatFilterParser, filter, isFavorite = false)
    }
    assertThat(history.named).containsExactly(
      "name:foo1",
      "name:foo3",
      "name:foo2",
    ).inOrder()
  }

  @Test
  fun add_namedFavoriteIsFavorite() {
    val history = AndroidLogcatFilterHistory()

    history.add(logcatFilterParser, "name:foo", isFavorite = true)
    assertThat(history.named).isEmpty()
    assertThat(history.favorites).containsExactly("name:foo")
  }
}