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
import org.junit.Before
import org.junit.Test

class MultiSelectionModelTest {
  private val observer = AspectObserver()
  private val eventCounter = SelectionChangeCounter()

  @Before
  fun setUp() {
    observer.dependencies.clear()
    eventCounter.reset()
  }

  @Test
  fun getSelection() {
    val selectionModel = MultiSelectionModel<Int>()
    selectionModel.setSelection(setOf(1, 2, 2))
    assertThat(selectionModel.selection).containsExactly(1, 2)
  }

  @Test
  fun setSelection() {
    val selectionModel = MultiSelectionModel<String>()
    selectionModel.addDependency(observer).onChange(MultiSelectionModel.Aspect.CHANGE_SELECTION, eventCounter)

    selectionModel.setSelection(setOf("foo", "bar"))
    assertThat(selectionModel.selection).containsExactly("foo", "bar")
    assertThat(eventCounter.count).isEqualTo(1)

    eventCounter.reset()
    selectionModel.setSelection(setOf("foo"))
    assertThat(selectionModel.selection).containsExactly("foo")
    // Selecting different items should fire a CHANGE_SELECTION event.
    assertThat(eventCounter.count).isEqualTo(1)

    eventCounter.reset()
    selectionModel.setSelection(setOf("foo"))
    assertThat(selectionModel.selection).containsExactly("foo")
    // Selecting the same items should not fire a CHANGE_SELECTION event.
    assertThat(eventCounter.count).isEqualTo(0)
  }

  @Test
  fun clearSelection() {
    val selectionModel = MultiSelectionModel<Int>()
    selectionModel.addDependency(observer).onChange(MultiSelectionModel.Aspect.CHANGE_SELECTION, eventCounter)
    selectionModel.setSelection(setOf(1, 2))

    eventCounter.reset()
    selectionModel.clearSelection()
    assertThat(selectionModel.selection).isEmpty()
    assertThat(eventCounter.count).isEqualTo(1)

    eventCounter.reset()
    selectionModel.clearSelection()
    assertThat(selectionModel.selection).isEmpty()
    // Clearing an already empty model should not fire CHANGE_SELECTION event.
    assertThat(eventCounter.count).isEqualTo(0)
  }

  @Test
  fun isSelected() {
    val selectionModel = MultiSelectionModel<Int>()
    selectionModel.setSelection(setOf(1))

    assertThat(selectionModel.isSelected(1)).isTrue()
    assertThat(selectionModel.isSelected(2)).isFalse()
  }

  @Test
  fun isEmpty() {
    val selectionModel = MultiSelectionModel<Int>()
    assertThat(selectionModel.isEmpty).isTrue()
    selectionModel.setSelection(setOf(1))
    assertThat(selectionModel.isEmpty).isFalse()
  }

  private class SelectionChangeCounter : Runnable {
    var count = 0

    override fun run() {
      count++
    }

    fun reset() {
      count = 0
    }
  }
}