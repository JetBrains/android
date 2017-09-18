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
package com.android.tools.idea.uibuilder.handlers.relative.targets

import com.android.tools.idea.common.model.AttributesTransaction
import com.android.tools.idea.uibuilder.scene.target.ResizeWithSnapBaseTarget

import com.android.SdkConstants.*
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.refactoring.rtl.RtlSupportProcessor
import com.android.tools.idea.uibuilder.scene.target.ResizeBaseTarget
import com.android.tools.idea.uibuilder.scene.target.ResizeBaseTarget.Type.*

/**
 * Target to handle the resizing of RelativeLayout's children
 * TODO: Handle the resizing in RTL mode
 */
class RelativeResizeTarget(type: ResizeBaseTarget.Type) : ResizeWithSnapBaseTarget(type) {

  private fun updateTopHeight(attributes: AttributesTransaction, y: Int, parent: SceneComponent) {
    val stripY = maxOf(parent.drawTop, minOf(y, myStartY2))

    attributes.setAndroidAttribute(ATTR_LAYOUT_HEIGHT, getNewHeight(stripY))
    updatAlignAttribute(attributes, stripY, topAttributeHandler)
  }

  private fun updateBottomHeight(attributes: AttributesTransaction, y: Int, parent: SceneComponent) {
    val stripY = maxOf(myStartY1, minOf(y, parent.drawBottom))

    attributes.setAndroidAttribute(ATTR_LAYOUT_HEIGHT, getNewHeight(stripY))
    updatAlignAttribute(attributes, stripY, bottomAttributeHandler)
  }

  private fun updateLeftWidth(attributes: AttributesTransaction, x: Int, parent: SceneComponent) {
    val stripX = maxOf(parent.drawLeft, minOf(x, myStartX2))

    attributes.setAndroidAttribute(ATTR_LAYOUT_WIDTH, getNewWidth(stripX))
    if (myComponent.scene.renderedApiLevel >= RtlSupportProcessor.RTL_TARGET_SDK_START) {
      updatAlignAttribute(attributes, stripX, startAttributeHandler)
    }
    updatAlignAttribute(attributes, stripX, leftAttributeHandler)
  }

  private fun updateRightWidth(attributes: AttributesTransaction, x: Int, parent: SceneComponent) {
    val stripX = maxOf(myStartX1, minOf(x, parent.drawRight))

    attributes.setAndroidAttribute(ATTR_LAYOUT_WIDTH, getNewWidth(stripX))
    if (myComponent.scene.renderedApiLevel >= RtlSupportProcessor.RTL_TARGET_SDK_START) {
      updatAlignAttribute(attributes, stripX, endAttributeHandler)
    }
    updatAlignAttribute(attributes, stripX, rightAttributeHandler)
  }

  private fun updatAlignAttribute(attributes: AttributesTransaction, value: Int, rules: AlignAttributeRules) {
    val parent = myComponent.parent!!
    if (attributes.getAndroidAttribute(rules.alignParentAttribute) == "true") {
      attributes.setAndroidAttribute(rules.marginAttribute, String.format(VALUE_N_DP, rules.alignParentRule(parent, value)))
      return
    }

    for ((id, rule) in rules.alignWidgetRules) {
      val alignWidget = NlComponent.extractId(attributes.getAndroidAttribute(id))
      if (alignWidget != null) {
        val alignedComponent = parent.getSceneComponent(alignWidget) ?: return
        attributes.setAndroidAttribute(rules.marginAttribute,
            String.format(VALUE_N_DP, rule(alignedComponent, value)))
        return
      }
    }
  }

  override fun updateAttributes(attributes: AttributesTransaction, x: Int, y: Int) {
    val parent = myComponent.parent ?: return

    val margins = attributes.getAndroidAttribute(ATTR_LAYOUT_MARGIN)
    if (margins != null) {
      // If has margin, split it to specified directions.
      attributes.setAndroidAttribute(ATTR_LAYOUT_MARGIN, null)
      attributes.setAndroidAttribute(ATTR_LAYOUT_MARGIN_LEFT, margins)
      attributes.setAndroidAttribute(ATTR_LAYOUT_MARGIN_RIGHT, margins)
      if (myComponent.scene.renderedApiLevel >= RtlSupportProcessor.RTL_TARGET_SDK_START) {
        attributes.setAndroidAttribute(ATTR_LAYOUT_MARGIN_START, margins)
        attributes.setAndroidAttribute(ATTR_LAYOUT_MARGIN_END, margins)
      }
      attributes.setAndroidAttribute(ATTR_LAYOUT_MARGIN_TOP, margins)
      attributes.setAndroidAttribute(ATTR_LAYOUT_MARGIN_BOTTOM, margins)
    }

    if (TOP == type || LEFT_TOP == type || RIGHT_TOP == type) {
      updateTopHeight(attributes, y, parent)
    }

    if (BOTTOM == type || LEFT_BOTTOM == type || RIGHT_BOTTOM == type) {
      updateBottomHeight(attributes, y, parent)
    }

    if (LEFT == type || LEFT_BOTTOM == type || LEFT_TOP == type) {
      updateLeftWidth(attributes, x, parent)
    }

    if (RIGHT == type || RIGHT_BOTTOM == type || RIGHT_TOP == type) {
      updateRightWidth(attributes, x, parent)
    }
  }
}

// Helper function to increase readibility
private val SceneComponent.drawLeft: Int
  get() = drawX
private val SceneComponent.drawTop: Int
  get() = drawY
private val SceneComponent.drawRight: Int
  get() = drawX + drawWidth
private val SceneComponent.drawBottom: Int
  get() = drawY + drawHeight

/**
 * Used to defined the rules for margin with constraints.
 */
private class AlignAttributeRules(
    val marginAttribute: String,
    val alignParentAttribute: String, val alignParentRule: (SceneComponent, Int) -> Int,
    val alignWidgetRules: Array<Pair<String, (SceneComponent, Int) -> Int>>)

private val topAttributeHandler = AlignAttributeRules(
    ATTR_LAYOUT_MARGIN_TOP,
    ATTR_LAYOUT_ALIGN_PARENT_TOP, { parent, coordinateY -> coordinateY - parent.drawTop },
    arrayOf(
        ATTR_LAYOUT_ALIGN_TOP to { aligned, coordinateY -> coordinateY - aligned.drawTop },
        ATTR_LAYOUT_BELOW to { aligned, coordinateY -> coordinateY - aligned.drawBottom }
    )
)

private val bottomAttributeHandler = AlignAttributeRules(
    ATTR_LAYOUT_MARGIN_BOTTOM,
    ATTR_LAYOUT_ALIGN_PARENT_BOTTOM, { parent, coordinateY -> parent.drawTop + parent.drawHeight - coordinateY },
    arrayOf(
        ATTR_LAYOUT_ALIGN_BOTTOM to { aligned, coordinateY -> aligned.drawBottom - coordinateY },
        ATTR_LAYOUT_ABOVE to { aligned, coordinateY -> aligned.drawTop - coordinateY }
    )
)

private val startAttributeHandler = AlignAttributeRules(
    ATTR_LAYOUT_MARGIN_START,
    ATTR_LAYOUT_ALIGN_PARENT_START, { parent, coordinateX -> coordinateX - parent.drawLeft },
    arrayOf(
        ATTR_LAYOUT_ALIGN_START to { aligned, coordinateX -> coordinateX - aligned.drawLeft },
        ATTR_LAYOUT_TO_END_OF to { aligned, coordinateX -> coordinateX - aligned.drawRight }
    )
)

private val leftAttributeHandler = AlignAttributeRules(
    ATTR_LAYOUT_MARGIN_LEFT,
    ATTR_LAYOUT_ALIGN_PARENT_LEFT, { parent, coordinateX -> coordinateX - parent.drawLeft },
    arrayOf(
        ATTR_LAYOUT_ALIGN_LEFT to { aligned, coordinateX -> coordinateX - aligned.drawLeft },
        ATTR_LAYOUT_TO_RIGHT_OF to { aligned, coordinateX -> coordinateX - aligned.drawRight }
    )
)

private val endAttributeHandler = AlignAttributeRules(
    ATTR_LAYOUT_MARGIN_END,
    ATTR_LAYOUT_ALIGN_PARENT_END, { parent, coordinateX -> parent.drawLeft + parent.drawWidth - coordinateX },
    arrayOf(
        ATTR_LAYOUT_ALIGN_END to { aligned, coordinateX -> aligned.drawRight - coordinateX },
        ATTR_LAYOUT_TO_START_OF to { aligned, coordinateX -> aligned.drawLeft - coordinateX }
    )
)

private val rightAttributeHandler = AlignAttributeRules(
    ATTR_LAYOUT_MARGIN_RIGHT,
    ATTR_LAYOUT_ALIGN_PARENT_RIGHT, { parent, coordinateX -> parent.drawLeft + parent.drawWidth - coordinateX },
    arrayOf(
        ATTR_LAYOUT_ALIGN_RIGHT to { aligned, coordinateX -> aligned.drawRight - coordinateX },
        ATTR_LAYOUT_TO_LEFT_OF to { aligned, coordinateX -> aligned.drawLeft - coordinateX }
    )
)
