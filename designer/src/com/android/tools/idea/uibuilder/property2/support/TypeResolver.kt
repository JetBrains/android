/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.SdkConstants
import com.android.tools.idea.uibuilder.property2.NelePropertyType
import org.jetbrains.android.dom.attrs.AttributeDefinition
import com.android.ide.common.rendering.api.AttributeFormat
import com.android.resources.ResourceType
import org.jetbrains.android.dom.AndroidDomUtil
import org.jetbrains.android.dom.navigation.NavigationSchema

/**
 * Temporary type resolver.
 *
 * Eventually we want the library and framework to specify the correct type for each attribute.
 * This is the fallback if this information is not available.
 */
object TypeResolver {

  fun resolveType(name: String, attribute: AttributeDefinition?): NelePropertyType {
    return lookupByName(name)
           ?: bySpecialType(name)
           ?: fromAttributeDefinition(attribute)
           ?: fallbackByName(name)
  }

  private fun bySpecialType(name: String): NelePropertyType? {
    val types = AndroidDomUtil.getSpecialResourceTypes(name)
    for (type in types) {
      when (type) {
        ResourceType.ID -> return NelePropertyType.ID
        //TODO: expand in a followup CL
        else -> {}
      }
    }
    return null
  }

  private fun fromAttributeDefinition(attribute: AttributeDefinition?): NelePropertyType? {
    if (attribute == null) return null
    var subType: NelePropertyType? = null

    for (format in attribute.formats) {
      when (format) {
        AttributeFormat.BOOLEAN -> return NelePropertyType.THREE_STATE_BOOLEAN
        AttributeFormat.COLOR -> return NelePropertyType.COLOR
        AttributeFormat.DIMENSION -> return NelePropertyType.DIMENSION
        AttributeFormat.FLOAT -> return NelePropertyType.FLOAT
        AttributeFormat.FRACTION -> return NelePropertyType.FRACTION
        AttributeFormat.INTEGER -> return NelePropertyType.INTEGER
        AttributeFormat.STRING -> return NelePropertyType.STRING
        AttributeFormat.FLAGS -> return NelePropertyType.FLAGS
        AttributeFormat.ENUM -> subType = NelePropertyType.ENUM
        else -> {}
      }
    }
    return subType
  }

  private fun lookupByName(name: String) =
    when (name) {
      SdkConstants.ATTR_ITEM_SHAPE_APPEARANCE,
      SdkConstants.ATTR_ITEM_SHAPE_APPEARANCE_OVERLAY,
      SdkConstants.ATTR_THEME,
      SdkConstants.ATTR_POPUP_THEME,
      SdkConstants.ATTR_SHAPE_APPEARANCE,
      SdkConstants.ATTR_SHAPE_APPEARANCE_OVERLAY,
      SdkConstants.ATTR_STYLE -> NelePropertyType.STYLE

      SdkConstants.ATTR_CLASS -> NelePropertyType.FRAGMENT

      SdkConstants.ATTR_COMPLETION_HINT_VIEW,
      SdkConstants.ATTR_LAYOUT,
      SdkConstants.ATTR_SHOW_IN -> NelePropertyType.LAYOUT

      SdkConstants.ATTR_FONT_FAMILY -> NelePropertyType.FONT

      SdkConstants.ATTR_CONTENT,
      SdkConstants.ATTR_DROP_DOWN_ANCHOR,
      SdkConstants.ATTR_HANDLE,
      SdkConstants.ATTR_LAYOUT_CONSTRAINT_CIRCLE,
      SdkConstants.ATTR_LAYOUT_CONSTRAINTSET,
      SdkConstants.ATTR_LIFT_ON_SCROLL_TARGET_VIEW_ID,
      SdkConstants.ATTR_MOTION_TARGET_ID,
      SdkConstants.ATTR_MOTION_TOUCH_ANCHOR_ID,
      SdkConstants.ATTR_MOTION_TOUCH_REGION_ID,
      SdkConstants.ATTR_NEXT_CLUSTER_FORWARD,
      SdkConstants.ATTR_NEXT_FOCUS_DOWN,
      SdkConstants.ATTR_NEXT_FOCUS_FORWARD,
      SdkConstants.ATTR_NEXT_FOCUS_LEFT,
      SdkConstants.ATTR_NEXT_FOCUS_RIGHT,
      SdkConstants.ATTR_NEXT_FOCUS_UP,
      SdkConstants.ATTR_TOOLBAR_ID -> NelePropertyType.ID

      SdkConstants.ATTR_EDITOR_EXTRAS -> NelePropertyType.ID // TODO: Support <input-extras> as resource type?

      SdkConstants.ATTR_BACKGROUND,
      SdkConstants.ATTR_BUTTON,
      SdkConstants.ATTR_CHECK_MARK,
      SdkConstants.ATTR_CHILD_DIVIDER,
      SdkConstants.ATTR_COLLAPSE_ICON,
      SdkConstants.ATTR_CONTENT_SCRIM,
      SdkConstants.ATTR_DIAL,
      SdkConstants.ATTR_DIVIDER,
      SdkConstants.ATTR_DRAWABLE_BOTTOM,
      SdkConstants.ATTR_DRAWABLE_BOTTOM_COMPAT,
      SdkConstants.ATTR_DRAWABLE_END,
      SdkConstants.ATTR_DRAWABLE_END_COMPAT,
      SdkConstants.ATTR_DRAWABLE_LEFT,
      SdkConstants.ATTR_DRAWABLE_LEFT_COMPAT,
      SdkConstants.ATTR_DRAWABLE_RIGHT,
      SdkConstants.ATTR_DRAWABLE_RIGHT_COMPAT,
      SdkConstants.ATTR_DRAWABLE_START,
      SdkConstants.ATTR_DRAWABLE_START_COMPAT,
      SdkConstants.ATTR_DRAWABLE_TOP,
      SdkConstants.ATTR_DRAWABLE_TOP_COMPAT,
      SdkConstants.ATTR_DROPDOWN_SELECTOR,
      SdkConstants.ATTR_FOREGROUND,
      SdkConstants.ATTR_HAND_HOUR,
      SdkConstants.ATTR_HAND_MINUTE,
      SdkConstants.ATTR_HEADER_BACKGROUND,
      SdkConstants.ATTR_INSET_BACKGROUND,
      SdkConstants.ATTR_INSET_FOREGROUND,
      SdkConstants.ATTR_ITEM_BACKGROUND,
      SdkConstants.ATTR_ITEM_ICON_TINT,
      SdkConstants.ATTR_LIST_SELECTOR,
      SdkConstants.ATTR_LOGO,
      SdkConstants.ATTR_OVER_SCROLL_FOOTER,
      SdkConstants.ATTR_OVER_SCROLL_HEADER,
      SdkConstants.ATTR_POPUP_BACKGROUND,
      SdkConstants.ATTR_QUERY_BACKGROUND,
      SdkConstants.ATTR_NAVIGATION_ICON,
      SdkConstants.ATTR_SELECTED_DATE_VERTICAL_BAR,
      SdkConstants.ATTR_STATUS_BAR_FOREGROUND,
      SdkConstants.ATTR_SUBMIT_BACKGROUND,
      SdkConstants.ATTR_SRC,
      SdkConstants.ATTR_SRC_COMPAT,
      SdkConstants.ATTR_TAB_BACKGROUND,
      SdkConstants.ATTR_TAB_ICON_TINT,
      SdkConstants.ATTR_TAB_STRIP_LEFT,
      SdkConstants.ATTR_TAB_STRIP_RIGHT,
      SdkConstants.ATTR_THUMB,
      SdkConstants.ATTR_TICK_MARK,
      SdkConstants.ATTR_TRACK,
      SdkConstants.ATTR_SCROLLBAR_THUMB_HORIZONTAL,
      SdkConstants.ATTR_SCROLLBAR_THUMB_VERTICAL,
      SdkConstants.ATTR_SCROLLBAR_TRACK_HORIZONTAL,
      SdkConstants.ATTR_SCROLLBAR_TRACK_VERTICAL,
      SdkConstants.ATTR_STATUS_BAR_SCRIM -> NelePropertyType.DRAWABLE

      SdkConstants.ATTR_IN_ANIMATION,
      SdkConstants.ATTR_OUT_ANIMATION,
      SdkConstants.ATTR_SHOW_MOTION_SPEC,
      SdkConstants.ATTR_HIDE_MOTION_SPEC,

      SdkConstants.ATTR_LAYOUT_ANIMATION,
      NavigationSchema.ATTR_ENTER_ANIM,
      NavigationSchema.ATTR_EXIT_ANIM,
      NavigationSchema.ATTR_POP_ENTER_ANIM,
      NavigationSchema.ATTR_POP_EXIT_ANIM -> NelePropertyType.ANIM

      SdkConstants.ATTR_STATE_LIST_ANIMATOR -> NelePropertyType.ANIMATOR

      SdkConstants.ATTR_AM_PM_BACKGROUND_COLOR,
      SdkConstants.ATTR_AM_PM_TEXT_COLOR,
      SdkConstants.ATTR_BACKGROUND_TINT,
      SdkConstants.ATTR_BUTTON_TINT,
      SdkConstants.ATTR_CHECK_MARK_TINT,
      SdkConstants.ATTR_CHIP_BACKGROUND_COLOR,
      SdkConstants.ATTR_CHIP_ICON_TINT,
      SdkConstants.ATTR_CHIP_STROKE_COLOR,
      SdkConstants.ATTR_CHIP_SURFACE_COLOR,
      SdkConstants.ATTR_CLOSE_ICON_TINT,
      SdkConstants.ATTR_DRAWABLE_TINT,
      SdkConstants.ATTR_END_ICON_TINT,
      SdkConstants.ATTR_ERROR_ICON_TINT,
      SdkConstants.ATTR_ERROR_TEXT_COLOR,
      SdkConstants.ATTR_FOREGROUND_TINT,
      SdkConstants.ATTR_HELPER_TEXT_TEXT_COLOR,
      SdkConstants.ATTR_HINT_TEXT_COLOR,
      SdkConstants.ATTR_ICON_TINT,
      SdkConstants.ATTR_INDETERMINATE_TINT,
      SdkConstants.ATTR_ITEM_RIPPLE_COLOR,
      SdkConstants.ATTR_ITEM_SHAPE_FILL_COLOR,
      SdkConstants.ATTR_ITEM_TEXT_COLOR,
      SdkConstants.ATTR_NUMBERS_INNER_TEXT_COLOR,
      SdkConstants.ATTR_NUMBERS_SELECTOR_COLOR,
      SdkConstants.ATTR_NUMBERS_TEXT_COLOR,
      SdkConstants.ATTR_PASSWORD_TOGGLE_TINT,
      SdkConstants.ATTR_PROGRESS_TINT,
      SdkConstants.ATTR_PROGRESS_BACKGROUND_TINT,
      SdkConstants.ATTR_RIPPLE_COLOR,
      SdkConstants.ATTR_SECONDARY_PROGRESS_TINT,
      SdkConstants.ATTR_START_ICON_TINT,
      SdkConstants.ATTR_STROKE_COLOR,
      SdkConstants.ATTR_TAB_TEXT_COLOR,
      SdkConstants.ATTR_TEXT_COLOR,
      SdkConstants.ATTR_TEXT_COLOR_HINT,
      SdkConstants.ATTR_TEXT_COLOR_LINK,
      SdkConstants.ATTR_THUMB_TINT,
      SdkConstants.ATTR_TICK_MARK_TINT,
      SdkConstants.ATTR_TINT,
      SdkConstants.ATTR_TRACK_TINT,
      "boxStrokeErrorColor",
      "checkedIconTint",
      "haloColor",
      "placeholderTextColor",
      "prefixTextColor",
      "suffixTextColor",
      "thumbColor",
      "tickColor",
      "tickColorActive",
      "tickColorInactive",
      "trackColor",
      "trackColorActive",
      "trackColorInactive" -> NelePropertyType.COLOR_STATE_LIST

      "values",  // actually an array of float for com.google.android.material.slider.RangeSlider
      SdkConstants.ATTR_AUTO_SIZE_PRESET_SIZES -> NelePropertyType.ARRAY

      SdkConstants.ATTR_INTERPOLATOR,
      SdkConstants.ATTR_LAYOUT_SCROLL_INTERPOLATOR -> NelePropertyType.INTERPOLATOR

      SdkConstants.ATTR_ENTRIES -> NelePropertyType.STRING_ARRAY

      SdkConstants.ATTR_IGNORE_GRAVITY -> NelePropertyType.THREE_STATE_BOOLEAN

      "value",
      "valueFrom",
      "valueTo" -> NelePropertyType.FLOAT

      SdkConstants.LAYOUT_CONSTRAINT_GUIDE_PERCENT,
      SdkConstants.ATTR_LAYOUT_WIDTH_PERCENT,
      SdkConstants.ATTR_LAYOUT_HEIGHT_PERCENT,
      SdkConstants.ATTR_LAYOUT_HORIZONTAL_BIAS,
      SdkConstants.ATTR_LAYOUT_VERTICAL_BIAS -> NelePropertyType.FRACTION

      SdkConstants.ATTR_ELEVATION -> NelePropertyType.DIMENSION

      SdkConstants.ATTR_MENU -> NelePropertyType.MENU

      // tools
      // TODO: Figure out a way to map this using ToolsAttributeUtil
      SdkConstants.ATTR_ITEM_COUNT -> NelePropertyType.INTEGER
      SdkConstants.ATTR_ACTION_BAR_NAV_MODE -> NelePropertyType.ENUM

      SdkConstants.ATTR_LISTFOOTER,
      SdkConstants.ATTR_LISTHEADER,
      SdkConstants.ATTR_LISTITEM -> NelePropertyType.LAYOUT

      SdkConstants.ATTR_GRAPH,
      SdkConstants.ATTR_NAV_GRAPH -> NelePropertyType.NAVIGATION

      NavigationSchema.ATTR_DESTINATION,
      SdkConstants.ATTR_START_DESTINATION,
      NavigationSchema.ATTR_POP_UP_TO -> NelePropertyType.DESTINATION

      SdkConstants.ATTR_NAME -> NelePropertyType.CLASS_NAME

      SdkConstants.ATTR_CONSTRAINT_LAYOUT_DESCRIPTION -> NelePropertyType.XML

      SdkConstants.ATTR_MOTION_TARGET -> NelePropertyType.ID_OR_STRING

      SdkConstants.ATTR_MOTION_WAVE_OFFSET -> NelePropertyType.DIMENSION_UNIT_LESS

      else -> null
    }

  private fun fallbackByName(name: String): NelePropertyType {
    val parts = split(name)
    val last = parts.last()
    val secondLast = if (parts.size > 1) parts.elementAt(parts.size - 2) else ""
    val thirdLast = if (parts.size > 2) parts.elementAt(parts.size - 3) else ""
    val forthLast = if (parts.size > 3) parts.elementAt(parts.size - 4) else ""
    when (last) {
      "drawable",
      "icon",
      "indicator" ->
        return NelePropertyType.DRAWABLE
      "color" ->
        return if (secondLast == "text") return NelePropertyType.COLOR_STATE_LIST else NelePropertyType.COLOR
      "appearance" ->
        if (secondLast == "text") return NelePropertyType.TEXT_APPEARANCE
      "handle" ->
        if (thirdLast == "text" && secondLast == "select") return NelePropertyType.DRAWABLE
      "layout" ->
        return NelePropertyType.LAYOUT
      "spec" ->
        if (secondLast == "motion") return NelePropertyType.ANIMATOR
      "style" ->
        return NelePropertyType.STYLE
      else -> {
        if (thirdLast == "text" && secondLast == "appearance") return NelePropertyType.TEXT_APPEARANCE
        if (forthLast == "text" && thirdLast == "select" && secondLast == "handle") return NelePropertyType.DRAWABLE
      }
    }
    return NelePropertyType.STRING
  }

  private fun split(name: String): Set<String> {
    val parts = mutableSetOf<String>()
    var part = name
    while (part.isNotEmpty()) {
      val index = part.indexOfFirst { it.isUpperCase() }
      if (index > 0) {
        parts.add(part.substring(0, index))
      }
      else if (index < 0) {
        parts.add(part)
      }
      part = if (index >= 0) part[index].toLowerCase().toString() + part.substring(index + 1) else ""
    }
    return parts
  }
}
