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
package com.android.tools.idea.common.scene

import java.awt.Rectangle

open class DefaultHitProvider : HitProvider {
  private val rect = Rectangle()

  override fun addHit(
    component: SceneComponent,
    sceneTransform: SceneContext,
    picker: ScenePicker,
  ) {
    component.fillRect(rect)

    picker.addRect(
      component,
      0,
      sceneTransform.getSwingXDip(rect.x.toFloat()),
      sceneTransform.getSwingYDip(rect.y.toFloat()),
      sceneTransform.getSwingXDip((rect.x + rect.width).toFloat()),
      sceneTransform.getSwingYDip((rect.y + rect.height).toFloat()),
    )
  }

  override fun intersects(
    component: SceneComponent,
    sceneTransform: SceneContext,
    rectangle: Rectangle,
  ): Boolean {
    component.fillRect(rect)
    return rectangle.intersects(rect)
  }
}
