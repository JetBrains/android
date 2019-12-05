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
import com.android.tools.layoutinspector.proto.LayoutInspectorProto.Property.Type
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
      "layout_gravity" to PropertyDefinition(ATTR_LAYOUT_GRAVITY, Type.GRAVITY, gravityMapper),
      "layout_weight" to PropertyDefinition(ATTR_LAYOUT_WEIGHT, Type.FLOAT),

      // android.view.View
      "bg_state_mUseColor" to PropertyDefinition(ATTR_BACKGROUND, Type.COLOR, colorMapper),
      "fg_state_mUseColor" to PropertyDefinition(ATTR_FOREGROUND, Type.COLOR, colorMapper),
      "getAlpha()" to PropertyDefinition(ATTR_ALPHA, Type.FLOAT),
      "getAutofillHints()" to PropertyDefinition(ATTR_AUTOFILL_HINTS, Type.STRING),
      "getBaseline()" to PropertyDefinition("baseline", Type.INT32),
      "getContentDescription()" to PropertyDefinition(ATTR_CONTENT_DESCRIPTION, Type.STRING),
      "getDefaultFocusHighlightEnabled()" to PropertyDefinition("defaultFocusHighlightEnabled", Type.BOOLEAN),
      "getElevation()" to PropertyDefinition(ATTR_ELEVATION, Type.FLOAT),
      "getFilterTouchesWhenObscured()" to PropertyDefinition("filterTouchesWhenObscured", Type.BOOLEAN),
      "getFitsSystemWindows()" to PropertyDefinition("fitsSystemWindows", Type.BOOLEAN),
      "getFocusable()" to PropertyDefinition(ATTR_FOCUSABLE, Type.STRING) { mapFocusable(it) },
      "getHeight()" to PropertyDefinition(ATTR_HEIGHT, Type.INT32),
      "getImportantForAccessibility()" to PropertyDefinition("importantForAccessibility", Type.STRING),
      "getImportantForAutofill()" to PropertyDefinition("importantForAutofill", Type.STRING),
      "getImportantForContentCapture()" to PropertyDefinition("importantForContentCapture", Type.INT32),
      "getLayoutDirection()" to PropertyDefinition("layoutDirection", Type.STRING) { mapLayoutDirection(it) },
      "getLocationOnScreen()" to PropertyDefinition("locationOnScreen", Type.OBJECT),
      "getMeasuredHeightAndState()" to PropertyDefinition("measuredHeightAndState", Type.INT32),
      "getMeasuredWidthAndState()" to PropertyDefinition("measuredWidthAndState", Type.INT32),
      "getPivotX()" to PropertyDefinition("transformPivotX", Type.FLOAT),
      "getPivotY()" to PropertyDefinition("transformPivotY", Type.FLOAT),
      "getRotation()" to PropertyDefinition("rotation", Type.FLOAT),
      "getRotationX()" to PropertyDefinition("rotationX", Type.FLOAT),
      "getRotationY()" to PropertyDefinition("rotationY", Type.FLOAT),
      "getScaleX()" to PropertyDefinition("scaleX", Type.FLOAT),
      "getScaleY()" to PropertyDefinition("scaleY", Type.FLOAT),
      "getScrollBarStyle()" to PropertyDefinition(ATTR_SCROLLBAR_STYLE, Type.STRING) { mapScrollBarStyle(it) },
      "getSolidColor()" to PropertyDefinition("solidColor", Type.COLOR, colorMapper),
      "getTag()" to PropertyDefinition(ATTR_TAG, Type.STRING),
      "getTextAlignment()" to PropertyDefinition(ATTR_TEXT_ALIGNMENT, Type.STRING) { mapTextAlignment(it) },
      "getTextDirection()" to PropertyDefinition("textDirection", Type.STRING) { mapTextDirection(it) },
      "getTransitionName()" to PropertyDefinition("transitionName", Type.OBJECT),
      "getTranslationX()" to PropertyDefinition("translationX", Type.FLOAT),
      "getTranslationY()" to PropertyDefinition("translationY", Type.FLOAT),
      "getTranslationZ()" to PropertyDefinition("translationZ", Type.FLOAT),
      "getVisibility()" to PropertyDefinition(ATTR_VISIBILITY, Type.STRING) { it.toLowerCase(Locale.US) },
      "getWidth()" to PropertyDefinition(ATTR_WIDTH, Type.INT32),
      "getX()" to PropertyDefinition(ATTR_X, Type.FLOAT),
      "getY()" to PropertyDefinition(ATTR_Y, Type.FLOAT),
      "getZ()" to PropertyDefinition(ATTR_Z, Type.FLOAT),
      "hasFocus()" to PropertyDefinition("hasFocus", Type.BOOLEAN),
      "hasOverlappingRendering()" to PropertyDefinition("hasOverlappingRendering", Type.BOOLEAN),
      "hasShadow()" to PropertyDefinition("hasShadow", Type.BOOLEAN),
      "hasTransientState()" to PropertyDefinition("hasTransientState", Type.BOOLEAN),
      "isActivated()" to PropertyDefinition("activated", Type.BOOLEAN),  // hasAttrId=false
      "isClickable()" to PropertyDefinition(ATTR_CLICKABLE, Type.BOOLEAN),
      "isDrawingCacheEnabled()" to PropertyDefinition("drawingCacheEnabled", Type.BOOLEAN),  // hasAttrId=false
      "isEnabled()" to PropertyDefinition(ATTR_ENABLED, Type.BOOLEAN),
      "isFocusableInTouchMode()" to PropertyDefinition("focusableInTouchMode", Type.BOOLEAN),
      "isFocused()" to PropertyDefinition("focused", Type.BOOLEAN),
      "isFocusedByDefault()" to PropertyDefinition("focusedByDefault", Type.BOOLEAN),
      "isForceDarkAllowed()" to PropertyDefinition("forceDarkAllowed", Type.BOOLEAN),
      "isHapticFeedbackEnabled()" to PropertyDefinition("hapticFeedbackEnabled", Type.BOOLEAN),
      "isHardwareAccelerated()" to PropertyDefinition("hardwareAccelerated", Type.BOOLEAN),
      "isHovered()" to PropertyDefinition("hovered", Type.BOOLEAN),
      "isInTouchMode()" to PropertyDefinition("inTouchMode", Type.BOOLEAN),
      "isKeyboardNavigationCluster()" to PropertyDefinition("keyboardNavigationCluster", Type.BOOLEAN),
      "isLayoutRtl()" to PropertyDefinition("layoutRtl", Type.BOOLEAN),  // hasAttrId=false
      "isOpaque()" to PropertyDefinition("opaque", Type.BOOLEAN),
      "isPressed()" to PropertyDefinition("pressed", Type.BOOLEAN),
      "isSelected()" to PropertyDefinition("selected", Type.BOOLEAN),  // hasAttrId=false
      "isSoundEffectsEnabled()" to PropertyDefinition("soundEffectsEnabled", Type.BOOLEAN),
      "mBottom" to PropertyDefinition(ATTR_BOTTOM, Type.INT32),
      "mClipBounds" to PropertyDefinition("clipBounds", Type.STRING),  // hasAttrId=false
      "mID" to PropertyDefinition(ATTR_ID, Type.RESOURCE) { mapId(it) },
      "mLayerType" to PropertyDefinition("layerType", Type.STRING) { mapLayerType(it) },
      "getLocationOnScreen_x()" to PropertyDefinition(ATTR_LEFT, Type.INT32),
      "mMeasuredHeight" to PropertyDefinition("measuredHeight", Type.INT32),
      "mMeasuredWidth" to PropertyDefinition("measuredWidth", Type.INT32),
      "mMinHeight" to PropertyDefinition(ATTR_MIN_HEIGHT, Type.INT32),
      "mMinWidth" to PropertyDefinition(ATTR_MIN_WIDTH, Type.INT32),
      "mPaddingBottom" to PropertyDefinition(ATTR_PADDING_BOTTOM, Type.INT32),
      "mPaddingLeft" to PropertyDefinition(ATTR_PADDING_LEFT, Type.INT32),
      "mPaddingRight" to PropertyDefinition(ATTR_PADDING_RIGHT, Type.INT32),
      "mPaddingTop" to PropertyDefinition(ATTR_PADDING_TOP, Type.INT32),
      "mRight" to PropertyDefinition(ATTR_RIGHT, Type.INT32),
      "mScrollX" to PropertyDefinition(ATTR_SCROLL_X, Type.INT32),
      "mScrollY" to PropertyDefinition(ATTR_SCROLL_Y, Type.INT32),
      "getLocationOnScreen_y()" to PropertyDefinition(ATTR_TOP, Type.INT32),

      // android.view.View.TransformationInfo
      "mAlpha" to PropertyDefinition(ATTR_ALPHA, Type.FLOAT),

      // android.view.ViewGroup
      "getClipChildren()" to PropertyDefinition("clipChildren", Type.BOOLEAN),
      "getClipToPadding()" to PropertyDefinition("clipToPadding", Type.BOOLEAN),
      "getDescendantFocusability()" to PropertyDefinition("descendantFocusability", Type.STRING) { mapDescendantFocusability(it) },
      "getPersistentDrawingCache()" to PropertyDefinition("persistentDrawingCache", Type.STRING) { mapPersistentDrawingCache(it) },
      "getTouchscreenBlocksFocus()" to PropertyDefinition("touchscreenBlocksFocus", Type.BOOLEAN),
      "isChildrenDrawingOrderEnabled()" to PropertyDefinition("childrenDrawingOrderEnabled", Type.BOOLEAN),
      "mChildCountWithTransientState" to PropertyDefinition("childCountWithTransientState", Type.INT32),

      // android.view.ViewGroup.LayoutParams
      "layout_height" to PropertyDefinition(ATTR_LAYOUT_HEIGHT, Type.INT32) { mapLayoutWidthAndHeight(it) },
      "layout_width" to PropertyDefinition(ATTR_LAYOUT_WIDTH, Type.INT32) { mapLayoutWidthAndHeight(it) },

      // android.view.ViewGroup.MarginLayoutParams
      "layout_bottomMargin" to PropertyDefinition(ATTR_LAYOUT_MARGIN_BOTTOM, Type.INT32),
      "layout_endMargin" to PropertyDefinition(ATTR_LAYOUT_MARGIN_END, Type.INT32),
      "layout_leftMargin" to PropertyDefinition(ATTR_LAYOUT_MARGIN_LEFT, Type.INT32),
      "layout_rightMargin" to PropertyDefinition(ATTR_LAYOUT_MARGIN_RIGHT, Type.INT32),
      "layout_startMargin" to PropertyDefinition(ATTR_LAYOUT_MARGIN_START, Type.INT32),
      "layout_topMargin" to PropertyDefinition(ATTR_LAYOUT_MARGIN_TOP, Type.INT32),

      // android.view.WindowManager.LayoutParams
      "layout_x" to PropertyDefinition(ATTR_LAYOUT_X, Type.INT32),
      "layout_y" to PropertyDefinition(ATTR_LAYOUT_Y, Type.INT32),
      "layout_flags_DIM_BEHIND" to PropertyDefinition(ATTR_DIM_BEHIND, Type.INT32),

      // android.webkit.WebView
      "getContentHeight()" to PropertyDefinition("contentHeight", Type.INT32),
      "getContentWidth()" to PropertyDefinition("contentWidth", Type.INT32),
      "getOriginalUrl()" to PropertyDefinition("originalUrl", Type.STRING),
      "getScale()" to PropertyDefinition("scale", Type.FLOAT),
      "getTitle()" to PropertyDefinition("title", Type.STRING),
      "getUrl()" to PropertyDefinition("url", Type.STRING),

      // android.widget.AbsListView
      "getCacheColorHint()" to PropertyDefinition("cacheColorHint", Type.COLOR, colorMapper),
      "getSelectedView()" to PropertyDefinition("selectedView", Type.OBJECT),
      "isFastScrollEnabled()" to PropertyDefinition("fastScrollEnabled", Type.BOOLEAN),
      "isScrollingCacheEnabled()" to PropertyDefinition("scrollingCacheEnabled", Type.BOOLEAN),
      "isSmoothScrollbarEnabled()" to PropertyDefinition("smoothScrollbarEnabled", Type.BOOLEAN),
      "isStackFromBottom()" to PropertyDefinition("stackFromBottom", Type.BOOLEAN),
      "isTextFilterEnabled()" to PropertyDefinition("textFilterEnabled", Type.BOOLEAN),

      // android.widget.AbsListView.LayoutParams
      "layout_forceAdd" to PropertyDefinition("layout_forceAdd", Type.BOOLEAN),
      "layout_recycledHeaderFooter" to PropertyDefinition("layout_recycledHeaderFooter", Type.BOOLEAN),

      // android.widget.ActionMenuView.LayoutParams
      "layout_cellsUsed" to PropertyDefinition("layout_cellsUsed", Type.INT32),
      "layout_expandable" to PropertyDefinition("layout_expandable", Type.BOOLEAN),
      "layout_extraPixels" to PropertyDefinition("layout_extraPixels", Type.INT32),
      "layout_isOverflowButton()" to PropertyDefinition("layout_isOverflowButton", Type.BOOLEAN),
      "layout_preventEdgeOffset" to PropertyDefinition("layout_preventEdgeOffset", Type.BOOLEAN),

      // android.widget.AdapterView
      "mFirstPosition" to PropertyDefinition("firstPosition", Type.INT32),
      "mItemCount" to PropertyDefinition(ATTR_ITEM_COUNT, Type.INT32),
      "mNextSelectedPosition" to PropertyDefinition("nextSelectedPosition", Type.INT32),
      "mSelectedPosition" to PropertyDefinition("selectedPosition", Type.INT32),

      // android.widget.CheckedTextView
      // android.widget.CompoundButton
      "isChecked()" to PropertyDefinition(ATTR_CHECKED, Type.BOOLEAN),

      // android.widget.FrameLayout
      "mForegroundPaddingBottom" to PropertyDefinition("foregroundPaddingBottom", Type.INT32),
      "mForegroundPaddingLeft" to PropertyDefinition("foregroundPaddingLeft", Type.INT32),
      "mForegroundPaddingRight" to PropertyDefinition("foregroundPaddingRight", Type.INT32),
      "mForegroundPaddingTop" to PropertyDefinition("foregroundPaddingTop", Type.INT32),
      "mMeasureAllChildren" to PropertyDefinition("measureAllChildren", Type.BOOLEAN),

      // android.widget.GridView
      "getNumColumns()" to PropertyDefinition("numColumns", Type.INT32),

      // android.widget.HorizontalScrollView
      "mFillViewport" to PropertyDefinition("fillViewport", Type.BOOLEAN),

      // android.widget.ImageView
      "getBaseline()" to PropertyDefinition("baseline", Type.INT32),

      // android.widget.LinearLayout
      "mBaselineAligned" to PropertyDefinition("baselineAligned", Type.BOOLEAN),
      "mBaselineAlignedChildIndex" to PropertyDefinition("baselineAlignedChildIndex", Type.INT32),
      "mBaselineChildTop" to PropertyDefinition("baselineChildTop", Type.INT32),
      "mGravity" to PropertyDefinition(ATTR_GRAVITY, Type.GRAVITY, gravityMapper),
      "mOrientation" to PropertyDefinition(ATTR_ORIENTATION, Type.STRING) { mapOrientation(it) },
      "mTotalLength" to PropertyDefinition("totalLength", Type.INT32),
      "mUseLargestChild" to PropertyDefinition("useLargestChild", Type.BOOLEAN),
      "mWeightSum" to PropertyDefinition("weightSum", Type.FLOAT),
      "weight" to PropertyDefinition("weight", Type.FLOAT),

      // android.widget.ListView
      "recycleOnMeasure()" to PropertyDefinition("recycleOnMeasure", Type.BOOLEAN),

      // android.widget.ProgressBar
      "isIndeterminate()" to PropertyDefinition(ATTR_INDETERMINATE, Type.BOOLEAN),
      "getMax()" to PropertyDefinition(ATTR_MAXIMUM, Type.INT32),
      "getMin()" to PropertyDefinition("min", Type.INT32),
      "getProgress()" to PropertyDefinition(ATTR_PROGRESS, Type.INT32),
      "getSecondaryProgress()" to PropertyDefinition("secondaryProgress", Type.INT32),

      // android.widget.RelativeLayout.LayoutParams
      "layout_alignWithParent" to PropertyDefinition(ATTR_LAYOUT_ALIGN_WITH_PARENT_MISSING, Type.BOOLEAN),
      "layout_mRules_leftOf" to PropertyDefinition(ATTR_LAYOUT_TO_LEFT_OF, Type.RESOURCE, idMapper),
      "layout_mRules_rightOf" to PropertyDefinition(ATTR_LAYOUT_TO_RIGHT_OF, Type.RESOURCE, idMapper),
      "layout_mRules_above" to PropertyDefinition(ATTR_LAYOUT_ABOVE, Type.RESOURCE, idMapper),
      "layout_mRules_below" to PropertyDefinition(ATTR_LAYOUT_BELOW, Type.RESOURCE, idMapper),
      "layout_mRules_alignBaseline" to PropertyDefinition(ATTR_LAYOUT_ALIGN_BASELINE, Type.RESOURCE, idMapper),
      "layout_mRules_alignLeft" to PropertyDefinition(ATTR_LAYOUT_ALIGN_LEFT, Type.RESOURCE, idMapper),
      "layout_mRules_alignTop" to PropertyDefinition(ATTR_LAYOUT_ALIGN_TOP, Type.RESOURCE, idMapper),
      "layout_mRules_alignRight" to PropertyDefinition(ATTR_LAYOUT_ALIGN_RIGHT, Type.RESOURCE, idMapper),
      "layout_mRules_alignBottom" to PropertyDefinition(ATTR_LAYOUT_ALIGN_BOTTOM, Type.RESOURCE, idMapper),
      "layout_mRules_alignParentLeft" to PropertyDefinition(ATTR_LAYOUT_ALIGN_PARENT_LEFT, Type.RESOURCE, idMapper),
      "layout_mRules_alignParentTop" to PropertyDefinition(ATTR_LAYOUT_ALIGN_PARENT_TOP, Type.RESOURCE, idMapper),
      "layout_mRules_alignParentRight" to PropertyDefinition(ATTR_LAYOUT_ALIGN_PARENT_RIGHT, Type.RESOURCE, idMapper),
      "layout_mRules_alignParentBottom" to PropertyDefinition(ATTR_LAYOUT_ALIGN_PARENT_BOTTOM, Type.RESOURCE, idMapper),
      "layout_mRules_center" to PropertyDefinition(ATTR_LAYOUT_CENTER_IN_PARENT, Type.RESOURCE, idMapper),
      "layout_mRules_centerHorizontal" to PropertyDefinition(ATTR_LAYOUT_CENTER_HORIZONTAL, Type.RESOURCE, idMapper),
      "layout_mRules_centerVertical" to PropertyDefinition(ATTR_LAYOUT_CENTER_VERTICAL, Type.RESOURCE, idMapper),
      "layout_mRules_startOf" to PropertyDefinition(ATTR_LAYOUT_TO_START_OF, Type.RESOURCE, idMapper),
      "layout_mRules_endOf" to PropertyDefinition(ATTR_LAYOUT_TO_END_OF, Type.RESOURCE, idMapper),
      "layout_mRules_alignStart" to PropertyDefinition(ATTR_LAYOUT_ALIGN_START, Type.RESOURCE, idMapper),
      "layout_mRules_alignEnd" to PropertyDefinition(ATTR_LAYOUT_ALIGN_END, Type.RESOURCE, idMapper),
      "layout_mRules_alignParentStart" to PropertyDefinition(ATTR_LAYOUT_ALIGN_PARENT_START, Type.RESOURCE, idMapper),
      "layout_mRules_alignParentEnd" to PropertyDefinition(ATTR_LAYOUT_ALIGN_PARENT_END, Type.RESOURCE, idMapper),

      // android.widget.ScrollView
      "mFillViewport" to PropertyDefinition("fillViewport", Type.BOOLEAN),

      // android.widget.TableRow.LayoutParams
      "layout_column" to PropertyDefinition("layout_column", Type.INT32),
      "layout_span" to PropertyDefinition("layout_span", Type.INT32),

      // android.widget.TextClock
      "mFormat" to PropertyDefinition("format", Type.STRING),
      "getFormat12Hour()" to PropertyDefinition("format12Hour", Type.STRING),
      "getFormat24Hour()" to PropertyDefinition("format24Hour", Type.STRING),
      "mHasSeconds" to PropertyDefinition("hasSeconds", Type.BOOLEAN),

      // android.widget.TextView
      "getEllipsize()" to PropertyDefinition("ellipsize", Type.STRING) { mapEllipsize(it) },
      "getScaledTextSize()" to PropertyDefinition("scaledTextSize", Type.FLOAT),
      "getSelectionEnd()" to PropertyDefinition("selectionEnd", Type.INT32),
      "getSelectionStart()" to PropertyDefinition("selectionStart", Type.INT32),
      "getTextSize()" to PropertyDefinition(ATTR_TEXT_SIZE, Type.FLOAT),
      "getTypefaceStyle()" to PropertyDefinition(ATTR_TEXT_STYLE, Type.STRING) { mapTextStyle(it) },
      "mCurTextColor" to PropertyDefinition(ATTR_TEXT_COLOR, Type.COLOR, colorMapper),
      "mGravity" to PropertyDefinition("gravity", Type.GRAVITY, gravityMapper),
      "mText" to PropertyDefinition("text", Type.STRING)
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
