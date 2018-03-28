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
import com.android.tools.idea.common.property2.impl.model.util.PropertyModelTestUtil
import com.android.tools.idea.common.property2.impl.ui.PropertyThreeStateCheckBox
import com.google.common.truth.Truth.assertThat
import com.intellij.util.ui.ThreeStateCheckBox.State.*
import org.junit.Test

class ThreeStateBooleanPropertyEditorModelTest {

  private fun createModel(): ThreeStateBooleanPropertyEditorModel {
    val property = PropertyModelTestUtil.makeProperty(SdkConstants.ANDROID_URI, SdkConstants.ATTR_INDETERMINATE, "@bool/boolValue")
    property.resolvedValue = "true"
    return ThreeStateBooleanPropertyEditorModel(property)
  }

  @Test
  fun testStateChangedShouldUpdatePropertyValue() {
    val model = createModel()
    val editor = PropertyThreeStateCheckBox(model)
    editor.state = SELECTED
    assertThat(model.property.value).isEqualTo("true")
    editor.state = NOT_SELECTED
    assertThat(model.property.value).isEqualTo("false")
    editor.state = DONT_CARE
    assertThat(model.property.value).isNull()
  }

  @Test
  fun testValueChangedNotificationShouldNotUpdatePropertyValue() {
    val model = createModel()
    PropertyThreeStateCheckBox(model)
    model.refresh()
    assertThat(model.property.value).isEqualTo("@bool/boolValue")
  }
}