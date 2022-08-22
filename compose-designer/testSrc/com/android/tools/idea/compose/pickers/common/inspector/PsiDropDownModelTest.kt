/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.compose.pickers.common.inspector

import com.android.tools.property.panel.api.EnumValue
import com.android.tools.property.panel.impl.model.util.FakeEnumSupport
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

internal class PsiDropDownModelTest {
  @Test
  fun valueChange() {
    val property = FakePsiProperty("prop", "value3", "default")
    val enumSupport = FakeEnumSupport("value1", "value2", "value3")

    val model = PsiDropDownModel(property, enumSupport)
    model.popupMenuWillBecomeVisible {}.get(1L, TimeUnit.SECONDS) // load values from enumSupport

    assertEquals(2, model.getIndexOfCurrentValue())

    property.value = null
    assertEquals(-1, model.getIndexOfCurrentValue())

    model.updateValueFromProperty() // updates the selected item in the model
    assertEquals("default", (model.selectedItem as? EnumValue)?.display)
    assertNull((model.selectedItem as? EnumValue)?.value)

    // Set an Item to the model as if it was selected
    val valueToSelect = enumSupport.values[1]
    model.selectedItem = valueToSelect
    assertEquals("value2", (model.selectedItem as? EnumValue)?.display)
    assertEquals("value2", (model.selectedItem as? EnumValue)?.value)

    // Apply selection, should reflect on property
    model.selectEnumValue()
    assertEquals("value2", property.value)
  }
}
