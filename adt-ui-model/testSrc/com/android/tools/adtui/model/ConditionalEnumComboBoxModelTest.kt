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
package com.android.tools.adtui.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener

class ConditionalEnumComboBoxModelTest {
  class Listener : ListDataListener {
    var triggered = false
      private set

    override fun contentsChanged(e: ListDataEvent) {
      triggered = true
    }

    override fun intervalRemoved(e: ListDataEvent) {}
    override fun intervalAdded(e: ListDataEvent) {}
  }

  enum class TestData {
    FIRST,
    SECOND,
    THIRD
  }

  @Test
  fun changeEventIsTriggeredOnUpdate() {
    val model = ConditionalEnumComboBoxModel<TestData>(TestData::class.java) { value -> value != TestData.SECOND }
    val listener = Listener()
    model.addListDataListener(listener)
    assertThat(model.size).isEqualTo(2)
    assertThat(model.getElementAt(0)).isEqualTo(TestData.FIRST)
    assertThat(model.getElementAt(1)).isEqualTo(TestData.THIRD)
    assertThat(listener.triggered).isFalse()
    model.update()
    assertThat(listener.triggered).isTrue()
  }

  @Test
  fun predicateIsTriggeredOnUpdate() {
    var returnElement = TestData.FIRST
    val model = ConditionalEnumComboBoxModel<TestData>(TestData::class.java) { value -> value == returnElement }
    assertThat(model.size).isEqualTo(1)
    assertThat(model.getElementAt(0)).isEqualTo(returnElement)
    returnElement = TestData.SECOND
    model.update()
    assertThat(model.size).isEqualTo(1)
    assertThat(model.getElementAt(0)).isEqualTo(returnElement)

  }

  @Test
  fun emptyListSetsNullAsSelected() {
    val model = ConditionalEnumComboBoxModel<TestData>(TestData::class.java) { false }
    assertThat(model.size).isEqualTo(0)
    assertThat(model.selectedItem).isEqualTo(null)
  }
}