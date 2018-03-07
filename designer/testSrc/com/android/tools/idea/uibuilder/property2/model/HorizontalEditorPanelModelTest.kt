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
package com.android.tools.idea.uibuilder.property2.model

import com.android.SdkConstants.*
import com.android.tools.idea.common.property2.api.*
import com.android.tools.idea.common.property2.impl.model.BasePropertyEditorModel
import com.android.tools.idea.common.property2.impl.model.util.PropertyModelUtil.makeProperty
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.mockito.Mockito

class HorizontalEditorPanelModelTest {

  private fun createTestData(): TestData {
    val formModel = Mockito.mock(FormModel::class.java)
    val property = makeProperty(ANDROID_URI, ATTR_TEXT_ALIGNMENT, TextAlignment.CENTER)
    val toggle1 = MockEditorModel(property, formModel)
    val toggle2 = MockEditorModel(property, formModel)
    val toggle3 = MockEditorModel(property, formModel)
    val model = HorizontalEditorPanelModel(property, formModel)
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
    assertThat(test.toggle1.focusRequest).isFalse()
    assertThat(test.toggle2.focusRequest).isFalse()
    assertThat(test.toggle3.focusRequest).isTrue()
  }

  @Test
  fun testPriorFromToggle2() {
    val test = createTestData()
    test.toggle2.focusGained()
    test.model.prior()
    assertThat(test.toggle1.focusRequest).isTrue()
    assertThat(test.toggle2.focusRequest).isFalse()
    assertThat(test.toggle3.focusRequest).isFalse()
  }

  @Test
  fun testPriorFromToggle3() {
    val test = createTestData()
    test.toggle3.focusGained()
    test.model.prior()
    assertThat(test.toggle1.focusRequest).isFalse()
    assertThat(test.toggle2.focusRequest).isTrue()
    assertThat(test.toggle3.focusRequest).isFalse()
  }

  @Test
  fun testPriorWithoutFocus() {
    val test = createTestData()
    test.model.prior()
    assertThat(test.toggle1.focusRequest).isFalse()
    assertThat(test.toggle2.focusRequest).isFalse()
    assertThat(test.toggle3.focusRequest).isTrue()
  }

  @Test
  fun testNextFromToggle1() {
    val test = createTestData()
    test.toggle1.focusGained()
    test.model.next()
    assertThat(test.toggle1.focusRequest).isFalse()
    assertThat(test.toggle2.focusRequest).isTrue()
    assertThat(test.toggle3.focusRequest).isFalse()
  }

  @Test
  fun testNextFromToggle2() {
    val test = createTestData()
    test.toggle2.focusGained()
    test.model.next()
    assertThat(test.toggle1.focusRequest).isFalse()
    assertThat(test.toggle2.focusRequest).isFalse()
    assertThat(test.toggle3.focusRequest).isTrue()
  }

  @Test
  fun testNextFromToggle3() {
    val test = createTestData()
    test.toggle3.focusGained()
    test.model.next()
    assertThat(test.toggle1.focusRequest).isTrue()
    assertThat(test.toggle2.focusRequest).isFalse()
    assertThat(test.toggle3.focusRequest).isFalse()
  }

  @Test
  fun testNextWithoutFocus() {
    val test = createTestData()
    test.model.next()
    assertThat(test.toggle1.focusRequest).isTrue()
    assertThat(test.toggle2.focusRequest).isFalse()
    assertThat(test.toggle3.focusRequest).isFalse()
  }

  @Test
  fun testFocusRequestIsPropagatedToToggle1() {
    val test = createTestData()
    test.model.focusRequest = true
    assertThat(test.toggle1.focusRequest).isTrue()
    assertThat(test.toggle2.focusRequest).isFalse()
    assertThat(test.toggle3.focusRequest).isFalse()
  }

  private class MockEditorModel(property: PropertyItem, formModel: FormModel): BasePropertyEditorModel(property, formModel) {
    override var focusRequest: Boolean = false
  }

  private class TestData(
    val model: HorizontalEditorPanelModel,
    val toggle1: MockEditorModel,
    val toggle2: MockEditorModel,
    val toggle3: MockEditorModel
  )
}
