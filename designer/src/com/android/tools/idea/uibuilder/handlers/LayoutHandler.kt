/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers

import com.android.SdkConstants
import com.android.tools.idea.common.model.Coordinates
import com.android.tools.idea.common.model.NlAttributesHolder
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.scene.Placeholder
import com.android.tools.idea.common.scene.Region
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.SnappingInfo
import com.android.tools.idea.uibuilder.handlers.frame.FrameLayoutHandler
import java.awt.Point

/** Handler for the <layout> tag */
class LayoutHandler : FrameLayoutHandler() {

  override fun getTitle(tagName: String): String {
    return "<layout>"
  }

  override fun getTitle(component: NlComponent): String {
    return "<layout>"
  }

  override fun getPlaceholders(component: SceneComponent, draggedComponents: List<SceneComponent>) =
    listOf(LayoutPlaceholder(component))
}

class LayoutPlaceholder(host: SceneComponent) : Placeholder(host) {

  override val region: Region = run {
    val sceneView = host.scene.sceneManager.sceneView
    val size = sceneView.scaledContentSize
    val width = Coordinates.getAndroidDimensionDip(sceneView, size.width)
    val height = Coordinates.getAndroidDimensionDip(sceneView, size.height)
    Region(0, 0, width, height)
  }

  override fun snap(info: SnappingInfo, retPoint: Point): Boolean {
    // Only allow to add component when there is no root View. Having only <data> tag is acceptable
    // since it is not a View.
    if (!(host.nlComponent.children.any { it.tagName != SdkConstants.TAG_DATA })) {
      if (region.contains(info.centerX, info.centerY)) {
        retPoint.x = info.left
        retPoint.y = info.top
        return true
      }
    }
    return false
  }

  override fun updateAttribute(sceneComponent: SceneComponent, attributes: NlAttributesHolder) =
    Unit
}
