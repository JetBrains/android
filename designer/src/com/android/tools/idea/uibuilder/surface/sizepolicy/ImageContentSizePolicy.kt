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

import com.android.tools.idea.common.model.Coordinates
import com.android.tools.idea.uibuilder.surface.ScreenView
import java.awt.Dimension

/**
 * [ImageContentSizePolicy] that obtains the size from the image render result if available. If not
 * available, it obtains the size from the given delegate.
 */
class ImageContentSizePolicy(private val sizePolicyDelegate: ContentSizePolicy) :
  ContentSizePolicy {
  private var cachedDimension: Dimension? = null

  override fun measure(screenView: ScreenView, outDimension: Dimension) {
    val result = screenView.sceneManager.renderResult
    val contentSize = result?.rootViewDimensions

    if (contentSize != null) {
      try {
        outDimension.setSize(
          Coordinates.pxToDp(screenView, contentSize.width),
          Coordinates.pxToDp(screenView, contentSize.height),
        )

        // Save in case a future render fails. This way we can keep a constant size for failed
        // renders.
        if (cachedDimension == null) {
          cachedDimension = Dimension(outDimension)
        } else {
          cachedDimension!!.size = outDimension
        }
        return
      } catch (ignored: AssertionError) {}
    }

    if (cachedDimension != null) {
      outDimension.size = cachedDimension
      return
    }

    sizePolicyDelegate.measure(screenView, outDimension)
  }
}
