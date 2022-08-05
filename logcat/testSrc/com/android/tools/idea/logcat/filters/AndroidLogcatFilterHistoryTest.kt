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

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tests for [AndroidLogcatFilterHistory]
 */
class AndroidLogcatFilterHistoryTest {
  @Test
  fun add_maxNonFavoriteItems() {
    val history = AndroidLogcatFilterHistory(maxNonFavoriteItems = 3)

    for (filter in listOf("foo1", "foo2", "foo3", "foo4")) {
      history.add(filter, isFavorite = false)
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
      history.add(filter, isFavorite = true)
    }
    assertThat(history.favorites).containsExactly(
      "foo4",
      "foo3",
      "foo2",
      "foo1",
    ).inOrder()
  }

  @Test
  fun add_nonFavoriteItems_bubbleUp() {
    val history = AndroidLogcatFilterHistory()

    for (filter in listOf("foo1", "foo2", "foo3", "foo1")) {
      history.add(filter, isFavorite = false)
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
      history.add(filter, isFavorite = true)
    }
    assertThat(history.favorites).containsExactly(
      "foo1",
      "foo3",
      "foo2",
    ).inOrder()
  }
}