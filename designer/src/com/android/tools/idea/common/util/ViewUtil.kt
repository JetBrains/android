/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.common.util

import android.view.View
import android.view.ViewGroup
import java.awt.Dimension

/**
 * Update the layout parameters of the given view object to the new size. It's needed to override
 * any values(width/height) that were set in Preview annotation.
 *
 * View object is actual android.view.View (or child class) object.
 *
 * @param viewObj the view object whose layout parameters will be updated.
 * @param newDeviceSize the new size of the device.
 */
fun updateLayoutParams(viewObject: Any, newDeviceSize: Dimension) {
  val view =
    viewObject as? View ?: throw IllegalArgumentException("viewObject is expected to be View")
  val layoutParams = view.layoutParams
  layoutParams.width = newDeviceSize.width
  layoutParams.height = newDeviceSize.height
}

/**
 * Update the layout parameters of the given view object to WRAP_CONTENT. It's needed to override
 * any values(width/height).
 *
 * View object is actual android.view.View (or child class) object.
 *
 * @param viewObj the view object whose layout parameters will be updated.
 */
fun updateLayoutParamsToWrapContent(viewObject: Any) {
  updateLayoutParams(
    viewObject,
    Dimension(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT),
  )
}
