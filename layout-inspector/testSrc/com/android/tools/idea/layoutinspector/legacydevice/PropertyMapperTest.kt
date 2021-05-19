/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.legacydevice

import com.android.SdkConstants
import com.android.tools.adtui.workbench.PropertiesComponentMock
import com.android.tools.idea.layoutinspector.properties.DimensionUnits
import com.android.tools.idea.layoutinspector.properties.PropertiesSettings
import com.android.tools.layoutinspector.proto.LayoutInspectorProto.Property.Type
import com.android.tools.property.testing.ApplicationRule
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.util.PropertiesComponent
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class PropertyMapperTest {
  @get:Rule
  val applicationRule = ApplicationRule()

  @Before
  fun init() {
    val propertiesComponent = PropertiesComponentMock()
    applicationRule.testApplication.registerService(PropertiesComponent::class.java, propertiesComponent)
    PropertiesSettings.dimensionUnits = DimensionUnits.PIXELS
  }

  @Test
  fun testRelativeLayoutLayoutParams() {
    check("layout_mRules_leftOf", "id/textView", SdkConstants.ATTR_LAYOUT_TO_LEFT_OF, Type.RESOURCE, "@id/textView")
    check("layout_mRules_rightOf", "false/NOID", SdkConstants.ATTR_LAYOUT_TO_RIGHT_OF, Type.RESOURCE, "")
    check("layout_mRules_above", "id/textView", SdkConstants.ATTR_LAYOUT_ABOVE, Type.RESOURCE, "@id/textView")
    check("layout_mRules_below", "id/textView", SdkConstants.ATTR_LAYOUT_BELOW, Type.RESOURCE, "@id/textView")
    check("layout_mRules_alignBaseline", "id/textView", SdkConstants.ATTR_LAYOUT_ALIGN_BASELINE, Type.RESOURCE, "@id/textView")
    check("layout_mRules_alignLeft", "id/textView", SdkConstants.ATTR_LAYOUT_ALIGN_LEFT, Type.RESOURCE, "@id/textView")
    check("layout_mRules_alignTop", "id/textView", SdkConstants.ATTR_LAYOUT_ALIGN_TOP, Type.RESOURCE, "@id/textView")
    check("layout_mRules_alignRight", "id/textView", SdkConstants.ATTR_LAYOUT_ALIGN_RIGHT, Type.RESOURCE, "@id/textView")
    check("layout_mRules_alignBottom", "id/textView", SdkConstants.ATTR_LAYOUT_ALIGN_BOTTOM, Type.RESOURCE, "@id/textView")
    check("layout_mRules_alignParentLeft", "id/textView", SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_LEFT, Type.RESOURCE, "@id/textView")
    check("layout_mRules_alignParentTop", "id/textView", SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_TOP, Type.RESOURCE, "@id/textView")
    check("layout_mRules_alignParentRight", "id/textView", SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_RIGHT, Type.RESOURCE, "@id/textView")
    check("layout_mRules_alignParentBottom", "id/textView", SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_BOTTOM, Type.RESOURCE, "@id/textView")
    check("layout_mRules_center", "id/textView", SdkConstants.ATTR_LAYOUT_CENTER_IN_PARENT, Type.RESOURCE, "@id/textView")
    check("layout_mRules_centerHorizontal", "id/textView", SdkConstants.ATTR_LAYOUT_CENTER_HORIZONTAL, Type.RESOURCE, "@id/textView")
    check("layout_mRules_centerVertical", "id/textView", SdkConstants.ATTR_LAYOUT_CENTER_VERTICAL, Type.RESOURCE, "@id/textView")
    check("layout_mRules_startOf", "id/textView", SdkConstants.ATTR_LAYOUT_TO_START_OF, Type.RESOURCE, "@id/textView")
    check("layout_mRules_endOf", "id/textView", SdkConstants.ATTR_LAYOUT_TO_END_OF, Type.RESOURCE, "@id/textView")
    check("layout_mRules_alignStart", "id/textView", SdkConstants.ATTR_LAYOUT_ALIGN_START, Type.RESOURCE, "@id/textView")
    check("layout_mRules_alignEnd", "id/textView", SdkConstants.ATTR_LAYOUT_ALIGN_END, Type.RESOURCE, "@id/textView")
    check("layout_mRules_alignParentStart", "id/textView", SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_START, Type.RESOURCE, "@id/textView")
    check("layout_mRules_alignParentEnd", "id/textView", SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_END, Type.RESOURCE, "@id/textView")
  }

  @Test
  fun testMarginParams() {
    check("layout_topMargin", "0", SdkConstants.ATTR_LAYOUT_MARGIN_TOP, Type.DIMENSION, "0")
    check("layout_bottomMargin", "10", SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM, Type.DIMENSION, "10")
    check("layout_leftMargin", "0", SdkConstants.ATTR_LAYOUT_MARGIN_LEFT, Type.DIMENSION, "0")
    check("layout_rightMargin", "50", SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT, Type.DIMENSION, "50")
    check("layout_startMargin", "-2147483648", SdkConstants.ATTR_LAYOUT_MARGIN_START, Type.DIMENSION, "-2147483648")
    check("layout_endMargin", "40", SdkConstants.ATTR_LAYOUT_MARGIN_END, Type.DIMENSION, "40")
  }

  @Test
  fun testGravity() {
    check("layout_gravity", (GRAVITY_LEFT or GRAVITY_RIGHT).toString(10), SdkConstants.ATTR_LAYOUT_GRAVITY, Type.GRAVITY, "fill_horizontal")
    check("mGravity", (GRAVITY_TOP or GRAVITY_RIGHT).toString(10), SdkConstants.ATTR_GRAVITY, Type.GRAVITY, "top|right")
  }

  @Test
  fun testColor() {
    check("mCurTextColor", "0x330088", SdkConstants.ATTR_TEXT_COLOR, Type.COLOR, "#330088")
    check("bg_state_mUseColor", "-1", SdkConstants.ATTR_BACKGROUND, Type.COLOR, "#FFFFFFFF")
    check("fg_state_mUseColor", "-1979711488", SdkConstants.ATTR_FOREGROUND, Type.COLOR, "#8A000000")
  }

  @Test
  fun testLayoutDirection() {
    check("getLayoutDirection()", "RESOLVED_DIRECTION_LTR", "layoutDirection", Type.STRING, "ltr")
    check("getLayoutDirection()", "RESOLVED_DIRECTION_RTL", "layoutDirection", Type.STRING, "rtl")
    check("getLayoutDirection()", "", "layoutDirection", Type.STRING, "")
  }

  @Test
  fun testScrollBarStyle() {
    check("getScrollBarStyle()", "INSIDE_INSET", "scrollbarStyle", Type.STRING, "insideInset")
    check("getScrollBarStyle()", "INSIDE_OVERLAY", "scrollbarStyle", Type.STRING, "insideOverlay")
    check("getScrollBarStyle()", "OUTSIDE_INSET", "scrollbarStyle", Type.STRING, "outsideInset")
    check("getScrollBarStyle()", "OUTSIDE_OVERLAY", "scrollbarStyle", Type.STRING, "outsideOverlay")
    check("getScrollBarStyle()", "", "scrollbarStyle", Type.STRING, "")
  }

  @Test
  fun testTextAlignment() {
    check("getTextAlignment()", "TEXT_START", SdkConstants.ATTR_TEXT_ALIGNMENT, Type.STRING, "textStart")
    check("getTextAlignment()", "TEXT_END", SdkConstants.ATTR_TEXT_ALIGNMENT, Type.STRING, "textEnd")
    check("getTextAlignment()", "CENTER", SdkConstants.ATTR_TEXT_ALIGNMENT, Type.STRING, "center")
    check("getTextAlignment()", "VIEW_START", SdkConstants.ATTR_TEXT_ALIGNMENT, Type.STRING, "viewStart")
    check("getTextAlignment()", "VIEW_END", SdkConstants.ATTR_TEXT_ALIGNMENT, Type.STRING, "viewEnd")
    check("getTextAlignment()", "INHERIT", SdkConstants.ATTR_TEXT_ALIGNMENT, Type.STRING, "inherit")
  }

  @Test
  fun testTextDirection() {
    check("getTextDirection()", "LTR", "textDirection", Type.STRING, "ltr")
    check("getTextDirection()", "RTL", "textDirection", Type.STRING, "rtl")
    check("getTextDirection()", "ANY_RTL", "textDirection", Type.STRING, "anyRtl")
    check("getTextDirection()", "LOCALE", "textDirection", Type.STRING, "locale")
    check("getTextDirection()", "FIRST_STRONG", "textDirection", Type.STRING, "firstStrong")
    check("getTextDirection()", "FIRST_STRONG_LTR", "textDirection", Type.STRING, "firstStrongLtr")
    check("getTextDirection()", "FIRST_STRONG_RTL", "textDirection", Type.STRING, "firstStrongRtl")
    check("getTextDirection()", "INHERIT", "textDirection", Type.STRING, "inherit")
    check("getTextDirection()", "", "textDirection", Type.STRING, "")
  }

  @Test
  fun testDescendantFocusability() {
    check("getDescendantFocusability()", "FOCUS_BEFORE_DESCENDANTS", "descendantFocusability", Type.STRING, "beforeDescendants")
    check("getDescendantFocusability()", "FOCUS_AFTER_DESCENDANTS", "descendantFocusability", Type.STRING, "afterDescendants")
    check("getDescendantFocusability()", "FOCUS_BLOCK_DESCENDANTS", "descendantFocusability", Type.STRING, "blocksDescendants")
    check("getDescendantFocusability()", "", "descendantFocusability", Type.STRING, "")
  }

  @Test
  fun testEllipsize() {
    check("getEllipsize()", "START", "ellipsize", Type.STRING, "start")
    check("getEllipsize()", "MIDDLE", "ellipsize", Type.STRING, "middle")
    check("getEllipsize()", "END", "ellipsize", Type.STRING, "end")
    check("getEllipsize()", "MARQUEE", "ellipsize", Type.STRING, "marquee")
    check("getEllipsize()", "END_SMALL", "ellipsize", Type.STRING, "end")
    check("getEllipsize()", "", "ellipsize", Type.STRING, "")
  }

  @Test
  fun testPersistentDrawingCache() {
    check("getPersistentDrawingCache()", "NONE", "persistentDrawingCache", Type.STRING, "none")
    check("getPersistentDrawingCache()", "ANIMATION", "persistentDrawingCache", Type.STRING, "animation")
    check("getPersistentDrawingCache()", "SCROLLING", "persistentDrawingCache", Type.STRING, "scrolling")
    check("getPersistentDrawingCache()", "ALL", "persistentDrawingCache", Type.STRING, "all")
    check("getPersistentDrawingCache()", "", "persistentDrawingCache", Type.STRING, "")
  }

  @Test
  fun testLayoutWidthAndHeight() {
    check("layout_height", "match_parent", SdkConstants.ATTR_LAYOUT_HEIGHT, Type.DIMENSION, "match_parent")
    check("layout_height", "wrap_content", SdkConstants.ATTR_LAYOUT_HEIGHT, Type.DIMENSION, "wrap_content")
    check("layout_height", "40", SdkConstants.ATTR_LAYOUT_HEIGHT, Type.DIMENSION, "40")
    check("layout_width", "match_parent", SdkConstants.ATTR_LAYOUT_WIDTH, Type.DIMENSION, "match_parent")
    check("layout_width", "wrap_content", SdkConstants.ATTR_LAYOUT_WIDTH, Type.DIMENSION, "wrap_content")
    check("layout_width", "40", SdkConstants.ATTR_LAYOUT_WIDTH, Type.DIMENSION, "40")
  }

  @Test
  fun testLayerType() {
    check("mLayerType", "SOFTWARE", "layerType", Type.STRING, "software")
    check("mLayerType", "HARDWARE", "layerType", Type.STRING, "hardware")
    check("mLayerType", "NONE", "layerType", Type.STRING, "none")
  }

  @Test
  fun testFocusable() {
    check("getFocusable()", "NOT_FOCUSABLE", SdkConstants.ATTR_FOCUSABLE, Type.STRING, "false")
    check("getFocusable()", "FOCUSABLE", SdkConstants.ATTR_FOCUSABLE, Type.STRING, "true")
    check("getFocusable()", "FOCUSABLE_AUTO", SdkConstants.ATTR_FOCUSABLE, Type.STRING, "auto")
    check("getFocusable()", "", SdkConstants.ATTR_FOCUSABLE, Type.STRING, "")
  }

  @Test
  fun testTypefaceStyle() {
    check("getTypefaceStyle()", "NORMAL", SdkConstants.ATTR_TEXT_STYLE, Type.STRING, "normal")
    check("getTypefaceStyle()", "BOLD", SdkConstants.ATTR_TEXT_STYLE, Type.STRING, "bold")
    check("getTypefaceStyle()", "ITALIC", SdkConstants.ATTR_TEXT_STYLE, Type.STRING, "italic")
    check("getTypefaceStyle()", "BOLD_ITALIC", SdkConstants.ATTR_TEXT_STYLE, Type.STRING, "bold|italic")
    check("getTypefaceStyle()", "", SdkConstants.ATTR_TEXT_STYLE, Type.STRING, "")
  }

  @Test
  fun testOrientation() {
    check("mOrientation", "0", SdkConstants.ATTR_ORIENTATION, Type.STRING, SdkConstants.VALUE_HORIZONTAL)
    check("mOrientation", "1", SdkConstants.ATTR_ORIENTATION, Type.STRING, SdkConstants.VALUE_VERTICAL)
    check("mOrientation", "", SdkConstants.ATTR_ORIENTATION, Type.STRING, "")
  }

  private fun check(property: String, value: String, expectedAttribute: String, expectedType: Type, expectedValue: String) {
    val definition = PropertyMapper.mapPropertyName(property) ?: error("Property: $property NOT found")
    assertThat(definition.name).isEqualTo(expectedAttribute)
    assertThat(definition.type).isEqualTo(expectedType)
    assertThat(definition.value_mapper(value)).isEqualTo(expectedValue)
  }
}