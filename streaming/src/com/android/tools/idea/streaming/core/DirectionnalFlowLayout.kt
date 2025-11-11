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

import com.intellij.util.ui.AbstractLayoutManager
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.Point

/**
 * Lays out child components in a row or column depending on [direction] with the given [gap]
 * between them. In the orthogonal direction the child components occupy all available space of the
 * container.
 */
class DirectionalFlowLayout(val direction: Direction = Direction.LEFT_TO_RIGHT, val gap: Int = 0) : AbstractLayoutManager() {

  override fun layoutContainer(container: Container) {
    val insets = container.insets
    when (direction) {
      Direction.LEFT_TO_RIGHT -> {
        val offset = Point(insets.left, insets.top)
        for (child in container.components) {
          child.setBounds(offset.x, offset.y, child.preferredSize.width, container.height - insets.top - insets.bottom)
          offset.x += child.preferredSize.width + gap
        }
      }
      Direction.RIGHT_TO_LEFT -> {
        val offset = Point(container.width - insets.right, insets.top)
        for (child in container.components) {
          offset.x -= child.preferredSize.width
          child.setBounds(offset.x, offset.y, child.preferredSize.width, container.height - insets.top - insets.bottom)
          offset.x -= gap
        }
      }
      Direction.TOP_TO_BOTTOM -> {
        val offset = Point(insets.left, insets.top)
        for (child in container.components) {
          child.setBounds(offset.x, offset.y, container.width - insets.left - insets.right, child.preferredSize.height)
          offset.y += child.preferredSize.height + gap
        }
      }
      Direction.BOTTOM_TO_TOP -> {
        val offset = Point(insets.left, container.height - insets.bottom)
        for (child in container.components) {
          offset.y -= child.preferredSize.height
          child.setBounds(offset.x, offset.y, container.width - insets.left - insets.right, child.preferredSize.height)
          offset.y -= gap
        }
      }
    }
  }

  override fun minimumLayoutSize(container: Container): Dimension =
      combinedSize(container, Component::getMinimumSize)

  override fun preferredLayoutSize(container: Container): Dimension =
      combinedSize(container, Component::getPreferredSize)

  private fun combinedSize(container: Container, sizeProperty: (Component) -> Dimension): Dimension {
    val result = Dimension()
    val insets = container.insets
    when (direction) {
      Direction.LEFT_TO_RIGHT,
      Direction.RIGHT_TO_LEFT -> {
        for (child in container.components) {
          val childSize = sizeProperty(child)
          result.width += childSize.width + gap
          result.height = result.height.coerceAtLeast(childSize.height)
        }
        if (result.width > 0) {
          result.width -= gap
        }
        result.width += insets.left + insets.right
      }
      Direction.TOP_TO_BOTTOM,
      Direction.BOTTOM_TO_TOP -> {
        for (child in container.components) {
          val childSize = sizeProperty(child)
          result.width = result.width.coerceAtLeast(childSize.width)
          result.height += childSize.height + gap
        }
        if (result.height > 0) {
          result.height -= gap
        }
        result.height += insets.top + insets.bottom
      }
    }
    return result
  }

  enum class Direction {
    LEFT_TO_RIGHT, RIGHT_TO_LEFT, TOP_TO_BOTTOM, BOTTOM_TO_TOP
  }
}
