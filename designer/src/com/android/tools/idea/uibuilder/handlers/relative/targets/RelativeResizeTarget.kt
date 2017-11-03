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
package com.android.tools.idea.uibuilder.handlers.relative.targets

import com.android.tools.idea.common.model.AndroidDpCoordinate
import com.android.tools.idea.common.model.AttributesTransaction
import com.android.tools.idea.uibuilder.scene.target.ResizeWithSnapBaseTarget

import com.android.SdkConstants.*
import com.android.tools.idea.uibuilder.scene.target.ResizeBaseTarget
import com.android.tools.idea.uibuilder.scene.target.ResizeBaseTarget.Type.*

/**
 * Target to handle the resizing of RelativeLayout's children
 * TODO?: Can we share instance of this class?
 */
class RelativeResizeTarget(type: ResizeBaseTarget.Type) : ResizeWithSnapBaseTarget(type) {

  private fun updateWidth(attributes: AttributesTransaction, width: String) =
      attributes.setAttribute(ANDROID_URI, ATTR_LAYOUT_WIDTH, width)

  private fun updateHeight(attributes: AttributesTransaction, height: String) =
      attributes.setAttribute(ANDROID_URI, ATTR_LAYOUT_HEIGHT, height)

  override fun updateAttributes(attributes: AttributesTransaction, x: Int, y: Int) {

    if (TOP == type || LEFT_TOP == type || RIGHT_TOP == type) {
      updateHeight(attributes, getNewHeight(y))
    }

    if (BOTTOM == type || LEFT_BOTTOM == type || RIGHT_BOTTOM == type) {
      updateHeight(attributes, getNewHeight(y))
    }

    if (LEFT == type || LEFT_BOTTOM == type || LEFT_TOP == type) {
      updateWidth(attributes, getNewWidth(x))
    }

    if (RIGHT == type || RIGHT_BOTTOM == type || RIGHT_TOP == type) {
      updateWidth(attributes, getNewWidth(x))
    }
  }
}
