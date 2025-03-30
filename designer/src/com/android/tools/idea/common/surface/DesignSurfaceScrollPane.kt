/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.common.surface

import com.intellij.openapi.wm.IdeGlassPane
import com.intellij.ui.components.JBScrollBar
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import org.intellij.lang.annotations.JdkConstants
import java.awt.Adjustable
import java.awt.Color
import java.awt.event.AdjustmentListener
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JScrollBar
import javax.swing.JScrollPane
import javax.swing.plaf.ScrollBarUI

class DesignSurfaceScrollPane private constructor() : JBScrollPane(0) {
  private class MyScrollBar(@JdkConstants.AdjustableOrientation orientation: Int) :
    JBScrollBar(orientation), IdeGlassPane.TopComponent {
    private var myPersistentUI: ScrollBarUI? = null

    override fun canBePreprocessed(e: MouseEvent): Boolean {
      return canBePreprocessed(e, this)
    }

    override fun setUI(ui: ScrollBarUI) {
      if (myPersistentUI == null) myPersistentUI = ui
      super.setUI(myPersistentUI)
      isOpaque = false
    }

    override fun getUnitIncrement(direction: Int): Int = 20

    override fun getBlockIncrement(direction: Int): Int = 1

    init {
      isOpaque = false
    }
  }

  override fun createVerticalScrollBar(): JScrollBar = MyScrollBar(Adjustable.VERTICAL)

  override fun createHorizontalScrollBar(): JScrollBar = MyScrollBar(Adjustable.HORIZONTAL)

  init {
    setupCorners()
  }

  companion object {
    /**
     * Returns a [JScrollPane] containing the given content and with the given background color.
     *
     * @param content the scrollable content.
     * @param background the scroll surface background.
     * @param onPanningChanged callback when the scrollable area changes size.
     */
    @JvmStatic
    fun createDefaultScrollPane(
      content: JComponent,
      background: Color,
      onPanningChanged: AdjustmentListener,
    ): JScrollPane =
      DesignSurfaceScrollPane().apply {
        setViewportView(content)
        border = JBUI.Borders.empty()
        viewport.background = background
        verticalScrollBarPolicy = VERTICAL_SCROLLBAR_ALWAYS
        horizontalScrollBarPolicy = HORIZONTAL_SCROLLBAR_ALWAYS
        horizontalScrollBar.addAdjustmentListener(onPanningChanged)
        verticalScrollBar.addAdjustmentListener(onPanningChanged)
      }
  }
}
