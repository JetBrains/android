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
package com.android.tools.idea.uibuilder.property2

import com.android.SdkConstants.*
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.uibuilder.property2.testutils.PropertyTestCase
import com.google.common.truth.Truth.assertThat
import org.jetbrains.android.resourceManagers.ModuleResourceManagers

class NeleFlagsPropertyItemTest : PropertyTestCase() {

  fun testTextStyleProperty() {
    val components = createComponents(component(TEXT_VIEW).withAttribute(ANDROID_URI, ATTR_TEXT_STYLE, TextStyle.VALUE_BOLD))
    val property = createPropertyItem(ATTR_TEXT_STYLE, NelePropertyType.STRING, components)
    assertThat(property.flags).hasSize(3)
    val normal = property.flag(TextStyle.VALUE_NORMAL)
    val bold = property.flag(TextStyle.VALUE_BOLD)
    val italic = property.flag(TextStyle.VALUE_ITALIC)
    assertThat(property.value).isEqualTo(TextStyle.VALUE_BOLD)
    assertThat(property.isFlagSet(bold)).isTrue()
    assertThat(property.isFlagSet(italic)).isFalse()
    assertThat(property.isFlagSet(normal)).isFalse()
    assertThat(property.maskValue).isEqualTo(1)
    assertThat(property.formattedValue).isEqualTo("[bold]")
  }

  fun testSetTextStyleProperty() {
    val components = createComponents(component(TEXT_VIEW).withAttribute(ANDROID_URI, ATTR_TEXT_STYLE, TextStyle.VALUE_BOLD))
    val property = createPropertyItem(ATTR_TEXT_STYLE, NelePropertyType.STRING, components)
    val italic = property.flag(TextStyle.VALUE_ITALIC)

    italic.value = "true"
    assertThat(property.value).isEqualTo(TextStyle.VALUE_BOLD + "|" + TextStyle.VALUE_ITALIC)
    assertThat(property.maskValue).isEqualTo(3)
    assertThat(property.formattedValue).isEqualTo("[bold, italic]")
  }

  fun testSetAndResetTextStyleProperty() {
    val components = createComponents(component(TEXT_VIEW).withAttribute(ANDROID_URI, ATTR_TEXT_STYLE, TextStyle.VALUE_BOLD))
    val property = createPropertyItem(ATTR_TEXT_STYLE, NelePropertyType.STRING, components)
    val bold = property.flag(TextStyle.VALUE_BOLD)
    val italic = property.flag(TextStyle.VALUE_ITALIC)

    bold.value = "false"
    italic.value = "true"
    assertThat(property.value).isEqualTo(TextStyle.VALUE_ITALIC)
    assertThat(property.maskValue).isEqualTo(2)
    assertThat(property.formattedValue).isEqualTo("[italic]")
  }

  fun testCenterImpliesMultipleEffectiveFlags() {
    val components = createComponents(component(TEXT_VIEW).withAttribute(ANDROID_URI, ATTR_GRAVITY, GRAVITY_VALUE_CENTER))
    val property = createPropertyItem(ATTR_GRAVITY, NelePropertyType.STRING, components)
    val center = property.flag(GRAVITY_VALUE_CENTER)
    val centerHorizontal = property.flag(GRAVITY_VALUE_CENTER_HORIZONTAL)
    val centerVertical = property.flag(GRAVITY_VALUE_CENTER_VERTICAL)

    assertThat(property.value).isEqualTo(GRAVITY_VALUE_CENTER)
    assertThat(center.value).isEqualTo(VALUE_TRUE)
    assertThat(center.effectiveValue).isTrue()
    assertThat(centerHorizontal.value).isEqualTo(VALUE_FALSE)
    assertThat(centerHorizontal.effectiveValue).isTrue()
    assertThat(centerVertical.value).isEqualTo(VALUE_FALSE)
    assertThat(centerVertical.effectiveValue).isTrue()
  }

  private fun createPropertyItem(attrName: String, type: NelePropertyType, components: List<NlComponent>): NeleFlagsPropertyItem {
    val model = NelePropertiesModel(testRootDisposable, myFacet)
    val resourceManagers = ModuleResourceManagers.getInstance(myFacet)
    val systemResourceManager = resourceManagers.systemResourceManager
    val definition = systemResourceManager?.attributeDefinitions?.getAttrDefByName(attrName)
    return NeleFlagsPropertyItem(ANDROID_URI, attrName, type, definition!!, "", model, components)
  }
}
