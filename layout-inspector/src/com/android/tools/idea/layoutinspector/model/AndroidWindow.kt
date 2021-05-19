/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.model

import com.android.tools.layoutinspector.proto.LayoutInspectorProto
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull

/**
 * Container for window-level information in the layout inspector.
 * [refreshImagesCallback] should be called when e.g. the zoom level changes, to regenerate the images associated with this window's view
 * tree, if necessary.
 */
class AndroidWindow(
  val root: ViewNode,
  val id: Any,
  var imageType: LayoutInspectorProto.ComponentTreeEvent.PayloadType = LayoutInspectorProto.ComponentTreeEvent.PayloadType.SKP,
  var payloadId: Int = 0,
  private var refreshImagesCallback: ((Double, AndroidWindow) -> Unit)? = null
) {

  val isDimBehind: Boolean
    get() = root.isDimBehind

  val width: Int
    get() = root.width

  val height: Int
    get() = root.height

  // TODO: find a way to achieve the behavior allowed by this in a cleaner fashion
  var hasSubImages = calculateHasSubimages()
    private set

  fun refreshImages(scale: Double) {
    refreshImagesCallback?.invoke(scale, this)
    hasSubImages = calculateHasSubimages()
  }

  private fun calculateHasSubimages(): Boolean =
    ViewNode.readDrawChildren { drawChildren ->
      root.flatten().minus(root).any { it.drawChildren().firstIsInstanceOrNull<DrawViewImage>() != null }
    }
}
