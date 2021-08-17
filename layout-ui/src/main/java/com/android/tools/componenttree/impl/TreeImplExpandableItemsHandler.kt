/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.componenttree.impl

import com.intellij.openapi.util.Pair
import com.intellij.ui.TreeExpandableItemsHandler
import java.awt.Component
import java.awt.Point
import java.awt.Rectangle

/**
 * Custom [TreeExpandableItemsHandler] that takes badges into account.
 *
 * A row can be "expanded" in 2 different ways:
 * - with a popup using [TreeExpandableItemsHandler] if the text expands beyond the right edge of the tree viewport.
 * - by simply hiding the badges if the text expands beyond the badges but fits inside the tree viewport.
 * We hide the badges in both situations.
 */
class TreeImplExpandableItemsHandler(private val tree: TreeImpl): TreeExpandableItemsHandler(tree) {

  private var rowWithHiddenBadges: Int = -1
    set(value) {
      if (field != value) {
        // Repaint the rows when the field is changed, to show/hide badges.
        if (field != -1) {
          tree.repaintRow(field)
        }
        field = value
        tree.repaintRow(field)
      }
    }

  override fun getCellKeyForPoint(point: Point): Int? {
    // Do not expand a label if the mouse is hovering over a badge.
    // Instead allow the user to click on the badge.
    val row = (if (point.x < tree.computeBadgePosition()) super.getCellKeyForPoint(point) else null) ?: -1
    if (row < 0) {
      rowWithHiddenBadges = -1
    }
    return row
  }

  override fun getCellRendererAndBounds(row: Int): Pair<Component, Rectangle>? {
    val result = super.getCellRendererAndBounds(row)
    if (result == null) {
      rowWithHiddenBadges = -1
      return null
    }
    // The bounds computed by the super class will include space for the badges.
    // Remove that since we don't want the badges to be shown when a row is expanded.
    val rect = result.second
    rect.width -= tree.computeBadgesWidth()
    val badgePos = tree.computeBadgePosition()

    // If the resulting bounds yields past the start of the badges, mark this row as having hidden badges.
    rowWithHiddenBadges = if (rect.x + rect.width > badgePos) row else -1

    // If the resulting bounds yields past the visible area of the tree, the base class will show a popup.
    return result
  }

  override fun getExpandedItems(): Collection<Int> =
    if (rowWithHiddenBadges < 0) emptyList() else listOf(rowWithHiddenBadges)
}
