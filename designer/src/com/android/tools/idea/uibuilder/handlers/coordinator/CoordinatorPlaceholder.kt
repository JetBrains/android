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
package com.android.tools.idea.uibuilder.handlers.coordinator

import com.android.SdkConstants
import com.android.tools.idea.common.model.NlAttributesHolder
import com.android.tools.idea.common.scene.Placeholder
import com.android.tools.idea.common.scene.Region
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.SnappingInfo
import com.android.tools.idea.uibuilder.handlers.relative.targets.drawCenterX
import com.android.tools.idea.uibuilder.handlers.relative.targets.drawCenterY
import com.android.tools.idea.uibuilder.model.ensureLiveId
import java.awt.Point

const val SIZE = 20

class CoordinatorPlaceholder(
  host: SceneComponent,
  private val anchor: SceneComponent,
  private val type: Type,
) : Placeholder(host) {

  override val associatedComponent: SceneComponent
    get() = anchor

  override fun updateAttribute(sceneComponent: SceneComponent, attributes: NlAttributesHolder) {
    attributes.setAttribute(
      SdkConstants.AUTO_URI,
      SdkConstants.ATTR_LAYOUT_ANCHOR_GRAVITY,
      getAnchorGravity(),
    )
    attributes.setAttribute(
      SdkConstants.AUTO_URI,
      SdkConstants.ATTR_LAYOUT_ANCHOR,
      SdkConstants.NEW_ID_PREFIX + anchor.nlComponent.ensureLiveId(),
    )
  }

  private val left =
    when (type) {
      Type.LEFT_TOP,
      Type.LEFT,
      Type.LEFT_BOTTOM -> anchor.drawX
      Type.TOP,
      Type.CENTER,
      Type.BOTTOM -> anchor.drawCenterX - SIZE / 2
      Type.RIGHT_TOP,
      Type.RIGHT,
      Type.RIGHT_BOTTOM -> anchor.drawX + anchor.drawWidth - SIZE
    }
  private val top =
    when (type) {
      Type.LEFT_TOP,
      Type.TOP,
      Type.RIGHT_TOP -> anchor.drawY
      Type.LEFT,
      Type.CENTER,
      Type.RIGHT -> anchor.drawCenterY - SIZE / 2
      Type.LEFT_BOTTOM,
      Type.BOTTOM,
      Type.RIGHT_BOTTOM -> anchor.drawY + anchor.drawHeight - SIZE
    }
  private val right = left + SIZE
  private val bottom = top + SIZE

  /**
   * Make the level higher than the anchor itself otherwise $CoordinatePlaceholder cannot be snapped
   * when anchor is a ViewGroup.
   */
  override val region = Region(left, top, right, bottom, anchor.depth + 1)

  override fun snap(info: SnappingInfo, retPoint: Point): Boolean {
    if (region.contains(info.centerX, info.centerY)) {
      retPoint.x = (region.left + region.right) / 2 - (info.right - info.left) / 2
      retPoint.y = (region.top + region.bottom) / 2 - (info.bottom - info.top) / 2
      return true
    }
    return false
  }

  private fun getAnchorGravity() =
    when (type) {
      Type.LEFT -> "start|center"
      Type.RIGHT -> "end|center"
      Type.TOP -> "top|center"
      Type.BOTTOM -> "bottom|center"
      Type.LEFT_TOP -> "start|top"
      Type.LEFT_BOTTOM -> "start|bottom"
      Type.CENTER -> "center"
      Type.RIGHT_TOP -> "end|top"
      Type.RIGHT_BOTTOM -> "end|bottom"
    }

  enum class Type {
    LEFT_TOP,
    LEFT,
    LEFT_BOTTOM,
    TOP,
    CENTER,
    BOTTOM,
    RIGHT_TOP,
    RIGHT,
    RIGHT_BOTTOM,
  }
}
