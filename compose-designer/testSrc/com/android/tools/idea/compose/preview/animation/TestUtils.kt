/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.animation

import androidx.compose.animation.tooling.ComposeAnimation
import androidx.compose.animation.tooling.ComposeAnimationType
import com.android.tools.idea.compose.preview.animation.timeline.ElementState
import com.android.tools.idea.compose.preview.animation.timeline.PositionProxy
import com.android.tools.idea.compose.preview.animation.timeline.TimelineElement
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Graphics2D
import javax.swing.JPanel
import kotlin.test.assertTrue

object TestUtils {
  private const val TEST_ELEMENT_WIDTH = 100
  private const val TEST_ELEMENT_HEIGHT = 10
  private const val TEST_ELEMENT_ROW_HEIGHT = 100

  /** Test [TimelineElement] with size [TEST_ELEMENT_WIDTH] x [TEST_ELEMENT_HEIGHT] */
  class TestTimelineElement(private val x: Int, private val y: Int, positionProxy: PositionProxy, state: ElementState = ElementState()) :
    TimelineElement(state, x, x + TEST_ELEMENT_WIDTH, positionProxy) {
    override fun contains(x: Int, y: Int): Boolean {
      return x in this.x + offsetPx..this.x + TEST_ELEMENT_WIDTH + offsetPx &&
             y in this.y..this.y + TEST_ELEMENT_HEIGHT
    }

    override var height = TEST_ELEMENT_ROW_HEIGHT
    override fun paint(g: Graphics2D) {
      g.fillRect(x + offsetPx, y, TEST_ELEMENT_WIDTH, TEST_ELEMENT_HEIGHT)
    }
  }

  fun testPreviewState(withCoordination: Boolean = true) = object : AnimationPreviewState {
    override fun isCoordinationAvailable() = withCoordination
  }

  /** Create [TimelinePanel] with 300x500 size. */
  fun createTestSlider(): TimelinePanel {
    val slider = TimelinePanel(testPreviewState()) {}
    slider.maximum = 100
    JPanel(BorderLayout()).apply {
      // Extra parent panel is required for slider to properly set all sizes.
      setSize(300, 500)
      add(slider, BorderLayout.CENTER)
    }
    return slider
  }

  fun createComposeAnimation(label: String? = null, type: ComposeAnimationType = ComposeAnimationType.ANIMATED_VALUE) =
    object : ComposeAnimation {
      override val animationObject = Any()
      override val type = type
      override val label = label
      override val states = setOf(Any())
    }

  fun assertBigger(minimumSize: Dimension, actualSize: Dimension) =
    assertTrue(minimumSize.width <= actualSize.width && minimumSize.height <= actualSize.height)
}
