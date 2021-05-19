/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.adtui

import com.android.tools.adtui.model.BoxSelectionListener
import com.android.tools.adtui.model.BoxSelectionModel
import com.android.tools.adtui.model.Range
import com.android.tools.adtui.swing.FakeUi
import com.google.common.truth.Truth.assertThat
import com.intellij.ui.components.JBList
import org.junit.Test

class BoxSelectionComponentTest {
  /**
   * Helper class for setting up the box selection UI.
   */
  private class BoxSelectionUi {
    val jList = JBList("foo", "bar")
    val boxSelectionModel = BoxSelectionModel(Range(), Range(0.0, 100.0))
    val boxSelection = BoxSelectionComponent(boxSelectionModel, jList)

    init {
      // Make sure box selection component sits on top of the JList.
      jList.setBounds(0, 0, 100, 100)
      boxSelection.setBounds(0, 0, 100, 100)
    }
  }

  private class TestBoxSelectionListener : BoxSelectionListener {
    var durationUsVar = 0L
    var trackCountVar = 0

    override fun boxSelectionCreated(durationUs: Long, trackCount: Int) {
      durationUsVar = durationUs
      trackCountVar = trackCount
    }
  }

  @Test
  fun selectSingleListItem() {
    val ui = BoxSelectionUi()
    val boxSelectionListener = TestBoxSelectionListener()
    ui.boxSelectionModel.addBoxSelectionListener(boxSelectionListener)
    val fakeUi = FakeUi(ui.boxSelection)
    fakeUi.mouse.drag(0, 0, 10, 10)
    assertThat(ui.boxSelection.model.selectionRange.isSameAs(Range(0.0, 10.0))).isTrue()
    assertThat(ui.jList.selectedValuesList).containsExactly("foo")
    assertThat(boxSelectionListener.durationUsVar).isEqualTo(10L)
    assertThat(boxSelectionListener.trackCountVar).isEqualTo(1)
  }

  @Test
  fun selectMultipleListItems() {
    val ui = BoxSelectionUi()
    val boxSelectionListener = TestBoxSelectionListener()
    ui.boxSelectionModel.addBoxSelectionListener(boxSelectionListener)
    val fakeUi = FakeUi(ui.boxSelection)
    fakeUi.mouse.drag(0, 0, 50, 90)
    assertThat(ui.boxSelection.model.selectionRange.isSameAs(Range(0.0, 50.0))).isTrue()
    assertThat(ui.jList.selectedValuesList).containsExactly("foo", "bar")
    assertThat(boxSelectionListener.durationUsVar).isEqualTo(50L)
    assertThat(boxSelectionListener.trackCountVar).isEqualTo(2)
  }

  @Test
  fun selectLastListItem() {
    val ui = BoxSelectionUi()
    val fakeUi = FakeUi(ui.boxSelection)
    fakeUi.mouse.drag(0, 60, 20, 30)
    assertThat(ui.boxSelection.model.selectionRange.isSameAs(Range(0.0, 20.0))).isTrue()
    assertThat(ui.jList.selectedValuesList).containsExactly("bar")
  }

  @Test
  fun clearSelection() {
    val ui = BoxSelectionUi()
    val fakeUi = FakeUi(ui.boxSelection)
    fakeUi.mouse.drag(0, 0, 10, 10)
    assertThat(ui.boxSelection.model.selectionRange.isEmpty).isFalse()
    assertThat(ui.jList.isSelectionEmpty).isFalse()
    fakeUi.mouse.click(0, 0)
    assertThat(ui.boxSelection.model.selectionRange.isEmpty).isTrue()
    assertThat(ui.jList.isSelectionEmpty).isTrue()
  }

  @Test
  fun noSelectionWhenDisabled() {
    val ui = BoxSelectionUi()
    val fakeUi = FakeUi(ui.boxSelection)
    ui.boxSelection.setEventHandlersEnabled(false)
    fakeUi.mouse.drag(0, 0, 10, 10)
    assertThat(ui.boxSelection.model.selectionRange.isEmpty).isTrue()
    assertThat(ui.jList.selectedValuesList).isEmpty()
  }
}