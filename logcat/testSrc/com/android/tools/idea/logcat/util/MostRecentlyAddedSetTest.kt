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
package com.android.tools.idea.logcat.util

import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.UsefulTestCase.assertThrows
import org.junit.Test

/**
 * Tests for [MostRecentlyAddedSet]
 *
 * Note: While the implementation makes it possible to assert contents with
 * [com.google.common.truth.Ordered.inOrder], the contract doesn't require it so assertions will be
 * made without it.
 */
class MostRecentlyAddedSetTest {

  @Test
  fun add_maxSizeIsZero() {
    assertThrows(AssertionError::class.java) { MostRecentlyAddedSet<String>(0) }
  }

  @Test
  fun add() {
    val set = MostRecentlyAddedSet<String>(4)

    (1..4).forEach { set.add("$it") }

    assertThat(set).containsExactly("1", "2", "3", "4")
  }

  @Test
  fun add_evictsLeastRecentlyAdded() {
    val set = MostRecentlyAddedSet<String>(4)
    (1..4).forEach { set.add("$it") }

    set.add("5")

    assertThat(set).containsExactly("2", "3", "4", "5")
  }

  @Test
  fun add_reAdd_evictsCorrectly() {
    val set = MostRecentlyAddedSet<String>(4)
    (1..4).forEach { set.add("$it") }

    set.add("1")
    set.add("5")

    assertThat(set).containsExactly("1", "3", "4", "5")
  }
}
