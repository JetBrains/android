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
package com.android.tools.idea.streaming.core

import com.android.tools.adtui.swing.FakeUi
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollBar
import com.intellij.ui.components.JBScrollPane
import java.awt.Adjustable
import java.awt.Dimension
import java.awt.Point
import javax.swing.BorderFactory
import javax.swing.JPanel
import javax.swing.JScrollBar
import javax.swing.JViewport
import javax.swing.border.Border
import javax.swing.plaf.ScrollBarUI
import org.junit.Rule
import org.junit.Test

/** Tests for [CenterAnchoredViewport]. */
@RunsInEdt
class CenterAnchoredViewportTest {

  @get:Rule val rule = EdtRule()

  private val view = JPanel().apply {
    background = JBColor.cyan
    border = BorderFactory.createLineBorder(JBColor.blue)
  }
  private val viewport = CenterAnchoredViewport().also { it.view = view }
  private val scrollPane = TestScrollPane().apply { setBounds(0, 0, 100, 160) }
  private val ui = FakeUi(scrollPane)

  @Test
  fun testZoomScrollAndResize() {
    assertThat(view.size).isEqualTo(Dimension(100, 160))
    assertThat(viewport.viewPosition).isEqualTo(Point(0, 0))

    // Zoom level 200%.
    view.preferredSize = Dimension(200, 400)
    ui.layoutAndDispatchEvents()
    assertThat(view.size).isEqualTo(Dimension(200, 400))
    assertThat(viewport.viewPosition).isEqualTo(Point(55, 125))

    // Zoom level 400%.
    view.preferredSize = Dimension(400, 800)
    ui.layoutAndDispatchEvents()
    assertThat(view.size).isEqualTo(Dimension(400, 800))
    assertThat(viewport.viewPosition).isEqualTo(Point(155, 325))

    // Scroll left and down.
    viewport.viewPosition = Point(50, 650)
    ui.layoutAndDispatchEvents()
    assertThat(viewport.viewPosition).isEqualTo(Point(50, 650))

    // Resize the scroll pane.
    scrollPane.size = Dimension(120, 140)
    ui.layoutAndDispatchEvents()
    assertThat(viewport.viewPosition).isEqualTo(Point(40, 660))

    // Zoom level 200%.
    view.preferredSize = Dimension(200, 400)
    ui.layoutAndDispatchEvents()
    assertThat(view.size).isEqualTo(Dimension(200, 400))
    assertThat(viewport.viewPosition).isEqualTo(Point(0, 270))

    // Zoom to fit.
    view.preferredSize = null
    ui.layoutAndDispatchEvents()
    assertThat(view.size).isEqualTo(Dimension(120, 140))
    assertThat(viewport.viewPosition).isEqualTo(Point(0, 0))
  }

  @Test
  fun testZoomInZoomOut() {
    scrollPane.size = Dimension(503, 1792)
    ui.layoutAndDispatchEvents()
    assertThat(viewport.viewPosition).isEqualTo(Point(0, 0))

    // Zoom level 50%.
    view.preferredSize = Dimension(704, 1487)
    ui.layoutAndDispatchEvents()
    assertThat(view.size).isEqualTo(Dimension(704, 1782))
    assertThat(viewport.viewPosition).isEqualTo(Point(101, 0))

    // Zoom level 100%.
    view.preferredSize = Dimension(1408, 2974)
    ui.layoutAndDispatchEvents()
    assertThat(view.size).isEqualTo(Dimension(1408, 2974))
    assertThat(viewport.viewPosition).isEqualTo(Point(459, 596))

    // Zoom level 50%.
    view.preferredSize = Dimension(704, 1487)
    ui.layoutAndDispatchEvents()
    assertThat(view.size).isEqualTo(Dimension(704, 1782))
    assertThat(viewport.viewPosition).isEqualTo(Point(101, 0))

    // Zoom to fit.
    view.preferredSize = null
    ui.layoutAndDispatchEvents()
    assertThat(view.size).isEqualTo(Dimension(503, 1792))
    assertThat(viewport.viewPosition).isEqualTo(Point(0, 0))
  }

  private inner class TestScrollPane : JBScrollPane(0) {

    init {
      setupCorners()
      verticalScrollBarPolicy = VERTICAL_SCROLLBAR_AS_NEEDED
      horizontalScrollBarPolicy = HORIZONTAL_SCROLLBAR_AS_NEEDED
      viewport.background = background
    }

    override fun createViewport(): JViewport = this@CenterAnchoredViewportTest.viewport

    override fun createVerticalScrollBar(): JScrollBar = TestScrollBar(Adjustable.VERTICAL)

    override fun createHorizontalScrollBar(): JScrollBar = TestScrollBar(Adjustable.HORIZONTAL)

    override fun setBorder(border: Border?) {
      // Don't allow borders to be set by the UI framework.
    }
  }

  private class TestScrollBar(orientation: Int) : JBScrollBar(orientation) {

    private var persistentUI: ScrollBarUI? = null

    init {
      isOpaque = false
    }

    override fun setUI(ui: ScrollBarUI) {
      if (persistentUI == null) {
        persistentUI = ui
      }
      super.setUI(persistentUI)
      isOpaque = false
    }

    override fun setVisible(isVisible: Boolean) {
      super.setVisible(isVisible)
      isOpaque = isVisible
    }

    override fun getUnitIncrement(direction: Int): Int = 5

    override fun getBlockIncrement(direction: Int): Int = 1
  }
}