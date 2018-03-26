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
import com.android.tools.idea.common.property2.impl.model.util.PropertyModelTestUtil.makeProperty
import com.android.tools.idea.uibuilder.property2.testutils.FakeInspectorLine
import com.android.tools.idea.uibuilder.property2.testutils.LineType
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.mockito.Mockito.*

class BasePropertyEditorModelTest {

  private fun createModel(): BasePropertyEditorModel {
    val property = makeProperty(ANDROID_URI, "color", "#00FF00")
    return object : BasePropertyEditorModel(property) {}
  }

  private fun createModelWithListener(): Pair<BasePropertyEditorModel, ValueChangedListener> {
    val model = createModel()
    val listener = mock(ValueChangedListener::class.java)
    model.addListener(listener)
    return model to listener
  }

  @Test
  fun testValueFromProperty() {
    val model = createModel()
    assertThat(model.value).isEqualTo("#00FF00")
  }

  @Test
  fun testSetValueIsPropagatedToPropertyAndValueListener() {
    val (model, listener) = createModelWithListener()
    model.value = "#FFFF00"
    verify(listener).valueChanged()
    assertThat(model.property.value).isEqualTo("#FFFF00")
  }

  @Test
  fun testSetVisibleIsPropagatedToValueListener() {
    val (model, listener) = createModelWithListener()
    model.visible = false
    verify(listener).valueChanged()
  }

  @Test
  fun testFocusRequestIsPropagatedToValueListener() {
    val (model, listener) = createModelWithListener()
    model.requestFocus()
    verify(listener).valueChanged()
  }

  @Test
  fun testFocusGainIsRecodedButNotPropagatedToListener() {
    val (model, listener) = createModelWithListener()
    model.focusGained()
    assertThat(model.hasFocus).isTrue()
    verify(listener, never()).valueChanged()
  }

  @Test
  fun testFocusLossWillUpdateValue() {
    // setup
    val (model, listener) = createModelWithListener()
    model.focusGained()

    // test
    model.focusLost("#333333")
    assertThat(model.hasFocus).isFalse()
    assertThat(model.property.value).isEqualTo("#333333")
    verify(listener).valueChanged()
  }

  @Test
  fun testEnterMovesToNextLineEditor() {
    val model = createModel()
    val line = FakeInspectorLine(LineType.PROPERTY)
    model.lineModel = line

    model.enterKeyPressed()
    assertThat(line.gotoNextLineWasRequested).isTrue()
  }

  @Test
  fun testFocusLossWithUnchangedValueWillNotUpdateValue() {
    val model = createModel()
    model.focusLost("#00FF00")
    assertThat(model.property.value).isEqualTo("#00FF00")
  }
}
