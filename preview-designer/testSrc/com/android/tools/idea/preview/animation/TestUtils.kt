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
package com.android.tools.idea.preview.animation

import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.stdui.TooltipLayeredPane
import com.android.tools.idea.preview.NoopAnimationTracker
import com.android.tools.idea.preview.animation.timeline.TimelineElement
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.ui.JBColor
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.Graphics2D
import java.awt.Point
import java.util.stream.Collectors
import javax.swing.JLabel
import javax.swing.JPanel
import org.junit.Assert

object TestUtils {
  private const val TEST_ELEMENT_WIDTH = 100
  private const val TEST_ELEMENT_HEIGHT = 10
  private const val TEST_ELEMENT_ROW_HEIGHT = 100

  fun TimelinePanel.scanForTooltips(): Set<TooltipInfo> =
    this.sliderUI.elements.flatMap { it.scanForTooltips(this.size) }.toSet()

  fun TimelineElement.scanForTooltips(dimension: Dimension): Set<TooltipInfo> {
    val set = mutableSetOf<TooltipInfo>()
    for (x in 0..dimension.width step 5) for (y in 0..dimension.height step 5) this.getTooltip(
        Point(x, y)
      )
      ?.let { set.add(it) }
    return set
  }

  /** Test [TimelineElement] with size [TEST_ELEMENT_WIDTH] x [TEST_ELEMENT_HEIGHT] */
  class TestTimelineElement(
    private val x: Int,
    private val y: Int,
    valueOffset: Int = 0,
    frozenState: SupportedAnimationManager.FrozenState =
      SupportedAnimationManager.FrozenState(false),
  ) : TimelineElement(valueOffset, frozenState, x, x + TEST_ELEMENT_WIDTH) {
    override fun contains(x: Int, y: Int): Boolean {
      return x in this.x + offsetPx..this.x + TEST_ELEMENT_WIDTH + offsetPx &&
        y in this.y..this.y + TEST_ELEMENT_HEIGHT
    }

    override var height = TEST_ELEMENT_ROW_HEIGHT

    override fun paint(g: Graphics2D) {
      g.fillRect(x + offsetPx, y, TEST_ELEMENT_WIDTH, TEST_ELEMENT_HEIGHT)
    }

    override fun getTooltip(point: Point): TooltipInfo? =
      if (contains(point)) TooltipInfo("$x", "$y") else null
  }

  /** Create [TimelinePanel] with 300x500 size. */
  fun createTestSlider(): TimelinePanel {
    val root = JPanel(BorderLayout())
    val slider = TimelinePanel(Tooltip(root, TooltipLayeredPane(root)), NoopAnimationTracker)
    slider.maximum = 100
    root.apply {
      // Extra parent panel is required for slider to properly set all sizes and to enable tooltips.
      setSize(300, 500)
      add(slider, BorderLayout.CENTER)
    }
    return slider
  }

  fun assertBigger(minimumSize: Dimension, actualSize: Dimension) =
    Assert.assertTrue(
      minimumSize.width <= actualSize.width && minimumSize.height <= actualSize.height
    )

  fun AnimationCard.findExpandButton(): Component {
    return (this.component.components[0] as Container).components[0]
  }

  fun Component.findToolbar(place: String): ActionToolbarImpl {
    return TreeWalker(this)
      .descendantStream()
      .filter { it is ActionToolbarImpl }
      .collect(Collectors.toList())
      .map { it as ActionToolbarImpl }
      .first { it.place == place }
  }

  fun createPlaybackPlaceHolder() =
    JLabel("Playback placeholder").apply { background = JBColor.blue }

  fun createTimelinePlaceHolder() =
    JLabel("Timeline placeholder").apply { background = JBColor.pink }

  fun findAllCards(parent: Component): List<Card> =
    TreeWalker(parent)
      .descendantStream()
      .filter { it is Card }
      .collect(Collectors.toList())
      .map { it as Card }
}
