/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.surface.layer

import com.android.tools.idea.common.error.Issue
import com.android.tools.idea.common.error.IssueListener
import com.android.tools.idea.common.model.Coordinates
import com.android.tools.idea.uibuilder.model.h
import com.android.tools.idea.uibuilder.model.w
import com.android.tools.idea.uibuilder.model.x
import com.android.tools.idea.uibuilder.model.y
import com.android.tools.idea.uibuilder.surface.ScreenView
import com.intellij.openapi.util.Disposer
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.util.ui.Animator
import com.intellij.util.ui.JBUI
import java.awt.Graphics2D

private const val ANIMATION_TIME_MILLIS = 350
private const val TOTAL_FRAMES = 20
private const val BORDER_WIDTH_MAX = 10
private const val BORDER_WIDTH_MIN = 1

private val borderColor = JBColor(0xffaf0f, 0xf2c55c)
private val transparentBorderColor = ColorUtil.withAlpha(borderColor, 0.0)

class UiCheckWarningLayer(screenView: ScreenView, shouldDisplay: () -> Boolean) :
  WarningLayer(screenView, shouldDisplay) {

  private var isAnimating = false
  private var borderWidth: Int = BORDER_WIDTH_MAX
  private val animator = BorderAnimator().apply { Disposer.register(this@UiCheckWarningLayer, { dispose() }) }

  private val borderUpdateListener =
    object : IssueListener {
      override fun onIssueSelected(issue: Issue?) {
        animator.reset()
        borderWidth = BORDER_WIDTH_MAX
        isAnimating = true
        animator.resume()
      }
    }

  init {
    screenView.surface.addIssueListener(borderUpdateListener)
  }

  override fun paint(gc: Graphics2D) {
    if (isAnimating) {
      val borderPainter =
        BorderPainter(
          JBUI.scale(borderWidth),
          borderColor,
          transparentBorderColor,
          useHighQuality = false,
        )
      componentsToHighlight.forEach {
        val swingX = Coordinates.getSwingX(screenView, it.x)
        val swingY = Coordinates.getSwingY(screenView, it.y)
        val swingWidth = Coordinates.getSwingDimension(screenView, it.w)
        val swingHeight = Coordinates.getSwingDimension(screenView, it.h)
        borderPainter.paint(gc, swingX, swingY, swingWidth, swingHeight)
      }
    } else {
      gc.color = borderColor
      componentsToHighlight.forEach {
        val swingX = Coordinates.getSwingX(screenView, it.x)
        val swingY = Coordinates.getSwingY(screenView, it.y)
        val swingWidth = Coordinates.getSwingDimension(screenView, it.w)
        val swingHeight = Coordinates.getSwingDimension(screenView, it.h)
        gc.drawRect(swingX, swingY, swingWidth, swingHeight)
      }
    }
  }

  override fun dispose() {
    screenView.surface.removeIssueListener(borderUpdateListener)
    super.dispose()
  }

  private inner class BorderAnimator :
    Animator("BorderAnimator", TOTAL_FRAMES, ANIMATION_TIME_MILLIS, false) {
    override fun paintNow(frame: Int, totalFrames: Int, cycle: Int) {
      borderWidth = BORDER_WIDTH_MAX - (BORDER_WIDTH_MAX - BORDER_WIDTH_MIN) * frame / totalFrames
      screenView.surface.repaint()
    }

    override fun paintCycleEnd() {
      isAnimating = false
      screenView.surface.repaint()
    }
  }
}
