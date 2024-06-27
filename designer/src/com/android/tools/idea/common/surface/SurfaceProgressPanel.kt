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
package com.android.tools.idea.common.surface

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.ui.JBColor
import com.intellij.util.ui.AsyncProcessIcon
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JPanel

/**
 * Panel which displays the progress icon. The progress icon can either be a large icon in the
 * center, when there is no rendering showing, or a small icon in the upper right corner when there
 * is a rendering. This is necessary because even though the progress icon looks good on some
 * renderings, depending on the layout theme colors it is invisible in other cases.
 */
class SurfaceProgressPanel(
  private val parentDisposable: Disposable,
  private val useSmallProgressIcon: () -> Boolean,
) : JPanel(BorderLayout()) {
  private var mySmallProgressIcon: AsyncProcessIcon? = null
  private var myLargeProgressIcon: AsyncProcessIcon? = null
  private var mySmall = false
  private var myProgressVisible = false

  init {
    isOpaque = false
    isVisible = false
  }

  /**
   * The "small" icon mode isn't just for the icon size; it's for the layout position too; see
   * [.doLayout]
   */
  private fun setSmallIcon(small: Boolean) {
    if (small != mySmall) {
      if (myProgressVisible && componentCount != 0) {
        val oldIcon = progressIcon
        oldIcon.suspend()
      }
      mySmall = true
      removeAll()
      val icon = progressIcon
      add(icon, BorderLayout.CENTER)
      if (myProgressVisible) {
        icon.isVisible = true
        icon.resume()
      }
    }
  }

  fun showProgressIcon() {
    if (!myProgressVisible) {
      setSmallIcon(useSmallProgressIcon())
      myProgressVisible = true
      isVisible = true
      val icon = progressIcon
      if (componentCount == 0) { // First time: haven't added icon yet?
        add(progressIcon, BorderLayout.CENTER)
      } else {
        icon.isVisible = true
      }
      icon.resume()
    }
  }

  fun hideProgressIcon() {
    if (myProgressVisible) {
      myProgressVisible = false
      isVisible = false
      val icon = progressIcon
      icon.isVisible = false
      icon.suspend()
    }
  }

  override fun doLayout() {
    super.doLayout()
    background = JBColor.RED // make this null instead?

    if (!myProgressVisible) {
      return
    }

    // Place the progress icon in the center if there's no rendering, and in the
    // upper right corner if there's a rendering. The reason for this is that the icon color
    // will depend on whether we're in a light or dark IDE theme, and depending on the rendering
    // in the layout it will be invisible. For example, in Darcula the icon is white, and if the
    // layout is rendering a white screen, the progress is invisible.
    val icon = progressIcon
    val size = icon.preferredSize
    if (mySmall) {
      icon.setBounds(width - size.width - 1, 1, size.width, size.height)
    } else {
      icon.setBounds(
        width / 2 - size.width / 2,
        height / 2 - size.height / 2,
        size.width,
        size.height,
      )
    }
  }

  override fun getPreferredSize(): Dimension {
    return progressIcon.preferredSize
  }

  private val progressIcon: AsyncProcessIcon
    get() = getProgressIcon(mySmall)

  private fun getProgressIcon(small: Boolean): AsyncProcessIcon {
    if (small) {
      if (mySmallProgressIcon == null) {
        mySmallProgressIcon =
          AsyncProcessIcon("Android layout rendering").also {
            Disposer.register(parentDisposable, it)
          }
      }
      return mySmallProgressIcon!!
    } else {
      if (myLargeProgressIcon == null) {
        myLargeProgressIcon =
          AsyncProcessIcon.Big("Android layout rendering").also {
            Disposer.register(parentDisposable, it)
          }
      }
      return myLargeProgressIcon!!
    }
  }
}
