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
package com.android.tools.idea.uibuilder.handlers.frame

import com.android.tools.idea.common.model.NlAttributesHolder
import com.android.tools.idea.common.scene.Placeholder
import com.android.tools.idea.common.scene.Region
import com.android.tools.idea.common.scene.SceneComponent
import java.awt.Point

/**
 * The region of [FramePlaceholder] covered the area of FrameLayout.
 */
class FramePlaceholder(host: SceneComponent,
                       private val left: Int,
                       private val top: Int,
                       private val right: Int,
                       private val bottom: Int
) : Placeholder(host) {

  override val region = Region(left, top, right, bottom, host.depth)

  override fun snap(left: Int, top: Int, right: Int, bottom: Int, retPoint: Point): Boolean {
    val centerX = (left + right) / 2
    val centerY = (top + bottom) / 2
    if (centerX in this.left..this.right && centerY in this.top..this.bottom) {
      retPoint.x = left
      retPoint.y = top
      return true
    }
    return false
  }

  override fun updateAttribute(sceneComponent: SceneComponent, attributes: NlAttributesHolder) = Unit
}
