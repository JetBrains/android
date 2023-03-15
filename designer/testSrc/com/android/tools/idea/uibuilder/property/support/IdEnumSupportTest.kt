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
package com.android.tools.idea.uibuilder.property.support

import com.android.SdkConstants
import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_CHECKED_CHIP
import com.android.SdkConstants.ATTR_ID
import com.android.SdkConstants.ATTR_LAYOUT_ALIGN_START
import com.android.SdkConstants.AUTO_URI
import com.android.SdkConstants.BUTTON
import com.android.SdkConstants.CHIP
import com.android.SdkConstants.CHIP_GROUP
import com.android.AndroidXConstants.CONSTRAINT_LAYOUT
import com.android.SdkConstants.RELATIVE_LAYOUT
import com.android.SdkConstants.TEXT_VIEW
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.resources.ResourceType
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.property.NlPropertyItem
import com.android.tools.idea.uibuilder.property.NlPropertyType
import com.android.tools.idea.uibuilder.property.testutils.SupportTestUtil
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.jetbrains.android.dom.AndroidDomUtil
import com.android.tools.dom.attrs.AttributeDefinition
import org.jetbrains.android.dom.navigation.NavigationSchema
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class IdEnumSupportTest {

  @JvmField @Rule
  val projectRule = AndroidProjectRule.inMemory()

  @JvmField @Rule
  val edtRule = EdtRule()

  @Test
  fun testAllIdAttributes() {
    // This test is here to check that all the attributes in AndroidDomUtil.getSpecialAttributeNamesByType(ResourceType.ID) are supported.
    val util = SupportTestUtil(projectRule, TEXT_VIEW, parentTag = RELATIVE_LAYOUT)
    for (attributeName in AndroidDomUtil.getSpecialAttributeNamesByType(ResourceType.ID)) {
      // These attributes are excluded from using IdEnumSupport in NeleEnumSupportProvider
      if (attributeName == ATTR_ID || attributeName == NavigationSchema.ATTR_DESTINATION) {
        continue
      }
      val property = util.makeProperty(ANDROID_URI, attributeName, NlPropertyType.ID)
      val enumSupport = IdEnumSupport(property)
      assertThat(enumSupport.values).isNotNull()
    }
  }

  @Test
  fun testRelativeLayoutAttribute() {
    val util = SupportTestUtil(projectRule, TEXT_VIEW, BUTTON, TEXT_VIEW, BUTTON, parentTag = RELATIVE_LAYOUT)
    val textView = util.findSiblingById("textview1")!!
    val property = NlPropertyItem(
      ANDROID_URI, ATTR_LAYOUT_ALIGN_START, NlPropertyType.ID, null, "", "", util.model, listOf(textView))
    val enumSupport = IdEnumSupport(property)
    assertThat(enumSupport.values.map { it.display }).containsExactly("@id/button2", "@id/textview3", "@id/button4")
  }

  @Test
  fun testConstraintLayoutAttribute() {
    val util = SupportTestUtil(projectRule, TEXT_VIEW, BUTTON, TEXT_VIEW, BUTTON, parentTag = CONSTRAINT_LAYOUT.newName())
    val textView = util.findSiblingById("textview1")!!
    val definition = AttributeDefinition(ResourceNamespace.TODO(), SdkConstants.ATTR_LAYOUT_START_TO_END_OF)
    val property = NlPropertyItem(
      AUTO_URI, SdkConstants.ATTR_LAYOUT_START_TO_END_OF, NlPropertyType.ID, definition, "", "", util.model, listOf(textView))
    definition.setValueMappings(mapOf(Pair("parent", 0)))
    val enumSupport = IdEnumSupport(property)
    assertThat(enumSupport.values.map { it.display }).containsExactly("@id/button2", "@id/textview3", "@id/button4", "parent")
  }

  @Test
  fun testChipGroupAttribute() {
    val util = SupportTestUtil(projectRule, CHIP, CHIP, CHIP, parentTag = CHIP_GROUP)
    val group = util.components[0].parent!!
    val property = NlPropertyItem(
      AUTO_URI, ATTR_CHECKED_CHIP, NlPropertyType.ID, null, "", "", util.model, listOf(group))
    val enumSupport = IdEnumSupport(property)
    assertThat(enumSupport.values.map { it.display }).containsExactly("@id/chip1", "@id/chip2", "@id/chip3")
  }

  @Test
  fun testAccessibilityAttribute() {
    val util = SupportTestUtil(projectRule, TEXT_VIEW, BUTTON, TEXT_VIEW, BUTTON, parentTag = RELATIVE_LAYOUT)
    val textView = util.findSiblingById("textview1")!!
    val property = NlPropertyItem(ANDROID_URI, SdkConstants.ATTR_ACCESSIBILITY_TRAVERSAL_BEFORE, NlPropertyType.ID,
                                  null, "", "", util.model, listOf(textView))
    val enumSupport = IdEnumSupport(property)
    assertThat(enumSupport.values.map { it.display }).containsExactly("@id/relativelayout", "@id/button2", "@id/textview3", "@id/button4")
  }
}
