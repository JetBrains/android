/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.pipeline.legacy

import com.android.SdkConstants
import com.android.tools.adtui.workbench.PropertiesComponentMock
import com.android.tools.idea.layoutinspector.properties.DimensionUnits
import com.android.tools.idea.layoutinspector.properties.PropertiesSettings
import com.android.tools.idea.testing.registerServiceInstance
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import com.android.tools.idea.layoutinspector.properties.PropertyType

class PropertyMapperTest {
  @get:Rule
  val disposableRule = DisposableRule()

  companion object {
    @JvmField
    @ClassRule
    val rule = ApplicationRule()
  }

  @Before
  fun init() {
    val application = ApplicationManager.getApplication()
    application.registerServiceInstance(PropertiesComponent::class.java, PropertiesComponentMock(), disposableRule.disposable)
    PropertiesSettings.dimensionUnits = DimensionUnits.PIXELS
  }

  @Test
  fun testRelativeLayoutLayoutParams() {
    check("layout_mRules_leftOf", "id/textView", SdkConstants.ATTR_LAYOUT_TO_LEFT_OF, PropertyType.RESOURCE, "@id/textView")
    check("layout_mRules_rightOf", "false/NOID", SdkConstants.ATTR_LAYOUT_TO_RIGHT_OF, PropertyType.RESOURCE, "")
    check("layout_mRules_above", "id/textView", SdkConstants.ATTR_LAYOUT_ABOVE, PropertyType.RESOURCE, "@id/textView")
    check("layout_mRules_below", "id/textView", SdkConstants.ATTR_LAYOUT_BELOW, PropertyType.RESOURCE, "@id/textView")
    check("layout_mRules_alignBaseline", "id/textView", SdkConstants.ATTR_LAYOUT_ALIGN_BASELINE, PropertyType.RESOURCE, "@id/textView")
    check("layout_mRules_alignLeft", "id/textView", SdkConstants.ATTR_LAYOUT_ALIGN_LEFT, PropertyType.RESOURCE, "@id/textView")
    check("layout_mRules_alignTop", "id/textView", SdkConstants.ATTR_LAYOUT_ALIGN_TOP, PropertyType.RESOURCE, "@id/textView")
    check("layout_mRules_alignRight", "id/textView", SdkConstants.ATTR_LAYOUT_ALIGN_RIGHT, PropertyType.RESOURCE, "@id/textView")
    check("layout_mRules_alignBottom", "id/textView", SdkConstants.ATTR_LAYOUT_ALIGN_BOTTOM, PropertyType.RESOURCE, "@id/textView")
    check("layout_mRules_alignParentLeft", "id/textView", SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_LEFT, PropertyType.RESOURCE, "@id/textView")
    check("layout_mRules_alignParentTop", "id/textView", SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_TOP, PropertyType.RESOURCE, "@id/textView")
    check("layout_mRules_alignParentRight", "id/textView", SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_RIGHT, PropertyType.RESOURCE, "@id/textView")
    check("layout_mRules_alignParentBottom", "id/textView", SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_BOTTOM, PropertyType.RESOURCE, "@id/textView")
    check("layout_mRules_center", "id/textView", SdkConstants.ATTR_LAYOUT_CENTER_IN_PARENT, PropertyType.RESOURCE, "@id/textView")
    check("layout_mRules_centerHorizontal", "id/textView", SdkConstants.ATTR_LAYOUT_CENTER_HORIZONTAL, PropertyType.RESOURCE, "@id/textView")
    check("layout_mRules_centerVertical", "id/textView", SdkConstants.ATTR_LAYOUT_CENTER_VERTICAL, PropertyType.RESOURCE, "@id/textView")
    check("layout_mRules_startOf", "id/textView", SdkConstants.ATTR_LAYOUT_TO_START_OF, PropertyType.RESOURCE, "@id/textView")
    check("layout_mRules_endOf", "id/textView", SdkConstants.ATTR_LAYOUT_TO_END_OF, PropertyType.RESOURCE, "@id/textView")
    check("layout_mRules_alignStart", "id/textView", SdkConstants.ATTR_LAYOUT_ALIGN_START, PropertyType.RESOURCE, "@id/textView")
    check("layout_mRules_alignEnd", "id/textView", SdkConstants.ATTR_LAYOUT_ALIGN_END, PropertyType.RESOURCE, "@id/textView")
    check("layout_mRules_alignParentStart", "id/textView", SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_START, PropertyType.RESOURCE, "@id/textView")
    check("layout_mRules_alignParentEnd", "id/textView", SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_END, PropertyType.RESOURCE, "@id/textView")
  }

  @Test
  fun testMarginParams() {
    check("layout_topMargin", "0", SdkConstants.ATTR_LAYOUT_MARGIN_TOP, PropertyType.DIMENSION, "0")
    check("layout_bottomMargin", "10", SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM, PropertyType.DIMENSION, "10")
    check("layout_leftMargin", "0", SdkConstants.ATTR_LAYOUT_MARGIN_LEFT, PropertyType.DIMENSION, "0")
    check("layout_rightMargin", "50", SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT, PropertyType.DIMENSION, "50")
    check("layout_startMargin", "-2147483648", SdkConstants.ATTR_LAYOUT_MARGIN_START, PropertyType.DIMENSION, "-2147483648")
    check("layout_endMargin", "40", SdkConstants.ATTR_LAYOUT_MARGIN_END, PropertyType.DIMENSION, "40")
  }

  @Test
  fun testGravity() {
    check("layout_gravity", (GRAVITY_LEFT or GRAVITY_RIGHT).toString(10), SdkConstants.ATTR_LAYOUT_GRAVITY, PropertyType.GRAVITY, "fill_horizontal")
    check("mGravity", (GRAVITY_TOP or GRAVITY_RIGHT).toString(10), SdkConstants.ATTR_GRAVITY, PropertyType.GRAVITY, "top|right")
  }

  @Test
  fun testColor() {
    check("mCurTextColor", "0x330088", SdkConstants.ATTR_TEXT_COLOR, PropertyType.COLOR, "#330088")
    check("bg_state_mUseColor", "-1", SdkConstants.ATTR_BACKGROUND, PropertyType.COLOR, "#FFFFFFFF")
    check("fg_state_mUseColor", "-1979711488", SdkConstants.ATTR_FOREGROUND, PropertyType.COLOR, "#8A000000")
  }

  @Test
  fun testLayoutDirection() {
    check("getLayoutDirection()", "RESOLVED_DIRECTION_LTR", "layoutDirection", PropertyType.STRING, "ltr")
    check("getLayoutDirection()", "RESOLVED_DIRECTION_RTL", "layoutDirection", PropertyType.STRING, "rtl")
    check("getLayoutDirection()", "", "layoutDirection", PropertyType.STRING, "")
  }

  @Test
  fun testScrollBarStyle() {
    check("getScrollBarStyle()", "INSIDE_INSET", "scrollbarStyle", PropertyType.STRING, "insideInset")
    check("getScrollBarStyle()", "INSIDE_OVERLAY", "scrollbarStyle", PropertyType.STRING, "insideOverlay")
    check("getScrollBarStyle()", "OUTSIDE_INSET", "scrollbarStyle", PropertyType.STRING, "outsideInset")
    check("getScrollBarStyle()", "OUTSIDE_OVERLAY", "scrollbarStyle", PropertyType.STRING, "outsideOverlay")
    check("getScrollBarStyle()", "", "scrollbarStyle", PropertyType.STRING, "")
  }

  @Test
  fun testTextAlignment() {
    check("getTextAlignment()", "TEXT_START", SdkConstants.ATTR_TEXT_ALIGNMENT, PropertyType.STRING, "textStart")
    check("getTextAlignment()", "TEXT_END", SdkConstants.ATTR_TEXT_ALIGNMENT, PropertyType.STRING, "textEnd")
    check("getTextAlignment()", "CENTER", SdkConstants.ATTR_TEXT_ALIGNMENT, PropertyType.STRING, "center")
    check("getTextAlignment()", "VIEW_START", SdkConstants.ATTR_TEXT_ALIGNMENT, PropertyType.STRING, "viewStart")
    check("getTextAlignment()", "VIEW_END", SdkConstants.ATTR_TEXT_ALIGNMENT, PropertyType.STRING, "viewEnd")
    check("getTextAlignment()", "INHERIT", SdkConstants.ATTR_TEXT_ALIGNMENT, PropertyType.STRING, "inherit")
  }

  @Test
  fun testTextDirection() {
    check("getTextDirection()", "LTR", "textDirection", PropertyType.STRING, "ltr")
    check("getTextDirection()", "RTL", "textDirection", PropertyType.STRING, "rtl")
    check("getTextDirection()", "ANY_RTL", "textDirection", PropertyType.STRING, "anyRtl")
    check("getTextDirection()", "LOCALE", "textDirection", PropertyType.STRING, "locale")
    check("getTextDirection()", "FIRST_STRONG", "textDirection", PropertyType.STRING, "firstStrong")
    check("getTextDirection()", "FIRST_STRONG_LTR", "textDirection", PropertyType.STRING, "firstStrongLtr")
    check("getTextDirection()", "FIRST_STRONG_RTL", "textDirection", PropertyType.STRING, "firstStrongRtl")
    check("getTextDirection()", "INHERIT", "textDirection", PropertyType.STRING, "inherit")
    check("getTextDirection()", "", "textDirection", PropertyType.STRING, "")
  }

  @Test
  fun testDescendantFocusability() {
    check("getDescendantFocusability()", "FOCUS_BEFORE_DESCENDANTS", "descendantFocusability", PropertyType.STRING, "beforeDescendants")
    check("getDescendantFocusability()", "FOCUS_AFTER_DESCENDANTS", "descendantFocusability", PropertyType.STRING, "afterDescendants")
    check("getDescendantFocusability()", "FOCUS_BLOCK_DESCENDANTS", "descendantFocusability", PropertyType.STRING, "blocksDescendants")
    check("getDescendantFocusability()", "", "descendantFocusability", PropertyType.STRING, "")
  }

  @Test
  fun testEllipsize() {
    check("getEllipsize()", "START", "ellipsize", PropertyType.STRING, "start")
    check("getEllipsize()", "MIDDLE", "ellipsize", PropertyType.STRING, "middle")
    check("getEllipsize()", "END", "ellipsize", PropertyType.STRING, "end")
    check("getEllipsize()", "MARQUEE", "ellipsize", PropertyType.STRING, "marquee")
    check("getEllipsize()", "END_SMALL", "ellipsize", PropertyType.STRING, "end")
    check("getEllipsize()", "", "ellipsize", PropertyType.STRING, "")
  }

  @Test
  fun testPersistentDrawingCache() {
    check("getPersistentDrawingCache()", "NONE", "persistentDrawingCache", PropertyType.STRING, "none")
    check("getPersistentDrawingCache()", "ANIMATION", "persistentDrawingCache", PropertyType.STRING, "animation")
    check("getPersistentDrawingCache()", "SCROLLING", "persistentDrawingCache", PropertyType.STRING, "scrolling")
    check("getPersistentDrawingCache()", "ALL", "persistentDrawingCache", PropertyType.STRING, "all")
    check("getPersistentDrawingCache()", "", "persistentDrawingCache", PropertyType.STRING, "")
  }

  @Test
  fun testLayoutWidthAndHeight() {
    check("layout_height", "match_parent", SdkConstants.ATTR_LAYOUT_HEIGHT, PropertyType.DIMENSION, "match_parent")
    check("layout_height", "wrap_content", SdkConstants.ATTR_LAYOUT_HEIGHT, PropertyType.DIMENSION, "wrap_content")
    check("layout_height", "40", SdkConstants.ATTR_LAYOUT_HEIGHT, PropertyType.DIMENSION, "40")
    check("layout_width", "match_parent", SdkConstants.ATTR_LAYOUT_WIDTH, PropertyType.DIMENSION, "match_parent")
    check("layout_width", "wrap_content", SdkConstants.ATTR_LAYOUT_WIDTH, PropertyType.DIMENSION, "wrap_content")
    check("layout_width", "40", SdkConstants.ATTR_LAYOUT_WIDTH, PropertyType.DIMENSION, "40")
  }

  @Test
  fun testLayerType() {
    check("mLayerType", "SOFTWARE", "layerType", PropertyType.STRING, "software")
    check("mLayerType", "HARDWARE", "layerType", PropertyType.STRING, "hardware")
    check("mLayerType", "NONE", "layerType", PropertyType.STRING, "none")
  }

  @Test
  fun testFocusable() {
    check("getFocusable()", "NOT_FOCUSABLE", SdkConstants.ATTR_FOCUSABLE, PropertyType.STRING, "false")
    check("getFocusable()", "FOCUSABLE", SdkConstants.ATTR_FOCUSABLE, PropertyType.STRING, "true")
    check("getFocusable()", "FOCUSABLE_AUTO", SdkConstants.ATTR_FOCUSABLE, PropertyType.STRING, "auto")
    check("getFocusable()", "", SdkConstants.ATTR_FOCUSABLE, PropertyType.STRING, "")
  }

  @Test
  fun testTypefaceStyle() {
    check("getTypefaceStyle()", "NORMAL", SdkConstants.ATTR_TEXT_STYLE, PropertyType.STRING, "normal")
    check("getTypefaceStyle()", "BOLD", SdkConstants.ATTR_TEXT_STYLE, PropertyType.STRING, "bold")
    check("getTypefaceStyle()", "ITALIC", SdkConstants.ATTR_TEXT_STYLE, PropertyType.STRING, "italic")
    check("getTypefaceStyle()", "BOLD_ITALIC", SdkConstants.ATTR_TEXT_STYLE, PropertyType.STRING, "bold|italic")
    check("getTypefaceStyle()", "", SdkConstants.ATTR_TEXT_STYLE, PropertyType.STRING, "")
  }

  @Test
  fun testOrientation() {
    check("mOrientation", "0", SdkConstants.ATTR_ORIENTATION, PropertyType.STRING, SdkConstants.VALUE_HORIZONTAL)
    check("mOrientation", "1", SdkConstants.ATTR_ORIENTATION, PropertyType.STRING, SdkConstants.VALUE_VERTICAL)
    check("mOrientation", "", SdkConstants.ATTR_ORIENTATION, PropertyType.STRING, "")
  }

  private fun check(property: String, value: String, expectedAttribute: String, expectedType: PropertyType, expectedValue: String) {
    val definition = PropertyMapper.mapPropertyName(property) ?: error("Property: $property NOT found")
    assertThat(definition.name).isEqualTo(expectedAttribute)
    assertThat(definition.type).isEqualTo(expectedType)
    assertThat(definition.value_mapper(value)).isEqualTo(expectedValue)
  }
}