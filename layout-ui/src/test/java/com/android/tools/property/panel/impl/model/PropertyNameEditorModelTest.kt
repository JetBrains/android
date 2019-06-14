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
import com.android.SdkConstants.ATTR_TEXT
import com.android.SdkConstants.ATTR_TEXT_COLOR
import com.android.tools.adtui.model.stdui.ValueChangedListener
import com.android.tools.property.panel.impl.model.util.FakeInspectorLineModel
import com.android.tools.property.panel.impl.model.util.FakeLineType
import com.android.tools.property.panel.impl.model.util.FakeNewPropertyItem
import com.android.tools.property.panel.impl.model.util.FakePropertyItem
import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.times

class PropertyNameEditorModelTest {

  private fun createModel(): Pair<PropertyNameEditorModel, ValueChangedListener> {
    val property = FakeNewPropertyItem(mapOf(ATTR_TEXT_COLOR to FakePropertyItem(ANDROID_URI, ATTR_TEXT_COLOR)))
    val model = PropertyNameEditorModel(property)
    val listener = Mockito.mock(ValueChangedListener::class.java)
    model.addListener(listener)
    return Pair(model, listener)
  }

  @Test
  fun testEnter() {
    val (model, listener) = createModel()
    val line = FakeInspectorLineModel(FakeLineType.PROPERTY)
    model.lineModel = line
    model.text = ATTR_TEXT
    assertThat(model.commit()).isFalse()
    Truth.assertThat(model.property.name).isEqualTo(ATTR_TEXT)
    Mockito.verify(listener).valueChanged()

    model.text = ATTR_TEXT_COLOR
    assertThat(model.commit()).isTrue()
    Truth.assertThat(model.property.name).isEqualTo(ATTR_TEXT_COLOR)
    Mockito.verify(listener, times(2)).valueChanged()
  }

  @Test
  fun testEscape() {
    val (model, listener) = createModel()
    model.text = "world"
    model.escape()
    Truth.assertThat(model.property.name).isEqualTo("")
    Mockito.verify(listener).valueChanged()
  }
}