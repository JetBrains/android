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

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_GRAVITY
import com.android.SdkConstants.ATTR_ID
import com.android.SdkConstants.ATTR_ORIENTATION
import com.android.SdkConstants.ATTR_SRC_COMPAT
import com.android.SdkConstants.ATTR_STYLE
import com.android.SdkConstants.ATTR_TEXT
import com.android.SdkConstants.ATTR_TEXT_COLOR
import com.android.SdkConstants.ATTR_TEXT_SIZE
import com.android.SdkConstants.AUTO_URI
import com.android.SdkConstants.FQCN_TEXT_VIEW
import com.android.SdkConstants.PREFIX_ANDROID
import com.android.SdkConstants.PREFIX_APP
import com.android.SdkConstants.TEXT_VIEW
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.tools.adtui.model.stdui.EDITOR_NO_ERROR
import com.android.tools.adtui.model.stdui.EditingErrorCategory.ERROR
import com.android.tools.idea.common.fixtures.ComponentDescriptor
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.property.testutils.SupportTestUtil
import com.android.tools.property.panel.api.PropertiesTable
import com.google.common.collect.HashBasedTable
import com.google.common.collect.Table
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class NlNewPropertyItemTest {
  @JvmField @Rule val projectRule = AndroidProjectRule.withSdk()

  @JvmField @Rule val edtRule = EdtRule()

  @Test
  fun testSetNameWithPrefix() {
    val properties = createTable()
    val model = properties.first!!.model
    val property = NlNewPropertyItem(model, properties)
    property.name = PREFIX_ANDROID + ATTR_TEXT
    assertThat(property.namespace).isEqualTo(ANDROID_URI)
    assertThat(property.delegate).isNotNull()
  }

  @Test
  fun testSetNameNoMatch() {
    val properties = createTable()
    val model = properties.first!!.model
    val property = NlNewPropertyItem(model, properties)
    property.name = ATTR_ORIENTATION
    assertThat(property.delegate).isNull()
  }

  @Test
  fun testSetNameWithoutPrefix() {
    val properties = createTable()
    val model = properties.first!!.model
    val property = NlNewPropertyItem(model, properties)
    property.name = ATTR_STYLE
    assertThat(property.namespace).isEqualTo("")
    assertThat(property.delegate).isNotNull()
  }

  @Test
  fun testSetNameWithAppPrefix() {
    val properties = createTable()
    val model = properties.first!!.model
    val property = NlNewPropertyItem(model, properties)
    property.name = PREFIX_APP + ATTR_SRC_COMPAT
    assertThat(property.namespace).isEqualTo(AUTO_URI)
    assertThat(property.delegate).isNotNull()
  }

  @Test
  fun testDelegate() {
    val properties = createTable()
    val model = properties.first!!.model
    val property = NlNewPropertyItem(model, properties)
    property.name = PREFIX_ANDROID + ATTR_TEXT
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    val delegate = property.delegate!!

    assertThat(delegate.namespace).isEqualTo(ANDROID_URI)
    assertThat(delegate.name).isEqualTo(ATTR_TEXT)

    property.value = "Hello"
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

    assertThat(property.value).isEqualTo("Hello")
    assertThat(property.type).isEqualTo(NlPropertyType.STRING)
    assertThat(property.definition!!.resourceReference)
      .isEqualTo(ResourceReference.attr(ResourceNamespace.ANDROID, ATTR_TEXT))
    assertThat(property.componentName).isEqualTo(FQCN_TEXT_VIEW)
    assertThat(property.components).containsExactly(delegate.components[0])
    assertThat(property.libraryName).isEqualTo("android")
    assertThat(property.resolvedValue).isEqualTo("Hello")
    assertThat(property.isReference).isFalse()
    assertThat(property.tooltipForValue).isEqualTo("")
  }

  @Test
  fun testIdDelegate() {
    val properties = createTable()
    val model = properties.first!!.model
    val property = NlNewPropertyItem(model, properties)
    property.name = PREFIX_ANDROID + ATTR_ID
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    val delegate = property.delegate!!

    assertThat(delegate.namespace).isEqualTo(ANDROID_URI)
    assertThat(delegate.name).isEqualTo(ATTR_ID)

    property.value = "abc"
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

    assertThat(property.value).isEqualTo("abc")
    assertThat(property.type).isEqualTo(NlPropertyType.ID)
    assertThat(property.definition!!.resourceReference)
      .isEqualTo(ResourceReference.attr(ResourceNamespace.ANDROID, ATTR_ID))
    assertThat(property.components).containsExactly(delegate.components[0])
    assertThat(property.resolvedValue).isEqualTo("@+id/abc")
    assertThat(property.isReference).isFalse()
    assertThat(property.tooltipForValue).isEqualTo("")
    assertThat(property.getCompletionValues()).isEmpty()
    assertThat(property.validate("abc")).isEqualTo(EDITOR_NO_ERROR)
    assertThat(property.validate("@+/textId")).isEqualTo(EDITOR_NO_ERROR)
  }

  @Test
  fun testDelegateWithoutPrefix() {
    val properties = createTable()
    val model = properties.first!!.model
    val property = NlNewPropertyItem(model, properties)
    property.name = ATTR_TEXT
    val delegate = property.delegate!!
    assertThat(delegate.namespace).isEqualTo(ANDROID_URI)
    assertThat(delegate.name).isEqualTo(ATTR_TEXT)
    assertThat(property.namespace).isEqualTo(ANDROID_URI)
    assertThat(property.name).isEqualTo(ATTR_TEXT)
  }

  @Test
  fun testFlagsDelegate() {
    val properties = createTable()
    val model = properties.first!!.model
    val property = NlNewPropertyItem(model, properties)
    property.name = PREFIX_ANDROID + ATTR_GRAVITY
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    val delegate = property.delegate!!

    assertThat(delegate.namespace).isEqualTo(ANDROID_URI)
    assertThat(delegate.name).isEqualTo(ATTR_GRAVITY)
    assertThat(property.children.map { it.name })
      .containsExactly(
        "top",
        "center_vertical",
        "bottom",
        "start",
        "left",
        "center_horizontal",
        "right",
        "end",
        "clip_horizontal",
        "clip_vertical",
        "center",
        "fill",
        "fill_horizontal",
        "fill_vertical",
      )

    property.flag("center")?.value = "true"
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

    assertThat(property.value).isEqualTo("center")
    assertThat(property.resolvedValue).isEqualTo("center")
    assertThat(property.isReference).isFalse()
    assertThat(property.tooltipForValue).isEqualTo("")
  }

  @Test
  fun testCompletion() {
    val properties = createTable()
    val model = properties.first!!.model
    val property = NlNewPropertyItem(model, properties)
    val values = property.nameEditingSupport.completion("")
    assertThat(values)
      .containsExactly(
        "style",
        "android:id",
        "android:text",
        "android:textSize",
        "android:textColor",
        "android:gravity",
        "app:srcCompat",
        "tools:id",
        "tools:text",
        "tools:textSize",
        "tools:textColor",
        "tools:gravity",
        "tools:srcCompat",
      )
  }

  @Test
  fun testAssignedAttributesAreNotInCompletions() {
    val properties = createTable()
    val model = properties.first!!.model
    val property = NlNewPropertyItem(model, properties, { it.rawValue == null })
    properties[ANDROID_URI, ATTR_TEXT].value = "Hello"
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    properties[ANDROID_URI, ATTR_TEXT_COLOR].value = "#445566"
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    val values = property.nameEditingSupport.completion("")
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    assertThat(values)
      .containsExactly(
        "style",
        "android:id",
        "android:textSize",
        "android:gravity",
        "app:srcCompat",
        "tools:id",
        "tools:text",
        "tools:textSize",
        "tools:textColor",
        "tools:gravity",
        "tools:srcCompat",
      )
  }

  @Test
  fun testValidationErrors() {
    val properties = createTable()
    val model = properties.first!!.model
    val property = NlNewPropertyItem(model, properties)
    properties[ANDROID_URI, ATTR_TEXT].value = "Hello"
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

    assertThat(property.nameEditingSupport.validation("android:xyz"))
      .isEqualTo(Pair(ERROR, "No property found by the name: 'android:xyz'"))
    assertThat(property.nameEditingSupport.validation("android:text"))
      .isEqualTo(Pair(ERROR, "A property by the name: 'android:text' is already specified"))
  }

  @Test
  fun testIsSameProperty() {
    val properties = createTable()
    val model = properties.first!!.model
    val property = NlNewPropertyItem(model, properties)
    property.name = PREFIX_ANDROID + ATTR_TEXT
    assertThat(property.isSameProperty("android:text")).isTrue()
    assertThat(property.isSameProperty("android:backgroundHint")).isFalse()
  }

  private fun createTable(): PropertiesTable<NlPropertyItem> {
    val descriptor =
      ComponentDescriptor(TEXT_VIEW)
        .withBounds(0, 0, 1000, 1000)
        .wrapContentWidth()
        .wrapContentHeight()
        .withAttribute(AUTO_URI, "something", "1")
    val util = SupportTestUtil(projectRule, descriptor)
    val property0 = util.makeProperty("", ATTR_STYLE, NlPropertyType.STYLE)
    val property1 = util.makeProperty(ANDROID_URI, ATTR_TEXT, NlPropertyType.STRING)
    val property2 = util.makeProperty(ANDROID_URI, ATTR_TEXT_SIZE, NlPropertyType.DIMENSION)
    val property3 = util.makeProperty(ANDROID_URI, ATTR_TEXT_COLOR, NlPropertyType.COLOR_STATE_LIST)
    val property4 = util.makeProperty(AUTO_URI, ATTR_SRC_COMPAT, NlPropertyType.DRAWABLE)
    val property5 = util.makeProperty(ANDROID_URI, ATTR_GRAVITY, NlPropertyType.ENUM)
    val property6 = util.makeIdProperty()
    val table: Table<String, String, NlPropertyItem> = HashBasedTable.create()

    // Override property1 such that componentName and library name is set for the delegate test
    // above:
    val textProperty =
      with(property1) {
        NlPropertyItem(
          namespace,
          name,
          type,
          definition,
          FQCN_TEXT_VIEW,
          "android",
          model,
          components,
        )
      }
    add(table, property0)
    add(table, textProperty)
    add(table, property2)
    add(table, property3)
    add(table, property4)
    add(table, property5)
    add(table, property6)
    return PropertiesTable.create(table)
  }

  private fun add(table: Table<String, String, NlPropertyItem>, property: NlPropertyItem) {
    table.put(property.namespace, property.name, property)
  }
}
