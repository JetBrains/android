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
import icons.StudioIcons
import org.jetbrains.android.resourceManagers.ModuleResourceManagers

internal const val EXPECTED_ID_TOOLTIP = """
android:id:  Supply an identifier name for this view, to later retrieve it
             with {@link android.view.View#findViewById View.findViewById()} or
             {@link android.app.Activity#findViewById Activity.findViewById()}.
             This must be a
             resource reference; typically you set this using the
             <code>@+</code> syntax to create a new ID resources.
             For example: <code>android:id="@+id/my_id"</code> which
             allows you to later retrieve the view
             with <code>findViewById(R.id.my_id)</code>.
"""

private const val STRINGS = """<?xml version="1.0" encoding="utf-8"?>
<resources>
  <string name="demo">Demo String</string>
  <string name="design">Design Demo</string>
</resources>
"""

private const val HELLO_WORLD = "Hello World"

class NelePropertyItemTest : PropertyTestCase() {

  override fun setUp() {
    super.setUp()
    myFixture.addFileToProject("/res/values/strings.xml", STRINGS)
  }

  fun testTextProperty() {
    val property = createPropertyItem(ATTR_TEXT, NelePropertyType.STRING, createTextView())
    assertThat(property.name).isEqualTo(ATTR_TEXT)
    assertThat(property.namespace).isEqualTo(ANDROID_URI)
    assertThat(property.type).isEqualTo(NelePropertyType.STRING)
    assertThat(property.value).isEqualTo("@string/demo")
    assertThat(property.isReference).isTrue()
    assertThat(property.resolvedValue).isEqualTo("Demo String")
    assertThat(property.tooltip).isEqualTo("android:text:  Text to display. ")
    assertThat(property.validate("Some")).isEmpty()
    assertThat(property.libraryName).isEmpty()
    assertThat(property.components).hasSize(1)
    assertThat(property.components[0].tagName).isEqualTo(TEXT_VIEW)
    assertThat(property.getActionIcon(false)).isEqualTo(StudioIcons.Common.PROPERTY_BOUND)
    assertThat(property.getActionIcon(true)).isEqualTo(StudioIcons.Common.PROPERTY_BOUND_FOCUS)
    assertThat(property.showActionButton).isTrue()
  }

  fun testUnboundTextProperty() {
    val property = createPropertyItem(ATTR_TEXT, NelePropertyType.STRING, createTextViewWithHardcodedValue())
    assertThat(property.name).isEqualTo(ATTR_TEXT)
    assertThat(property.namespace).isEqualTo(ANDROID_URI)
    assertThat(property.type).isEqualTo(NelePropertyType.STRING)
    assertThat(property.value).isEqualTo("Hardcoded string")
    assertThat(property.isReference).isFalse()
    assertThat(property.resolvedValue).isEqualTo("Hardcoded string")
    assertThat(property.getActionIcon(false)).isEqualTo(StudioIcons.Common.PROPERTY_UNBOUND)
    assertThat(property.getActionIcon(true)).isEqualTo(StudioIcons.Common.PROPERTY_UNBOUND_FOCUS)
    assertThat(property.showActionButton).isTrue()
  }

  fun testTextDesignProperty() {
    val property = createPropertyItem(ATTR_TEXT, NelePropertyType.STRING, createTextView())
    val design = property.designProperty
    assertThat(design.name).isEqualTo(ATTR_TEXT)
    assertThat(design.namespace).isEqualTo(TOOLS_URI)
    assertThat(design.type).isEqualTo(NelePropertyType.STRING)
    assertThat(design.value).isEqualTo("@string/design")
    assertThat(design.isReference).isTrue()
    assertThat(design.resolvedValue).isEqualTo("Design Demo")
    assertThat(design.tooltip).isEqualTo("tools:text:  Text to display. ")
    assertThat(design.validate("Some")).isEmpty()
    assertThat(design.libraryName).isEmpty()
    assertThat(design.components).hasSize(1)
    assertThat(design.components[0].tagName).isEqualTo(TEXT_VIEW)
    assertThat(property.getActionIcon(false)).isEqualTo(StudioIcons.Common.PROPERTY_BOUND)
    assertThat(property.getActionIcon(true)).isEqualTo(StudioIcons.Common.PROPERTY_BOUND_FOCUS)
    assertThat(design.showActionButton).isTrue()
    assertThat(design.designProperty).isEqualTo(design)
  }

  fun testIsReference() {
    val property = createPropertyItem(ATTR_TEXT, NelePropertyType.STRING, createTextView())
    assertThat(isReferenceValue(property, "hello")).isFalse()
    assertThat(isReferenceValue(property, "@string/hello")).isTrue()
    assertThat(isReferenceValue(property, "@android:string/hello")).isTrue()
    assertThat(isReferenceValue(property, "?backgroundColor")).isTrue()
    // IDs should not be treated as references:
    assertThat(isReferenceValue(property, "@id/hello")).isFalse()
    assertThat(isReferenceValue(property, "@+id/hello")).isFalse()
    assertThat(isReferenceValue(property, "@android:id/hello")).isFalse()
  }

  fun testGetSameValueFromMultipleComponents() {
    val property = createPropertyItem(ATTR_TEXT, NelePropertyType.STRING, createTextViewAndButtonWithSameTextValue())
    assertThat(property.value).isEqualTo("@string/demo")
    assertThat(property.isReference).isTrue()
    assertThat(property.resolvedValue).isEqualTo("Demo String")
  }

  fun testGetDifferentValueFromMultipleComponents() {
    val property = createPropertyItem(ATTR_TEXT, NelePropertyType.STRING, createTextViewAndButtonWithDifferentTextValue())
    assertThat(property.value).isNull()
    assertThat(property.isReference).isFalse()
    assertThat(property.resolvedValue).isNull()
  }

  fun testSetValueOnMultipleComponents() {
    val components = createTextViewAndButtonWithDifferentTextValue()
    val property = createPropertyItem(ATTR_TEXT, NelePropertyType.STRING, components)
    property.value = HELLO_WORLD

    assertThat(property.value).isEqualTo(HELLO_WORLD)
    assertThat(property.isReference).isFalse()
    assertThat(property.resolvedValue).isEqualTo(HELLO_WORLD)
    assertThat(components[0].getAttribute(ANDROID_URI, ATTR_TEXT)).isEqualTo(HELLO_WORLD)
    assertThat(components[1].getAttribute(ANDROID_URI, ATTR_TEXT)).isEqualTo(HELLO_WORLD)
  }

  private fun createPropertyItem(attrName: String, type: NelePropertyType, components: List<NlComponent>): NelePropertyItem {
    val model = NelePropertiesModel(testRootDisposable, myFacet)
    val resourceManagers = ModuleResourceManagers.getInstance(myFacet)
    val systemResourceManager = resourceManagers.systemResourceManager
    val definition = systemResourceManager?.attributeDefinitions?.getAttrDefByName(attrName)
    return NelePropertyItem(ANDROID_URI, attrName, type, definition, "", model, components)
  }

  private fun createTextView(): List<NlComponent> {
    return createComponents(
        component(TEXT_VIEW)
          .withAttribute(ANDROID_URI, ATTR_TEXT, "@string/demo")
          .withAttribute(TOOLS_URI, ATTR_TEXT, "@string/design")
    )
  }

  private fun createTextViewWithHardcodedValue(): List<NlComponent> {
    return createComponents(
      component(TEXT_VIEW)
        .withAttribute(ANDROID_URI, ATTR_TEXT, "Hardcoded string")
        .withAttribute(TOOLS_URI, ATTR_TEXT, "Hardcoded design string")
    )
  }

  private fun createTextViewAndButtonWithSameTextValue(): List<NlComponent> {
    return createComponents(
        component(TEXT_VIEW)
          .withAttribute(ANDROID_URI, ATTR_TEXT, "@string/demo")
          .withAttribute(TOOLS_URI, ATTR_TEXT, "@string/design"),
        component(BUTTON)
          .withAttribute(ANDROID_URI, ATTR_TEXT, "@string/demo")
          .withAttribute(TOOLS_URI, ATTR_TEXT, "@string/design")
    )
  }

  private fun createTextViewAndButtonWithDifferentTextValue(): List<NlComponent> {
    return createComponents(
        component(TEXT_VIEW)
          .withAttribute(ANDROID_URI, ATTR_TEXT, "@string/demo")
          .withAttribute(TOOLS_URI, ATTR_TEXT, "@string/design"),
        component(BUTTON)
          .withAttribute(ANDROID_URI, ATTR_TEXT, "other")
          .withAttribute(TOOLS_URI, ATTR_TEXT, "something")
    )
  }

  private fun isReferenceValue(property: NelePropertyItem, value: String): Boolean {
    property.value = value
    return property.isReference
  }
}
