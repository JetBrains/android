/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.insights

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SelectionTest {

  @Test
  fun `select with valid value should change it`() {
    val selection = Selection(1, listOf(1, 2, 3))
    assertThat(selection.select(2)).isEqualTo(Selection(2, listOf(1, 2, 3)))
  }

  @Test
  fun `select with invalid value should be a noop`() {
    val selection = Selection(1, listOf(1, 2, 3))
    assertThat(selection.select(4)).isEqualTo(selection)
  }

  @Test
  fun `select with null value should deselect`() {
    val selection = Selection(1, listOf(1, 2, 3))
    assertThat(selection.select(null)).isEqualTo(Selection(null, listOf(1, 2, 3)))
  }

  @Test
  fun `deselect should make selection null`() {
    val selection = Selection(1, listOf(1, 2, 3))
    assertThat(selection.deselect()).isEqualTo(Selection(null, listOf(1, 2, 3)))
  }

  @Test
  fun `selectionOf for enums without initialValue should create valid selection`() {
    val selection = selectionOf<SelectionEnum>()
    assertThat(selection).isEqualTo(Selection(null, listOf(SelectionEnum.ONE, SelectionEnum.TWO)))
  }

  @Test
  fun `selectionOf for enums with initialValue should create valid selection`() {
    val selection = selectionOf(initialValue = SelectionEnum.ONE)
    assertThat(selection)
      .isEqualTo(Selection(SelectionEnum.ONE, listOf(SelectionEnum.ONE, SelectionEnum.TWO)))
  }

  @Test
  fun `multi-select with valid value should change it`() {
    val selection = MultiSelection(setOf(1), listOf(1, 2, 3))
    assertThat(selection.toggle(2)).isEqualTo(MultiSelection(setOf(1, 2), listOf(1, 2, 3)))
  }

  @Test
  fun `multi-select with invalid value should be a noop`() {
    val selection = MultiSelection(setOf(1), listOf(1, 2, 3))
    assertThat(selection.toggle(4)).isEqualTo(selection)
  }

  @Test
  fun `multiSelectionOf for enums without initialValue should create valid selection`() {
    val selection = multiSelectionOf<SelectionEnum>()
    assertThat(selection)
      .isEqualTo(MultiSelection(emptySet(), listOf(SelectionEnum.ONE, SelectionEnum.TWO)))
  }

  @Test
  fun `multiSelectionOf for enums with initialValue should create valid selection`() {
    val selection = multiSelectionOf(initialValue = setOf(SelectionEnum.TWO))
    assertThat(selection)
      .isEqualTo(
        MultiSelection(setOf(SelectionEnum.TWO), listOf(SelectionEnum.ONE, SelectionEnum.TWO))
      )
  }

  @Test
  fun `multi-select with`() {
    val one = "one"
    val two = "two"
    val selection = MultiSelection(setOf(one), listOf(one, two))
    selection.select("three")
    assertThat(selection.select("three")).isEqualTo(selection)
  }

  @Test
  fun `multi-select with1`() {
    val one = "one"
    val two = "two"
    val selection = MultiSelection(setOf(one), listOf(one, two))
    selection.select(one)
    assertThat(selection.select("three")).isEqualTo(selection)
  }

  @Test
  fun `multi-select with2`() {
    val one = "one"
    val two = "two"
    val selection = MultiSelection(setOf(one), listOf(one, two))
    selection.select(two)
    assertThat(selection.select(two)).isEqualTo(MultiSelection(setOf(one, two), listOf(one, two)))
  }
}

enum class SelectionEnum {
  ONE,
  TWO
}
