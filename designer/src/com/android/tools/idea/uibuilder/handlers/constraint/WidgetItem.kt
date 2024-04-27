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
package com.android.tools.idea.uibuilder.handlers.constraint

import com.android.SdkConstants
import com.android.tools.idea.common.model.NlAttributesReader

open class WidgetItem(
  val namespace: String,
  val attribute: String,
  val condition: (NlAttributesReader) -> Boolean,
  val displayName: String,
  val boldTextFunc: (NlAttributesReader) -> String?,
  val fadingTextFuc: (NlAttributesReader) -> String?
)

private class ConstraintWidgetItem(displayName: String, attribute: String, margin: String?) :
  WidgetItem(
    SdkConstants.SHERPA_URI,
    attribute,
    { it.getAttribute(SdkConstants.SHERPA_URI, attribute) != null },
    displayName,
    { it.getAttribute(SdkConstants.SHERPA_URI, attribute) },
    {
      if (margin == null) null
      else
        it.getAndroidAttribute(SdkConstants.ATTR_LAYOUT_MARGIN)
          ?: it.getAndroidAttribute(margin)
          ?: "0dp"
    }
  )

private val CONSTRAINT_ATTRIBUTES_ITEMS: Array<WidgetItem> =
  arrayOf(
    ConstraintWidgetItem(
      "Start → StartOf",
      SdkConstants.ATTR_LAYOUT_START_TO_START_OF,
      SdkConstants.ATTR_LAYOUT_MARGIN_START
    ),
    ConstraintWidgetItem(
      "Start → EndOf",
      SdkConstants.ATTR_LAYOUT_START_TO_END_OF,
      SdkConstants.ATTR_LAYOUT_MARGIN_START
    ),
    ConstraintWidgetItem(
      "End → StartOf",
      SdkConstants.ATTR_LAYOUT_END_TO_START_OF,
      SdkConstants.ATTR_LAYOUT_MARGIN_END
    ),
    ConstraintWidgetItem(
      "End → EndOf",
      SdkConstants.ATTR_LAYOUT_END_TO_END_OF,
      SdkConstants.ATTR_LAYOUT_MARGIN_END
    ),
    ConstraintWidgetItem(
      "Left → LeftOf",
      SdkConstants.ATTR_LAYOUT_LEFT_TO_LEFT_OF,
      SdkConstants.ATTR_LAYOUT_MARGIN_LEFT
    ),
    ConstraintWidgetItem(
      "Left → RightOf",
      SdkConstants.ATTR_LAYOUT_LEFT_TO_RIGHT_OF,
      SdkConstants.ATTR_LAYOUT_MARGIN_LEFT
    ),
    ConstraintWidgetItem(
      "Right → LeftOf",
      SdkConstants.ATTR_LAYOUT_RIGHT_TO_LEFT_OF,
      SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT
    ),
    ConstraintWidgetItem(
      "Right → RightOf",
      SdkConstants.ATTR_LAYOUT_RIGHT_TO_RIGHT_OF,
      SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT
    ),
    ConstraintWidgetItem(
      "Top → TopOf",
      SdkConstants.ATTR_LAYOUT_TOP_TO_TOP_OF,
      SdkConstants.ATTR_LAYOUT_MARGIN_TOP
    ),
    ConstraintWidgetItem(
      "Top → BottomOf",
      SdkConstants.ATTR_LAYOUT_TOP_TO_BOTTOM_OF,
      SdkConstants.ATTR_LAYOUT_MARGIN_TOP
    ),
    ConstraintWidgetItem(
      "Bottom → TopOf",
      SdkConstants.ATTR_LAYOUT_BOTTOM_TO_TOP_OF,
      SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM
    ),
    ConstraintWidgetItem(
      "Bottom → BottomOf",
      SdkConstants.ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF,
      SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM
    ),
    ConstraintWidgetItem(
      "Baseline → BaselineOf",
      SdkConstants.ATTR_LAYOUT_BASELINE_TO_BASELINE_OF,
      null
    )
  )

private class ConstraintValueWidgetItem(displayName: String, attribute: String) :
  WidgetItem(
    SdkConstants.SHERPA_URI,
    attribute,
    { it.getAttribute(SdkConstants.SHERPA_URI, attribute) != null },
    displayName,
    { null },
    { it.getAttribute(SdkConstants.SHERPA_URI, attribute) }
  )

private val DIMENSION_ATTRIBUTES_ITEMS: Array<WidgetItem> =
  arrayOf(
    ConstraintValueWidgetItem("Vertical Bias", SdkConstants.ATTR_LAYOUT_VERTICAL_BIAS),
    ConstraintValueWidgetItem("Horizontal Bias", SdkConstants.ATTR_LAYOUT_HORIZONTAL_BIAS),
    ConstraintValueWidgetItem("Dimension Ratio", SdkConstants.ATTR_LAYOUT_DIMENSION_RATIO)
  )

private class ConstraintCircleWidgetItem(
  displayName: String,
  attribute: String,
  radius: String,
  angle: String
) :
  WidgetItem(
    SdkConstants.SHERPA_URI,
    attribute,
    { it.getAttribute(SdkConstants.SHERPA_URI, attribute) != null },
    displayName,
    { it.getAttribute(SdkConstants.SHERPA_URI, attribute) },
    {
      "${it.getAttribute(SdkConstants.SHERPA_URI, radius) ?: "0"}, ${it.getAttribute(SdkConstants.SHERPA_URI, angle) ?: "0"}"
    }
  )

private val CONSTRAINT_CIRCLE_ATTRIBUTES_ITEMS: Array<WidgetItem> =
  arrayOf(
    ConstraintCircleWidgetItem(
      "Constraint Circle",
      SdkConstants.ATTR_LAYOUT_CONSTRAINT_CIRCLE,
      SdkConstants.ATTR_LAYOUT_CONSTRAINT_CIRCLE_ANGLE,
      SdkConstants.ATTR_LAYOUT_CONSTRAINT_CIRCLE_RADIUS
    )
  )

private class AndroidAttributeWidgetItem(displayName: String, attribute: String) :
  WidgetItem(
    SdkConstants.ANDROID_URI,
    attribute,
    { it.getAndroidAttribute(attribute) != null },
    displayName,
    { null },
    { it.getAndroidAttribute(attribute) }
  )

private val CONSTRAINT_GUIDELINE_ATTRIBUTES_ITEMS: Array<WidgetItem> =
  arrayOf(
    ConstraintValueWidgetItem("Guideline Begin", SdkConstants.LAYOUT_CONSTRAINT_GUIDE_BEGIN),
    ConstraintValueWidgetItem("Guideline End", SdkConstants.LAYOUT_CONSTRAINT_GUIDE_END),
    ConstraintValueWidgetItem("Guideline %", SdkConstants.LAYOUT_CONSTRAINT_GUIDE_PERCENT),
    AndroidAttributeWidgetItem("Orientation", SdkConstants.ATTR_ORIENTATION)
  )

// TODO: add some attributes into SdkConstants
private val CONSTRAINT_WIDTH_ATTRIBUTES_ITEMS: Array<WidgetItem> =
  arrayOf(
    ConstraintValueWidgetItem("Constraint Width", SdkConstants.ATTR_LAYOUT_CONSTRAINED_WIDTH),
    ConstraintValueWidgetItem("Constraint Width Default", "layout_constraintWidth_default"),
    ConstraintValueWidgetItem("Constraint Width Min", "layout_constraintWidth_min"),
    ConstraintValueWidgetItem("Constraint Width Max", "layout_constraintWidth_max"),
    ConstraintValueWidgetItem("Constraint Width Percent", "layout_constraintWidth_percent"),
    ConstraintValueWidgetItem("Constraint Width Weight", "layout_constraintHorizontal_weight"),
    AndroidAttributeWidgetItem("Min Width", SdkConstants.ATTR_MIN_WIDTH),
    AndroidAttributeWidgetItem("Max Width", SdkConstants.ATTR_MAX_WIDTH)
  )

// TODO: add some attributes into SdkConstants
private val CONSTRAINT_HEIGHT_ATTRIBUTES_ITEMS: Array<WidgetItem> =
  arrayOf(
    ConstraintValueWidgetItem("Constraint Height", SdkConstants.ATTR_LAYOUT_CONSTRAINED_HEIGHT),
    ConstraintValueWidgetItem("Constraint Height Default", "layout_constraintHeight_default"),
    ConstraintValueWidgetItem("Constraint Height Min", "layout_constraintHeight_min"),
    ConstraintValueWidgetItem("Constraint Height Max", "layout_constraintHeight_max"),
    ConstraintValueWidgetItem("Constraint Height Percent", "layout_constraintHeight_percent"),
    ConstraintValueWidgetItem("Constraint Height Weight", "layout_constraintVertical_weight"),
    AndroidAttributeWidgetItem("Min Height", SdkConstants.ATTR_MIN_HEIGHT),
    AndroidAttributeWidgetItem("Max Height", SdkConstants.ATTR_MAX_HEIGHT)
  )

private val CONSTRAINT_CHAIN_STYLE_ATTRIBUTE_ITEMS: Array<WidgetItem> =
  arrayOf(
    ConstraintValueWidgetItem(
      "Horizontal Chain Style",
      SdkConstants.ATTR_LAYOUT_HORIZONTAL_CHAIN_STYLE
    ),
    ConstraintValueWidgetItem(
      "Vertical Chain Style",
      SdkConstants.ATTR_LAYOUT_VERTICAL_CHAIN_STYLE
    ),
    ConstraintValueWidgetItem("Chain Uses RTL", SdkConstants.ATTR_LAYOUT_CHAIN_HELPER_USE_RTL)
  )

private class ReferencedIdsWidgetItem(displayName: String) :
  WidgetItem(
    SdkConstants.SHERPA_URI,
    SdkConstants.CONSTRAINT_REFERENCED_IDS,
    { it.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.CONSTRAINT_REFERENCED_IDS) != null },
    displayName,
    { it.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.CONSTRAINT_REFERENCED_IDS) },
    { null }
  )

private val CONSTRAINT_BARRIER_ATTRIBUTES_ITEMS: Array<WidgetItem> =
  arrayOf(
    ConstraintValueWidgetItem(
      "Barrier Allows Gone Widgets",
      SdkConstants.ATTR_BARRIER_ALLOWS_GONE_WIDGETS
    ),
    ReferencedIdsWidgetItem("Constraint Referenced Ids"),
    ConstraintValueWidgetItem("Barrier Direction", SdkConstants.ATTR_BARRIER_DIRECTION)
  )

val CONSTRAINT_WIDGET_SECTION_ITEMS: Array<WidgetItem> =
  CONSTRAINT_ATTRIBUTES_ITEMS +
    DIMENSION_ATTRIBUTES_ITEMS +
    CONSTRAINT_CIRCLE_ATTRIBUTES_ITEMS +
    CONSTRAINT_GUIDELINE_ATTRIBUTES_ITEMS +
    CONSTRAINT_WIDTH_ATTRIBUTES_ITEMS +
    CONSTRAINT_HEIGHT_ATTRIBUTES_ITEMS +
    CONSTRAINT_CHAIN_STYLE_ATTRIBUTE_ITEMS +
    CONSTRAINT_BARRIER_ATTRIBUTES_ITEMS
