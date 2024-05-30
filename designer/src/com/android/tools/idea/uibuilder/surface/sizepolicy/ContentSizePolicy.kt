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
package com.android.tools.idea.uibuilder.surface.sizepolicy

import com.android.tools.idea.uibuilder.surface.ScreenView
import com.android.tools.rendering.RenderResult
import java.awt.Dimension

/** Policy for determining the content size of a [ScreenView]. */
interface ContentSizePolicy {
  /**
   * Called by the [ScreenView] when it needs to be measured.
   *
   * @param screenView The [ScreenView] to measure.
   * @param outDimension A [Dimension] to return the size.
   */
  fun measure(screenView: ScreenView, outDimension: Dimension)

  /**
   * Called by the [ScreenView] when it needs to check the content size.
   *
   * @return true if content size is determined.
   */
  fun hasContentSize(screenView: ScreenView): Boolean {
    if (!screenView.isVisible) {
      return false
    }
    val result = screenView.sceneManager.renderResult
    return result != null && !isErrorResult(result)
  }

  /** Returns if the given [RenderResult] is for an error in Layoutlib. */
  private fun isErrorResult(result: RenderResult): Boolean {
    // If the RenderResult does not have an image, then we probably have an error. If we do,
    // Layoutlib will
    // sometimes return images of 1x1 when exceptions happen. Try to determine if that's the case
    // here.
    val image = result.renderedImage
    return result.logger.hasErrors() && (!image.isValid || image.width * image.height < 2)
  }
}
