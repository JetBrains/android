/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.adtui.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MultiSelectionModelTest {

  @Test
  fun getSelection() {
    val selectionModel = MultiSelectionModel<Int>()
    selectionModel.addToSelection(1)
    selectionModel.addToSelection(2)
    selectionModel.addToSelection(2)
    assertThat(selectionModel.selection).containsExactly(1, 2)
  }

  @Test
  fun addToSelection() {
    val selectionModel = MultiSelectionModel<String>()
    selectionModel.addToSelection("foo")
    assertThat(selectionModel.selection).containsExactly("foo")
  }

  @Test
  fun deselect() {
    val selectionModel = MultiSelectionModel<Boolean>()
    selectionModel.addToSelection(true)
    selectionModel.addToSelection(false)

    selectionModel.deselect(true)
    assertThat(selectionModel.selection).containsExactly(false)
    selectionModel.deselect(true)
    assertThat(selectionModel.selection).containsExactly(false)
  }

  @Test
  fun clearSelection() {
    val selectionModel = MultiSelectionModel<Int>()
    selectionModel.addToSelection(1)
    selectionModel.addToSelection(2)

    selectionModel.clearSelection()
    assertThat(selectionModel.selection).isEmpty()
  }

  @Test
  fun isSelected() {
    val selectionModel = MultiSelectionModel<Int>()
    selectionModel.addToSelection(1)

    assertThat(selectionModel.isSelected(1)).isTrue()
    assertThat(selectionModel.isSelected(2)).isFalse()
  }
}