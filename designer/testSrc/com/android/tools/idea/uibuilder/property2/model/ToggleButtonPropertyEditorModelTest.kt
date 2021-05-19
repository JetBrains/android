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
import com.android.SdkConstants.TEXT_VIEW
import com.android.tools.adtui.model.stdui.ValueChangedListener
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.uibuilder.property2.NelePropertyType
import com.android.tools.idea.uibuilder.property2.testutils.PropertyTestCase
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.PlatformTestUtil
import icons.StudioIcons.LayoutEditor.Properties.TEXT_ALIGN_CENTER
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.verify

class ToggleButtonPropertyEditorModelTest : PropertyTestCase() {

  private fun createModel(propertyValue: String?, trueValue: String, falseValue: String): ToggleButtonPropertyEditorModel {
    val property = createPropertyItem(ANDROID_URI, ATTR_TEXT_ALIGNMENT, NelePropertyType.STRING, createTextView(propertyValue))
    return ToggleButtonPropertyEditorModel("description", TEXT_ALIGN_CENTER, trueValue, falseValue, property)
  }

  @Test
  fun testGetSelected() {
    assertThat(createModel("left", "left", "").selected).isTrue()
    assertThat(createModel("right", "left", "").selected).isFalse()
    assertThat(createModel(null, "left", "").selected).isFalse()
    assertThat(createModel("left", "left", "right").selected).isTrue()
    assertThat(createModel("right", "left", "right").selected).isFalse()
    assertThat(createModel(null, "left", "right").selected).isFalse()
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
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();

    assertThat(model.property.value).isEqualTo(expected)
  }

  private fun createTextView(propertyValue: String?): List<NlComponent> {
    return if (propertyValue == null) {
      createComponents(component(TEXT_VIEW))
    } else {
      createComponents(component(TEXT_VIEW).withAttribute(ANDROID_URI, ATTR_TEXT_ALIGNMENT, propertyValue))
    }
  }
}
