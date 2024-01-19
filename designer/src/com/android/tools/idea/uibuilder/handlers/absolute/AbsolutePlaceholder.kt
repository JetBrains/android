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
package com.android.tools.idea.uibuilder.handlers.absolute

import com.android.SdkConstants
import com.android.tools.idea.common.model.NlAttributesHolder
import com.android.tools.idea.common.scene.Placeholder
import com.android.tools.idea.common.scene.Region
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.SnappingInfo
import java.awt.Point
import java.util.Locale

class AbsolutePlaceholder(host: SceneComponent) : Placeholder(host) {

  override val dominate = false

  override val region =
    Region(
      host.drawX,
      host.drawY,
      host.drawX + host.drawWidth,
      host.drawY + host.drawHeight,
      host.depth,
    )

  override fun snap(info: SnappingInfo, retPoint: Point): Boolean {
    if (region.contains(info.centerX, info.centerY)) {
      retPoint.x = info.left
      retPoint.y = info.top
      return true
    }
    return false
  }

  override fun updateAttribute(sceneComponent: SceneComponent, attributes: NlAttributesHolder) {
    val positionX =
      String.format(Locale.US, SdkConstants.VALUE_N_DP, sceneComponent.drawX - host.drawX)
    val positionY =
      String.format(Locale.US, SdkConstants.VALUE_N_DP, sceneComponent.drawY - host.drawY)
    attributes.setAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_X, positionX)
    attributes.setAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_Y, positionY)
  }
}
