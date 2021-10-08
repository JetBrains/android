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
package com.android.tools.adtui

import com.android.tools.adtui.stdui.CommonButton
import com.google.common.annotations.VisibleForTesting
import com.intellij.ui.components.JBViewport
import java.awt.BorderLayout
import java.awt.Point
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.AbstractButton
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.math.max
import kotlin.math.min

class HorizontalScrollView @JvmOverloads constructor(private val content: JComponent,
                                                     scrollSensitivity: Int = 30,
                                                     @VisibleForTesting val leftButton: AbstractButton = CommonButton("←"),
                                                     @VisibleForTesting val rightButton: AbstractButton = CommonButton("→"))
          : JPanel(BorderLayout()) {
  private val main = JBViewport().apply { view = content }
  private val xMax get() = max(0, content.width - main.width)

  init {
    leftButton.addActionListener { scrollBy(-scrollSensitivity) }
    rightButton.addActionListener { scrollBy(scrollSensitivity) }

    val listener = object : ComponentAdapter() {
      override fun componentResized(e: ComponentEvent) = refreshButtons()
    }
    content.addComponentListener(listener)
    main.addComponentListener(listener)
    main.addMouseWheelListener { scrollBy(it.scrollAmount * it.wheelRotation * scrollSensitivity / 2) }

    add(main, BorderLayout.CENTER)
    add(leftButton, BorderLayout.WEST)
    add(rightButton, BorderLayout.EAST)
    refreshButtons()
  }

  private fun clampX(x: Int) = min(xMax, max(0, x))
  private fun scrollBy(dx: Int) = scrollTo(main.viewPosition.x + dx)
  fun scrollTo(newX: Int) {
    main.viewPosition = with(main.viewPosition) { Point(clampX(newX), y) }
    refreshButtons()
  }

  private fun refreshButtons() {
    val contentTooWide = content.width >= main.width
    leftButton.isVisible = contentTooWide && main.viewPosition.x > 0
    rightButton.isVisible = contentTooWide && main.viewPosition.x < xMax
  }
}