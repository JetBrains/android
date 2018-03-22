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

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_TEXT_ALIGNMENT
import com.android.tools.adtui.model.stdui.ValueChangedListener
import com.android.tools.idea.common.property2.api.FormModel
import com.android.tools.idea.common.property2.impl.model.util.PropertyModelTestUtil
import com.google.common.truth.Truth.assertThat
import icons.StudioIcons.LayoutEditor.Properties.TEXT_ALIGN_CENTER
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.verify

class ToggleButtonPropertyEditorModelTest {

  private fun createModel(propertyValue: String?, trueValue: String, falseValue: String): ToggleButtonPropertyEditorModel {
    val formModel = Mockito.mock(FormModel::class.java)
    val property = PropertyModelTestUtil.makeProperty(ANDROID_URI, ATTR_TEXT_ALIGNMENT, propertyValue)
    return ToggleButtonPropertyEditorModel("description", TEXT_ALIGN_CENTER, trueValue, falseValue, property, formModel)
  }

  @Test
  fun testGetSelected() {
    assertThat(createModel("left", "left_resolved", "").selected).isTrue()
    assertThat(createModel("right", "left_resolved", "").selected).isFalse()
    assertThat(createModel(null, "left_resolved", "").selected).isFalse()
    assertThat(createModel("left", "left_resolved", "right_resolved").selected).isTrue()
    assertThat(createModel("right", "left_resolved", "right_resolved").selected).isFalse()
    assertThat(createModel(null, "left_resolved", "right_resolved").selected).isFalse()
  }

  @Test
  fun testSetSelected() {
    checkSetSelected("right", "left", "", true, "left")
    checkSetSelected("right", "left", "", false, null)
    checkSetSelected("right", "left", "right", true, "left")
    checkSetSelected("right", "left", "right", false, "right")
  }

  private fun checkSetSelected(propertyValue: String?, trueValue: String, falseValue: String, setValue: Boolean, expected: String?) {
    val model = createModel(propertyValue, trueValue, falseValue)
    val listener = Mockito.mock(ValueChangedListener::class.java)
    model.addListener(listener)

    model.selected = setValue
    assertThat(model.property.value).isEqualTo(expected)
    verify(listener).valueChanged()
  }
}
