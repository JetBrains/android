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
package com.android.tools.idea.common.property2.impl.model

import com.android.SdkConstants.ANDROID_URI
import com.android.tools.adtui.model.stdui.ValueChangedListener
import com.android.tools.idea.common.property2.api.FormModel
import com.android.tools.idea.common.property2.api.InspectorLineModel
import com.android.tools.idea.common.property2.impl.model.util.PropertyModelUtil.makeProperty
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.mockito.Mockito.*

class BasePropertyEditorModelTest {

  private fun createModel(showResolvedValues: Boolean): BasePropertyEditorModel {
    val formModel = mock(FormModel::class.java)
    val property = makeProperty(ANDROID_URI, "color", "#00FF00")
    `when`(formModel.showResolvedValues).thenReturn(showResolvedValues)
    formModel.showResolvedValues = showResolvedValues
    return object : BasePropertyEditorModel(property, formModel) {}
  }

  private fun createModelWithListener(showResolvedValues: Boolean): Pair<BasePropertyEditorModel, ValueChangedListener> {
    val model = createModel(showResolvedValues)
    val listener = mock(ValueChangedListener::class.java)
    model.addListener(listener)
    return model to listener
  }

  @Test
  fun testValueFromProperty() {
    val model = createModel(false)
    assertThat(model.value).isEqualTo("#00FF00")
  }

  @Test
  fun testResolvedValueFromProperty() {
    val model = createModel(true)
    assertThat(model.value).isEqualTo("#00FF00_resolved")
  }

  @Test
  fun testSetValueIsPropagatedToPropertyAndValueListener() {
    val (model, listener) = createModelWithListener(false)
    model.value = "#FFFF00"
    verify(listener).valueChanged()
    assertThat(model.property.value).isEqualTo("#FFFF00")
  }

  @Test
  fun testSetVisibleIsPropagatedToValueListener() {
    val (model, listener) = createModelWithListener(false)
    model.visible = false
    verify(listener).valueChanged()
  }

  @Test
  fun testFocusRequestIsPropagatedToValueListener() {
    val (model, listener) = createModelWithListener(false)
    model.focusRequest = true
    verify(listener).valueChanged()
  }

  @Test
  fun testFocusGainIsRecodedButNotPropagatedToListener() {
    val (model, listener) = createModelWithListener(false)
    model.focusGained()
    assertThat(model.focus).isTrue()
    verify(listener, never()).valueChanged()
  }

  @Test
  fun testFocusLossWillUpdateValue() {
    // setup
    val (model, listener) = createModelWithListener(false)
    model.focusGained()

    // test
    model.focusLost("#333333")
    assertThat(model.focus).isFalse()
    assertThat(model.property.value).isEqualTo("#333333")
    verify(listener).valueChanged()
  }

  @Test
  fun testEnterMovesToNextLineEditor() {
    val model = createModel(false)
    val line = mock(InspectorLineModel::class.java)
    model.line = line

    model.enterKeyPressed()
    verify(model.formModel).moveToNextLineEditor(line)
  }

  @Test
  fun testFocusLossWithUnchangedValueWillNotUpdateValue() {
    val model = createModel(false)
    model.focusLost("#00FF00")
    assertThat(model.property.value).isEqualTo("#00FF00")
  }

  @Test
  fun testFocusLossWithUnchangedResolvedValueWillNotUpdateValue() {
    val model = createModel(true)
    model.focusLost("#00FF00_resolved")
    assertThat(model.property.value).isEqualTo("#00FF00")
  }
}
