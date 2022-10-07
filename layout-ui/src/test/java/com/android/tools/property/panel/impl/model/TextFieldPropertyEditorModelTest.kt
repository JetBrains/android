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
package com.android.tools.property.panel.impl.model

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_ID
import com.android.SdkConstants.ATTR_TEXT
import com.android.tools.adtui.model.stdui.EditingErrorCategory
import com.android.tools.adtui.model.stdui.EditingSupport
import com.android.tools.adtui.model.stdui.EditingValidation
import com.android.tools.adtui.model.stdui.ValueChangedListener
import com.android.tools.property.panel.api.PropertyItem
import com.android.tools.property.panel.impl.model.util.FakeInspectorLineModel
import com.android.tools.property.panel.impl.model.util.FakeLineType
import com.android.tools.property.panel.impl.model.util.FakeAsyncPropertyItem
import com.android.tools.property.panel.impl.model.util.FakePropertyItem
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions

class TextFieldPropertyEditorModelTest {

  private fun createModel(): Pair<TextFieldPropertyEditorModel, ValueChangedListener> {
    val property = FakePropertyItem(ANDROID_URI, "text", "hello")
    property.defaultValue = "from"
    return createModel(property)
  }

  private fun createModel(property: PropertyItem): Pair<TextFieldPropertyEditorModel, ValueChangedListener> {
    val model = TextFieldPropertyEditorModel(property, true)
    val listener = mock(ValueChangedListener::class.java)
    model.addListener(listener)
    return Pair(model, listener)
  }

  @Test
  fun testValue() {
    val (model, _) = createModel()
    assertThat(model.value).isEqualTo("hello")
    assertThat(model.placeHolderValue).isEqualTo("from")
  }

  @Test
  fun testEnter() {
    val (model, _) = createModel()
    val line = FakeInspectorLineModel(FakeLineType.PROPERTY)
    model.lineModel = line
    model.text = "world"
    model.commit()
    assertThat(model.property.value).isEqualTo("world")
  }

  @Test
  fun testEnterWithInvalidInput() {
    val (model, _) = createModel(FakePropertyItem(ANDROID_URI, "text", "hello", editingSupport = object: EditingSupport {
      override val validation: EditingValidation = { Pair(EditingErrorCategory.ERROR, "Error") }
    }))
    val line = FakeInspectorLineModel(FakeLineType.PROPERTY)
    model.lineModel = line
    model.text = "world"
    model.commit()
    assertThat(model.property.value).isEqualTo("hello")
  }

  @Test
  fun testEscape() {
    val (model, listener) = createModel()
    model.escape()
    assertThat(model.property.value).isEqualTo("hello")
    verify(listener).valueChanged()
  }

  @Test
  fun testFocusGainWillReadValueFromModel() {
    // setup
    val (model, listener) = createModel()
    model.text = "#333333"
    model.focusGained()

    assertThat(model.text).isEqualTo("hello")
    verifyNoMoreInteractions(listener)
  }

  @Test
  fun testFocusLossWillUpdateValue() {
    // setup
    val (model, _) = createModel()
    model.focusGained()
    model.text = "#333333"

    // test
    model.focusLost()
    assertThat(model.hasFocus).isFalse()
    assertThat(model.property.value).isEqualTo("#333333")
  }

  @Test
  fun testFocusLossWithUnchangedValueWillNotUpdateValue() {
    // setup
    val (model, listener) = createModel()
    model.focusGained()

    // test
    model.focusLost()
    assertThat(model.property.value).isEqualTo("hello")
    verify(listener, never()).valueChanged()
  }

  @Test
  fun testEnterKeyWithAsyncPropertySetterDoesNotNavigateToNextEditor() {
    // setup
    val property = FakeAsyncPropertyItem(ANDROID_URI, ATTR_ID, "textView")
    val (model, _) = createModel(property)
    val line = FakeInspectorLineModel(FakeLineType.PROPERTY)
    model.lineModel = line
    model.focusGained()
    model.text = "imageView"

    // test
    model.commit()
    assertThat(property.lastUpdatedValue).isEqualTo("imageView")
    assertThat(property.updateCount).isEqualTo(1)
  }

  @Test
  fun testFocusLossAfterEnterKeyWithAsyncPropertySetter() {
    // setup
    val property = FakePropertyItem(ANDROID_URI, ATTR_ID, "textView")
    val (model, _) = createModel(property)
    model.focusGained()
    model.text = "imageView"
    model.commit()

    // test
    model.focusLost()
    assertThat(property.value).isEqualTo("imageView")
    assertThat(property.updateCount).isEqualTo(1)
  }

  @Test
  fun testUpdateAfterPropertyChange() {
    // setup
    val property = FakePropertyItem(ANDROID_URI, ATTR_TEXT, "Hello")
    val (model, listener) = createModel(property)

    // test
    property.value = "World"
    model.refresh()
    assertThat(model.text).isEqualTo("World")
    verify(listener).valueChanged()
  }
}
