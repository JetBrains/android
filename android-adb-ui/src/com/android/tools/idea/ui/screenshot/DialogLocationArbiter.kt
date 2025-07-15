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
package com.android.tools.idea.ui.screenshot

import com.android.annotations.concurrency.UiThread
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.containers.DisposableWrapperList
import java.awt.Dimension
import java.awt.GraphicsConfiguration
import java.awt.Point
import java.awt.Rectangle
import java.awt.Toolkit

/** Arbitration mechanism allowing multiple dialogs to avoid being displayed on top of each other. */
@UiThread
class DialogLocationArbiter {

  private val shownDialogs = DisposableWrapperList<DialogWrapper>()

  /** Returns a screen location for the given dialog, or null to use the default location */
  fun suggestLocation(dialog: DialogWrapper): Point? {
    val location = calculateSuggestedLocation(dialog.size)
    shownDialogs.add(dialog, dialog.disposable)
    return location
  }

  /** Has to be called when a dialog participating in location arbitration is shown. */
  fun dialogShown(dialog: DialogWrapper) {
    if (!shownDialogs.contains(dialog)) {
      shownDialogs.add(dialog, dialog.disposable)
    }
  }

  private fun calculateSuggestedLocation(size: Dimension): Point? {
    if (shownDialogs.isEmpty()) {
      return null
    }
    var location: Point? = null
    val sortedDialogs = ArrayList(shownDialogs)
    sortedDialogs.sortWith(compareBy<DialogWrapper> { it.location.y }.thenBy { it.location.x })
    for (dialog in sortedDialogs) {
      if (location == null || location == dialog.location) {
        location = calculateAdjustedLocation(dialog.location, size, dialog.window.graphicsConfiguration)
      }
    }
    return location
  }

  fun calculateAdjustedLocation(baseLocation: Point, size: Dimension, graphicsConfiguration: GraphicsConfiguration): Point {
    val bounds = graphicsConfiguration.adjustedBounds
    val offset = JBUIScale.scale(40)
    return Point((baseLocation.x + offset).coerceIn(bounds.x, bounds.x + bounds.width - size.width),
                 (baseLocation.y + offset).coerceIn(bounds.y, bounds.y + bounds.height - size.height))
  }

  private val GraphicsConfiguration.adjustedBounds: Rectangle
    get() {
      val bounds = bounds
      val insets = Toolkit.getDefaultToolkit().getScreenInsets(this)
      bounds.x += insets.left
      bounds.y += insets.top
      bounds.width -= insets.left + insets.right
      bounds.height -= insets.top + insets.bottom
      return bounds
    }
}
