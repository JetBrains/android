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

import com.android.AndroidXConstants.CONSTRAINT_LAYOUT
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
import com.android.SdkConstants.TEXT_VIEW
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.tools.dom.attrs.AttributeDefinition
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.property.NlPropertyType
import com.android.tools.idea.uibuilder.property.testutils.SupportTestUtil
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

private const val MATCH_CONSTRAINT = "0dp (match constraint)"
private const val WRAP_CONTENT = "wrap_content"

@RunsInEdt
class NlEnumSupportProviderTest {
  private val projectRule = AndroidProjectRule.withSdk()

  @get:Rule val chain = RuleChain.outerRule(projectRule).around(EdtRule())!!

  @Test
  fun testAttributeWithNoEnumSupport() {
    val util = SupportTestUtil(projectRule, TEXT_VIEW, parentTag = CONSTRAINT_LAYOUT.defaultName())
    val property = util.makeProperty(ANDROID_URI, ATTR_TEXT, NlPropertyType.STRING)
    val provider = NlEnumSupportProvider(util.model)
    val enumSupport = provider(property)
    assertThat(enumSupport).isNull()
  }

  @Test
  fun testFromViewHandlerForConstraintLayout() {
    val util = SupportTestUtil(projectRule, TEXT_VIEW, parentTag = CONSTRAINT_LAYOUT.defaultName())
    val property = util.makeProperty(ANDROID_URI, ATTR_LAYOUT_HEIGHT, NlPropertyType.DIMENSION)
    val provider = NlEnumSupportProvider(util.model)
    val enumSupport = provider(property) ?: error("No EnumSupport Found")
    assertThat(enumSupport.values.map { it.value }).containsExactly("0dp", WRAP_CONTENT)
    assertThat(enumSupport.values.map { it.display })
      .containsExactly(MATCH_CONSTRAINT, WRAP_CONTENT)
      .inOrder()
  }

  @Test
  fun testTextAppearance() {
    val util = SupportTestUtil(projectRule, TEXT_VIEW)
    val property = util.makeProperty(ANDROID_URI, ATTR_TEXT_APPEARANCE, NlPropertyType.STYLE)
    val provider = NlEnumSupportProvider(util.model)
    val enumSupport = provider(property) ?: error("No EnumSupport Found")
    assertThat(enumSupport).isInstanceOf(TextAppearanceEnumSupport::class.java)
    assertThat(enumSupport.values.size).isAtLeast(9)
  }

  @Test
  fun testCalendarMonthTextAppearance() {
    val util = SupportTestUtil(projectRule, CALENDAR_VIEW)
    val property = util.makeProperty(ANDROID_URI, "monthTextAppearance", NlPropertyType.STYLE)
    val provider = NlEnumSupportProvider(util.model)
    val enumSupport = provider(property) ?: error("No EnumSupport Found")
    assertThat(enumSupport).isInstanceOf(TextAppearanceEnumSupport::class.java)
    assertThat(enumSupport.values.size).isAtLeast(9)
  }

  @Test
  fun testFontFamily() {
    val util = SupportTestUtil(projectRule, TEXT_VIEW)
    val property = util.makeProperty(ANDROID_URI, ATTR_FONT_FAMILY, NlPropertyType.STYLE)
    val provider = NlEnumSupportProvider(util.model)
    val enumSupport = provider(property) ?: error("No EnumSupport Found")
    assertThat(enumSupport).isInstanceOf(FontEnumSupport::class.java)
    assertThat(enumSupport.values.size).isAtLeast(9)
  }

  @Test
  fun testFontFamilyFromAutoNamespace() {
    val util = SupportTestUtil(projectRule, TEXT_VIEW)
    val property = util.makeProperty(AUTO_URI, ATTR_FONT_FAMILY, NlPropertyType.STYLE)
    val provider = NlEnumSupportProvider(util.model)
    val enumSupport = provider(property) ?: error("No EnumSupport Found")
    assertThat(enumSupport).isInstanceOf(FontEnumSupport::class.java)
    assertThat(enumSupport.values.size).isAtLeast(9)
  }

  @Test
  fun testTypeface() {
    val util = SupportTestUtil(projectRule, TEXT_VIEW)
    val property = util.makeProperty(ANDROID_URI, ATTR_TYPEFACE, NlPropertyType.DIMENSION)
    val provider = NlEnumSupportProvider(util.model)
    val enumSupport = provider(property) ?: error("No EnumSupport Found")
    assertThat(enumSupport.values.map { it.value })
      .containsExactly("normal", "sans", "serif", "monospace")
      .inOrder()
  }

  @Test
  fun testTextSize() {
    checkTextSizeAttribute(ATTR_TEXT_SIZE)
  }

  @Test
  fun testLineSpacingExtra() {
    checkTextSizeAttribute(ATTR_LINE_SPACING_EXTRA)
  }

  private fun checkTextSizeAttribute(attributeName: String) {
    val util = SupportTestUtil(projectRule, TEXT_VIEW)
    val property = util.makeProperty(ANDROID_URI, attributeName, NlPropertyType.DIMENSION)
    val provider = NlEnumSupportProvider(util.model)
    val enumSupport = provider(property) ?: error("No EnumSupport Found")
    assertThat(enumSupport.values.map { it.value })
      .containsExactly(
        "8sp",
        "10sp",
        "12sp",
        "14sp",
        "16sp",
        "20sp",
        "24sp",
        "34sp",
        "48sp",
        "60sp",
        "96sp",
      )
      .inOrder()
  }

  @Test
  fun testLayoutWidth() {
    checkSizeAttribute(ATTR_LAYOUT_WIDTH)
  }

  @Test
  fun testLayoutHeight() {
    checkSizeAttribute(ATTR_LAYOUT_HEIGHT)
  }

  @Test
  fun testDropDownWidth() {
    checkSizeAttribute(ATTR_DROPDOWN_WIDTH)
  }

  @Test
  fun testDropDownHeight() {
    checkSizeAttribute(ATTR_DROPDOWN_HEIGHT)
  }

  private fun checkSizeAttribute(attributeName: String) {
    val util = SupportTestUtil(projectRule, AUTO_COMPLETE_TEXT_VIEW)
    val property = util.makeProperty(ANDROID_URI, attributeName, NlPropertyType.DIMENSION)
    val provider = NlEnumSupportProvider(util.model)
    val enumSupport = provider(property) ?: error("No EnumSupport Found")
    assertThat(enumSupport.values.map { it.value })
      .containsExactly("match_parent", WRAP_CONTENT)
      .inOrder()
  }

  @Test
  fun testOnClick() {
    val util = SupportTestUtil(projectRule, BUTTON)
    val property = util.makeProperty(ANDROID_URI, ATTR_ON_CLICK, NlPropertyType.STRING)
    val provider = NlEnumSupportProvider(util.model)
    val enumSupport = provider(property) ?: error("No EnumSupport Found")
    assertThat(enumSupport).isInstanceOf(OnClickEnumSupport::class.java)
  }

  @Test
  fun testFromAttributeDefinition() {
    val util = SupportTestUtil(projectRule, TEXT_VIEW)
    val definition = AttributeDefinition(ResourceNamespace.RES_AUTO, ATTR_VISIBILITY)
    definition.setValueMappings(mapOf("visible" to 1, "invisible" to 2, "gone" to 3))
    val property = util.makeProperty(ANDROID_URI, definition, NlPropertyType.ENUM)
    val provider = NlEnumSupportProvider(util.model)
    val enumSupport = provider(property) ?: error("No EnumSupport Found")
    assertThat(enumSupport.values.map { it.value })
      .containsExactly("visible", "invisible", "gone")
      .inOrder()
  }
}
