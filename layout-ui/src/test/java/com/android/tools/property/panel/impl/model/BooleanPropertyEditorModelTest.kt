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

import com.android.SdkConstants
import com.android.tools.property.panel.api.EditorContext
import com.android.tools.property.panel.impl.model.util.FakePropertyItem
import com.android.tools.property.panel.impl.ui.PropertyCheckBox
import com.google.common.truth.Truth
import com.intellij.testFramework.ApplicationRule
import org.junit.ClassRule
import org.junit.Test

class BooleanPropertyEditorModelTest {

  companion object {
    @JvmField @ClassRule val rule = ApplicationRule()
  }

  private fun createModel(): BooleanPropertyEditorModel {
    val property =
      FakePropertyItem(
        SdkConstants.ANDROID_URI,
        SdkConstants.ATTR_INDETERMINATE,
        SdkConstants.VALUE_TRUE,
      )
    property.resolvedValue = SdkConstants.VALUE_TRUE
    return BooleanPropertyEditorModel(property)
  }

  @Test
  fun testStateChangedShouldUpdatePropertyValue() {
    val model = createModel()
    val editor = PropertyCheckBox(model, EditorContext.STAND_ALONE_EDITOR)
    editor.state = false
    Truth.assertThat(model.property.value).isEqualTo(SdkConstants.VALUE_FALSE)
    editor.state = true
    Truth.assertThat(model.property.value).isEqualTo(SdkConstants.VALUE_TRUE)
  }

  @Test
  fun testValueChangedNotificationShouldNotUpdatePropertyValue() {
    val model = createModel()
    PropertyCheckBox(model, EditorContext.STAND_ALONE_EDITOR)
    model.refresh()
    Truth.assertThat(model.property.value).isEqualTo(SdkConstants.VALUE_TRUE)
  }
}
