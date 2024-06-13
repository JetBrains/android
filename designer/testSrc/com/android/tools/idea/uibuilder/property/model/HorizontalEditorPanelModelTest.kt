/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.property.model

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_TEXT_ALIGNMENT
import com.android.SdkConstants.TextAlignment
import com.android.tools.idea.uibuilder.property.ui.HorizontalEditorPanel
import com.android.tools.property.panel.api.PropertyItem
import com.android.tools.property.panel.impl.model.BasePropertyEditorModel
import com.android.tools.property.panel.impl.model.util.FakePropertyItem
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HorizontalEditorPanelModelTest {

  private fun createTestData(): TestData {
    val property = FakePropertyItem(ANDROID_URI, ATTR_TEXT_ALIGNMENT, TextAlignment.CENTER)
    val toggle1 = FakeEditorModel(property)
    val toggle2 = FakeEditorModel(property)
    val toggle3 = FakeEditorModel(property)
    val model = HorizontalEditorPanelModel(property)
    model.add(toggle1)
    model.add(toggle2)
    model.add(toggle3)
    return TestData(model, toggle1, toggle2, toggle3)
  }

  @Test
  fun testPriorFromToggle1() {
    val test = createTestData()
    test.toggle1.focusGained()
    test.model.prior()
    assertThat(test.toggle1.focusWasRequested).isFalse()
    assertThat(test.toggle2.focusWasRequested).isFalse()
    assertThat(test.toggle3.focusWasRequested).isTrue()
  }

  @Test
  fun testPriorFromToggle2() {
    val test = createTestData()
    test.toggle2.focusGained()
    test.model.prior()
    assertThat(test.toggle1.focusWasRequested).isTrue()
    assertThat(test.toggle2.focusWasRequested).isFalse()
    assertThat(test.toggle3.focusWasRequested).isFalse()
  }

  @Test
  fun testPriorFromToggle3() {
    val test = createTestData()
    test.toggle3.focusGained()
    test.model.prior()
    assertThat(test.toggle1.focusWasRequested).isFalse()
    assertThat(test.toggle2.focusWasRequested).isTrue()
    assertThat(test.toggle3.focusWasRequested).isFalse()
  }

  @Test
  fun testPriorWithoutFocus() {
    val test = createTestData()
    test.model.prior()
    assertThat(test.toggle1.focusWasRequested).isFalse()
    assertThat(test.toggle2.focusWasRequested).isFalse()
    assertThat(test.toggle3.focusWasRequested).isTrue()
  }

  @Test
  fun testNextFromToggle1() {
    val test = createTestData()
    test.toggle1.focusGained()
    test.model.next()
    assertThat(test.toggle1.focusWasRequested).isFalse()
    assertThat(test.toggle2.focusWasRequested).isTrue()
    assertThat(test.toggle3.focusWasRequested).isFalse()
  }

  @Test
  fun testNextFromToggle2() {
    val test = createTestData()
    test.toggle2.focusGained()
    test.model.next()
    assertThat(test.toggle1.focusWasRequested).isFalse()
    assertThat(test.toggle2.focusWasRequested).isFalse()
    assertThat(test.toggle3.focusWasRequested).isTrue()
  }

  @Test
  fun testNextFromToggle3() {
    val test = createTestData()
    test.toggle3.focusGained()
    test.model.next()
    assertThat(test.toggle1.focusWasRequested).isTrue()
    assertThat(test.toggle2.focusWasRequested).isFalse()
    assertThat(test.toggle3.focusWasRequested).isFalse()
  }

  @Test
  fun testNextWithoutFocus() {
    val test = createTestData()
    test.model.next()
    assertThat(test.toggle1.focusWasRequested).isTrue()
    assertThat(test.toggle2.focusWasRequested).isFalse()
    assertThat(test.toggle3.focusWasRequested).isFalse()
  }

  @Test
  fun testFocusRequestIsPropagatedToToggle1() {
    val test = createTestData()
    test.model.requestFocus()
    assertThat(test.toggle1.focusWasRequested).isTrue()
    assertThat(test.toggle2.focusWasRequested).isFalse()
    assertThat(test.toggle3.focusWasRequested).isFalse()
  }

  @Test
  fun testModelIsHookedOnPanelCreation() {
    val test = createTestData()
    val panel = HorizontalEditorPanel(test.model)
    test.model.visible = false
    assertThat(panel.isVisible).isFalse()
  }

  private class FakeEditorModel(property: PropertyItem) : BasePropertyEditorModel(property) {

    var focusWasRequested = false
      private set

    override fun requestFocus() {
      focusWasRequested = true
    }
  }

  private class TestData(
    val model: HorizontalEditorPanelModel,
    val toggle1: FakeEditorModel,
    val toggle2: FakeEditorModel,
    val toggle3: FakeEditorModel,
  )
}
