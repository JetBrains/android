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

import com.google.errorprone.annotations.OverridingMethodsMustInvokeSuper
import layoutinspector.view.inspection.LayoutInspectorViewProtocol
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull

/**
 * Container for window-level information in the layout inspector.
 * [refreshImages] should be called when e.g. the zoom level changes, to regenerate the images associated with this window's view tree, if
 * necessary.
 *
 * @param root The root view node associated with this layout tree.
 * @param id An arbitrary ID, which can be any unique value, the details of which are left up to each implementing class.
 * @param imageType The type of image backing this window's screenshot. Note that this value is mutable and may change after receiving new
 *     layout events.
 */
// TODO(b/177374701): Investigate separating the response parsing logic from the window model data
abstract class AndroidWindow(
  val root: ViewNode,
  val id: Any,
  imageType: ImageType
) {
  var imageType: ImageType = imageType
    private set

  enum class ImageType(val protoType: LayoutInspectorViewProtocol.Screenshot.Type) {
    UNKNOWN(LayoutInspectorViewProtocol.Screenshot.Type.UNKNOWN),

    /**
     * The image associated with this window is a SKIA picture
     */
    SKP(LayoutInspectorViewProtocol.Screenshot.Type.SKP),

    /**
     * The image associated with this window is a PNG (which we requested)
     */
    BITMAP_AS_REQUESTED(LayoutInspectorViewProtocol.Screenshot.Type.BITMAP),
  }

  val isDimBehind: Boolean
    get() = root.isDimBehind

  val width: Int
    get() = root.width

  val height: Int
    get() = root.height

  // TODO: find a way to achieve the behavior allowed by this in a cleaner fashion
  var hasSubImages = calculateHasSubimages()
    private set

  @OverridingMethodsMustInvokeSuper
  open fun copyFrom(other: AndroidWindow) {
    imageType = other.imageType
  }

  fun refreshImages(scale: Double) {
    doRefreshImages(scale)
    hasSubImages = calculateHasSubimages()
  }

  /**
   * Method triggered whenever the rendering of the window should change, for example because of a change in scale or because a new
   * screenshot is ready.
   *
   * Subclasses are expected to respect this window's [imageType] and call [ViewNode.writeDrawChildren] to generate draw results into
   * [ViewNode.drawChildren].
   */
  protected abstract fun doRefreshImages(scale: Double)

  private fun calculateHasSubimages(): Boolean =
    ViewNode.readDrawChildren { drawChildren ->
      root.flatten().minus(root).any { it.drawChildren().firstIsInstanceOrNull<DrawViewImage>() != null }
    }
}
