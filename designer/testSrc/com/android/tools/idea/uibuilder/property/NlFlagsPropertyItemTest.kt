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
package com.android.tools.idea.uibuilder.property

import com.android.SdkConstants.*
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.tools.adtui.model.stdui.EDITOR_NO_ERROR
import com.android.tools.adtui.model.stdui.EditingErrorCategory
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.uibuilder.property.testutils.PropertyTestCase
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.PlatformTestUtil
import org.intellij.lang.annotations.Language
import org.jetbrains.android.resourceManagers.ModuleResourceManagers

class NlFlagsPropertyItemTest : PropertyTestCase() {

  fun testTextStyleProperty() {
    val components = createComponents(component(TEXT_VIEW).withAttribute(ANDROID_URI, ATTR_TEXT_STYLE, TextStyle.VALUE_BOLD))
    val property = createFlagsPropertyItem(ATTR_TEXT_STYLE, NlPropertyType.STRING, components)
    assertThat(property.children).hasSize(3)
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
    val property = createFlagsPropertyItem(ATTR_TEXT_STYLE, NlPropertyType.STRING, components)
    val italic = property.flag(TextStyle.VALUE_ITALIC)

    italic.value = "true"
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

    assertThat(property.value).isEqualTo(TextStyle.VALUE_BOLD + "|" + TextStyle.VALUE_ITALIC)
    assertThat(property.maskValue).isEqualTo(3)
    assertThat(property.formattedValue).isEqualTo("[bold, italic]")
  }

  fun testSetAndResetTextStyleProperty() {
    val components = createComponents(component(TEXT_VIEW).withAttribute(ANDROID_URI, ATTR_TEXT_STYLE, TextStyle.VALUE_BOLD))
    val property = createFlagsPropertyItem(ATTR_TEXT_STYLE, NlPropertyType.STRING, components)
    val bold = property.flag(TextStyle.VALUE_BOLD)
    val italic = property.flag(TextStyle.VALUE_ITALIC)

    bold.value = "false"
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

    italic.value = "true"
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

    assertThat(property.value).isEqualTo(TextStyle.VALUE_ITALIC)
    assertThat(property.maskValue).isEqualTo(2)
    assertThat(property.formattedValue).isEqualTo("[italic]")
  }

  fun testCenterImpliesMultipleEffectiveFlags() {
    val components = createComponents(component(TEXT_VIEW).withAttribute(ANDROID_URI, ATTR_GRAVITY, GRAVITY_VALUE_CENTER))
    val property = createFlagsPropertyItem(ATTR_GRAVITY, NlPropertyType.STRING, components)
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

  fun testValidate() {
    myFixture.addFileToProject("res/values/values.xml", VALUE_RESOURCES)
    val components = createComponents(component(TEXT_VIEW).withAttribute(ANDROID_URI, ATTR_GRAVITY, GRAVITY_VALUE_CENTER))
    val property = createFlagsPropertyItem(ATTR_GRAVITY, NlPropertyType.STRING, components)
    assertThat(property.editingSupport.validation("")).isEqualTo(EDITOR_NO_ERROR)
    assertThat(property.editingSupport.validation("left")).isEqualTo(EDITOR_NO_ERROR)
    assertThat(property.editingSupport.validation("start|bottom")).isEqualTo(EDITOR_NO_ERROR)
    assertThat(property.editingSupport.validation("start|wednesday|bottom")).isEqualTo(
      Pair(EditingErrorCategory.ERROR, "Invalid value: 'wednesday'"))
    assertThat(property.editingSupport.validation("start|wednesday|bottom|winter|left|january")).isEqualTo(
      Pair(EditingErrorCategory.ERROR, "Invalid values: 'wednesday', 'winter', 'january'"))
    assertThat(property.editingSupport.validation("@bool/useBorder")).isEqualTo(
      Pair(EditingErrorCategory.ERROR, "Unexpected resource type: 'bool' expected: string"))
    assertThat(property.editingSupport.validation("@string/hello")).isEqualTo(
      Pair(EditingErrorCategory.ERROR, "Invalid value: 'Hello'"))
    assertThat(property.editingSupport.validation("@string/myGravity")).isEqualTo(EDITOR_NO_ERROR)
    assertThat(property.editingSupport.validation("@string/errGravity")).isEqualTo(
      Pair(EditingErrorCategory.ERROR, "Invalid value: 'wednesday'"))
  }

  private fun createFlagsPropertyItem(attrName: String, type: NlPropertyType, components: List<NlComponent>): NlFlagsPropertyItem {
    val model = NlPropertiesModel(testRootDisposable, myFacet)
    val resourceManagers = ModuleResourceManagers.getInstance(myFacet)
    val frameworkResourceManager = resourceManagers.frameworkResourceManager
    val definition =
        frameworkResourceManager?.attributeDefinitions?.getAttrDefinition(ResourceReference.attr(ResourceNamespace.ANDROID, attrName))
    return NlFlagsPropertyItem(ANDROID_URI, attrName, type, definition!!, "", "", model, components)
  }

  @Language("XML")
  private val VALUE_RESOURCES = """<?xml version="1.0" encoding="utf-8"?>
    <resources>
      <bool name="useBorder">true</bool>
      <string name="hello">Hello</string>
      <string name="myGravity">start|bottom</string>
      <string name="errGravity">start|wednesday|end</string>
    </resources>
  """.trimIndent()
}
