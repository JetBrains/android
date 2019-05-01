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
package com.android.tools.idea.uibuilder.menu

import com.android.SdkConstants
import com.android.tools.idea.common.model.Coordinates
import com.android.tools.idea.common.model.NlAttributesHolder
import com.android.tools.idea.common.scene.Placeholder
import com.android.tools.idea.common.scene.Region
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.SnappingInfo
import com.android.tools.idea.uibuilder.handlers.relative.targets.drawTop
import java.awt.Point

class MenuPlaceholder(host: SceneComponent) : Placeholder(host) {

  private val parentItem = host.parent
  private val sceneView = host.scene.sceneManager.sceneView

  override val dominate: Boolean = parentItem != null

  override val region: Region
    get() {
      if (parentItem == null) {
        val left = Coordinates.getAndroidXDip(sceneView, sceneView.x)
        val top = Coordinates.getAndroidYDip(sceneView, sceneView.y)
        val size = sceneView.size
        val width = Coordinates.getAndroidDimensionDip(sceneView, size.width)
        val height = Coordinates.getAndroidDimensionDip(sceneView, size.height)
        return Region(left, top, left + width, top + height, host.depth)
      }
      else {
        val width = parentItem.drawWidth / 4
        val left = parentItem.drawX + parentItem.drawWidth - width
        val right = parentItem.drawX + parentItem.drawWidth
        return Region(left, parentItem.drawTop, right, parentItem.drawTop + parentItem.drawHeight, host.depth)
      }
    }

  override fun snap(info: SnappingInfo, retPoint: Point): Boolean {
    if (info.tag == SdkConstants.TAG_ITEM || info.tag == SdkConstants.TAG_CATEGORY) {
      if (region.contains(info.centerX, info.centerY)) {
        retPoint.x = info.left
        retPoint.y = info.top
        return true
      }
    }
    return false
  }

  override fun updateAttribute(sceneComponent: SceneComponent, attributes: NlAttributesHolder) = Unit
}
