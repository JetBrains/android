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
package com.android.tools.idea.uibuilder.property2.support

import com.android.SdkConstants.*
import com.android.tools.idea.uibuilder.property2.NelePropertyType
import com.android.tools.idea.uibuilder.property2.testutils.SupportTestUtil
import com.google.common.truth.Truth.assertThat
import org.jetbrains.android.AndroidTestCase
import org.jetbrains.android.dom.attrs.AttributeDefinition

class EnumSupportProviderImplTest: AndroidTestCase() {

  fun testAttributeWithNoEnumSupport() {
    val util = SupportTestUtil(testRootDisposable, myFacet, myFixture, TEXT_VIEW, CONSTRAINT_LAYOUT.defaultName())
    val property = util.makeProperty(ANDROID_URI, ATTR_TEXT, NelePropertyType.STRING)
    val provider = EnumSupportProviderImpl()
    val enumSupport = provider(property)
    assertThat(enumSupport).isNull()
  }

  fun testFromViewHandlerForConstraintLayout() {
    val util = SupportTestUtil(testRootDisposable, myFacet, myFixture, TEXT_VIEW, CONSTRAINT_LAYOUT.defaultName())
    val property = util.makeProperty(ANDROID_URI, ATTR_LAYOUT_HEIGHT, NelePropertyType.DIMENSION)
    val provider = EnumSupportProviderImpl()
    val enumSupport = provider(property) ?: error("No EnumSupport Found")
    assertThat(enumSupport.values.map{ it.value }).containsExactly("0dp", "wrap_content")
    assertThat(enumSupport.values.map{ it.display }).containsExactly("match_constraint", "wrap_content").inOrder()
  }

  fun testTextAppearance() {
    val util = SupportTestUtil(testRootDisposable, myFacet, myFixture, TEXT_VIEW)
    val property = util.makeProperty(ANDROID_URI, ATTR_TEXT_APPEARANCE, NelePropertyType.STYLE)
    val provider = EnumSupportProviderImpl()
    val enumSupport = provider(property) ?: error("No EnumSupport Found")
    assertThat(enumSupport).isInstanceOf(TextAppearanceEnumSupport::class.java)
    assertThat(enumSupport.values.size).isAtLeast(9)
  }

  fun testCalendarMonthTextAppearance() {
    val util = SupportTestUtil(testRootDisposable, myFacet, myFixture, CALENDAR_VIEW)
    val property = util.makeProperty(ANDROID_URI, "monthTextAppearance", NelePropertyType.STYLE)
    val provider = EnumSupportProviderImpl()
    val enumSupport = provider(property) ?: error("No EnumSupport Found")
    assertThat(enumSupport).isInstanceOf(TextAppearanceEnumSupport::class.java)
    assertThat(enumSupport.values.size).isAtLeast(9)
  }

  fun testFontFamily() {
    val util = SupportTestUtil(testRootDisposable, myFacet, myFixture, TEXT_VIEW)
    val property = util.makeProperty(ANDROID_URI, ATTR_FONT_FAMILY, NelePropertyType.STYLE)
    val provider = EnumSupportProviderImpl()
    val enumSupport = provider(property) ?: error("No EnumSupport Found")
    assertThat(enumSupport).isInstanceOf(FontEnumSupport::class.java)
    assertThat(enumSupport.values.size).isAtLeast(9)
  }

  fun testTypeface() {
    val util = SupportTestUtil(testRootDisposable, myFacet, myFixture, TEXT_VIEW)
    val property = util.makeProperty(ANDROID_URI, ATTR_TYPEFACE, NelePropertyType.DIMENSION)
    val provider = EnumSupportProviderImpl()
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
    val util = SupportTestUtil(testRootDisposable, myFacet, myFixture, TEXT_VIEW)
    val property = util.makeProperty(ANDROID_URI, attributeName, NelePropertyType.DIMENSION)
    val provider = EnumSupportProviderImpl()
    val enumSupport = provider(property) ?: error("No EnumSupport Found")
    assertThat(enumSupport.values.map{ it.value }).containsExactly("8sp", "10sp", "12sp", "14sp", "18sp", "24sp", "30sp", "36sp").inOrder()
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
    val util = SupportTestUtil(testRootDisposable, myFacet, myFixture, AUTO_COMPLETE_TEXT_VIEW)
    val property = util.makeProperty(ANDROID_URI, attributeName, NelePropertyType.DIMENSION)
    val provider = EnumSupportProviderImpl()
    val enumSupport = provider(property) ?: error("No EnumSupport Found")
    assertThat(enumSupport.values.map{ it.value }).containsExactly("match_parent", "wrap_content").inOrder()
  }

  fun testOnClick() {
    val util = SupportTestUtil(testRootDisposable, myFacet, myFixture, BUTTON)
    val property = util.makeProperty(ANDROID_URI, ATTR_ON_CLICK, NelePropertyType.STRING)
    val provider = EnumSupportProviderImpl()
    val enumSupport = provider(property) ?: error("No EnumSupport Found")
    assertThat(enumSupport).isInstanceOf(OnClickEnumSupport::class.java)
  }

  fun testFromAttributeDefinition() {
    val util = SupportTestUtil(testRootDisposable, myFacet, myFixture, TEXT_VIEW)
    val definition = AttributeDefinition(ATTR_VISIBILITY, "", TEXT_VIEW, emptyList())
    definition.addValue("visible")
    definition.addValue("invisible")
    definition.addValue("gone")
    val property = util.makeProperty(ANDROID_URI, definition)
    val provider = EnumSupportProviderImpl()
    val enumSupport = provider(property) ?: error("No EnumSupport Found")
    assertThat(enumSupport.values.map{ it.value }).containsExactly("visible", "invisible", "gone").inOrder()
  }
}
