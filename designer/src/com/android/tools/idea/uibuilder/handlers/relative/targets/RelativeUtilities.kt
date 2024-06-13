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
package com.android.tools.idea.uibuilder.handlers.relative.targets

import com.android.SdkConstants
import com.android.tools.idea.common.model.NlAttributesHolder
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.refactoring.rtl.RtlSupportProcessor
import com.android.tools.idea.uibuilder.model.TextDirection
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.google.common.collect.ImmutableList
import java.util.Locale

/** Get the proper attributes for real layout file, which consider minimal SDK and target SDK. */
internal fun getProperAttributesForLayout(
  component: SceneComponent,
  attribute: String?,
): List<String> {
  val rtlDirection =
    if (component.scene.isInRTL) TextDirection.RIGHT_TO_LEFT else TextDirection.LEFT_TO_RIGHT

  if (attribute == null) {
    return emptyList()
  }
  val builder = ImmutableList.Builder<String>()

  val viewEditor = (component.scene.sceneManager as LayoutlibSceneManager).viewEditor
  if (viewEditor.minSdkVersion.apiLevel < RtlSupportProcessor.RTL_TARGET_SDK_START) {
    builder.add(attribute)
  }

  if (viewEditor.targetSdkVersion.apiLevel >= RtlSupportProcessor.RTL_TARGET_SDK_START) {
    val rtlAttribute =
      when (attribute) {
        SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_LEFT -> rtlDirection.attrAlignParentLeft
        SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_RIGHT -> rtlDirection.attrAlignParentRight
        SdkConstants.ATTR_LAYOUT_ALIGN_LEFT -> rtlDirection.attrLeft
        SdkConstants.ATTR_LAYOUT_ALIGN_RIGHT -> rtlDirection.attrRight
        SdkConstants.ATTR_LAYOUT_TO_LEFT_OF -> rtlDirection.attrLeftOf
        SdkConstants.ATTR_LAYOUT_TO_RIGHT_OF -> rtlDirection.attrRightOf
        SdkConstants.ATTR_LAYOUT_MARGIN_LEFT -> rtlDirection.attrMarginLeft
        SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT -> rtlDirection.attrMarginRight
        else -> attribute
      }
    builder.add(rtlAttribute)
  }

  return builder.build()
}

// Helper extension functions to increase readability
internal val SceneComponent.drawLeft: Int
  get() = drawX
internal val SceneComponent.drawTop: Int
  get() = drawY
internal val SceneComponent.drawRight: Int
  get() = drawX + drawWidth
internal val SceneComponent.drawBottom: Int
  get() = drawY + drawHeight
internal val SceneComponent.drawCenterX: Int
  get() = drawX + drawWidth / 2
internal val SceneComponent.drawCenterY: Int
  get() = drawY + drawHeight / 2

internal fun Int.toDpString(): String = String.format(Locale.US, SdkConstants.VALUE_N_DP, this)

internal fun updateAlignAttribute(
  component: SceneComponent,
  attributes: NlAttributesHolder,
  value: Int,
  rules: AlignAttributeRules,
) {
  val parent = component.parent!!
  if (attributes.getAndroidAttribute(rules.alignParentAttribute) == SdkConstants.VALUE_TRUE) {
    attributes.setAndroidAttribute(
      rules.marginAttribute,
      String.format(Locale.US, SdkConstants.VALUE_N_DP, rules.alignParentRule(parent, value)),
    )
    return
  }

  for ((id, rule) in rules.alignWidgetRules) {
    val alignWidget = NlComponent.extractId(attributes.getAndroidAttribute(id))
    if (alignWidget != null) {
      val alignedComponent = parent.getSceneComponent(alignWidget) ?: return
      attributes.setAndroidAttribute(
        rules.marginAttribute,
        String.format(Locale.US, SdkConstants.VALUE_N_DP, rule(alignedComponent, value)),
      )
      return
    }
  }
}

/**
 * Used to defined the rules for margin with constraints.
 *
 * @param marginAttribute The margin this rule applies for
 * @param alignParentAttribute The constraint of this rule if the applies component is parent
 * @param alignParentRule The function to calculate the value of margin if the applies component is
 *   parent
 * @param alignWidgetRules The list of aligning attributes and their associated function to
 *   calculate the margin value
 */
internal class AlignAttributeRules(
  val marginAttribute: String,
  val alignParentAttribute: String,
  val alignParentRule: (SceneComponent, Int) -> Int,
  vararg val alignWidgetRules: Pair<String, (SceneComponent, Int) -> Int>,
)

internal val TOP_ATTRIBUTE_RULES =
  AlignAttributeRules(
    SdkConstants.ATTR_LAYOUT_MARGIN_TOP,
    SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_TOP,
    { parent, coordinateY -> coordinateY - parent.drawTop },
    SdkConstants.ATTR_LAYOUT_ALIGN_TOP to { aligned, coordinateY -> coordinateY - aligned.drawTop },
    SdkConstants.ATTR_LAYOUT_BELOW to { aligned, coordinateY -> coordinateY - aligned.drawBottom },
  )

internal val BOTTOM_ATTRIBUTE_RULES =
  AlignAttributeRules(
    SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM,
    SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_BOTTOM,
    { parent, coordinateY -> parent.drawBottom - coordinateY },
    SdkConstants.ATTR_LAYOUT_ALIGN_BOTTOM to
      { aligned, coordinateY ->
        aligned.drawBottom - coordinateY
      },
    SdkConstants.ATTR_LAYOUT_ABOVE to { aligned, coordinateY -> aligned.drawTop - coordinateY },
  )

internal val START_ATTRIBUTE_RULES =
  AlignAttributeRules(
    SdkConstants.ATTR_LAYOUT_MARGIN_START,
    SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_START,
    { parent, coordinateX -> coordinateX - parent.drawLeft },
    SdkConstants.ATTR_LAYOUT_ALIGN_START to
      { aligned, coordinateX ->
        coordinateX - aligned.drawLeft
      },
    SdkConstants.ATTR_LAYOUT_TO_END_OF to
      { aligned, coordinateX ->
        coordinateX - aligned.drawRight
      },
  )

internal val RTL_START_ATTRIBUTE_RULES =
  AlignAttributeRules(
    SdkConstants.ATTR_LAYOUT_MARGIN_START,
    SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_START,
    { parent, coordinateX -> parent.drawRight - coordinateX },
    SdkConstants.ATTR_LAYOUT_ALIGN_START to
      { aligned, coordinateX ->
        aligned.drawRight - coordinateX
      },
    SdkConstants.ATTR_LAYOUT_TO_END_OF to { aligned, coordinateX -> aligned.drawLeft - coordinateX },
  )

internal val LEFT_ATTRIBUTE_RULES =
  AlignAttributeRules(
    SdkConstants.ATTR_LAYOUT_MARGIN_LEFT,
    SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_LEFT,
    { parent, coordinateX -> coordinateX - parent.drawLeft },
    SdkConstants.ATTR_LAYOUT_ALIGN_LEFT to
      { aligned, coordinateX ->
        coordinateX - aligned.drawLeft
      },
    SdkConstants.ATTR_LAYOUT_TO_RIGHT_OF to
      { aligned, coordinateX ->
        coordinateX - aligned.drawRight
      },
  )

internal val END_ATTRIBUTE_RULES =
  AlignAttributeRules(
    SdkConstants.ATTR_LAYOUT_MARGIN_END,
    SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_END,
    { parent, coordinateX -> parent.drawRight - coordinateX },
    SdkConstants.ATTR_LAYOUT_ALIGN_END to
      { aligned, coordinateX ->
        aligned.drawRight - coordinateX
      },
    SdkConstants.ATTR_LAYOUT_TO_START_OF to
      { aligned, coordinateX ->
        aligned.drawLeft - coordinateX
      },
  )

internal val RTL_END_ATTRIBUTE_RULES =
  AlignAttributeRules(
    SdkConstants.ATTR_LAYOUT_MARGIN_END,
    SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_END,
    { parent, coordinateX -> coordinateX - parent.drawLeft },
    SdkConstants.ATTR_LAYOUT_ALIGN_END to
      { aligned, coordinateX ->
        coordinateX - aligned.drawLeft
      },
    SdkConstants.ATTR_LAYOUT_TO_START_OF to
      { aligned, coordinateX ->
        coordinateX - aligned.drawRight
      },
  )

internal val RIGHT_ATTRIBUTE_RULES =
  AlignAttributeRules(
    SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT,
    SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_RIGHT,
    { parent, coordinateX -> parent.drawRight - coordinateX },
    SdkConstants.ATTR_LAYOUT_ALIGN_RIGHT to
      { aligned, coordinateX ->
        aligned.drawRight - coordinateX
      },
    SdkConstants.ATTR_LAYOUT_TO_LEFT_OF to
      { aligned, coordinateX ->
        aligned.drawLeft - coordinateX
      },
  )

internal val TOP_ALIGN_ATTRIBUTES =
  arrayOf(
    SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_TOP,
    SdkConstants.ATTR_LAYOUT_ALIGN_TOP,
    SdkConstants.ATTR_LAYOUT_BELOW,
  )

internal val BOTTOM_ALIGN_ATTRIBUTES =
  arrayOf(
    SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_BOTTOM,
    SdkConstants.ATTR_LAYOUT_ALIGN_BOTTOM,
    SdkConstants.ATTR_LAYOUT_ABOVE,
  )

internal val START_ALIGN_ATTRIBUTES =
  arrayOf(
    SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_START,
    SdkConstants.ATTR_LAYOUT_ALIGN_START,
    SdkConstants.ATTR_LAYOUT_TO_END_OF,
  )

internal val LEFT_ALIGN_ATTRIBUTES =
  arrayOf(
    SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_LEFT,
    SdkConstants.ATTR_LAYOUT_ALIGN_LEFT,
    SdkConstants.ATTR_LAYOUT_TO_RIGHT_OF,
  )

internal val END_ALIGN_ATTRIBUTES =
  arrayOf(
    SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_END,
    SdkConstants.ATTR_LAYOUT_ALIGN_END,
    SdkConstants.ATTR_LAYOUT_TO_START_OF,
  )

internal val RIGHT_ALIGN_ATTRIBUTES =
  arrayOf(
    SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_RIGHT,
    SdkConstants.ATTR_LAYOUT_ALIGN_RIGHT,
    SdkConstants.ATTR_LAYOUT_TO_LEFT_OF,
  )

internal val RELATIVE_LAYOUT_ATTRIBUTES =
  TOP_ALIGN_ATTRIBUTES +
    BOTTOM_ALIGN_ATTRIBUTES +
    START_ALIGN_ATTRIBUTES +
    LEFT_ALIGN_ATTRIBUTES +
    END_ALIGN_ATTRIBUTES +
    RIGHT_ALIGN_ATTRIBUTES +
    SdkConstants.ATTR_LAYOUT_CENTER_HORIZONTAL +
    SdkConstants.ATTR_LAYOUT_CENTER_VERTICAL +
    SdkConstants.ATTR_LAYOUT_CENTER_IN_PARENT
