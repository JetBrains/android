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

import com.android.SdkConstants.*
import com.android.tools.idea.common.model.NlAttributesHolder
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.refactoring.rtl.RtlSupportProcessor
import com.android.tools.idea.uibuilder.scene.target.ResizeBaseTarget
import com.android.tools.idea.uibuilder.scene.target.ResizeBaseTarget.Type.*
import com.android.tools.idea.uibuilder.scene.target.ResizeWithSnapBaseTarget

/**
 * Target to handle the resizing of RelativeLayout's children
 *
 * TODO: Handle the resizing in RTL mode
 */
class RelativeResizeTarget(type: ResizeBaseTarget.Type) : ResizeWithSnapBaseTarget(type) {

  private fun updateTopHeight(attributes: NlAttributesHolder, y: Int, parent: SceneComponent) {
    val stripY = maxOf(parent.drawTop, minOf(y, myStartY2))

    attributes.setAndroidAttribute(ATTR_LAYOUT_HEIGHT, getNewHeight(stripY))
    updateAlignAttribute(myComponent, attributes, stripY, TOP_ATTRIBUTE_RULES)
  }

  private fun updateBottomHeight(attributes: NlAttributesHolder, y: Int, parent: SceneComponent) {
    val stripY = maxOf(myStartY1, minOf(y, parent.drawBottom))

    attributes.setAndroidAttribute(ATTR_LAYOUT_HEIGHT, getNewHeight(stripY))
    updateAlignAttribute(myComponent, attributes, stripY, BOTTOM_ATTRIBUTE_RULES)
  }

  private fun updateLeftWidth(attributes: NlAttributesHolder, x: Int, parent: SceneComponent) {
    val stripX = maxOf(parent.drawLeft, minOf(x, myStartX2))

    attributes.setAndroidAttribute(ATTR_LAYOUT_WIDTH, getNewWidth(stripX))
    if (myComponent.scene.renderedApiLevel >= RtlSupportProcessor.RTL_TARGET_SDK_START) {
      updateAlignAttribute(myComponent, attributes, stripX, START_ATTRIBUTE_RULES)
    }
    updateAlignAttribute(myComponent, attributes, stripX, LEFT_ATTRIBUTE_RULES)
  }

  private fun updateRightWidth(attributes: NlAttributesHolder, x: Int, parent: SceneComponent) {
    val stripX = maxOf(myStartX1, minOf(x, parent.drawRight))

    attributes.setAndroidAttribute(ATTR_LAYOUT_WIDTH, getNewWidth(stripX))
    if (myComponent.scene.renderedApiLevel >= RtlSupportProcessor.RTL_TARGET_SDK_START) {
      updateAlignAttribute(myComponent, attributes, stripX, END_ATTRIBUTE_RULES)
    }
    updateAlignAttribute(myComponent, attributes, stripX, RIGHT_ATTRIBUTE_RULES)
  }

  override fun updateAttributes(attributes: NlAttributesHolder, x: Int, y: Int) {
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
