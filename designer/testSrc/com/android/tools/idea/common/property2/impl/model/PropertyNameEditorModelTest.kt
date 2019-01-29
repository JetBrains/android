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

import com.android.tools.adtui.model.stdui.ValueChangedListener
import com.android.tools.idea.common.property2.impl.model.util.PropertyModelTestUtil
import com.android.tools.idea.uibuilder.property2.testutils.FakeInspectorLine
import com.android.tools.idea.uibuilder.property2.testutils.LineType
import com.google.common.truth.Truth
import org.junit.Test
import org.mockito.Mockito

class PropertyNameEditorModelTest {

  private fun createModel(): Pair<PropertyNameEditorModel, ValueChangedListener> {
    val property = PropertyModelTestUtil.makeProperty("", "", "")
    val model = PropertyNameEditorModel(property)
    val listener = Mockito.mock(ValueChangedListener::class.java)
    model.addListener(listener)
    return Pair(model, listener)
  }

  @Test
  fun testEnter() {
    val (model, listener) = createModel()
    val line = FakeInspectorLine(LineType.PROPERTY)
    model.lineModel = line
    model.text = "world"
    model.enterKeyPressed()
    Truth.assertThat(model.property.name).isEqualTo("world")
    Mockito.verify(listener).valueChanged()
    Truth.assertThat(line.gotoNextLineWasRequested).isTrue()
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