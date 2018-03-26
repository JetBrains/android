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
import org.mockito.Mockito.*

class ComboBoxPropertyEditorModelTest {

  private fun createModel(): ComboBoxPropertyEditorModel {
    val property = PropertyModelTestUtil.makeProperty(SdkConstants.ANDROID_URI, "visibility", "visible")
    val enumSupport = PropertyModelTestUtil.makeEnumSupport("visible", "invisible", "gone")
    return ComboBoxPropertyEditorModel(property, enumSupport, true)
  }

  private fun createModelWithListener(): Pair<ComboBoxPropertyEditorModel, ValueChangedListener> {
    val model = createModel()
    val listener = mock(ValueChangedListener::class.java)
    model.addListener(listener)
    return model to listener
  }

  @Test
  fun testSelectedItemFromInit() {
    val model = createModel()
    model.popupMenuWillBecomeVisible()
    assertThat(model.selectedItem.toString()).isEqualTo("visible")
  }

  @Test
  fun testEnter() {
    val (model, listener) = createModelWithListener()
    val line = FakeInspectorLine(LineType.PROPERTY)
    model.lineModel = line
    model.enterKeyPressed("gone")
    assertThat(model.property.value).isEqualTo("gone")
    assertThat(model.isPopupVisible).isFalse()
    verify(listener).valueChanged()
    assertThat(line.gotoNextLineWasRequested).isTrue()
  }

  @Test
  fun testEscape() {
    // setup
    val model = createModel()
    model.isPopupVisible = true
    model.selectedItem = "gone"
    val listener = mock(ValueChangedListener::class.java)
    model.addListener(listener)

    // test
    model.escapeKeyPressed()
    assertThat(model.property.value).isEqualTo("visible")
    assertThat(model.isPopupVisible).isFalse()
    verify(listener).valueChanged()
  }

  @Test
  fun testEnterInPopup() {
    // setup
    val model = createModel()
    model.isPopupVisible = true
    model.selectedItem = "gone"
    val listener = mock(ValueChangedListener::class.java)
    model.addListener(listener)

    // test
    model.popupMenuWillBecomeInvisible(false)
    assertThat(model.property.value).isEqualTo("gone")
    assertThat(model.isPopupVisible).isFalse()
    verify(listener).valueChanged()
  }

  @Test
  fun testEscapeInPopup() {
    // setup
    val model = createModel()
    model.isPopupVisible = true
    model.selectedItem = "gone"
    val listener = mock(ValueChangedListener::class.java)
    model.addListener(listener)

    // test
    model.popupMenuWillBecomeInvisible(true)
    assertThat(model.property.value).isEqualTo("visible")
    assertThat(model.isPopupVisible).isFalse()
    verifyZeroInteractions(listener)
  }

  @Test
  fun testListModel() {
    val model = createModel()
    assertThat(model.size).isEqualTo(3)
    assertThat(model.getElementAt(0).toString()).isEqualTo("visible")
    assertThat(model.getElementAt(1).toString()).isEqualTo("invisible")
    assertThat(model.getElementAt(2).toString()).isEqualTo("gone")
  }
}
