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
package com.android.tools.idea.uibuilder.property.support

import com.android.SdkConstants
import com.android.AndroidXConstants.PreferenceAndroidX
import com.android.SdkConstants.PreferenceAttributes
import com.android.SdkConstants.PreferenceClasses
import com.android.ide.common.rendering.api.AttributeFormat
import com.android.resources.ResourceType
import com.android.tools.idea.uibuilder.property.NlPropertyType
import com.intellij.psi.PsiClass
import org.jetbrains.android.dom.AndroidDomUtil
import org.jetbrains.android.dom.attrs.AttributeDefinition
import org.jetbrains.android.dom.navigation.NavigationSchema

/**
 * Temporary type resolver.
 *
 * Eventually we want the library and framework to specify the correct type for each attribute.
 * This is the fallback if this information is not available.
 */
object TypeResolver {

  fun resolveType(name: String, attribute: AttributeDefinition?, componentClass: PsiClass?): NlPropertyType {
    return lookupByName(name, componentClass)
           ?: bySpecialType(name)
           ?: fromAttributeDefinition(attribute)
           ?: fallbackByName(name)
  }

  private fun bySpecialType(name: String): NlPropertyType? {
    val types = AndroidDomUtil.getSpecialResourceTypes(name)
    for (type in types) {
      when (type) {
        ResourceType.ID -> return NlPropertyType.ID
        //TODO: expand in a followup CL
        else -> {}
      }
    }
    return null
  }

  private fun fromAttributeDefinition(attribute: AttributeDefinition?): NlPropertyType? {
    if (attribute == null) return null
    var subType: NlPropertyType? = null

    for (format in attribute.formats) {
      when (format) {
        AttributeFormat.BOOLEAN -> return NlPropertyType.THREE_STATE_BOOLEAN
        AttributeFormat.COLOR -> return NlPropertyType.COLOR
        AttributeFormat.DIMENSION -> return NlPropertyType.DIMENSION
        AttributeFormat.FLOAT -> return NlPropertyType.FLOAT
        AttributeFormat.FRACTION -> return NlPropertyType.FRACTION
        AttributeFormat.INTEGER -> return NlPropertyType.INTEGER
        AttributeFormat.STRING -> return NlPropertyType.STRING
        AttributeFormat.FLAGS -> return NlPropertyType.FLAGS
        AttributeFormat.ENUM -> subType = NlPropertyType.ENUM
        else -> {}
      }
    }
    return subType
  }

  private fun lookupByName(name: String, componentClass: PsiClass?) =
    when (name) {
      SdkConstants.ATTR_ITEM_SHAPE_APPEARANCE,
      SdkConstants.ATTR_ITEM_SHAPE_APPEARANCE_OVERLAY,
      SdkConstants.ATTR_THEME,
      SdkConstants.ATTR_POPUP_THEME,
      SdkConstants.ATTR_SHAPE_APPEARANCE,
      SdkConstants.ATTR_SHAPE_APPEARANCE_OVERLAY,
      SdkConstants.ATTR_STYLE,
      "lineBreakStyle",
      "lineBreakWordStyle" -> NlPropertyType.STYLE

      SdkConstants.ATTR_CLASS -> NlPropertyType.FRAGMENT

      SdkConstants.ATTR_COMPLETION_HINT_VIEW,
      SdkConstants.ATTR_LAYOUT,
      SdkConstants.ATTR_SHOW_IN -> NlPropertyType.LAYOUT

      SdkConstants.ATTR_FONT_FAMILY -> NlPropertyType.FONT

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
      SdkConstants.ATTR_TOOLBAR_ID -> NlPropertyType.ID

      SdkConstants.ATTR_EDITOR_EXTRAS -> NlPropertyType.ID // TODO: Support <input-extras> as resource type?

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
      SdkConstants.ATTR_HAND_SECOND,
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
      SdkConstants.ATTR_STATUS_BAR_SCRIM -> NlPropertyType.DRAWABLE

      SdkConstants.ATTR_IN_ANIMATION,
      SdkConstants.ATTR_OUT_ANIMATION,
      SdkConstants.ATTR_SHOW_MOTION_SPEC,
      SdkConstants.ATTR_HIDE_MOTION_SPEC,

      SdkConstants.ATTR_LAYOUT_ANIMATION,
      NavigationSchema.ATTR_ENTER_ANIM,
      NavigationSchema.ATTR_EXIT_ANIM,
      NavigationSchema.ATTR_POP_ENTER_ANIM,
      NavigationSchema.ATTR_POP_EXIT_ANIM -> NlPropertyType.ANIM

      SdkConstants.ATTR_STATE_LIST_ANIMATOR -> NlPropertyType.ANIMATOR

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
      "collapsedTitleTextColor",
      "dividerColor",
      "expandedTitleTextColor",
      "haloColor",
      "placeholderTextColor",
      "prefixTextColor",
      "subheaderColor",
      "suffixTextColor",
      "thumbColor",
      "thumbStrokeColor",
      "tickColor",
      "tickColorActive",
      "tickColorInactive",
      "dialTint",
      "hand_hourTint",
      "hand_minuteTint",
      "hand_secondTint",
      "trackColor",
      "trackColorActive",
      "trackColorInactive" -> NlPropertyType.COLOR_STATE_LIST

      "values",  // actually an array of float for com.google.android.material.slider.RangeSlider
      SdkConstants.ATTR_AUTO_SIZE_PRESET_SIZES -> NlPropertyType.ARRAY

      "titlePositionInterpolator",
      SdkConstants.ATTR_INTERPOLATOR,
      SdkConstants.ATTR_LAYOUT_SCROLL_INTERPOLATOR -> NlPropertyType.INTERPOLATOR

      PreferenceAttributes.ATTR_ENTRY_VALUES,
      PreferenceAttributes.ATTR_ENTRIES -> NlPropertyType.STRING_ARRAY

      SdkConstants.ATTR_IGNORE_GRAVITY -> NlPropertyType.THREE_STATE_BOOLEAN

      "value",
      "valueFrom",
      "valueTo" -> NlPropertyType.FLOAT

      SdkConstants.LAYOUT_CONSTRAINT_GUIDE_PERCENT,
      SdkConstants.ATTR_LAYOUT_WIDTH_PERCENT,
      SdkConstants.ATTR_LAYOUT_HEIGHT_PERCENT,
      SdkConstants.ATTR_LAYOUT_HORIZONTAL_BIAS,
      SdkConstants.ATTR_LAYOUT_VERTICAL_BIAS -> NlPropertyType.FRACTION

      "contentPadding",
      "contentPaddingBottom",
      "contentPaddingLeft",
      "contentPaddingRight",
      "contentPaddingTop",
      SdkConstants.ATTR_ELEVATION -> NlPropertyType.DIMENSION

      SdkConstants.ATTR_MENU -> NlPropertyType.MENU

      SdkConstants.ATTR_BACKGROUND_TINT_MODE -> NlPropertyType.ENUM

      // tools
      // TODO: Figure out a way to map this using ToolsAttributeUtil
      SdkConstants.ATTR_ITEM_COUNT -> NlPropertyType.INTEGER
      SdkConstants.ATTR_ACTION_BAR_NAV_MODE -> NlPropertyType.ENUM

      SdkConstants.ATTR_LISTFOOTER,
      SdkConstants.ATTR_LISTHEADER,
      SdkConstants.ATTR_LISTITEM -> NlPropertyType.LAYOUT

      SdkConstants.ATTR_GRAPH,
      SdkConstants.ATTR_NAV_GRAPH -> NlPropertyType.NAVIGATION

      NavigationSchema.ATTR_DESTINATION,
      SdkConstants.ATTR_START_DESTINATION,
      NavigationSchema.ATTR_POP_UP_TO -> NlPropertyType.DESTINATION

      SdkConstants.ATTR_NAME -> NlPropertyType.CLASS_NAME

      SdkConstants.ATTR_CONSTRAINT_LAYOUT_DESCRIPTION -> NlPropertyType.XML

      SdkConstants.ATTR_MOTION_TARGET -> NlPropertyType.ID_OR_STRING

      SdkConstants.ATTR_MOTION_WAVE_OFFSET -> NlPropertyType.DIMENSION_UNIT_LESS

      PreferenceAttributes.ATTR_DEFAULT_VALUE -> defaultValueType(componentClass)

      else -> null
    }

  /**
   * Find the type of the "defaultValue" attribute.
   *
   * The attribute "defaultValue" defined on the "Preference" tag is known to have multiple types.
   * Classes derived from "android.preference.Preference" or "androidx.preference.Preference" override the method "onGetDefaultValue"
   * to read the value expected for this attribute. It can be either a boolean, string, integer, or a string array depending on the
   * [componentClass].
   *
   * Some derived classes do not read the value at all e.g. "PreferenceCategory".
   * For these components we simply return [NlPropertyType.UNKNOWN] indicating that we should hide this attribute in the properties panel.
   */
  private fun defaultValueType(componentClass: PsiClass?): NlPropertyType = when (componentClass?.qualifiedName) {
    null -> NlPropertyType.UNKNOWN
    PreferenceClasses.CLASS_EDIT_TEXT_PREFERENCE -> NlPropertyType.STRING
    PreferenceClasses.CLASS_LIST_PREFERENCE -> NlPropertyType.STRING
    PreferenceClasses.CLASS_MULTI_CHECK_PREFERENCE  -> NlPropertyType.STRING
    PreferenceClasses.CLASS_MULTI_SELECT_LIST_PREFERENCE -> NlPropertyType.STRING_ARRAY
    PreferenceClasses.CLASS_RINGTONE_PREFERENCE -> NlPropertyType.STRING
    PreferenceClasses.CLASS_SEEK_BAR_PREFERENCE -> NlPropertyType.INTEGER
    PreferenceClasses.CLASS_TWO_STATE_PREFERENCE -> NlPropertyType.THREE_STATE_BOOLEAN

    PreferenceAndroidX.CLASS_EDIT_TEXT_PREFERENCE_ANDROIDX.oldName() -> NlPropertyType.STRING
    PreferenceAndroidX.CLASS_EDIT_TEXT_PREFERENCE_ANDROIDX.newName() -> NlPropertyType.STRING
    PreferenceAndroidX.CLASS_LIST_PREFERENCE_ANDROIDX.oldName() -> NlPropertyType.STRING
    PreferenceAndroidX.CLASS_LIST_PREFERENCE_ANDROIDX.newName() -> NlPropertyType.STRING
    PreferenceAndroidX.CLASS_MULTI_CHECK_PREFERENCE_ANDROIDX.oldName() -> NlPropertyType.STRING
    PreferenceAndroidX.CLASS_MULTI_CHECK_PREFERENCE_ANDROIDX.newName() -> NlPropertyType.STRING
    PreferenceAndroidX.CLASS_MULTI_SELECT_LIST_PREFERENCE_ANDROIDX.oldName() -> NlPropertyType.STRING_ARRAY
    PreferenceAndroidX.CLASS_MULTI_SELECT_LIST_PREFERENCE_ANDROIDX.newName() -> NlPropertyType.STRING_ARRAY
    PreferenceAndroidX.CLASS_RINGTONE_PREFERENCE_ANDROIDX.oldName() -> NlPropertyType.STRING
    PreferenceAndroidX.CLASS_RINGTONE_PREFERENCE_ANDROIDX.newName() -> NlPropertyType.STRING
    PreferenceAndroidX.CLASS_SEEK_BAR_PREFERENCE_ANDROIDX.oldName() -> NlPropertyType.INTEGER
    PreferenceAndroidX.CLASS_SEEK_BAR_PREFERENCE_ANDROIDX.newName() -> NlPropertyType.INTEGER
    PreferenceAndroidX.CLASS_TWO_STATE_PREFERENCE_ANDROIDX.oldName() -> NlPropertyType.THREE_STATE_BOOLEAN
    PreferenceAndroidX.CLASS_TWO_STATE_PREFERENCE_ANDROIDX.newName() -> NlPropertyType.THREE_STATE_BOOLEAN

    else -> defaultValueType(componentClass.superClass)
  }

  private fun fallbackByName(name: String): NlPropertyType {
    val parts = split(name)
    val last = parts.last()
    val secondLast = if (parts.size > 1) parts.elementAt(parts.size - 2) else ""
    val thirdLast = if (parts.size > 2) parts.elementAt(parts.size - 3) else ""
    val forthLast = if (parts.size > 3) parts.elementAt(parts.size - 4) else ""
    when (last) {
      "drawable",
      "icon",
      "indicator" ->
        return NlPropertyType.DRAWABLE
      "color" ->
        return if (secondLast == "text") return NlPropertyType.COLOR_STATE_LIST else NlPropertyType.COLOR
      "appearance" ->
        if (secondLast == "text") return NlPropertyType.TEXT_APPEARANCE
      "handle" ->
        if (thirdLast == "text" && secondLast == "select") return NlPropertyType.DRAWABLE
      "layout" ->
        return NlPropertyType.LAYOUT
      "spec" ->
        if (secondLast == "motion") return NlPropertyType.ANIMATOR
      "style" ->
        return NlPropertyType.STYLE
      else -> {
        if (thirdLast == "text" && secondLast == "appearance") return NlPropertyType.TEXT_APPEARANCE
        if (forthLast == "text" && thirdLast == "select" && secondLast == "handle") return NlPropertyType.DRAWABLE
      }
    }
    return NlPropertyType.STRING
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
