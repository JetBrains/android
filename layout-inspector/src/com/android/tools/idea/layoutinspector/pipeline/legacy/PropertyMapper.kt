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

import com.android.SdkConstants.ATTR_ALPHA
import com.android.SdkConstants.ATTR_AUTOFILL_HINTS
import com.android.SdkConstants.ATTR_BACKGROUND
import com.android.SdkConstants.ATTR_CHECKED
import com.android.SdkConstants.ATTR_CLICKABLE
import com.android.SdkConstants.ATTR_CONTENT_DESCRIPTION
import com.android.SdkConstants.ATTR_ELEVATION
import com.android.SdkConstants.ATTR_ENABLED
import com.android.SdkConstants.ATTR_FOCUSABLE
import com.android.SdkConstants.ATTR_FOREGROUND
import com.android.SdkConstants.ATTR_GRAVITY
import com.android.SdkConstants.ATTR_HEIGHT
import com.android.SdkConstants.ATTR_ID
import com.android.SdkConstants.ATTR_INDETERMINATE
import com.android.SdkConstants.ATTR_ITEM_COUNT
import com.android.SdkConstants.ATTR_LAYOUT_ABOVE
import com.android.SdkConstants.ATTR_LAYOUT_ALIGN_BASELINE
import com.android.SdkConstants.ATTR_LAYOUT_ALIGN_BOTTOM
import com.android.SdkConstants.ATTR_LAYOUT_ALIGN_END
import com.android.SdkConstants.ATTR_LAYOUT_ALIGN_LEFT
import com.android.SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_BOTTOM
import com.android.SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_END
import com.android.SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_LEFT
import com.android.SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_RIGHT
import com.android.SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_START
import com.android.SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_TOP
import com.android.SdkConstants.ATTR_LAYOUT_ALIGN_RIGHT
import com.android.SdkConstants.ATTR_LAYOUT_ALIGN_START
import com.android.SdkConstants.ATTR_LAYOUT_ALIGN_TOP
import com.android.SdkConstants.ATTR_LAYOUT_ALIGN_WITH_PARENT_MISSING
import com.android.SdkConstants.ATTR_LAYOUT_BELOW
import com.android.SdkConstants.ATTR_LAYOUT_CENTER_HORIZONTAL
import com.android.SdkConstants.ATTR_LAYOUT_CENTER_IN_PARENT
import com.android.SdkConstants.ATTR_LAYOUT_CENTER_VERTICAL
import com.android.SdkConstants.ATTR_LAYOUT_GRAVITY
import com.android.SdkConstants.ATTR_LAYOUT_HEIGHT
import com.android.SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM
import com.android.SdkConstants.ATTR_LAYOUT_MARGIN_END
import com.android.SdkConstants.ATTR_LAYOUT_MARGIN_LEFT
import com.android.SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT
import com.android.SdkConstants.ATTR_LAYOUT_MARGIN_START
import com.android.SdkConstants.ATTR_LAYOUT_MARGIN_TOP
import com.android.SdkConstants.ATTR_LAYOUT_TO_END_OF
import com.android.SdkConstants.ATTR_LAYOUT_TO_LEFT_OF
import com.android.SdkConstants.ATTR_LAYOUT_TO_RIGHT_OF
import com.android.SdkConstants.ATTR_LAYOUT_TO_START_OF
import com.android.SdkConstants.ATTR_LAYOUT_WEIGHT
import com.android.SdkConstants.ATTR_LAYOUT_WIDTH
import com.android.SdkConstants.ATTR_LAYOUT_X
import com.android.SdkConstants.ATTR_LAYOUT_Y
import com.android.SdkConstants.ATTR_MAXIMUM
import com.android.SdkConstants.ATTR_MIN_HEIGHT
import com.android.SdkConstants.ATTR_MIN_WIDTH
import com.android.SdkConstants.ATTR_ORIENTATION
import com.android.SdkConstants.ATTR_PADDING_BOTTOM
import com.android.SdkConstants.ATTR_PADDING_LEFT
import com.android.SdkConstants.ATTR_PADDING_RIGHT
import com.android.SdkConstants.ATTR_PADDING_TOP
import com.android.SdkConstants.ATTR_PROGRESS
import com.android.SdkConstants.ATTR_SCROLLBAR_STYLE
import com.android.SdkConstants.ATTR_TAG
import com.android.SdkConstants.ATTR_TEXT_ALIGNMENT
import com.android.SdkConstants.ATTR_TEXT_COLOR
import com.android.SdkConstants.ATTR_TEXT_SIZE
import com.android.SdkConstants.ATTR_TEXT_STYLE
import com.android.SdkConstants.ATTR_VISIBILITY
import com.android.SdkConstants.ATTR_WIDTH
import com.android.SdkConstants.VALUE_HORIZONTAL
import com.android.SdkConstants.VALUE_VERTICAL
import com.android.tools.idea.layoutinspector.properties.PropertyType
import java.util.Locale

/**
 * Conversion of legacy layout inspector properties to recognizable attribute names and values.
 */
object PropertyMapper {
  private var mapping: Map<String, PropertyDefinition>? = null

  fun mapPropertyName(fieldName: String): PropertyDefinition? {
    val mapping = mapping ?: createMapping()
    return mapping[fieldName]
  }

  private fun createMapping(): Map<String, PropertyDefinition> {
    val gravityIntMapping = GravityIntMapping()
    val gravityMapper: (String) -> String = { gravityIntMapping.fromIntValue(toInt(it) ?: 0).joinToString(separator = "|") }
    val idMapper: (String) -> String = { mapId(it) }
    val colorMapper: (String) -> String = { mapColor(it) }

    return mapOf(

      // android.app.ActionBar.LayoutParams
      // android.widget.LinearLayout.LayoutParams
      "layout_gravity" to PropertyDefinition(ATTR_LAYOUT_GRAVITY, PropertyType.GRAVITY, gravityMapper),
      "layout_weight" to PropertyDefinition(ATTR_LAYOUT_WEIGHT, PropertyType.FLOAT),

      // android.view.View
      "bg_state_mUseColor" to PropertyDefinition(ATTR_BACKGROUND, PropertyType.COLOR, colorMapper),
      "fg_state_mUseColor" to PropertyDefinition(ATTR_FOREGROUND, PropertyType.COLOR, colorMapper),
      "getAlpha()" to PropertyDefinition(ATTR_ALPHA, PropertyType.FLOAT),
      "getAutofillHints()" to PropertyDefinition(ATTR_AUTOFILL_HINTS, PropertyType.STRING),
      "getBaseline()" to PropertyDefinition("baseline", PropertyType.DIMENSION),
      "getContentDescription()" to PropertyDefinition(ATTR_CONTENT_DESCRIPTION, PropertyType.STRING),
      "getDefaultFocusHighlightEnabled()" to PropertyDefinition("defaultFocusHighlightEnabled", PropertyType.BOOLEAN),
      "getElevation()" to PropertyDefinition(ATTR_ELEVATION, PropertyType.DIMENSION_FLOAT),
      "getFilterTouchesWhenObscured()" to PropertyDefinition("filterTouchesWhenObscured", PropertyType.BOOLEAN),
      "getFitsSystemWindows()" to PropertyDefinition("fitsSystemWindows", PropertyType.BOOLEAN),
      "getFocusable()" to PropertyDefinition(ATTR_FOCUSABLE, PropertyType.STRING) { mapFocusable(it) },
      "getHeight()" to PropertyDefinition(ATTR_HEIGHT, PropertyType.DIMENSION),
      "getImportantForAccessibility()" to PropertyDefinition("importantForAccessibility", PropertyType.STRING),
      "getImportantForAutofill()" to PropertyDefinition("importantForAutofill", PropertyType.STRING),
      "getImportantForContentCapture()" to PropertyDefinition("importantForContentCapture", PropertyType.INT32),
      "getLayoutDirection()" to PropertyDefinition("layoutDirection", PropertyType.STRING) { mapLayoutDirection(it) },
      "getLocationOnScreen()" to PropertyDefinition("locationOnScreen", PropertyType.OBJECT),
      "getMeasuredHeightAndState()" to PropertyDefinition("measuredHeightAndState", PropertyType.DIMENSION),
      "getMeasuredWidthAndState()" to PropertyDefinition("measuredWidthAndState", PropertyType.DIMENSION),
      "getPivotX()" to PropertyDefinition("transformPivotX", PropertyType.DIMENSION_FLOAT),
      "getPivotY()" to PropertyDefinition("transformPivotY", PropertyType.DIMENSION_FLOAT),
      "getRotation()" to PropertyDefinition("rotation", PropertyType.FLOAT),
      "getRotationX()" to PropertyDefinition("rotationX", PropertyType.FLOAT),
      "getRotationY()" to PropertyDefinition("rotationY", PropertyType.FLOAT),
      "getScaleX()" to PropertyDefinition("scaleX", PropertyType.FLOAT),
      "getScaleY()" to PropertyDefinition("scaleY", PropertyType.FLOAT),
      "getScrollBarStyle()" to PropertyDefinition(ATTR_SCROLLBAR_STYLE, PropertyType.STRING) { mapScrollBarStyle(it) },
      "getSolidColor()" to PropertyDefinition("solidColor", PropertyType.COLOR, colorMapper),
      "getTag()" to PropertyDefinition(ATTR_TAG, PropertyType.STRING),
      "getTextAlignment()" to PropertyDefinition(ATTR_TEXT_ALIGNMENT, PropertyType.STRING) { mapTextAlignment(it) },
      "getTextDirection()" to PropertyDefinition("textDirection", PropertyType.STRING) { mapTextDirection(it) },
      "getTransitionName()" to PropertyDefinition("transitionName", PropertyType.OBJECT),
      "getTranslationX()" to PropertyDefinition("translationX", PropertyType.DIMENSION_FLOAT),
      "getTranslationY()" to PropertyDefinition("translationY", PropertyType.DIMENSION_FLOAT),
      "getTranslationZ()" to PropertyDefinition("translationZ", PropertyType.DIMENSION_FLOAT),
      "getVisibility()" to PropertyDefinition(ATTR_VISIBILITY, PropertyType.STRING) { it.lowercase(Locale.US) },
      "getWidth()" to PropertyDefinition(ATTR_WIDTH, PropertyType.DIMENSION),
      "getX()" to PropertyDefinition(ATTR_X, PropertyType.DIMENSION_FLOAT),
      "getY()" to PropertyDefinition(ATTR_Y, PropertyType.DIMENSION_FLOAT),
      "getZ()" to PropertyDefinition(ATTR_Z, PropertyType.DIMENSION_FLOAT),
      "hasFocus()" to PropertyDefinition("hasFocus", PropertyType.BOOLEAN),
      "hasOverlappingRendering()" to PropertyDefinition("hasOverlappingRendering", PropertyType.BOOLEAN),
      "hasShadow()" to PropertyDefinition("hasShadow", PropertyType.BOOLEAN),
      "hasTransientState()" to PropertyDefinition("hasTransientState", PropertyType.BOOLEAN),
      "isActivated()" to PropertyDefinition("activated", PropertyType.BOOLEAN),  // hasAttrId=false
      "isClickable()" to PropertyDefinition(ATTR_CLICKABLE, PropertyType.BOOLEAN),
      "isDrawingCacheEnabled()" to PropertyDefinition("drawingCacheEnabled", PropertyType.BOOLEAN),  // hasAttrId=false
      "isEnabled()" to PropertyDefinition(ATTR_ENABLED, PropertyType.BOOLEAN),
      "isFocusableInTouchMode()" to PropertyDefinition("focusableInTouchMode", PropertyType.BOOLEAN),
      "isFocused()" to PropertyDefinition("focused", PropertyType.BOOLEAN),
      "isFocusedByDefault()" to PropertyDefinition("focusedByDefault", PropertyType.BOOLEAN),
      "isForceDarkAllowed()" to PropertyDefinition("forceDarkAllowed", PropertyType.BOOLEAN),
      "isHapticFeedbackEnabled()" to PropertyDefinition("hapticFeedbackEnabled", PropertyType.BOOLEAN),
      "isHardwareAccelerated()" to PropertyDefinition("hardwareAccelerated", PropertyType.BOOLEAN),
      "isHovered()" to PropertyDefinition("hovered", PropertyType.BOOLEAN),
      "isInTouchMode()" to PropertyDefinition("inTouchMode", PropertyType.BOOLEAN),
      "isKeyboardNavigationCluster()" to PropertyDefinition("keyboardNavigationCluster", PropertyType.BOOLEAN),
      "isLayoutRtl()" to PropertyDefinition("layoutRtl", PropertyType.BOOLEAN),  // hasAttrId=false
      "isOpaque()" to PropertyDefinition("opaque", PropertyType.BOOLEAN),
      "isPressed()" to PropertyDefinition("pressed", PropertyType.BOOLEAN),
      "isSelected()" to PropertyDefinition("selected", PropertyType.BOOLEAN),  // hasAttrId=false
      "isSoundEffectsEnabled()" to PropertyDefinition("soundEffectsEnabled", PropertyType.BOOLEAN),
      "mBottom" to PropertyDefinition(ATTR_BOTTOM, PropertyType.DIMENSION),
      "mClipBounds" to PropertyDefinition("clipBounds", PropertyType.STRING),  // hasAttrId=false
      "mID" to PropertyDefinition(ATTR_ID, PropertyType.RESOURCE) { mapId(it) },
      "mLayerType" to PropertyDefinition("layerType", PropertyType.STRING) { mapLayerType(it) },
      "getLocationOnScreen_x()" to PropertyDefinition(ATTR_LEFT, PropertyType.DIMENSION),
      "mMeasuredHeight" to PropertyDefinition("measuredHeight", PropertyType.DIMENSION),
      "mMeasuredWidth" to PropertyDefinition("measuredWidth", PropertyType.DIMENSION),
      "mMinHeight" to PropertyDefinition(ATTR_MIN_HEIGHT, PropertyType.DIMENSION),
      "mMinWidth" to PropertyDefinition(ATTR_MIN_WIDTH, PropertyType.DIMENSION),
      "mPaddingBottom" to PropertyDefinition(ATTR_PADDING_BOTTOM, PropertyType.DIMENSION),
      "mPaddingLeft" to PropertyDefinition(ATTR_PADDING_LEFT, PropertyType.DIMENSION),
      "mPaddingRight" to PropertyDefinition(ATTR_PADDING_RIGHT, PropertyType.DIMENSION),
      "mPaddingTop" to PropertyDefinition(ATTR_PADDING_TOP, PropertyType.DIMENSION),
      "mRight" to PropertyDefinition(ATTR_RIGHT, PropertyType.DIMENSION),
      "mScrollX" to PropertyDefinition(ATTR_SCROLL_X, PropertyType.DIMENSION),
      "mScrollY" to PropertyDefinition(ATTR_SCROLL_Y, PropertyType.DIMENSION),
      "getLocationOnScreen_y()" to PropertyDefinition(ATTR_TOP, PropertyType.DIMENSION),

      // android.view.View.TransformationInfo
      "mAlpha" to PropertyDefinition(ATTR_ALPHA, PropertyType.FLOAT),

      // android.view.ViewGroup
      "getClipChildren()" to PropertyDefinition("clipChildren", PropertyType.BOOLEAN),
      "getClipToPadding()" to PropertyDefinition("clipToPadding", PropertyType.BOOLEAN),
      "getDescendantFocusability()" to PropertyDefinition("descendantFocusability", PropertyType.STRING) { mapDescendantFocusability(it) },
      "getPersistentDrawingCache()" to PropertyDefinition("persistentDrawingCache", PropertyType.STRING) { mapPersistentDrawingCache(it) },
      "getTouchscreenBlocksFocus()" to PropertyDefinition("touchscreenBlocksFocus", PropertyType.BOOLEAN),
      "isChildrenDrawingOrderEnabled()" to PropertyDefinition("childrenDrawingOrderEnabled", PropertyType.BOOLEAN),
      "mChildCountWithTransientState" to PropertyDefinition("childCountWithTransientState", PropertyType.INT32),

      // android.view.ViewGroup.LayoutParams
      "layout_height" to PropertyDefinition(ATTR_LAYOUT_HEIGHT, PropertyType.DIMENSION) { mapLayoutWidthAndHeight(it) },
      "layout_width" to PropertyDefinition(ATTR_LAYOUT_WIDTH, PropertyType.DIMENSION) { mapLayoutWidthAndHeight(it) },

      // android.view.ViewGroup.MarginLayoutParams
      "layout_bottomMargin" to PropertyDefinition(ATTR_LAYOUT_MARGIN_BOTTOM, PropertyType.DIMENSION),
      "layout_endMargin" to PropertyDefinition(ATTR_LAYOUT_MARGIN_END, PropertyType.DIMENSION),
      "layout_leftMargin" to PropertyDefinition(ATTR_LAYOUT_MARGIN_LEFT, PropertyType.DIMENSION),
      "layout_rightMargin" to PropertyDefinition(ATTR_LAYOUT_MARGIN_RIGHT, PropertyType.DIMENSION),
      "layout_startMargin" to PropertyDefinition(ATTR_LAYOUT_MARGIN_START, PropertyType.DIMENSION),
      "layout_topMargin" to PropertyDefinition(ATTR_LAYOUT_MARGIN_TOP, PropertyType.DIMENSION),

      // android.view.WindowManager.LayoutParams
      "layout_x" to PropertyDefinition(ATTR_LAYOUT_X, PropertyType.DIMENSION),
      "layout_y" to PropertyDefinition(ATTR_LAYOUT_Y, PropertyType.DIMENSION),
      "layout_flags_DIM_BEHIND" to PropertyDefinition(ATTR_DIM_BEHIND, PropertyType.INT32),

      // android.webkit.WebView
      "getContentHeight()" to PropertyDefinition("contentHeight", PropertyType.DIMENSION),
      "getContentWidth()" to PropertyDefinition("contentWidth", PropertyType.DIMENSION),
      "getOriginalUrl()" to PropertyDefinition("originalUrl", PropertyType.STRING),
      "getScale()" to PropertyDefinition("scale", PropertyType.FLOAT),
      "getTitle()" to PropertyDefinition("title", PropertyType.STRING),
      "getUrl()" to PropertyDefinition("url", PropertyType.STRING),

      // android.widget.AbsListView
      "getCacheColorHint()" to PropertyDefinition("cacheColorHint", PropertyType.COLOR, colorMapper),
      "getSelectedView()" to PropertyDefinition("selectedView", PropertyType.OBJECT),
      "isFastScrollEnabled()" to PropertyDefinition("fastScrollEnabled", PropertyType.BOOLEAN),
      "isScrollingCacheEnabled()" to PropertyDefinition("scrollingCacheEnabled", PropertyType.BOOLEAN),
      "isSmoothScrollbarEnabled()" to PropertyDefinition("smoothScrollbarEnabled", PropertyType.BOOLEAN),
      "isStackFromBottom()" to PropertyDefinition("stackFromBottom", PropertyType.BOOLEAN),
      "isTextFilterEnabled()" to PropertyDefinition("textFilterEnabled", PropertyType.BOOLEAN),

      // android.widget.AbsListView.LayoutParams
      "layout_forceAdd" to PropertyDefinition("layout_forceAdd", PropertyType.BOOLEAN),
      "layout_recycledHeaderFooter" to PropertyDefinition("layout_recycledHeaderFooter", PropertyType.BOOLEAN),

      // android.widget.ActionMenuView.LayoutParams
      "layout_cellsUsed" to PropertyDefinition("layout_cellsUsed", PropertyType.INT32),
      "layout_expandable" to PropertyDefinition("layout_expandable", PropertyType.BOOLEAN),
      "layout_extraPixels" to PropertyDefinition("layout_extraPixels", PropertyType.DIMENSION),
      "layout_isOverflowButton()" to PropertyDefinition("layout_isOverflowButton", PropertyType.BOOLEAN),
      "layout_preventEdgeOffset" to PropertyDefinition("layout_preventEdgeOffset", PropertyType.BOOLEAN),

      // android.widget.AdapterView
      "mFirstPosition" to PropertyDefinition("firstPosition", PropertyType.INT32),
      "mItemCount" to PropertyDefinition(ATTR_ITEM_COUNT, PropertyType.INT32),
      "mNextSelectedPosition" to PropertyDefinition("nextSelectedPosition", PropertyType.INT32),
      "mSelectedPosition" to PropertyDefinition("selectedPosition", PropertyType.INT32),

      // android.widget.CheckedTextView
      // android.widget.CompoundButton
      "isChecked()" to PropertyDefinition(ATTR_CHECKED, PropertyType.BOOLEAN),

      // android.widget.FrameLayout
      "mForegroundPaddingBottom" to PropertyDefinition("foregroundPaddingBottom", PropertyType.DIMENSION),
      "mForegroundPaddingLeft" to PropertyDefinition("foregroundPaddingLeft", PropertyType.DIMENSION),
      "mForegroundPaddingRight" to PropertyDefinition("foregroundPaddingRight", PropertyType.DIMENSION),
      "mForegroundPaddingTop" to PropertyDefinition("foregroundPaddingTop", PropertyType.DIMENSION),
      "mMeasureAllChildren" to PropertyDefinition("measureAllChildren", PropertyType.BOOLEAN),

      // android.widget.GridView
      "getNumColumns()" to PropertyDefinition("numColumns", PropertyType.INT32),

      // android.widget.HorizontalScrollView
      "mFillViewport" to PropertyDefinition("fillViewport", PropertyType.BOOLEAN),

      // android.widget.ImageView
      "getBaseline()" to PropertyDefinition("baseline", PropertyType.DIMENSION),

      // android.widget.LinearLayout
      "mBaselineAligned" to PropertyDefinition("baselineAligned", PropertyType.BOOLEAN),
      "mBaselineAlignedChildIndex" to PropertyDefinition("baselineAlignedChildIndex", PropertyType.INT32),
      "mBaselineChildTop" to PropertyDefinition("baselineChildTop", PropertyType.DIMENSION),
      "mGravity" to PropertyDefinition(ATTR_GRAVITY, PropertyType.GRAVITY, gravityMapper),
      "mOrientation" to PropertyDefinition(ATTR_ORIENTATION, PropertyType.STRING) { mapOrientation(it) },
      "mTotalLength" to PropertyDefinition("totalLength", PropertyType.DIMENSION),
      "mUseLargestChild" to PropertyDefinition("useLargestChild", PropertyType.BOOLEAN),
      "mWeightSum" to PropertyDefinition("weightSum", PropertyType.FLOAT),
      "weight" to PropertyDefinition("weight", PropertyType.FLOAT),

      // android.widget.ListView
      "recycleOnMeasure()" to PropertyDefinition("recycleOnMeasure", PropertyType.BOOLEAN),

      // android.widget.ProgressBar
      "isIndeterminate()" to PropertyDefinition(ATTR_INDETERMINATE, PropertyType.BOOLEAN),
      "getMax()" to PropertyDefinition(ATTR_MAXIMUM, PropertyType.INT32),
      "getMin()" to PropertyDefinition("min", PropertyType.INT32),
      "getProgress()" to PropertyDefinition(ATTR_PROGRESS, PropertyType.INT32),
      "getSecondaryProgress()" to PropertyDefinition("secondaryProgress", PropertyType.INT32),

      // android.widget.RelativeLayout.LayoutParams
      "layout_alignWithParent" to PropertyDefinition(ATTR_LAYOUT_ALIGN_WITH_PARENT_MISSING, PropertyType.BOOLEAN),
      "layout_mRules_leftOf" to PropertyDefinition(ATTR_LAYOUT_TO_LEFT_OF, PropertyType.RESOURCE, idMapper),
      "layout_mRules_rightOf" to PropertyDefinition(ATTR_LAYOUT_TO_RIGHT_OF, PropertyType.RESOURCE, idMapper),
      "layout_mRules_above" to PropertyDefinition(ATTR_LAYOUT_ABOVE, PropertyType.RESOURCE, idMapper),
      "layout_mRules_below" to PropertyDefinition(ATTR_LAYOUT_BELOW, PropertyType.RESOURCE, idMapper),
      "layout_mRules_alignBaseline" to PropertyDefinition(ATTR_LAYOUT_ALIGN_BASELINE, PropertyType.RESOURCE, idMapper),
      "layout_mRules_alignLeft" to PropertyDefinition(ATTR_LAYOUT_ALIGN_LEFT, PropertyType.RESOURCE, idMapper),
      "layout_mRules_alignTop" to PropertyDefinition(ATTR_LAYOUT_ALIGN_TOP, PropertyType.RESOURCE, idMapper),
      "layout_mRules_alignRight" to PropertyDefinition(ATTR_LAYOUT_ALIGN_RIGHT, PropertyType.RESOURCE, idMapper),
      "layout_mRules_alignBottom" to PropertyDefinition(ATTR_LAYOUT_ALIGN_BOTTOM, PropertyType.RESOURCE, idMapper),
      "layout_mRules_alignParentLeft" to PropertyDefinition(ATTR_LAYOUT_ALIGN_PARENT_LEFT, PropertyType.RESOURCE, idMapper),
      "layout_mRules_alignParentTop" to PropertyDefinition(ATTR_LAYOUT_ALIGN_PARENT_TOP, PropertyType.RESOURCE, idMapper),
      "layout_mRules_alignParentRight" to PropertyDefinition(ATTR_LAYOUT_ALIGN_PARENT_RIGHT, PropertyType.RESOURCE, idMapper),
      "layout_mRules_alignParentBottom" to PropertyDefinition(ATTR_LAYOUT_ALIGN_PARENT_BOTTOM, PropertyType.RESOURCE, idMapper),
      "layout_mRules_center" to PropertyDefinition(ATTR_LAYOUT_CENTER_IN_PARENT, PropertyType.RESOURCE, idMapper),
      "layout_mRules_centerHorizontal" to PropertyDefinition(ATTR_LAYOUT_CENTER_HORIZONTAL, PropertyType.RESOURCE, idMapper),
      "layout_mRules_centerVertical" to PropertyDefinition(ATTR_LAYOUT_CENTER_VERTICAL, PropertyType.RESOURCE, idMapper),
      "layout_mRules_startOf" to PropertyDefinition(ATTR_LAYOUT_TO_START_OF, PropertyType.RESOURCE, idMapper),
      "layout_mRules_endOf" to PropertyDefinition(ATTR_LAYOUT_TO_END_OF, PropertyType.RESOURCE, idMapper),
      "layout_mRules_alignStart" to PropertyDefinition(ATTR_LAYOUT_ALIGN_START, PropertyType.RESOURCE, idMapper),
      "layout_mRules_alignEnd" to PropertyDefinition(ATTR_LAYOUT_ALIGN_END, PropertyType.RESOURCE, idMapper),
      "layout_mRules_alignParentStart" to PropertyDefinition(ATTR_LAYOUT_ALIGN_PARENT_START, PropertyType.RESOURCE, idMapper),
      "layout_mRules_alignParentEnd" to PropertyDefinition(ATTR_LAYOUT_ALIGN_PARENT_END, PropertyType.RESOURCE, idMapper),

      // android.widget.ScrollView
      "mFillViewport" to PropertyDefinition("fillViewport", PropertyType.BOOLEAN),

      // android.widget.TableRow.LayoutParams
      "layout_column" to PropertyDefinition("layout_column", PropertyType.INT32),
      "layout_span" to PropertyDefinition("layout_span", PropertyType.INT32),

      // android.widget.TextClock
      "mFormat" to PropertyDefinition("format", PropertyType.STRING),
      "getFormat12Hour()" to PropertyDefinition("format12Hour", PropertyType.STRING),
      "getFormat24Hour()" to PropertyDefinition("format24Hour", PropertyType.STRING),
      "mHasSeconds" to PropertyDefinition("hasSeconds", PropertyType.BOOLEAN),

      // android.widget.TextView
      "getEllipsize()" to PropertyDefinition("ellipsize", PropertyType.STRING) { mapEllipsize(it) },
      "getScaledTextSize()" to PropertyDefinition("scaledTextSize", PropertyType.FLOAT),
      "getSelectionEnd()" to PropertyDefinition("selectionEnd", PropertyType.INT32),
      "getSelectionStart()" to PropertyDefinition("selectionStart", PropertyType.INT32),
      "getTextSize()" to PropertyDefinition(ATTR_TEXT_SIZE, PropertyType.DIMENSION_FLOAT),
      "getTypefaceStyle()" to PropertyDefinition(ATTR_TEXT_STYLE, PropertyType.STRING) { mapTextStyle(it) },
      "mCurTextColor" to PropertyDefinition(ATTR_TEXT_COLOR, PropertyType.COLOR, colorMapper),
      "mGravity" to PropertyDefinition("gravity", PropertyType.GRAVITY, gravityMapper),
      "mText" to PropertyDefinition("text", PropertyType.STRING)
    )
  }

  internal fun toInt(value: String): Int? {
    if (value.startsWith("0x")) {
      return value.substring(2).toIntOrNull(16)
    }
    else {
      return value.toIntOrNull()
    }
  }

  private fun mapLayoutDirection(value: String): String =
    when (value) {
      "RESOLVED_DIRECTION_LTR" -> "ltr"
      "RESOLVED_DIRECTION_RTL" -> "rtl"
      else -> ""
    }

  private fun mapScrollBarStyle(value: String): String =
    when (value) {
      "INSIDE_OVERLAY" -> "insideOverlay"
      "INSIDE_INSET" -> "insideInset"
      "OUTSIDE_OVERLAY" -> "outsideOverlay"
      "OUTSIDE_INSET" -> "outsideInset"
      else -> ""
    }

  private fun mapTextAlignment(value: String): String =
    when (value) {
      "INHERIT" -> "inherit"
      "GRAVITY" -> "gravity"
      "TEXT_START" -> "textStart"
      "TEXT_END" -> "textEnd"
      "CENTER" -> "center"
      "VIEW_START" -> "viewStart"
      "VIEW_END" -> "viewEnd"
      else -> ""
    }

  private fun mapTextDirection(value: String): String =
    when (value) {
      "INHERIT" -> "inherit"
      "ANY_RTL" -> "anyRtl"
      "LTR" -> "ltr"
      "RTL" -> "rtl"
      "LOCALE" -> "locale"
      "FIRST_STRONG" -> "firstStrong"
      "FIRST_STRONG_LTR" -> "firstStrongLtr"
      "FIRST_STRONG_RTL" -> "firstStrongRtl"
      else -> ""
    }

  private fun mapDescendantFocusability(value: String): String =
    when (value) {
      "FOCUS_BEFORE_DESCENDANTS" -> "beforeDescendants"
      "FOCUS_AFTER_DESCENDANTS" -> "afterDescendants"
      "FOCUS_BLOCK_DESCENDANTS" -> "blocksDescendants"
      else -> ""
    }

  private fun mapEllipsize(value: String): String =
    when (value) {
      "START" -> "start"
      "MIDDLE" -> "middle"
      "END" -> "end"
      "MARQUEE" -> "marquee"
      "END_SMALL" -> "end"
      else -> ""
    }

  private fun mapPersistentDrawingCache(value: String): String =
    when (value) {
      "NONE" -> "none"
      "ANIMATION" -> "animation"
      "SCROLLING" -> "scrolling"
      "ALL" -> "all"
      else -> ""
    }

  private fun mapLayoutWidthAndHeight(value: String): String =
    when (value) {
      "MATCH_PARENT" -> "match_parent"
      "WRAP_CONTENT" -> "wrap_content"
      else -> value
    }

  private fun mapLayerType(value: String): String =
    when (value) {
      "NONE" -> "none"
      "SOFTWARE" -> "software"
      "HARDWARE" -> "hardware"
      else -> ""
    }

  private fun mapFocusable(value: String): String =
    when (value) {
      "NOT_FOCUSABLE" -> "false"
      "FOCUSABLE" -> "true"
      "FOCUSABLE_AUTO" -> "auto"
      else -> ""
    }

  private fun mapTextStyle(value: String): String =
    when (value) {
      "NORMAL" -> "normal"
      "BOLD" -> "bold"
      "ITALIC" -> "italic"
      "BOLD_ITALIC" -> "bold|italic"
      else -> ""
    }

  private fun mapOrientation(value: String): String =
    when (value) {
      "0" -> VALUE_HORIZONTAL
      "1" -> VALUE_VERTICAL
      else -> ""
    }

  private fun mapColor(value: String): String {
    val intValue = toInt(value) ?: 0
    return if ((intValue and 0xFF000000.toInt()) != 0) "#%08X".format(intValue) else "#%06X".format(intValue)
  }

  private fun mapId(value: String): String = if (value.startsWith("id/")) "@$value" else ""
}
