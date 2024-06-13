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
package com.android.tools.idea.uibuilder.handlers.linear

import com.android.tools.idea.common.model.NlAttributesHolder
import com.android.tools.idea.common.scene.Placeholder
import com.android.tools.idea.common.scene.Region
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.SnappingInfo
import java.awt.Point

const val SIZE = 8

object LinearPlaceholderFactory {

  @JvmStatic
  fun createVerticalPlaceholder(
    host: SceneComponent,
    anchor: SceneComponent?,
    snappedY: Int,
    left: Int,
    right: Int,
  ): Placeholder = VerticalPlaceholder(host, anchor, snappedY, left, right)

  @JvmStatic
  fun createHorizontalPlaceholder(
    host: SceneComponent,
    anchor: SceneComponent?,
    snappedX: Int,
    top: Int,
    bottom: Int,
  ): Placeholder = HorizontalPlaceholder(host, anchor, snappedX, top, bottom)

  private abstract class LinearPlaceholder(
    host: SceneComponent,
    private val anchor: SceneComponent?,
  ) : Placeholder(host) {

    override fun findNextSibling(
      appliedComponent: SceneComponent,
      newParent: SceneComponent,
    ): SceneComponent? = anchor

    override fun updateAttribute(sceneComponent: SceneComponent, attributes: NlAttributesHolder) =
      Unit
  }

  private class VerticalPlaceholder(
    host: SceneComponent,
    anchor: SceneComponent?,
    private val snappedY: Int,
    left: Int,
    right: Int,
  ) : LinearPlaceholder(host, anchor) {
    override val region = Region(left, snappedY - SIZE, right, snappedY + SIZE, host.depth)

    override fun snap(info: SnappingInfo, retPoint: Point): Boolean {
      val r = region
      if (r.contains(info.centerX, info.centerY)) {
        retPoint.x = info.left
        retPoint.y = snappedY - (info.centerY - info.top)
        return true
      }
      return false
    }
  }

  private class HorizontalPlaceholder(
    host: SceneComponent,
    anchor: SceneComponent?,
    private val snappedX: Int,
    top: Int,
    bottom: Int,
  ) : LinearPlaceholder(host, anchor) {
    override val region = Region(snappedX - SIZE, top, snappedX + SIZE, bottom, host.depth)

    override fun snap(info: SnappingInfo, retPoint: Point): Boolean {
      val r = region
      if (r.contains(info.centerX, info.centerY)) {
        retPoint.x = snappedX - (info.centerX - info.left)
        retPoint.y = info.top
        return true
      }
      return false
    }
  }
}
