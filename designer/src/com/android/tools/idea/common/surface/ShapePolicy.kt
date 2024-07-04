/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.common.surface

import com.android.sdklib.devices.screenShape
import java.awt.Rectangle
import java.awt.Shape

/** Policy for determining the [Shape] of a [SceneView]. */
interface ShapePolicy {
  fun getShape(sceneView: SceneView): Shape?
}

/** A [ShapePolicy] that uses the device configuration shape. */
@JvmField
val DEVICE_CONFIGURATION_SHAPE_POLICY: ShapePolicy =
  object : ShapePolicy {
    override fun getShape(sceneView: SceneView): Shape? {
      val device = sceneView.configuration.cachedDevice ?: return null

      val size = sceneView.scaledContentSize
      val originX = sceneView.x.toDouble()
      val originY = sceneView.y.toDouble()
      return device.screenShape(originX, originY, size)
    }
  }

/** A [ShapePolicy] that a square size. The size is determined from the rendered size. */
@JvmField
val SQUARE_SHAPE_POLICY: ShapePolicy =
  object : ShapePolicy {
    override fun getShape(sceneView: SceneView): Shape {
      val size = sceneView.scaledContentSize
      return Rectangle(sceneView.x, sceneView.y, size.width, size.height)
    }
  }
