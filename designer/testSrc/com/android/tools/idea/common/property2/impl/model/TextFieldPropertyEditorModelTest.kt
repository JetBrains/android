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

import com.android.SdkConstants
import com.android.tools.adtui.model.stdui.ValueChangedListener
import com.android.tools.idea.common.property2.impl.model.util.PropertyModelTestUtil
import com.android.tools.idea.uibuilder.property2.testutils.LineType
import com.android.tools.idea.uibuilder.property2.testutils.FakeInspectorLine
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

class TextFieldPropertyEditorModelTest {

  private fun createModel(): Pair<TextFieldPropertyEditorModel, ValueChangedListener> {
    val property = PropertyModelTestUtil.makeProperty(SdkConstants.ANDROID_URI, "text", "hello")
    val model = TextFieldPropertyEditorModel(property, true)
    val listener = mock(ValueChangedListener::class.java)
    model.addListener(listener)
    return Pair(model, listener)
  }

  @Test
  fun testEnter() {
    val (model, listener) = createModel()
    val line = FakeInspectorLine(LineType.PROPERTY)
    model.lineModel = line
    model.enter("world")
    assertThat(model.property.value).isEqualTo("world")
    verify(listener).valueChanged()
    assertThat(line.gotoNextLineWasRequested).isTrue()
  }

  @Test
  fun testEscape() {
    val (model, listener) = createModel()
    model.escape()
    assertThat(model.property.value).isEqualTo("hello")
    verify(listener).valueChanged()
  }
}
