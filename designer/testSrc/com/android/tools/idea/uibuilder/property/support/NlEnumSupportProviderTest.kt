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

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_DROPDOWN_HEIGHT
import com.android.SdkConstants.ATTR_DROPDOWN_WIDTH
import com.android.SdkConstants.ATTR_FONT_FAMILY
import com.android.SdkConstants.ATTR_LAYOUT_HEIGHT
import com.android.SdkConstants.ATTR_LAYOUT_WIDTH
import com.android.SdkConstants.ATTR_LINE_SPACING_EXTRA
import com.android.SdkConstants.ATTR_ON_CLICK
import com.android.SdkConstants.ATTR_TEXT
import com.android.SdkConstants.ATTR_TEXT_APPEARANCE
import com.android.SdkConstants.ATTR_TEXT_SIZE
import com.android.SdkConstants.ATTR_TYPEFACE
import com.android.SdkConstants.ATTR_VISIBILITY
import com.android.SdkConstants.AUTO_COMPLETE_TEXT_VIEW
import com.android.SdkConstants.AUTO_URI
import com.android.SdkConstants.BUTTON
import com.android.SdkConstants.CALENDAR_VIEW
import com.android.AndroidXConstants.CONSTRAINT_LAYOUT
import com.android.SdkConstants.TEXT_VIEW
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.tools.idea.uibuilder.property.NlPropertyType
import com.android.tools.idea.uibuilder.property.testutils.SupportTestUtil
import com.google.common.truth.Truth.assertThat
import org.jetbrains.android.AndroidTestCase
import com.android.tools.dom.attrs.AttributeDefinition

class NlEnumSupportProviderTest: AndroidTestCase() {

  private val MATCH_CONSTRAINT = "0dp (match constraint)"
  private val WRAP_CONTENT = "wrap_content"

  fun testAttributeWithNoEnumSupport() {
    val util = SupportTestUtil(myFacet, myFixture, TEXT_VIEW, parentTag = CONSTRAINT_LAYOUT.defaultName())
    val property = util.makeProperty(ANDROID_URI, ATTR_TEXT, NlPropertyType.STRING)
    val provider = NlEnumSupportProvider(util.model)
    val enumSupport = provider(property)
    assertThat(enumSupport).isNull()
  }

  fun testFromViewHandlerForConstraintLayout() {
    val util = SupportTestUtil(myFacet, myFixture, TEXT_VIEW, parentTag = CONSTRAINT_LAYOUT.defaultName())
    val property = util.makeProperty(ANDROID_URI, ATTR_LAYOUT_HEIGHT, NlPropertyType.DIMENSION)
    val provider = NlEnumSupportProvider(util.model)
    val enumSupport = provider(property) ?: error("No EnumSupport Found")
    assertThat(enumSupport.values.map{ it.value }).containsExactly("0dp", WRAP_CONTENT)
    assertThat(enumSupport.values.map{ it.display }).containsExactly(MATCH_CONSTRAINT, WRAP_CONTENT).inOrder()
  }

  fun testTextAppearance() {
    val util = SupportTestUtil(myFacet, myFixture, TEXT_VIEW)
    val property = util.makeProperty(ANDROID_URI, ATTR_TEXT_APPEARANCE, NlPropertyType.STYLE)
    val provider = NlEnumSupportProvider(util.model)
    val enumSupport = provider(property) ?: error("No EnumSupport Found")
    assertThat(enumSupport).isInstanceOf(TextAppearanceEnumSupport::class.java)
    assertThat(enumSupport.values.size).isAtLeast(9)
  }

  fun testCalendarMonthTextAppearance() {
    val util = SupportTestUtil(myFacet, myFixture, CALENDAR_VIEW)
    val property = util.makeProperty(ANDROID_URI, "monthTextAppearance", NlPropertyType.STYLE)
    val provider = NlEnumSupportProvider(util.model)
    val enumSupport = provider(property) ?: error("No EnumSupport Found")
    assertThat(enumSupport).isInstanceOf(TextAppearanceEnumSupport::class.java)
    assertThat(enumSupport.values.size).isAtLeast(9)
  }

  fun testFontFamily() {
    val util = SupportTestUtil(myFacet, myFixture, TEXT_VIEW)
    val property = util.makeProperty(ANDROID_URI, ATTR_FONT_FAMILY, NlPropertyType.STYLE)
    val provider = NlEnumSupportProvider(util.model)
    val enumSupport = provider(property) ?: error("No EnumSupport Found")
    assertThat(enumSupport).isInstanceOf(FontEnumSupport::class.java)
    assertThat(enumSupport.values.size).isAtLeast(9)
  }

  fun testFontFamilyFromAutoNamespace() {
    val util = SupportTestUtil(myFacet, myFixture, TEXT_VIEW)
    val property = util.makeProperty(AUTO_URI, ATTR_FONT_FAMILY, NlPropertyType.STYLE)
    val provider = NlEnumSupportProvider(util.model)
    val enumSupport = provider(property) ?: error("No EnumSupport Found")
    assertThat(enumSupport).isInstanceOf(FontEnumSupport::class.java)
    assertThat(enumSupport.values.size).isAtLeast(9)
  }

  fun testTypeface() {
    val util = SupportTestUtil(myFacet, myFixture, TEXT_VIEW)
    val property = util.makeProperty(ANDROID_URI, ATTR_TYPEFACE, NlPropertyType.DIMENSION)
    val provider = NlEnumSupportProvider(util.model)
    val enumSupport = provider(property) ?: error("No EnumSupport Found")
    assertThat(enumSupport.values.map{ it.value }).containsExactly("normal", "sans", "serif", "monospace").inOrder()
  }

  fun testTextSize() {
    checkTextSizeAttribute(ATTR_TEXT_SIZE)
  }

  fun testLineSpacingExtra() {
    checkTextSizeAttribute(ATTR_LINE_SPACING_EXTRA)
  }

  private fun checkTextSizeAttribute(attributeName: String) {
    val util = SupportTestUtil(myFacet, myFixture, TEXT_VIEW)
    val property = util.makeProperty(ANDROID_URI, attributeName, NlPropertyType.DIMENSION)
    val provider = NlEnumSupportProvider(util.model)
    val enumSupport = provider(property) ?: error("No EnumSupport Found")
    assertThat(enumSupport.values.map{ it.value })
      .containsExactly("8sp", "10sp", "12sp", "14sp", "16sp", "20sp", "24sp", "34sp", "48sp", "60sp", "96sp").inOrder()
  }

  fun testLayoutWidth() {
    checkSizeAttribute(ATTR_LAYOUT_WIDTH)
  }

  fun testLayoutHeight() {
    checkSizeAttribute(ATTR_LAYOUT_HEIGHT)
  }

  fun testDropDownWidth() {
    checkSizeAttribute(ATTR_DROPDOWN_WIDTH)
  }

  fun testDropDownHeight() {
    checkSizeAttribute(ATTR_DROPDOWN_HEIGHT)
  }

  private fun checkSizeAttribute(attributeName: String) {
    val util = SupportTestUtil(myFacet, myFixture, AUTO_COMPLETE_TEXT_VIEW)
    val property = util.makeProperty(ANDROID_URI, attributeName, NlPropertyType.DIMENSION)
    val provider = NlEnumSupportProvider(util.model)
    val enumSupport = provider(property) ?: error("No EnumSupport Found")
    assertThat(enumSupport.values.map{ it.value }).containsExactly("match_parent", WRAP_CONTENT).inOrder()
  }

  fun testOnClick() {
    val util = SupportTestUtil(myFacet, myFixture, BUTTON)
    val property = util.makeProperty(ANDROID_URI, ATTR_ON_CLICK, NlPropertyType.STRING)
    val provider = NlEnumSupportProvider(util.model)
    val enumSupport = provider(property) ?: error("No EnumSupport Found")
    assertThat(enumSupport).isInstanceOf(OnClickEnumSupport::class.java)
  }

  fun testFromAttributeDefinition() {
    val util = SupportTestUtil(myFacet, myFixture, TEXT_VIEW)
    val definition = AttributeDefinition(ResourceNamespace.RES_AUTO, ATTR_VISIBILITY)
    definition.setValueMappings(mapOf("visible" to 1, "invisible" to 2, "gone" to 3))
    val property = util.makeProperty(ANDROID_URI, definition, NlPropertyType.ENUM)
    val provider = NlEnumSupportProvider(util.model)
    val enumSupport = provider(property) ?: error("No EnumSupport Found")
    assertThat(enumSupport.values.map{ it.value }).containsExactly("visible", "invisible", "gone").inOrder()
  }
}
