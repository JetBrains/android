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
package com.android.tools.idea.layoutinspector.model

import com.android.tools.idea.layoutinspector.pipeline.appinspection.view.ViewAndroidWindow
import java.awt.Polygon
import java.awt.Rectangle
import org.jetbrains.annotations.VisibleForTesting

/** Horizontal and vertical gap between windows */
@VisibleForTesting const val WINDOWS_GAP = 100

/**
 * Rearrange the coordinates of the [ViewNode]s inside each window so that windows are rendered on a
 * grid
 */
fun reLayoutWindowsForXr(writeAccess: ViewNode.WriteAccess, windows: List<AndroidWindow>) {
  val hasXr = windows.filterIsInstance<ViewAndroidWindow>().find { it.isXr } != null

  if (!hasXr) {
    return
  }

  var x = 0
  var y = 0
  var currentRowHeight = 0

  val largestWindowWidth = windows.maxOfOrNull { it.getMaxWidth() } ?: 0

  windows
    .sortedByDescending { it.getMaxWidth() }
    .forEach { window ->
      val winWidth = window.getMaxWidth()
      val winHeight = window.getMaxHeight()

      // Each row can only be as wide as the width of the largest window.
      if (x + winWidth > largestWindowWidth) {
        x = 0
        y += currentRowHeight + WINDOWS_GAP
        currentRowHeight = 0
      }

      window.root.setPosition(writeAccess, x, y)

      x += winWidth + WINDOWS_GAP

      // The height of the current row is the height of the tallest window.
      currentRowHeight = maxOf(currentRowHeight, winHeight)
    }
}

/**
 * Returns the max width of the window. It's not enough to just check the size of the root, since
 * children ViewNodes could be rendered outside the root bounds.
 */
private fun AndroidWindow.getMaxWidth(): Int {
  val minX = root.flattenedList().minOfOrNull { it.layoutBounds.x } ?: 0
  val maxX = root.flattenedList().maxOfOrNull { it.layoutBounds.x + it.layoutBounds.width } ?: 0
  return maxX - minX
}

/**
 * Returns the max height of the window. It's not enough to just check the size of the root, since
 * children ViewNodes could be rendered outside the root bounds.
 */
private fun AndroidWindow.getMaxHeight(): Int {
  val minY = root.flattenedList().minOfOrNull { it.layoutBounds.y } ?: 0
  val maxY = root.flattenedList().maxOfOrNull { it.layoutBounds.y + it.layoutBounds.height } ?: 0
  return maxY - minY
}

/**
 * Set the position of this view node to be [xCoord] and [yCoord]. Since [ViewNode] is a tree, all
 * the child nodes also need to be shifted accordingly.
 */
private fun ViewNode.setPosition(writeAccess: ViewNode.WriteAccess, xCoord: Int, yCoord: Int) {
  val xShift = xCoord - layoutBounds.x
  val yShift = yCoord - layoutBounds.y

  // TODO: i am not sure why it's necessary to divide by 2. Without it the layout of the windows is
  // broken.
  translate(writeAccess, xShift / 2, yShift / 2)
}

private fun ViewNode.translate(writeAccess: ViewNode.WriteAccess, dx: Int, dy: Int) {
  writeAccess.apply {
    layoutBounds.translate(dx, dy)
    if (renderBounds is Rectangle) {
      (renderBounds as Rectangle).translate(dx, dy)
    } else if (renderBounds is Polygon) {
      (renderBounds as Polygon).translate(dx, dy)
    }

    children.forEach { it.translate(writeAccess, dx, dy) }
  }
}
