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
package com.android.tools.idea.uibuilder.handlers.frame

import com.android.SdkConstants.*
import com.android.sdklib.AndroidDpCoordinate
import com.android.tools.idea.common.model.NlAttributesHolder
import com.android.tools.idea.uibuilder.scene.target.ResizeWithSnapBaseTarget

open class FrameResizeTarget(type: Type) : ResizeWithSnapBaseTarget(type) {

  override fun updateAttributes(
    attributes: NlAttributesHolder,
    @AndroidDpCoordinate x: Int,
    @AndroidDpCoordinate y: Int,
  ) {
    when (myType) {
      Type.LEFT,
      Type.RIGHT -> updateWidth(attributes, getNewWidth(x))
      Type.TOP,
      Type.BOTTOM -> updateHeight(attributes, getNewHeight(y))
      Type.LEFT_TOP,
      Type.LEFT_BOTTOM,
      Type.RIGHT_TOP,
      Type.RIGHT_BOTTOM -> {
        updateWidth(attributes, getNewWidth(x))
        updateHeight(attributes, getNewHeight(y))
      }
    }
  }

  private fun updateWidth(attributes: NlAttributesHolder, width: String) =
    attributes.setAttribute(ANDROID_URI, ATTR_LAYOUT_WIDTH, width)

  private fun updateHeight(attributes: NlAttributesHolder, height: String) =
    attributes.setAttribute(ANDROID_URI, ATTR_LAYOUT_HEIGHT, height)
}
