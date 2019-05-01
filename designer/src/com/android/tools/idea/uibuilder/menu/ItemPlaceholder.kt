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
import com.android.tools.idea.common.model.NlAttributesHolder
import com.android.tools.idea.common.scene.Placeholder
import com.android.tools.idea.common.scene.Region
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.SnappingInfo
import com.android.tools.idea.uibuilder.handlers.relative.targets.drawLeft
import com.android.tools.idea.uibuilder.handlers.relative.targets.drawTop
import java.awt.Point

class ItemPlaceholder(host: SceneComponent) : Placeholder(host) {

  override val region = Region(host.drawLeft, host.drawTop, host.drawLeft + host.drawWidth, host.drawTop + host.drawHeight, host.depth)

  override fun snap(info: SnappingInfo, retPoint: Point): Boolean {
    if (info.tag == SdkConstants.TAG_MENU && host.children.none { it.authoritativeNlComponent.tagName == SdkConstants.TAG_MENU }) {
      // Only <menu> can be put into <item>. Moreover, <item> can only have one <menu> in it.
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
