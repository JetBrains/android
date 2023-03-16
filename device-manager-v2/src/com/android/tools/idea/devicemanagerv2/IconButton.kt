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
package com.android.tools.idea.devicemanagerv2

import com.android.tools.adtui.common.ColoredIconGenerator.generateColoredIcon
import com.intellij.util.ui.JBDimension
import java.awt.Color
import java.awt.Dimension
import javax.swing.Icon
import javax.swing.JButton

internal open class IconButton(initialBaseIcon: Icon) : JButton(initialBaseIcon) {
  var baseIcon = initialBaseIcon
  var iconColor: Color? = null
    set(value) {
      field = value
      icon = baseIcon.applyColor(value)
    }

  init {
    val size: Dimension = JBDimension(22, 22)
    maximumSize = size
    minimumSize = size
    preferredSize = size

    icon = baseIcon
  }

  override fun updateUI() {
    super.updateUI()
    // This is called when the component is created and when the theme is changed. If we wish
    // to override any properties set by the superclass, we must do so here, so that they persist
    // after theme changes.
    border = null
    isContentAreaFilled = false
    // This method gets called from the superclass constructor before the class is fully initialized
    // and the following will crash; only update on subsequent calls to updateUI.
    if (icon != null) {
      icon = baseIcon.applyColor(iconColor)
    }
  }
}

private fun Icon.applyColor(color: Color?): Icon =
  when (color) {
    null -> this
    else -> generateColoredIcon(this, color)
  }
