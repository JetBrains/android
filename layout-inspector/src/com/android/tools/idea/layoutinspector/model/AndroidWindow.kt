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

import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol
import com.google.errorprone.annotations.OverridingMethodsMustInvokeSuper
import java.awt.Shape

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
     * We received a SKP with this window but haven't parsed it yet
     */
    SKP_PENDING(LayoutInspectorViewProtocol.Screenshot.Type.SKP),

    /**
     * The SKP associated with this window has been parsed and the images merged in.
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
    get() = root.layoutBounds.width

  val height: Int
    get() = root.layoutBounds.height

  open val deviceClip: Shape? = null

  @OverridingMethodsMustInvokeSuper
  open fun copyFrom(other: AndroidWindow) {
    if (other.imageType == ImageType.SKP_PENDING && imageType == ImageType.SKP) {
      // we already have an skp merged in, don't go back to pending when we get a new one
    }
    else {
      imageType = other.imageType
    }
  }

  /**
   * Method triggered whenever the rendering of the window should change, for example because of a change in scale or because a new
   * screenshot is ready.
   *
   * Subclasses are expected to respect this window's [imageType] and call [ViewNode.writeDrawChildren] to generate draw results into
   * [ViewNode.drawChildren].
   */
  abstract fun refreshImages(scale: Double)

  fun skpLoadingComplete() {
    imageType = ImageType.SKP
  }
}
