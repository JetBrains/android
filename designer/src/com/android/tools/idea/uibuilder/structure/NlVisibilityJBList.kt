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
package com.android.tools.idea.uibuilder.structure

import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.Disposable
import com.intellij.ui.CollectionListModel
import com.intellij.ui.components.JBList
import java.awt.Point
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent

class NlVisibilityJBList : JBList<ButtonPresentation>(), Disposable {
  var currHovered = -1
  var currClicked = -1
  private var currModel: NlVisibilityModel? = null

  private var popupMenu: NlVisibilityPopupMenu? = null

  @VisibleForTesting
  val mouseListener = object: MouseAdapter() {
    override fun mouseMoved(e: MouseEvent?) {
      updateCurrentlyHovered(e)
    }

    override fun mouseEntered(e: MouseEvent?) {
      updateCurrentlyHovered(e)
    }

    override fun mouseClicked(e: MouseEvent?) {
      if (e == null || e.button != MouseEvent.BUTTON1 || e.clickCount != 1) {
        return
      }
      val clickedIndex = locationToIndex(e.point)
      if (clickedIndex != -1 && clickedIndex < model.size) {
        onMouseClicked(e.point)
      }
    }

    override fun mouseExited(e: MouseEvent?) {
      currHovered = -1
      repaint()
    }
  }

  init {
    addMouseMotionListener(mouseListener)
    addMouseListener(mouseListener)
  }

  private fun updateCurrentlyHovered(e: MouseEvent?) {
    if (e == null || currClicked != -1) {
      return
    }

    currHovered = locationToIndex(e.point)
    repaint()
  }

  private fun onMouseClicked(p: Point) {
    val clickedIndex = locationToIndex(p)
    if (clickedIndex != -1 && clickedIndex < model.size) {
      currHovered = -1

      model.getElementAt(clickedIndex).model?.let {
        currClicked = clickedIndex
        currModel = it
        val y = currClicked * NlVisibilityButton.HEIGHT + NlVisibilityButton.HEIGHT/2
        val x = NlVisibilityButton.WIDTH
        val p = Point(x, y)

        if (popupMenu == null) {
          popupMenu = NlVisibilityPopupMenu(::popupOnClick, ::popupOnClose)
        }
        popupMenu!!.show(it, this, p)
      }
      repaint()
    }
  }

  private fun popupOnClick(visibility: NlVisibilityModel.Visibility, uri: String) {
    currModel?.writeToComponent(visibility, uri)
  }

  private fun popupOnClose() {
    currModel = null
    currClicked = -1
  }

  override fun dispose() {
    currHovered - 1
    currClicked - 1
    currModel = null
    for (i in 0 until model.size) {
      val item = model.getElementAt(i)
      item.model = null
    }
    // Apparently this doesn't clear the memory.
    model = CollectionListModel()
    if (popupMenu != null) {
      popupMenu!!.cancel()
      popupMenu = null
    }
  }
}