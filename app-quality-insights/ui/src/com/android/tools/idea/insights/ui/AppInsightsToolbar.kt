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
package com.android.tools.idea.insights.ui

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import java.awt.Rectangle

/**
 * An implementation of [ActionToolbarImpl] customized to the UI needs of AQI.
 *
 * The height of the toolbar is customized to match the other toolbars in AQI.
 */
class AppInsightsToolbar(place: String, group: ActionGroup, horizontal: Boolean) :
  ActionToolbarImpl(place, group, horizontal, false, true) {
  init {
    layoutStrategy = InsightsToolbarLayoutStrategy(ToolbarLayoutStrategy.NOWRAP_STRATEGY)
  }

  override fun getSeparatorHeight() = JBUI.scale(31)
}

private class InsightsToolbarLayoutStrategy(private val delegate: ToolbarLayoutStrategy) :
  ToolbarLayoutStrategy by delegate {
  override fun calcPreferredSize(toolbar: ActionToolbar): Dimension {
    val actualDimension = delegate.calcPreferredSize(toolbar)
    val scaledHeight = commonToolbarHeight()
    if (actualDimension.height > scaledHeight) {
      return actualDimension
    }
    return Dimension(actualDimension.width, scaledHeight)
  }

  override fun calculateBounds(toolbar: ActionToolbar): List<Rectangle> {
    val values = delegate.calculateBounds(toolbar)

    return values.map { rectangle ->
      val scaledHeight = commonToolbarHeight()
      val newY =
        if (rectangle.height < scaledHeight) {
          (scaledHeight - rectangle.height) / 2
        } else rectangle.y
      Rectangle(rectangle.x, newY, rectangle.width, rectangle.height)
    }
  }
}
